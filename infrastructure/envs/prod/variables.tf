variable "region" {
  description = "AWS region (Mumbai)."
  type        = string
  default     = "ap-south-1"
}

variable "beta_key" {
  description = "Shared key the beta app sends to the update endpoint. Pass with TF_VAR_beta_key; never commit it."
  type        = string
  sensitive   = true
}

variable "api_throttle_rate" {
  description = "Steady requests per second allowed on the whole API."
  type        = number
  default     = 10
}

variable "api_throttle_burst" {
  description = "Burst requests allowed on the whole API."
  type        = number
  default     = 20
}
