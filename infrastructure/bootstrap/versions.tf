terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # State lives in the bucket this stack itself created (first apply ran with local state, then migrated).
  backend "s3" {
    bucket         = "khojo-tf-state-143920986615"
    key            = "bootstrap/terraform.tfstate"
    region         = "ap-south-1"
    dynamodb_table = "khojo-tf-lock"
    encrypt        = true
  }
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
