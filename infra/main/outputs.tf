output "function_url" {
  description = "Public HTTPS URL of the deployed app"
  value       = aws_lambda_function_url.app.function_url
}

output "ecr_repository_url" {
  description = "Push images here before the first `terraform apply` that creates the Lambda function"
  value       = aws_ecr_repository.app.repository_url
}

output "dynamodb_table_name" {
  value = aws_dynamodb_table.app.name
}

output "lambda_function_name" {
  value = aws_lambda_function.app.function_name
}

output "eventbridge_schedule_name" {
  description = "The cron rule that polls the daily mBank statement - see CLAUDE.md for why this exists instead of an in-app @Scheduled job"
  value       = aws_scheduler_schedule.bankstatement_poll.name
}

output "cognito_hosted_ui_domain" {
  description = "Hosted UI base domain - full login URL is https://<this>/login?client_id=<cognito_spa_client_id>&response_type=code&redirect_uri=<function_url>"
  value       = "${aws_cognito_user_pool_domain.app.domain}.auth.${var.region}.amazoncognito.com"
}

output "cognito_user_pool_id" {
  value = aws_cognito_user_pool.app.id
}

output "cognito_spa_client_id" {
  value = aws_cognito_user_pool_client.spa.id
}
