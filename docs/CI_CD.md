# CI/CD behavior

Ported from `ultron-android` and extended. Full design: [Delivery Plan, section 3](Khojo_AI_Delivery_Plan.md).

## Status

| Piece | State |
|---|---|
| `checks.yml` (change detection, version bump, workflow tests, Android, backend, infra, eval gate, `Required checks`) | ✅ Sprint 1 |
| Terraform bootstrap (state, OIDC roles, budgets) | ✅ written, **not applied yet** (needs your AWS admin login) |
| `publish-ota.yml` + `publish-update.ps1` + `khojo-updates` Lambda + prod Terraform (bucket, API) | ✅ written (Sprint 2A), **not applied or run yet** |
| `deploy-backend.yml` | Sprint 4 |
| ~~`play-release.yml`~~ | Dropped: no Play Store ([ADR-002](adr/ADR-002-no-play-store-activation-keys.md)). OTA is the only release path |

## Validation (`Checks`)

Runs on every PR and can be called by the release workflow. `scripts/ci_changes.py` reads the full diff and sets `android`, `backend`, `infra`, `eval`, `publish`. Docs-only PRs still run the cheap workflow-logic tests and the always-running `Required checks` gate, so a skipped job can never leave a required status pending.

- **Version bumped:** a PR that reaches the APK must put a newer `## x.y.z` on top of `changelog.md`.
- **Android:** unit tests + lint with a fixed `-PappVersionCode=1`. Today both flavors (`ota`, `play`); after the flavor collapse (ADR-002) just `testDebugUnitTest lintDebug`.
- **Search-quality gate:** `eval/run_eval.py --quick` fails if Recall@5 or MRR@10 falls more than 2 points below `eval/baseline.json`. It passes until the baseline exists (created after the Sprint 2 benchmark).
- **Infra:** `terraform fmt -check` and `validate` on each root module.

## Repository protection (set once the GitHub repo exists)

`main`: PR required, `Required checks` required, branch up to date, conversations resolved, enforce for admins, no force-push or deletion. Verify with the GitHub API before relying on it.

## Secrets and variables

See Delivery Plan section 3.7. AWS access is OIDC only (no AWS keys in GitHub). Signing keys are stored as secrets **and** backed up offline.

## Releases (OTA beta channel)

`Publish OTA` runs on `main` pushes that touch `android/**`, the publish script, the workflow or local actions. Order matters:

1. **preflight** - main only; an obsolete commit (a newer one already exists) is skipped.
2. **validate** - the same commit runs `Checks`. A failure stops the upload.
3. **build** (parallel with validate) - restores the signing key, runs `publish-update.ps1 -Mode Build`: builds the minified `assembleOtaRelease` APK and writes `ota-manifest.json` (commit, sha256, size, version, rollout). Nothing is uploaded.
4. **publish** (needs validate + build) - signs in to AWS with OIDC, `-Mode Publish` re-verifies the manifest, requires a strictly newer version than live, uploads the APK **first** and `latest.json` **last**, then keeps only the newest 3 APKs.

Manual run (`Actions -> Publish OTA -> Run workflow`) takes optional notes and a `rollout_percent` (e.g. 20 offers the build to ~20% of installs). Local publish: `./scripts/publish-update.ps1` (needs `KHOJO_OTA_BUCKET`, AWS login and the signing key).

### One-time setup for OTA publishing

1. **Signing key** (never regenerate it; losing it forces every tester to reinstall). Create it once and back it up offline in two places:
   ```bash
   keytool -genkeypair -v -keystore ~/.khojo-android/ota.keystore -alias khojo-ota -keyalg RSA -keysize 2048 -validity 10000
   ```
2. **GitHub secrets:** `KHOJO_OTA_KEYSTORE` (`base64 -w0 ~/.khojo-android/ota.keystore`), `KHOJO_OTA_KEYSTORE_PASSWORD`, `KHOJO_OTA_KEY_ALIAS`, `APP_CONFIG_PROPERTIES` (contents of `android/local.properties`: `API_BASE_URL=<api_url output>` and `BETA_KEY=<same beta key>`).
3. **GitHub variables:** `AWS_OTA_PUBLISH_ROLE_ARN` (bootstrap output) and `KHOJO_OTA_BUCKET` (prod output `ota_bucket`).
4. Apply `infrastructure/envs/prod` (see `infrastructure/README.md`).

The update endpoint uses a shared **beta key** for now. It is compiled into the beta APK, so it only keeps casual visitors out; Cognito sign-in replaces it in Sprint 7.
