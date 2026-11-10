output "api_url" {
  description = "Base URL for API_BASE_URL in the app's local.properties / APP_CONFIG_PROPERTIES secret."
  value       = aws_apigatewayv2_api.main.api_endpoint
}

output "ota_bucket" {
  description = "Set as GitHub variable KHOJO_OTA_BUCKET."
  value       = aws_s3_bucket.ota.bucket
}
