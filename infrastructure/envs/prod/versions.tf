terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.0"
    }
  }

  # Partial config; pass the rest at init (the bucket name contains your account id):
  #   terraform init -backend-config="bucket=khojo-tf-state-<account-id>" \
  #     -backend-config="key=envs/prod/terraform.tfstate" -backend-config="region=ap-south-1" \
  #     -backend-config="dynamodb_table=khojo-tf-lock" -backend-config="encrypt=true"
  backend "s3" {}
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "khojo-ai"
      ManagedBy = "terraform"
      Env       = "prod"
    }
  }
}
