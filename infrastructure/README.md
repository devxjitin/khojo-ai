# Infrastructure (Terraform)

| Folder | What it holds | When |
|---|---|---|
| `bootstrap/` | Remote-state bucket + lock table, GitHub OIDC provider and the 3 CI roles, budget alerts | Sprint 1 (once, by hand) |
| `envs/staging`, `envs/prod` | OTA bucket, `khojo-updates` Lambda, API Gateway, S3 temp, SQS, DynamoDB, ECS/Lambda inference | Sprint 2 onwards |
| `modules/` | Shared building blocks used by both envs | as needed |

## One-time bootstrap (needs your AWS admin credentials)

```bash
cd infrastructure/bootstrap
terraform init
terraform apply -var github_repo=<owner>/khojo-ai -var budget_email=<you@example.com>
```

Then:
1. Copy the three `*_role_arn` outputs into GitHub → Settings → Secrets and variables → Actions → **Variables**
   (`AWS_OTA_PUBLISH_ROLE_ARN`, `AWS_BACKEND_DEPLOY_ROLE_ARN`, `AWS_PLAN_ROLE_ARN`) and set `AWS_REGION=ap-south-1`.
2. Uncomment the `backend "s3"` block in `versions.tf`, fill in the account id, and run `terraform init -migrate-state`.
3. Create GitHub Environments `staging` and `prod` (add yourself as required reviewer on `prod`).
4. In AWS Service Quotas (ap-south-1) request **Running On-Demand G and VT instances** and **All G and VT Spot Instance Requests** (GPU approval can take days).
5. Write down your credit balance, expiry date and which services it covers (Billing → Credits).

`terraform fmt -check` and `terraform validate` run in CI (`infra` job); `terraform fmt -recursive` before you commit.

## Prod environment (OTA bucket + update API) - Sprint 2

After the bootstrap and the state migration:

```bash
cd infrastructure/envs/prod
export TF_VAR_beta_key="<long random string>"        # also goes into APP_CONFIG_PROPERTIES as BETA_KEY
terraform init -backend-config="bucket=khojo-tf-state-<account-id>" \
  -backend-config="key=envs/prod/terraform.tfstate" -backend-config="region=ap-south-1" \
  -backend-config="dynamodb_table=khojo-tf-lock" -backend-config="encrypt=true"
terraform apply
```

Outputs: `api_url` (use as `API_BASE_URL`) and `ota_bucket` (GitHub variable `KHOJO_OTA_BUCKET`). Map `api.<your-domain>` to the API later (custom domain + ACM certificate).
