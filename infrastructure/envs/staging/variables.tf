variable "region" {
  description = "AWS region (Mumbai)."
  type        = string
  default     = "ap-south-1"
}

variable "image_tag" {
  description = "Git SHA of the inference image in ECR. Empty creates only the ECR repository (the first deploy step)."
  type        = string
  default     = ""
}

variable "memory_mb" {
  description = "Lambda memory. The CPU share grows with it; ADR-004 plans 8 to 10 GB for the model."
  type        = number
  default     = 8192
}

variable "timeout_seconds" {
  description = "Long enough for the first request after a cold start, which loads the model."
  type        = number
  default     = 120
}
