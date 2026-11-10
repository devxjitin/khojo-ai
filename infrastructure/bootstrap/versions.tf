terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # First apply runs with local state; then uncomment this block and run
  # `terraform init -migrate-state` so the state moves into the bucket created below.
  # backend "s3" {
  #   bucket         = "khojo-tf-state-<account-id>"
  #   key            = "bootstrap/terraform.tfstate"
  #   region         = "ap-south-1"
  #   dynamodb_table = "khojo-tf-lock"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "khojo-ai"
      ManagedBy = "terraform"
      Stack     = "bootstrap"
    }
  }
}
