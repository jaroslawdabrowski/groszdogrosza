variable "region" {
  description = "AWS region for all groszdogrosza infrastructure"
  type        = string
  default     = "eu-central-1"
}

variable "project" {
  description = "Short project name, used to prefix resource names"
  type        = string
  default     = "groszdogrosza"
}

variable "environment" {
  description = "Deployment environment name (single environment for now: prod - this is a real, single-instance app for one class, not multi-tenant)"
  type        = string
  default     = "prod"
}

variable "image_tag" {
  description = "Tag of the Lambda container image in ECR to deploy (see scripts/deploy.sh)"
  type        = string
  default     = "latest"
}

variable "lambda_memory_mb" {
  description = "Lambda memory allocation in MB (also determines proportional CPU)"
  type        = number
  default     = 512
}

variable "lambda_timeout_seconds" {
  description = "Lambda invocation timeout in seconds"
  type        = number
  default     = 15
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention for the Lambda function's log group"
  type        = number
  default     = 14
}

variable "bankstatement_poll_secret" {
  description = "Shared secret EventBridge Scheduler sends as the X-Poll-Secret header to POST /internal/bankstatement/poll. Set via TF_VAR_bankstatement_poll_secret, never committed."
  type        = string
  sensitive   = true
  default     = ""
}

variable "bankstatement_imap_username" {
  description = "Gmail address the mBank statement mail arrives at. Set via TF_VAR_bankstatement_imap_username."
  type        = string
  default     = ""
}

variable "bankstatement_imap_app_password" {
  description = "Gmail App Password for IMAP access (NOT the Google account password - see CLAUDE.md). Set via TF_VAR_bankstatement_imap_app_password, never committed."
  type        = string
  sensitive   = true
  default     = ""
}
