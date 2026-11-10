# CI/CD behavior

Ported from `ultron-android` and extended. Full design: [Delivery Plan, section 3](Khojo_AI_Delivery_Plan.md).

## Status

| Piece | State |
|---|---|
| `checks.yml` (change detection, version bump, workflow tests, Android, backend, infra, eval gate, `Required checks`) | ✅ Sprint 1 |
| Terraform bootstrap (state, OIDC roles, budgets) | ✅ written, **not applied yet** (needs your AWS admin login) |
| `publish-ota.yml` + `publish-update.ps1` + `khojo-updates` Lambda | Sprint 2 (mini 2A) |
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
