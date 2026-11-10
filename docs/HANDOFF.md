# Khojo AI - Handoff / current status

**Written:** 10 Nov 2026, end of the first working session. **Read this first in the next session.**
Plans: `docs/Khojo_AI_Final_Blueprint_v2.2.md` (what/why) and `docs/Khojo_AI_Delivery_Plan.md` (phases, 21 sprints, CI/CD design).
Repo: https://github.com/devxjitin/khojo-ai (private, owner `devxjitin`). AWS account `143920986615`, region `ap-south-1`, IAM user `jitin-admin`.

## Where we are

Phase 0 (Foundation, CI/CD, Benchmark) - **code is written and CI-green, but nothing is merged and nothing past the AWS bootstrap is deployed.**

| Sprint item | Code | Deployed / live |
|---|---|---|
| S1: repo, CI `checks.yml`, Android shell (flavors `ota` + `play`), Terraform bootstrap | ✅ on `main` | Bootstrap ✅ **applied** |
| S2A: OTA bucket + `khojo-updates` Lambda + API, `publish-update.ps1`, `publish-ota.yml` | ✅ PR #1 (green) | ❌ not applied |
| S2B: in-app updater (ota flavor only) | ✅ PR #2 (green, stacked on #1) | ❌ never run on a phone |
| S2C: SigLIP 2 benchmark script, Hinglish dictionary, ADR-003/004 | ✅ PR #3 (green, stacked on #2) | ❌ benchmark **never run** |
| Bootstrap fix: reuse the account's existing GitHub OIDC provider + S3 state backend | ✅ PR #4 (green) | applied from this branch |

**The app today is an empty shell** (title, tagline, version, update card). No search, no photo picking. No APK has ever been installed on a phone.

## What exists in AWS (created by Terraform bootstrap, 15 resources)

- S3 `khojo-tf-state-143920986615` + DynamoDB lock `khojo-tf-lock`. State is already migrated; `terraform plan` showed no changes.
- IAM roles (GitHub OIDC, no stored keys): `gh-khojo-ota-publish` (main branch only), `gh-khojo-backend-deploy` (GitHub environments `staging`/`prod`, currently `PowerUserAccess` - tighten in Sprint 4), `gh-khojo-plan-readonly` (pull requests).
- 3 budgets (5 / 16 / 53 USD per month) emailing `devxjitin@gmail.com`.
- The GitHub OIDC provider belongs to the account already (made for ultron-android); our Terraform only looks it up.
- Other things in the account belong to other projects (ultron, trustid, nira, age-prediction). Do not touch them.

GitHub repo **variables already set:** `AWS_OTA_PUBLISH_ROLE_ARN`, `AWS_BACKEND_DEPLOY_ROLE_ARN`, `AWS_PLAN_ROLE_ARN`, `AWS_REGION=ap-south-1`.

## Next steps, in order

1. **Apply `infrastructure/envs/prod`** (OTA bucket, Lambda, API; plan was reviewed: 15 to add, 0 change, 0 destroy). Needs approval from the user before applying.
   - Run from PR #1's branch (`sprint-2a-ota-pipeline`) or after #1 is merged.
   - A beta key was generated into the scratchpad as `beta_key.txt` (temp folder, may be gone). Nothing is deployed with it yet, so **just generate a new one** if missing: `python -c "import secrets;print(secrets.token_urlsafe(32))"`. Pass as `TF_VAR_beta_key`.
   - The saved `prod.tfplan` on the old machine state is stale; re-run `terraform plan`.
   - Init command: see `infrastructure/README.md` ("Prod environment").
   - Outputs needed afterwards: `api_url` and `ota_bucket`.
2. **OTA signing key** (`keytool` command in `docs/CI_CD.md`). The user must back it up offline in 2 places; losing it forces every tester to reinstall. Do not regenerate it later.
3. **GitHub secrets** (ask the user first; do not put secrets in chat): `KHOJO_OTA_KEYSTORE` (base64), `KHOJO_OTA_KEYSTORE_PASSWORD`, `KHOJO_OTA_KEY_ALIAS`, `APP_CONFIG_PROPERTIES` (= `API_BASE_URL=<api_url>` and `BETA_KEY=<same key>`). **GitHub variable** `KHOJO_OTA_BUCKET=<ota_bucket output>`.
4. **Merge in order:** #4, #1, #2, #3 (stacked PRs retarget to `main` on their own). Merging #1 triggers `Publish OTA`, which fails unless steps 1-3 are done.
5. First OTA build -> install the APK **manually once** on a phone -> merge a tiny change (bump `changelog.md`) -> confirm the phone offers the update. This whole chain has never run end to end; expect small fixes.
6. Branch protection on `main` (PR required, `Required checks`, no force-push). Not set; private repos on a Free plan may not allow it. Check.
7. Then Sprint 3 (app shell, onboarding, Photo Picker, ObjectBox) per the delivery plan.

## Needs the user (cannot be done by Claude)

- Run `aws login` when the session expires (Terraform cannot read that session directly; use `eval "$(aws configure export-credentials --format env)"` before `terraform`).
- Request GPU quota in Mumbai (Service Quotas): "Running On-Demand G and VT instances" and "All G and VT Spot Instance Requests". Approval can take days, and the benchmark needs it.
- Note AWS credit balance, expiry date and which services it covers (Billing -> Credits).
- Build the benchmark dataset: ~1,000 consented photos + 100+ labelled queries (`eval/README.md`), then run the benchmark on a GPU box and fill in ADR-003 (model choice) and create `eval/baseline.json`.
- 10-15 seller interviews, trademark check for "Khojo AI", domain mapping for `api.<domain>` later.

## Decisions made and things to know

- **Two Android flavors:** `ota` (id `com.khojoai.app.beta`, self-updates from AWS, has the installer permission) and `play` (id `com.khojoai.app`, Play-only). Verified in merged manifests that `play` has no installer permission.
- **Beta key** protects the update endpoint for now. It is inside the APK, so it only keeps casual visitors out. Cognito sign-in replaces it in Sprint 7.
- **Hilt is pinned to 2.58.** 2.57 cannot read Kotlin 2.3 metadata; 2.59/2.60 need AGP 9 (we are on AGP 8.13.2, Kotlin 2.3.21).
- **`.gitattributes` forces LF**; `android/gradlew` must stay executable (`git update-index --chmod=+x`). Both bit us once.
- **Version rule:** any PR that touches `android/**` (or the publish script) must add a newer `## x.y.z` on top of `android/app/src/main/assets/changelog.md`. Current top version on the PR branches: `0.0.3`.
- **Terraform lock files** (`.terraform.lock.hcl`) are intentionally not committed (they were generated on Windows and would break Linux CI). `*.tfplan` files are gitignored (in PR #4).
- Terraform is installed locally at `%LOCALAPPDATA%\Microsoft\WinGet\Packages\Hashicorp.Terraform_Microsoft.Winget.Source_8wekyb3d8bbwe\terraform.exe` (v1.17.0); it may need a new shell to be on `PATH`.
- Local Android builds need `ANDROID_HOME=C:\Users\devxj\AppData\Local\Android\Sdk` and JDK 17 (`C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot`).
- The reference implementation for the OTA design is `devxjitin/ultron-android` (`.github/workflows/publish-ota.yml`, `scripts/publish-update.ps1`, `docs/CI_CD.md`).

## Not verified yet (do not claim these work)

- Anything on a real phone, including the update flow.
- `publish-ota.yml` and `publish-update.ps1` against real AWS (script only syntax-checked; `assembleOtaRelease` was built locally, unsigned).
- The Lambda against real S3 (unit-tested with fakes only).
- `benchmark_siglip.py` (compiles, pure helpers unit-tested; needs torch, a GPU and the dataset).
- Known small gaps in the updater: no manual "Check for updates" button, checks only at app start, no Wi-Fi-only option.
