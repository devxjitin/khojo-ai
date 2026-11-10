# Khojo AI - Handoff / current status

**Written:** 10 Nov 2026, end of the first working session. **Read this first in the next session.**
Plans: `docs/Khojo_AI_Final_Blueprint_v2.2.md` (what/why, now v2.3) and `docs/Khojo_AI_Delivery_Plan.md` (phases, 21 sprints, CI/CD design, now v1.1).

> **Big decision (10 Nov 2026, later the same day): no Play Store.** The app is installed from our website as an APK, updates through AWS OTA for everyone, and unlocks with an **activation key**, copied from `devxjitin/ultron-android` (`mcp/license`, `mcp/license-admin`, `ultronapp/`). See `docs/adr/ADR-002-no-play-store-activation-keys.md`. The `play` flavor, Play Billing, Play Console and `play-release.yml` are dropped; Cognito is replaced by Firebase anonymous auth (as in Ultron).

Repo: https://github.com/devxjitin/khojo-ai (private, owner `devxjitin`). AWS account `143920986615`, region `ap-south-1`, IAM user `jitin-admin`.

## Where we are

Phase 0 (Foundation, CI/CD, Benchmark) - **code is written and CI-green. #4 and #5 are merged; #1, #2, #3 are still open. In AWS the bootstrap and `envs/prod` (OTA bucket + update API) are applied.**

| Sprint item | Code | Deployed / live |
|---|---|---|
| S1: repo, CI `checks.yml`, Android shell (flavors `ota` + `play`), Terraform bootstrap | ✅ on `main` | Bootstrap ✅ **applied** |
| S2A: OTA bucket + `khojo-updates` Lambda + API, `publish-update.ps1`, `publish-ota.yml` | ✅ PR #1 (green) | ✅ **applied** (10 Nov 2026, from PR #1's branch); endpoint answers 401 without key, 404 with key until the first release |
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

1. ~~Apply `infrastructure/envs/prod`~~ **Done 10 Nov 2026** (15 resources, then a 1-resource IAM fix adding `s3:ListBucket` so a missing `latest.json` is a 404 instead of a 500; the fix is commit `697a582` on PR #1's branch).
   - `api_url` = `https://237zro3s3h.execute-api.ap-south-1.amazonaws.com`, `ota_bucket` = `khojo-app-updates-143920986615`.
   - The beta key is in `~/.khojo-android/beta_key.txt` (C:/Users/devxj/.khojo-android/) (not in git). It must be the same value in `APP_CONFIG_PROPERTIES` (`BETA_KEY=`). If that file is lost, generate a new key and re-apply with `TF_VAR_beta_key`.
   - Later applies: run from the branch that holds the current `envs/prod` code, re-run `terraform plan` first, and ask the user before applying. Init command is in `infrastructure/README.md` ("Prod environment"). In PowerShell quote `'-out=prod.tfplan'`, or the dot splits the argument.
2. **OTA signing key** (`keytool` command in `docs/CI_CD.md`). The user must back it up offline in 2 places; losing it forces every user to reinstall (there is no Play App Signing to fall back on). Do not regenerate it later.
3. **GitHub secrets** (ask the user first; do not put secrets in chat): `KHOJO_OTA_KEYSTORE` (base64), `KHOJO_OTA_KEYSTORE_PASSWORD`, `KHOJO_OTA_KEY_ALIAS`, `APP_CONFIG_PROPERTIES` (= `API_BASE_URL=<api_url>` and `BETA_KEY=<same key>`). **GitHub variable** `KHOJO_OTA_BUCKET=<ota_bucket output>`.
4. **Merge in order:** #4, #5 (docs), #1, #2, #3 (stacked PRs retarget to `main` on their own). Merging #1 triggers `Publish OTA`, which fails unless steps 1-3 are done.
   - PR #1's `docs/CI_CD.md` still says "Cognito sign-in replaces [the beta key] in Sprint 7". Change it to "Firebase JWT + activation key replace it in Sprint 4B" while merging.
5. **Drop the `play` flavor before any phone install** (new small PR, ADR-002): one build, `applicationId = com.khojoai.app`, `REQUEST_INSTALL_PACKAGES` and the updater move from `src/ota` to `src/main`, delete the `playUpload` signing config, CI runs `testDebugUnitTest lintDebug`, `ci_changes.py`/`publish-update.ps1`/`publish-ota.yml` use `assembleRelease`. Must land before the first install, because changing the app id later forces every user to reinstall.
6. First OTA build -> install the APK **manually once** on a phone -> merge a tiny change (bump `changelog.md`) -> confirm the phone offers the update. This whole chain has never run end to end; expect small fixes.
7. Branch protection on `main` (PR required, `Required checks`, no force-push). Not set; private repos on a Free plan may not allow it. Check.
8. Then Sprint 3 (app shell, onboarding, Photo Picker, ObjectBox) per the delivery plan. Activation keys come in Sprint 4B (Firebase anon auth + App Check, `khojo-licenses` table, `license` Lambda, Activation screen); the key admin web app in 7C; paid key sales in 8B.

## Needs the user (cannot be done by Claude)

- Run `aws login` when the session expires (Terraform cannot read that session directly; use `eval "$(aws configure export-credentials --format env)"` before `terraform`).
- Request GPU quota in Mumbai (Service Quotas): "Running On-Demand G and VT instances" and "All G and VT Spot Instance Requests". Approval can take days, and the benchmark needs it.
- Note AWS credit balance, expiry date and which services it covers (Billing -> Credits).
- Build the benchmark dataset: ~1,000 consented photos + 100+ labelled queries (`eval/README.md`), then run the benchmark on a GPU box and fill in ADR-003 (model choice) and create `eval/baseline.json`.
- 10-15 seller interviews, trademark check for "Khojo AI", domain mapping for `api.<domain>` later.
- Before Sprint 4B: a Firebase project for Khojo (Anonymous Auth + App Check with Play Integrity, device integrity only) and its `google-services.json`.
- Before Sprint 7C: a Resend account with the domain verified (DKIM/SPF), API key stored as SSM SecureString `/khojo/resend-api-key`; admin Google email(s) for `ADMIN_EMAILS`.
- Before public launch: register with Google's Android developer verification (package + signing key). Enforcement started 30 Sep 2026 in 4 countries, global in 2027; India's date is not announced yet.

## Decisions made and things to know

- **Two Android flavors today** (`ota` = `com.khojoai.app.beta`, `play` = `com.khojoai.app`). **Being collapsed into one build** (`com.khojoai.app`, OTA updater in main), see next step 5 and ADR-002.
- **Beta key** protects the update endpoint for now. It is inside the APK, so it only keeps casual visitors out. Firebase JWT + activation keys replace it in Sprint 4B.
- **Activation keys** (ADR-002): `KHOJ-XXXX-XXXX-XXXX-XXXX`, only the SHA-256 is stored, one phone at a time, max 1 move a day, `durationDays` from first activation, 7 days offline grace. Unlike Ultron, every cloud call checks the license + plan quota on the server. Avoid the two bugs from Ultron's 10 Nov audit: reissue must keep the running expiry, and the dashboard must count a reissued sale once.
- **Hilt is pinned to 2.58.** 2.57 cannot read Kotlin 2.3 metadata; 2.59/2.60 need AGP 9 (we are on AGP 8.13.2, Kotlin 2.3.21).
- **`.gitattributes` forces LF**; `android/gradlew` must stay executable (`git update-index --chmod=+x`). Both bit us once.
- **Version rule:** any PR that touches `android/**` (or the publish script) must add a newer `## x.y.z` on top of `android/app/src/main/assets/changelog.md`. Current top version on the PR branches: `0.0.3`.
- **Terraform lock files** (`.terraform.lock.hcl`) are intentionally not committed (they were generated on Windows and would break Linux CI). `*.tfplan` files are gitignored (in PR #4).
- Terraform is installed locally at `%LOCALAPPDATA%\Microsoft\WinGet\Packages\Hashicorp.Terraform_Microsoft.Winget.Source_8wekyb3d8bbwe\terraform.exe` (v1.17.0); it may need a new shell to be on `PATH`.
- Local Android builds need `ANDROID_HOME=C:\Users\devxj\AppData\Local\Android\Sdk` and JDK 17 (`C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot`).
- The reference implementation is `devxjitin/ultron-android`, checked out locally at `X:\KIRA`: OTA (`.github/workflows/publish-ota.yml`, `scripts/publish-update.ps1`, `docs/CI_CD.md`) and activation keys (`mcp/license/lambda_function.py`, `mcp/license-admin/`, `ultronapp/`, `android/app/src/main/java/com/assistant/app/data/License.kt`, `network/LicenseClient.kt`, `viewmodel/AccessViewModel.kt`, `ui/AccessScreens.kt`, `infra/api.yaml`, `docs/ARCHITECTURE.md` sections 21-22).

## Not verified yet (do not claim these work)

- Anything on a real phone, including the update flow.
- `publish-ota.yml` and `publish-update.ps1` against real AWS (script only syntax-checked; `assembleOtaRelease` was built locally, unsigned).
- The Lambda against real S3 (unit-tested with fakes only).
- `benchmark_siglip.py` (compiles, pure helpers unit-tested; needs torch, a GPU and the dataset).
- Known small gaps in the updater: no manual "Check for updates" button, checks only at app start, no Wi-Fi-only option.
