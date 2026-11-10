data "aws_caller_identity" "current" {}

locals {
  account_id  = data.aws_caller_identity.current.account_id
  ota_bucket  = "khojo-app-updates-${local.account_id}"
  lambda_name = "khojo-updates"
}

# ---------- Private bucket holding the APKs and latest.json ----------

resource "aws_s3_bucket" "ota" {
  bucket = local.ota_bucket
}

resource "aws_s3_bucket_public_access_block" "ota" {
  bucket                  = aws_s3_bucket.ota.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "ota" {
  bucket = aws_s3_bucket.ota.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "ota" {
  bucket = aws_s3_bucket.ota.id

  versioning_configuration {
    status = "Enabled"
  }
}

# The publish script keeps only the 3 newest APKs; this removes the leftovers of deleted versions after a week.
resource "aws_s3_bucket_lifecycle_configuration" "ota" {
  bucket = aws_s3_bucket.ota.id

  rule {
    id     = "expire-old-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 7
    }
  }

  depends_on = [aws_s3_bucket_versioning.ota]
}

# ---------- Lambda that answers "is there a newer build?" ----------

data "archive_file" "updates" {
  type        = "zip"
  source_file = "${path.module}/../../../lambdas/khojo-updates/lambda_function.py"
  output_path = "${path.module}/.build/khojo-updates.zip"
}

data "aws_iam_policy_document" "lambda_trust" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "updates_access" {
  statement {
    sid       = "ReadReleases"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.ota.arn}/latest.json", "${aws_s3_bucket.ota.arn}/apk/*"]
  }
}

resource "aws_iam_role" "updates" {
  name               = "khojo-updates-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_trust.json
}

resource "aws_iam_role_policy" "updates" {
  name   = "read-releases"
  role   = aws_iam_role.updates.id
  policy = data.aws_iam_policy_document.updates_access.json
}

resource "aws_iam_role_policy_attachment" "updates_logs" {
  role       = aws_iam_role.updates.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_cloudwatch_log_group" "updates" {
  name              = "/aws/lambda/${local.lambda_name}"
  retention_in_days = 14
}

resource "aws_lambda_function" "updates" {
  function_name    = local.lambda_name
  role             = aws_iam_role.updates.arn
  runtime          = "python3.13"
  handler          = "lambda_function.lambda_handler"
  filename         = data.archive_file.updates.output_path
  source_code_hash = data.archive_file.updates.output_base64sha256
  memory_size      = 128
  timeout          = 10

  environment {
    variables = {
      BUCKET   = aws_s3_bucket.ota.bucket
      BETA_KEY = var.beta_key
    }
  }

  depends_on = [aws_cloudwatch_log_group.updates, aws_iam_role_policy_attachment.updates_logs]
}

# ---------- HTTP API in front of it ----------
# The default execute-api URL works immediately; map api.<your-domain> to it later (custom domain + ACM certificate).

resource "aws_apigatewayv2_api" "main" {
  name          = "khojo-api"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_integration" "updates" {
  api_id                 = aws_apigatewayv2_api.main.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.updates.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "latest_release" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "GET /v1/releases/latest"
  target    = "integrations/${aws_apigatewayv2_integration.updates.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.main.id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    throttling_rate_limit  = var.api_throttle_rate
    throttling_burst_limit = var.api_throttle_burst
  }
}

resource "aws_lambda_permission" "api_invokes_updates" {
  statement_id  = "AllowApiGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.updates.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/*"
}
