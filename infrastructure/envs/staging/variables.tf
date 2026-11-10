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
  description = "Lambda memory. This account is capped at 3008 MB (CreateFunction rejects more); ADR-004 wanted 8 to 10 GB, so ask for a Lambda memory quota increase before judging cold-start speed."
  type        = number
  default     = 3008
}

variable "timeout_seconds" {
  description = "Long enough for the first request after a cold start, which loads the model."
  type        = number
  default     = 120
}
