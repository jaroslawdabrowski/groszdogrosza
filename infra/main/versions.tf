terraform {
  required_version = ">= 1.9"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Partial config: fill in bucket/dynamodb_table/region with the outputs
  # from infra/bootstrap, e.g.:
  #   terraform init -backend-config="bucket=groszdogrosza-terraform-state" \
  #                   -backend-config="dynamodb_table=groszdogrosza-terraform-lock" \
  #                   -backend-config="region=eu-central-1"
  backend "s3" {
    key     = "groszdogrosza/main.tfstate"
    encrypt = true
  }
}

provider "aws" {
  region = var.region
}
