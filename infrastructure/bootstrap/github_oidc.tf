# GitHub Actions signs in to AWS with short-lived OIDC tokens: no long-lived AWS keys are stored in GitHub.

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

locals {
  oidc_arn = aws_iam_openid_connect_provider.github.arn
  oidc_sub = "token.actions.githubusercontent.com:sub"
  oidc_aud = "token.actions.githubusercontent.com:aud"
}

# --- 1) OTA publish: only the main branch may upload APKs and move latest.json ---

data "aws_iam_policy_document" "ota_trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_arn]
    }

    condition {
      test     = "StringEquals"
      variable = local.oidc_aud
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = local.oidc_sub
      values   = ["repo:${var.github_repo}:ref:refs/heads/main"]
    }
  }
}

data "aws_iam_policy_document" "ota_publish" {
  statement {
    sid       = "ListBucket"
    actions   = ["s3:ListBucket"]
    resources = ["arn:aws:s3:::khojo-app-updates-${local.account_id}"]
  }

  statement {
    sid       = "ReadWriteReleases"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["arn:aws:s3:::khojo-app-updates-${local.account_id}/apk/*"]
  }

  statement {
    sid       = "ReadWriteManifest"
    actions   = ["s3:GetObject", "s3:PutObject"]
    resources = ["arn:aws:s3:::khojo-app-updates-${local.account_id}/latest.json"]
  }
}

resource "aws_iam_role" "ota_publish" {
  name               = "gh-khojo-ota-publish"
  assume_role_policy = data.aws_iam_policy_document.ota_trust.json
}

resource "aws_iam_role_policy" "ota_publish" {
  name   = "ota-publish"
  role   = aws_iam_role.ota_publish.id
  policy = data.aws_iam_policy_document.ota_publish.json
}

# --- 2) Backend deploy: only the staging / prod GitHub Environments (prod needs your manual approval) ---

data "aws_iam_policy_document" "deploy_trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_arn]
    }

    condition {
      test     = "StringEquals"
      variable = local.oidc_aud
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = local.oidc_sub
      values = [
        "repo:${var.github_repo}:environment:staging",
        "repo:${var.github_repo}:environment:prod",
      ]
    }
  }
}

resource "aws_iam_role" "backend_deploy" {
  name               = "gh-khojo-backend-deploy"
  assume_role_policy = data.aws_iam_policy_document.deploy_trust.json
}

# Starts broad on purpose for Sprint 1; tighten to the exact ECR / Lambda / ECS / SQS resources once Sprint 4 creates them.
resource "aws_iam_role_policy_attachment" "backend_deploy_power_user" {
  role       = aws_iam_role.backend_deploy.name
  policy_arn = "arn:aws:iam::aws:policy/PowerUserAccess"
}

# --- 3) terraform plan on pull requests: read-only, plus the state lock ---

data "aws_iam_policy_document" "plan_trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_arn]
    }

    condition {
      test     = "StringEquals"
      variable = local.oidc_aud
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = local.oidc_sub
      values   = ["repo:${var.github_repo}:pull_request"]
    }
  }
}

data "aws_iam_policy_document" "plan_state" {
  statement {
    sid       = "ReadState"
    actions   = ["s3:GetObject", "s3:ListBucket"]
    resources = [aws_s3_bucket.tf_state.arn, "${aws_s3_bucket.tf_state.arn}/*"]
  }

  statement {
    sid       = "StateLock"
    actions   = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:DeleteItem"]
    resources = [aws_dynamodb_table.tf_lock.arn]
  }
}

resource "aws_iam_role" "plan_readonly" {
  name               = "gh-khojo-plan-readonly"
  assume_role_policy = data.aws_iam_policy_document.plan_trust.json
}

resource "aws_iam_role_policy_attachment" "plan_read_only_access" {
  role       = aws_iam_role.plan_readonly.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

resource "aws_iam_role_policy" "plan_state" {
  name   = "plan-state"
  role   = aws_iam_role.plan_readonly.id
  policy = data.aws_iam_policy_document.plan_state.json
}
