locals {
  name = "${var.project}-${var.environment}"
}

# --- ECR: holds the Quarkus Lambda container image ---
# Create this first (`terraform apply -target=aws_ecr_repository.app`), push an
# image with scripts/build.sh + scripts/deploy.sh, then apply everything else -
# aws_lambda_function.app below fails to create if the image doesn't exist yet.
resource "aws_ecr_repository" "app" {
  name                 = local.name
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep only the 10 most recent images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

# --- DynamoDB: single on-demand table, composite key (pk/sk) ---
# See DynamoDbTableInitializer.java for the key layout this app writes - this resource
# just has to match its pk/sk attribute names and types, DynamoDB itself doesn't need to
# know about the item shapes on top. PAY_PER_REQUEST because this app's traffic (one
# class, a handful of collections a year) is nowhere near enough to justify provisioned
# capacity - same reasoning as the sibling "turboorders" project.
resource "aws_dynamodb_table" "app" {
  name         = local.name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "pk"
  range_key    = "sk"

  attribute {
    name = "pk"
    type = "S"
  }
  attribute {
    name = "sk"
    type = "S"
  }
}

# --- Cognito: OIDC identity provider ---
# Admin-created accounts only (no self-service sign-up) - @Authenticated alone doesn't
# restrict *which* Cognito users can log in, and this app has no other allowlist. Create
# the treasurer's account after this exists via `aws cognito-idp admin-create-user` (see
# scripts/deploy.sh) - not Terraform-managed, to avoid putting a real password in state.
resource "aws_cognito_user_pool" "app" {
  name                     = local.name
  auto_verified_attributes = ["email"]
  username_attributes      = ["email"]

  admin_create_user_config {
    allow_admin_create_user_only = true
  }

  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_numbers   = true
    require_symbols   = false
    require_uppercase = true
  }
}

resource "aws_cognito_user_pool_client" "spa" {
  name         = "${local.name}-spa"
  user_pool_id = aws_cognito_user_pool.app.id

  generate_secret                      = false # public SPA client, PKCE only - no secret to leak
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid"]
  supported_identity_providers         = ["COGNITO"]

  # Just localhost here, deliberately - aws_lambda_function_url.app.function_url can't be
  # referenced here without creating a dependency cycle (this client's id feeds the
  # Lambda's own env vars, which the function URL resource then depends on). Add the real
  # Function URL to both lists as a one-time manual step after first creating this stack
  # (see scripts/deploy.sh for the exact `aws cognito-idp update-user-pool-client`
  # command) - ignore_changes below means Terraform won't revert it.
  callback_urls = [
    "http://localhost:4200/",
    "http://localhost:8080/",
  ]
  logout_urls = [
    "http://localhost:4200/",
    "http://localhost:8080/",
  ]

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }
  access_token_validity  = 60
  id_token_validity      = 60
  refresh_token_validity = 30

  lifecycle {
    ignore_changes = [callback_urls, logout_urls]
  }
}

resource "aws_cognito_user_pool_domain" "app" {
  domain       = local.name # -> groszdogrosza-prod.auth.eu-central-1.amazoncognito.com
  user_pool_id = aws_cognito_user_pool.app.id
}

# --- IAM: least-privilege Lambda execution role ---
resource "aws_iam_role" "lambda_exec" {
  name = "${local.name}-lambda-exec"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "dynamodb_access" {
  name = "${local.name}-dynamodb-access"
  role = aws_iam_role.lambda_exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:DeleteItem",
        "dynamodb:Query",
        "dynamodb:Scan",
        "dynamodb:CreateTable", # DynamoDbTableInitializer creates the table on first boot if missing
        "dynamodb:DescribeTable",
      ]
      Resource = [
        aws_dynamodb_table.app.arn,
        "${aws_dynamodb_table.app.arn}/index/*",
      ]
    }]
  })
}

# --- Lambda: Quarkus app as a container image, exposed via a Function URL ---
resource "aws_cloudwatch_log_group" "app" {
  name              = "/aws/lambda/${local.name}"
  retention_in_days = var.log_retention_days
}

resource "aws_lambda_function" "app" {
  function_name = local.name
  role          = aws_iam_role.lambda_exec.arn
  package_type  = "Image"
  image_uri     = "${aws_ecr_repository.app.repository_url}:${var.image_tag}"
  timeout       = var.lambda_timeout_seconds
  memory_size   = var.lambda_memory_mb

  environment {
    variables = {
      # Issuer is the pool's own cognito-idp URL, not the Hosted UI domain - that domain
      # is only for the browser-facing authorize/token/logout endpoints, which
      # angular-oauth2-oidc's OIDC discovery resolves automatically from this issuer.
      QUARKUS_OIDC_AUTH_SERVER_URL = "https://cognito-idp.${var.region}.amazonaws.com/${aws_cognito_user_pool.app.id}"
      QUARKUS_OIDC_CLIENT_ID       = aws_cognito_user_pool_client.spa.id
      QUARKUS_DYNAMODB_AWS_REGION  = var.region

      # Bank statement automation - see CLAUDE.md for why IMAP+App Password and why
      # EventBridge Scheduler (not @Scheduled) drives the poll in Lambda.
      GROSZDOGROSZA_BANKSTATEMENT_POLL_SECRET       = var.bankstatement_poll_secret
      GROSZDOGROSZA_BANKSTATEMENT_IMAP_USERNAME     = var.bankstatement_imap_username
      GROSZDOGROSZA_BANKSTATEMENT_IMAP_APP_PASSWORD = var.bankstatement_imap_app_password
    }
  }

  depends_on = [aws_cloudwatch_log_group.app, aws_iam_role_policy_attachment.lambda_basic_execution]
}

resource "aws_lambda_function_url" "app" {
  function_name      = aws_lambda_function.app.function_name
  authorization_type = "NONE" # AWS-layer auth is off; Cognito (browser) / poll secret (EventBridge) are enforced inside the app
}

resource "aws_lambda_permission" "public_url" {
  statement_id           = "AllowPublicFunctionUrlInvoke"
  action                 = "lambda:InvokeFunctionUrl"
  function_name          = aws_lambda_function.app.function_name
  principal               = "*"
  function_url_auth_type = "NONE"
}

# AWS requires BOTH InvokeFunctionUrl and InvokeFunction on the resource policy for a
# NONE-auth Function URL to work (confirmed on the sibling "turboorders" project's stack,
# same AWS-wide requirement since Oct 2025) - see its CLAUDE.md for the full explanation of
# why this is broader than AWS's own recommended (currently unavailable in this provider
# version) scoped condition.
resource "aws_lambda_permission" "public_invoke" {
  statement_id  = "AllowPublicInvokeFunction"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.app.function_name
  principal     = "*"
}

# --- EventBridge: daily poll trigger for /internal/bankstatement/poll ---
# A Lambda Function URL invocation is a plain HTTPS call, not the Lambda Invoke API, so a
# direct "invoke this Lambda" EventBridge Scheduler target (which uses the Invoke API and
# can't attach a custom header) doesn't work here - the poll endpoint's only auth is the
# X-Poll-Secret header (see BankStatementPollResource.java). An EventBridge "API
# destination" is what actually lets a scheduled rule call an arbitrary HTTPS endpoint
# with a custom header baked into a reusable "connection".
resource "aws_cloudwatch_event_connection" "bankstatement_poll" {
  name               = "${local.name}-bankstatement-poll"
  authorization_type = "API_KEY"

  auth_parameters {
    api_key {
      key   = "X-Poll-Secret"
      value = var.bankstatement_poll_secret
    }
  }
}

resource "aws_cloudwatch_event_api_destination" "bankstatement_poll" {
  name                             = "${local.name}-bankstatement-poll"
  invocation_endpoint               = aws_lambda_function_url.app.function_url
  http_method                      = "POST"
  invocation_rate_limit_per_second = 1
  connection_arn                    = aws_cloudwatch_event_connection.bankstatement_poll.arn
}

resource "aws_iam_role" "scheduler_exec" {
  name = "${local.name}-scheduler-exec"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "scheduler_invoke_api_destination" {
  name = "${local.name}-scheduler-invoke"
  role = aws_iam_role.scheduler_exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "events:InvokeApiDestination"
      Resource = aws_cloudwatch_event_api_destination.bankstatement_poll.arn
    }]
  })
}

# Fires once a day, a bit after the mBank statement mail typically arrives ("rano" per the
# user - 6:00 UTC = 7:00/8:00 Warsaw local time depending on DST). Adjust to taste once
# real mail delivery timing is confirmed.
resource "aws_scheduler_schedule" "bankstatement_poll" {
  name       = "${local.name}-bankstatement-poll"
  group_name = "default"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression          = "cron(0 6 * * ? *)"
  schedule_expression_timezone = "UTC"

  target {
    arn      = aws_cloudwatch_event_api_destination.bankstatement_poll.arn
    role_arn = aws_iam_role.scheduler_exec.arn
  }
}
