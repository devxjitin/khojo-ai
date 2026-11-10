variable "region" {
  description = "AWS region for everything (Mumbai)."
  type        = string
  default     = "ap-south-1"
}

variable "github_repo" {
  description = "GitHub repository allowed to assume the CI roles, as owner/name."
  type        = string
}

variable "budget_email" {
  description = "Email that receives AWS Budget alerts."
  type        = string
}

variable "budget_limits_usd" {
  description = "Monthly alert thresholds in USD (roughly 500 / 1,500 / 5,000 INR)."
  type        = list(number)
  default     = [5, 16, 53]
}
