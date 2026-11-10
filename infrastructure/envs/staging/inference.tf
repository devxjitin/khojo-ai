locals {
  name = "khojo-inference-staging"
}

# ---------- Where the images live ----------

resource "aws_ecr_repository" "inference" {
  name = "khojo-inference"
  # A tag always means the same image (the tag is the git SHA; there is never a "latest").
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "inference" {
  repository = aws_ecr_repository.inference.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the newest 5 images; each one is about 3 GB."
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 5
      }
      action = { type = "expire" }
    }]
  })
}

# ---------- The function (created once an image exists) ----------

data "aws_iam_policy_document" "lambda_trust" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "inference" {
  name               = local.name
  assume_role_policy = data.aws_iam_policy_document.lambda_trust.json
}

resource "aws_iam_role_policy_attachment" "inference_logs" {
  role       = aws_iam_role.inference.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_cloudwatch_log_group" "inference" {
  name              = "/aws/lambda/${local.name}"
  retention_in_days = 14
}

resource "aws_lambda_function" "inference" {
  count = var.image_tag == "" ? 0 : 1

  function_name = local.name
  role          = aws_iam_role.inference.arn
  package_type  = "Image"
  image_uri     = "${aws_ecr_repository.inference.repository_url}:${var.image_tag}"
  architectures = ["x86_64"]
  memory_size   = var.memory_mb
  timeout       = var.timeout_seconds

  environment {
    variables = {
      KHOJO_ENCODER = "siglip"
      # The Lambda Web Adapter (copied into the image) forwards each invoke to the FastAPI server on this port.
      AWS_LWA_PORT                 = "8080"
      AWS_LWA_READINESS_CHECK_PATH = "/healthz"
    }
  }

  depends_on = [aws_cloudwatch_log_group.inference, aws_iam_role_policy_attachment.inference_logs]
}

# Only callers who can sign a request with AWS credentials may use it. The service has no sign-in of its own until
# Sprint 4B puts the licence check in front of it, so it must not be open to the internet.
resource "aws_lambda_function_url" "inference" {
  count = var.image_tag == "" ? 0 : 1

  function_name      = aws_lambda_function.inference[0].function_name
  authorization_type = "AWS_IAM"
}
