# Khojo AI - Handoff / current status

**Updated:** 10 Nov 2026, end of the first working session. **Read this first in the next session.**
Plans: `docs/Khojo_AI_Final_Blueprint_v2.2.md` (what/why, v2.3) and `docs/Khojo_AI_Delivery_Plan.md` (phases, 21 sprints, CI/CD design, v1.1).

> **No Play Store.** The app is installed from our website as an APK, updates through AWS OTA for everyone, and unlocks with an **activation key**, copied from `devxjitin/ultron-android`. See `docs/adr/ADR-002-no-play-store-activation-keys.md`. There is one Android build, no `play` flavor, no Play Billing, no Cognito (Firebase anonymous auth, as in Ultron, comes in Sprint 4B).

Repo: https://github.com/devxjitin/khojo-ai (**public**, owner `devxjitin`; public so GitHub Actions minutes are free. History was scanned for secrets before that: none). AWS account `143920986615`, region `ap-south-1`, IAM user `jitin-admin`.

## Where we are

Phase 0 is almost done: Sprint 1 and Sprint 2 (A, B, C) are **merged and deployed**, and the whole OTA chain works end to end.

| Item | State |
|---|---|
| Repo, CI (`checks.yml`), branch protection on `main` | ✅ done (PR required, `Required checks`, up to date, no force-push, admins included) |
| Terraform bootstrap (state, OIDC roles, budgets) | ✅ applied; roles trust this repo's **ID-based** OIDC subject (variable `github_repo_subject`) |
| `envs/prod`: OTA bucket + `khojo-updates` Lambda + API | ✅ applied |
| `Publish OTA` (build, sign, S3, `latest.json`) | ✅ works; versions 0.0.4 and 0.0.5 published |
| In-app updater | ✅ verified on a real phone: installed 0.0.4, got the update card, installed 0.0.5 |
| SigLIP 2 benchmark script + Hinglish dictionary + ADR-003/004 | ✅ merged. **Benchmark/testing is owned by the user** (they test on their own phone with their own images); Claude does not run it and no GPU is needed now |

**The app today is an empty shell** (title, tagline, version, update card). No search, no photo picking, no sign-in, no activation screen yet.

## What exists in AWS

- Terraform state: S3 `khojo-tf-state-143920986615` + DynamoDB lock `khojo-tf-lock`.
- IAM roles (GitHub OIDC, no stored keys): `gh-khojo-ota-publish` (main branch only), `gh-khojo-backend-deploy` (GitHub environments `staging`/`prod`, currently `PowerUserAccess` - tighten in Sprint 4), `gh-khojo-plan-readonly` (pull requests).
- OTA: S3 `khojo-app-updates-143920986615` (private), Lambda `khojo-updates`, HTTP API `https://237zro3s3h.execute-api.ap-south-1.amazonaws.com` (`GET /v1/releases/latest` needs header `x-beta-key`).
- 3 budgets (5 / 16 / 53 USD per month) emailing `devxjitin@gmail.com`.
- The GitHub OIDC provider belongs to the account already (made for ultron-android); our Terraform only looks it up.
- Other things in the account belong to other projects (ultron, trustid, nira, age-prediction). Do not touch them.

GitHub **variables:** `AWS_OTA_PUBLISH_ROLE_ARN`, `AWS_BACKEND_DEPLOY_ROLE_ARN`, `AWS_PLAN_ROLE_ARN`, `AWS_REGION`, `KHOJO_OTA_BUCKET`. **Secrets:** `KHOJO_OTA_KEYSTORE`, `KHOJO_OTA_KEYSTORE_PASSWORD`, `KHOJO_OTA_KEY_ALIAS`, `APP_CONFIG_PROPERTIES` (API URL + beta key).

Local files (not in git): `~/.khojo-android/ota.keystore`, `ota_keystore_password.txt` and `beta_key.txt` (C:/Users/devxj/.khojo-android/). The keystore and its password were **backed up offline by the user in 2 places**. Never regenerate the keystore (every user would have to reinstall) and never print these files.

## Next steps, in order

1. **Sprint 3** (delivery plan): app shell, onboarding, consent screen, Photo Picker, ObjectBox. One mini-sprint = one PR; every `android/**` PR needs a newer `## x.y.z` on top of `changelog.md` (top is `0.0.5`). Merging to `main` publishes the build to the phone automatically.
2. **Sprint 4**: inference service, then **activation keys** (4B): Firebase anonymous auth + App Check, `khojo-licenses` DynamoDB table, `license` Lambda (`/v1/license/activate`, `/v1/license/status`), Activation screen, server-side license + quota check on every cloud call; the beta key goes away. Port from `X:\KIRA` (see below). Key admin web app in 7C, paid key sales in 8B.
3. Website download page (`GET /v1/releases/download` is described in the plan but not built; only `latest` exists).
4. Small updater gaps: no manual "Check for updates" button (it only checks at app start), no Wi-Fi-only option.

## Needs the user (cannot be done by Claude)

- Run `aws login` when the session expires (use `aws configure export-credentials --format process` to feed Terraform).
- **GPU quota: not needed now.** Mumbai quota is 0 (checked 10 Nov: G/VT on-demand 0, G/VT spot 0, P 0; no request pending). Request it only when Sprint 8C (growth infra, GPU Spot batch worker) gets close, since approval can take days.
- Note AWS credit balance, expiry date and which services it covers (Billing -> Credits).
- **Testing is the user's job**, not Claude's: add images to the phone, test search quality yourself, and tell Claude which model (Base or So400m) to pin. Until then the working default is **SigLIP 2 Base** (cheaper, faster, can run on-device later). Record the result in ADR-003 and, if you want the CI quality gate, create `eval/baseline.json` (`eval/README.md`; never commit photos, the repo is public).
- 10-15 seller interviews, trademark check for "Khojo AI", domain mapping for `api.<domain>`.
- Before Sprint 4B: a Firebase project for Khojo (Anonymous Auth + App Check with Play Integrity, device integrity only) and its `google-services.json`.
- Before Sprint 7C: a Resend account with the domain verified (DKIM/SPF), API key stored as SSM SecureString `/khojo/resend-api-key`; admin Google email(s) for `ADMIN_EMAILS`.
- Before public launch: register with Google's Android developer verification (package `com.khojoai.app` + signing key). Enforcement started 30 Sep 2026 in 4 countries, global in 2027; India's date is not announced yet.

## Decisions made and things to know

- **Activation keys** (ADR-002): `KHOJ-XXXX-XXXX-XXXX-XXXX`, only the SHA-256 is stored, one phone at a time, max 1 move a day, `durationDays` from first activation, 7 days offline grace. Unlike Ultron, every cloud call checks the license + plan quota on the server. Avoid the two bugs from Ultron's 10 Nov audit: reissue must keep the running expiry, and the dashboard must count a reissued sale once.
- **Beta key** protects the update endpoint for now. It is inside the APK, so it only keeps casual visitors out.
- **OIDC subject:** this repo's tokens use `repo:devxjitin@292993567/khojo-ai@1452479166:...`. The IAM roles must trust that form (a name-based trust fails with "Not authorized to perform sts:AssumeRoleWithWebIdentity"). Check with `gh api repos/devxjitin/khojo-ai/actions/oidc/customization/sub`.
- **Applying Terraform:** always `terraform plan` first and ask the user before `apply`. Bootstrap needs `-var github_repo=devxjitin/khojo-ai -var budget_email=... -var github_repo_subject=devxjitin@292993567/khojo-ai@1452479166`. `envs/prod` needs `TF_VAR_beta_key` (value in `beta_key.txt`) and the backend flags from `infrastructure/README.md`. In PowerShell quote `'-out=x.tfplan'` or the dot splits the argument.
- **Merging PRs:** branch protection needs "Update branch" before each merge. For stacked PRs do **not** use `--delete-branch` (GitHub closes the dependent PR); retarget the next PR to `main` first. Squash merges rewrite history, so a dependent branch usually needs conflicts resolved by taking `main`'s version of files it did not change.
- **Workflow YAML:** an unquoted `: ` inside a description made `publish-ota.yml` invalid and produced runs with 0 jobs. Quote such strings.
- **Hilt is pinned to 2.58.** 2.57 cannot read Kotlin 2.3 metadata; 2.59/2.60 need AGP 9 (we are on AGP 8.13.2, Kotlin 2.3.21).
- **`.gitattributes` forces LF**; `android/gradlew` must stay executable (`git update-index --chmod=+x`).
- **Terraform lock files** (`.terraform.lock.hcl`) are intentionally not committed. `*.tfplan` files are gitignored.
- Terraform is installed at `%LOCALAPPDATA%\Microsoft\WinGet\Packages\Hashicorp.Terraform_Microsoft.Winget.Source_8wekyb3d8bbwe\terraform.exe`.
- Local Android builds need `ANDROID_HOME=C:\Users\devxj\AppData\Local\Android\Sdk` and JDK 17 (`C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot`). Without the keystore the local release APK is unsigned; with `KHOJO_OTA_KEYSTORE_PASSWORD` and `KHOJO_OTA_KEY_ALIAS=khojo-ota` set it is signed.
- The reference implementation is `devxjitin/ultron-android`, checked out locally at `X:\KIRA`: OTA (`.github/workflows/publish-ota.yml`, `scripts/publish-update.ps1`) and activation keys (`mcp/license/lambda_function.py`, `mcp/license-admin/`, `ultronapp/`, `android/app/src/main/java/com/assistant/app/data/License.kt`, `network/LicenseClient.kt`, `viewmodel/AccessViewModel.kt`, `ui/AccessScreens.kt`, `infra/api.yaml`, `docs/ARCHITECTURE.md` sections 21-22).

## Not verified yet (do not claim these work)

- `benchmark_siglip.py` (compiles, pure helpers unit-tested; never run. The user decides if and when to run it; CPU works for Base, only So400m really wants a GPU).
- Search quality of any model on real photos (the user tests this themselves).
- Anything beyond the empty shell: search, indexing, backend inference, activation keys, billing-free key sales.
- The install and update flow on other phones or Android versions (tested on one phone only).
