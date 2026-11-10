output "ecr_repository_url" {
  description = "Where the deploy workflow pushes the inference image."
  value       = aws_ecr_repository.inference.repository_url
}

output "inference_url" {
  description = "Function URL (AWS_IAM auth). Empty until an image has been deployed."
  value       = try(aws_lambda_function_url.inference[0].function_url, "")
}
