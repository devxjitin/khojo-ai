# Credits hide the bill until they run out; alerts keep a runaway job from burning them silently.

resource "aws_budgets_budget" "monthly" {
  count = length(var.budget_limits_usd)

  name         = "khojo-monthly-${var.budget_limits_usd[count.index]}-usd"
  budget_type  = "COST"
  limit_amount = tostring(var.budget_limits_usd[count.index])
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.budget_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.budget_email]
  }
}
