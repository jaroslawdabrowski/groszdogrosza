#!/usr/bin/env bash
# Pushes the locally built Lambda image to ECR and applies the Terraform stack.
#
# TODO: this is a scaffolding-phase skeleton, not yet run end-to-end against real AWS.
#
# First-time setup (once per AWS account):
#   1. cd infra/bootstrap && terraform init && terraform apply
#   2. cd infra/main && terraform init -backend-config="bucket=<state bucket from step 1>" \
#        -backend-config="dynamodb_table=<lock table from step 1>" -backend-config="region=eu-central-1"
#   3. Set the real secrets (never commit them): copy terraform.tfvars.example to
#      terraform.tfvars and fill in bankstatement_poll_secret / bankstatement_imap_username /
#      bankstatement_imap_app_password (see CLAUDE.md for how to create a Gmail App Password),
#      or export the equivalent TF_VAR_* env vars instead.
#   4. terraform apply -target=aws_ecr_repository.app   (creates just the ECR repo)
#   5. Run this script.
#   6. Register the real Function URL as a Cognito callback/logout URL - can't be done in
#      Terraform itself (would create a dependency cycle, see the comment on
#      aws_cognito_user_pool_client.spa in main.tf):
#        POOL_ID=$(terraform output -raw cognito_user_pool_id)
#        CLIENT_ID=$(terraform output -raw cognito_spa_client_id)
#        FUNCTION_URL=$(terraform output -raw function_url)
#        aws cognito-idp update-user-pool-client --user-pool-id "$POOL_ID" --client-id "$CLIENT_ID" \
#          --callback-urls "$FUNCTION_URL" "http://localhost:4200/" "http://localhost:8080/" \
#          --logout-urls "$FUNCTION_URL" "http://localhost:4200/" "http://localhost:8080/" \
#          --allowed-o-auth-flows code --allowed-o-auth-scopes openid \
#          --allowed-o-auth-flows-user-pool-client --supported-identity-providers COGNITO
#   7. Create the treasurer's account (self-service sign-up is disabled - see main.tf):
#        aws cognito-idp admin-create-user --user-pool-id "$POOL_ID" --username you@example.com \
#          --user-attributes Name=email,Value=you@example.com Name=email_verified,Value=true
#        aws cognito-idp admin-set-user-password --user-pool-id "$POOL_ID" --username you@example.com \
#          --password '<temp-password>' --permanent
#
# Every subsequent deploy: scripts/build.sh && scripts/deploy.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA_DIR="$REPO_ROOT/infra/main"

IMAGE_NAME="${IMAGE_NAME:-groszdogrosza}"
AWS_REGION="${AWS_REGION:-eu-central-1}"

# Match whatever unique tag scripts/build.sh generated (see its comment for why
# reusing a fixed tag like "latest" across deploys silently skips the Lambda update).
IMAGE_TAG="${IMAGE_TAG:-}"
if [ -z "$IMAGE_TAG" ]; then
  if [ -f "$REPO_ROOT/target/.image-tag" ]; then
    IMAGE_TAG="$(cat "$REPO_ROOT/target/.image-tag")"
  else
    echo "No IMAGE_TAG set and target/.image-tag not found - run scripts/build.sh first." >&2
    exit 1
  fi
fi

cd "$INFRA_DIR"
ECR_REPOSITORY_URL="$(terraform output -raw ecr_repository_url)"

echo "==> Logging in to ECR: ${ECR_REPOSITORY_URL}"
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_REPOSITORY_URL"

echo "==> Tagging and pushing ${IMAGE_NAME}:${IMAGE_TAG} to ${ECR_REPOSITORY_URL}:${IMAGE_TAG}"
docker tag "${IMAGE_NAME}:${IMAGE_TAG}" "${ECR_REPOSITORY_URL}:${IMAGE_TAG}"
docker push "${ECR_REPOSITORY_URL}:${IMAGE_TAG}"

echo "==> Applying Terraform (creates/updates the Lambda function to use the new image)"
terraform plan -input=false -var="image_tag=${IMAGE_TAG}" -out=.deploy.tfplan
terraform apply -input=false .deploy.tfplan
rm -f .deploy.tfplan

echo "==> Deployed. Function URL:"
terraform output -raw function_url
echo
