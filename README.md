# Khojo AI — *Bas bolo. Khojo.* 🔎

Search your own photos, screenshots, videos and audio by describing them (English / Hindi / Hinglish), then share, copy or play the exact moment. Android first.

| Folder | What |
|---|---|
| `android/` | Kotlin + Compose app. Two flavors: `ota` (self-updates from AWS) and `play` (Google Play) |
| `inference-service/` | FastAPI + SigLIP 2 (Sprint 4) |
| `lambdas/` | `khojo-updates` OTA endpoint, control plane (Sprint 2+) |
| `infrastructure/` | Terraform (`bootstrap/` first, then `envs/`) |
| `eval/` | Search-quality dataset, queries and `run_eval.py` |
| `scripts/` | CI helpers (`ci_changes.py`, `check_version_bump.py`), publish script (Sprint 2) |
| `docs/` | Blueprint, delivery plan, CI/CD notes, ADRs |

Plans: [`docs/Khojo_AI_Final_Blueprint_v2.2.md`](docs/Khojo_AI_Final_Blueprint_v2.2.md) (what and why) and [`docs/Khojo_AI_Delivery_Plan.md`](docs/Khojo_AI_Delivery_Plan.md) (phases, sprints, CI/CD).

## Build locally

Needs JDK 17 and Android SDK 36 (`ANDROID_HOME`).

```bash
cd android
./gradlew testOtaDebugUnitTest testPlayDebugUnitTest -PappVersionCode=1
./gradlew assembleOtaDebug      # installable beta build
```

CI logic tests: `python -m unittest discover -s tests/ci`

## Shipping a change

1. Branch → PR. `Checks` must be green (`Required checks` is the only status branch protection needs).
2. If the PR touches the app, add a newer `## x.y.z` entry at the top of `android/app/src/main/assets/changelog.md`.
3. Merge to `main`. From Sprint 2 this publishes the OTA build to testers automatically.

See [`docs/CI_CD.md`](docs/CI_CD.md).
