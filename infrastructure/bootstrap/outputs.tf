output "state_bucket" {
  value = aws_s3_bucket.tf_state.bucket
}

output "ota_publish_role_arn" {
  description = "Set as GitHub variable AWS_OTA_PUBLISH_ROLE_ARN."
  value       = aws_iam_role.ota_publish.arn
}

output "backend_deploy_role_arn" {
  description = "Set as GitHub variable AWS_BACKEND_DEPLOY_ROLE_ARN."
  value       = aws_iam_role.backend_deploy.arn
}

output "plan_role_arn" {
  description = "Set as GitHub variable AWS_PLAN_ROLE_ARN."
  value       = aws_iam_role.plan_readonly.arn
}
