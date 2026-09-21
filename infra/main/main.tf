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

# --- S3: collection attachments (photos/receipts documenting what money was spent on) ---
# See CLAUDE.md, "Collection attachments" - the browser uploads/downloads directly against
# presigned URLs, the Lambda never sees the file bytes, so this bucket only needs CORS for
# the app's own origin plus the usual private-bucket lockdown.
resource "aws_s3_bucket" "attachments" {
  bucket = "${local.name}-attachments"
}

resource "aws_s3_bucket_public_access_block" "attachments" {
  bucket = aws_s3_bucket.attachments.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_cors_configuration" "attachments" {
  bucket = aws_s3_bucket.attachments.id

  cors_rule {
    allowed_methods = ["GET", "PUT"]
    # The Function URL is the app's one and only origin (frontend + backend served from the
    # same Lambda, see Quinoa above) - no separate SPA domain to also allow.
    allowed_origins = [trimsuffix(aws_lambda_function_url.app.function_url, "/")]
    allowed_headers = ["*"]
    max_age_seconds = 3000
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

    # Codifies a manual Console edit made 2026-09-20, after at least two parents reported
    # "Incorrect username or password" on a temp password that looked right when copy-pasted
    # from the invite email. The original/default template ended the sentence with "{####}."
    # - a trailing period directly after the password placeholder, no space before it - the
    # working theory is that copy-pasting the whole sentence grabbed that period along with
    # the password. Fixed by dropping the trailing period. Declared here now purely so
    # `terraform apply` doesn't silently revert this fix on the next deploy - Terraform had
    # no opinion on this block before, so it just saw a real AWS value with nothing in config
    # to match and planned to delete it back to Cognito's own generic default text.
    invite_message_template {
      email_subject = "Twoje tymczasowe hasło do aplikacji Grosz do Grosza"
      email_message = "Twoja nazwa użytkownika {username} twoje tymczasowe hasło: {####}"
      sms_message   = "Your username is {username} and temporary password is {####}."
    }
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

  # Bumped from 60 minutes to the maximum Cognito allows (24h) 2026-09-21, after the
  # treasurer noticed the short TTL forcing frequent re-logins - the app's own bearer
  # credential is actually the ID token, not the access token (see auth.interceptor.ts for
  # why), so both are extended together; the access token itself isn't currently used for
  # anything, but there's no reason to leave it short while everything else is long.
  token_validity_units {
    access_token  = "hours"
    id_token      = "hours"
    refresh_token = "days"
  }
  access_token_validity  = 24
  id_token_validity      = 24
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
      # NOT CreateTable/DescribeTable - DynamoDbTableInitializer (the class that would
      # want it) is @IfBuildProfile("dev") only and doesn't exist in the packaged Lambda
      # at all; Terraform (aws_dynamodb_table.app above) is the only thing that manages
      # this table's lifecycle in prod. Granting CreateTable here would just be an unused
      # privilege.
      Action = [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:DeleteItem",
        "dynamodb:Query",
        "dynamodb:Scan",
      ]
      Resource = [
        aws_dynamodb_table.app.arn,
        "${aws_dynamodb_table.app.arn}/index/*",
      ]
    }]
  })
}

resource "aws_iam_role_policy" "attachments_access" {
  name = "${local.name}-attachments-access"
  role = aws_iam_role.lambda_exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      # No ListBucket/CreateBucket - the app only ever addresses objects it already knows
      # the exact key of (see AttachmentPolicy/S3AttachmentStorageAdapter), and Terraform
      # owns the bucket's own lifecycle, same reasoning as dynamodb_access above.
      # HeadObject (used to confirm an upload actually landed, see
      # ConfirmAttachmentUploadUseCase) is authorized under s3:GetObject - there's no
      # separate IAM action for it.
      Action   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"]
      Resource = ["${aws_s3_bucket.attachments.arn}/*"]
    }]
  })
}

resource "aws_iam_role_policy" "cognito_admin_access" {
  name = "${local.name}-cognito-admin-access"
  role = aws_iam_role.lambda_exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      # Only AdminCreateUser is needed - both "create account" and "resend invitation" in
      # the app are the same underlying call (MessageAction default vs RESEND). Scoped to
      # this one user pool, not "*".
      Action   = ["cognito-idp:AdminCreateUser"]
      Resource = [aws_cognito_user_pool.app.arn]
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
      QUARKUS_S3_AWS_REGION        = var.region

      # Collection attachments bucket - see CLAUDE.md, "Collection attachments" and
      # S3AttachmentStorageAdapter. Must match aws_s3_bucket.attachments.bucket; without
      # this the app falls back to application.properties' literal "groszdogrosza-attachments"
      # default, which happens to match here too (both derive from the same
      # "${local.name}-attachments" naming) but is set explicitly anyway, same reasoning as
      # GROSZDOGROSZA_DYNAMODB_TABLE_NAME below - never rely on a default staying in sync
      # with a Terraform-computed name by coincidence.
      GROSZDOGROSZA_ATTACHMENTS_BUCKET_NAME = aws_s3_bucket.attachments.bucket

      # Without this, the app falls back to application.properties' literal
      # "groszdogrosza" default (correct for local dev, where DynamoDbTableInitializer
      # creates a table by that exact name) - but the real table Terraform creates is
      # named "${local.name}" (e.g. "groszdogrosza-prod"), so every DynamoDB call would
      # 403 with "no identity-based policy allows ... on resource ... table/groszdogrosza"
      # (the IAM policy correctly scopes access to the REAL table name, which just never
      # gets requested). Confirmed the hard way against the real deployment.
      GROSZDOGROSZA_DYNAMODB_TABLE_NAME = aws_dynamodb_table.app.name

      # Lets the treasurer create/resend a parent's login account from the app itself - see
      # parent.adapter.out.cognito.CognitoAccountManagementAdapter.
      GROSZDOGROSZA_COGNITO_USER_POOL_ID = aws_cognito_user_pool.app.id

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
  principal              = "*"
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
  name = "${local.name}-bankstatement-poll"
  # BUG, found 2026-09-20 by checking CloudWatch Logs for the first time after wiring up
  # real IMAP credentials: this pointed at the bare Function URL root ("/") instead of the
  # actual poll path, so EVERY scheduled invocation since this was first deployed 404'd
  # before ever reaching BankStatementPollResource - confirmed by a clean absence of even
  # the "Rejected bankstatement poll request" log line (that guard never ran either) and a
  # direct `curl -X POST` against the bare Function URL returning 404. The daily poll has
  # never actually fetched mail, not once, until this was fixed.
  invocation_endpoint              = "${trimsuffix(aws_lambda_function_url.app.function_url, "/")}/internal/bankstatement/poll"
  http_method                      = "POST"
  invocation_rate_limit_per_second = 1
  connection_arn                   = aws_cloudwatch_event_connection.bankstatement_poll.arn
}

resource "aws_iam_role" "scheduler_exec" {
  name = "${local.name}-scheduler-exec"

  # Confirmed against real AWS (not a guess): EventBridge *Scheduler* (aws_scheduler_schedule)
  # does NOT support an API destination as a target - CreateSchedule rejects the API
  # destination's ARN with "Provided Arn is not in correct format" (Scheduler's target
  # types are Lambda/SQS/SNS/StepFunctions/ECS/an event bus/a fixed set of "universal"
  # aws-sdk targets, and a raw "arn:...:api-destination/..." isn't one of them). API
  # destinations as a scheduled target is specifically a classic EventBridge *Rule* feature
  # (aws_cloudwatch_event_rule + aws_cloudwatch_event_target's http_target), a different,
  # older service under the same "EventBridge" umbrella name - hence the assumed principal
  # here is events.amazonaws.com, not scheduler.amazonaws.com, despite the resource's name
  # (kept as "scheduler_exec" to avoid a bigger rename; it's really the event rule's role).
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "events.amazonaws.com" }
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

# Fires once a day at 9:00 Warsaw local time (the user's chosen time, confirmed while
# testing the real Gmail IMAP pipeline for the first time). Classic aws_cloudwatch_event_rule
# cron is always UTC with no timezone parameter (that's a aws_scheduler_schedule-only
# feature, and Scheduler can't target an API destination - see the comment on the
# aws_cloudwatch_event_connection above), so this is hardcoded to 7:00 UTC = 9:00 CEST and
# will need bumping to 8:00 UTC when Poland switches back to CET in late October, and again
# each spring/autumn after that.
resource "aws_cloudwatch_event_rule" "bankstatement_poll" {
  name                = "${local.name}-bankstatement-poll"
  schedule_expression = "cron(0 7 * * ? *)"
}

resource "aws_cloudwatch_event_target" "bankstatement_poll" {
  rule      = aws_cloudwatch_event_rule.bankstatement_poll.name
  target_id = "bankstatement-poll"
  arn       = aws_cloudwatch_event_api_destination.bankstatement_poll.arn
  role_arn  = aws_iam_role.scheduler_exec.arn
}
