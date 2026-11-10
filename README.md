# Khojo AI — *Bas bolo. Khojo.* 🔎

Search your own photos, screenshots, videos and audio by describing them (English / Hindi / Hinglish), then share, copy or play the exact moment. Android first.

**Not on the Play Store.** The APK is downloaded from our website, updates arrive over AWS OTA, and the app unlocks with an activation key (same model as `ultron-android`). See [ADR-002](docs/adr/ADR-002-no-play-store-activation-keys.md).

| Folder | What |
|---|---|
| `android/` | Kotlin + Compose app, one build (`com.khojoai.app`) that updates itself from AWS |
| `inference-service/` | FastAPI + SigLIP 2 (Sprint 4) |
| `lambdas/` | `khojo-updates` OTA endpoint, `license` + `license-admin` activation keys (Sprint 4B/7C), control plane |
| `infrastructure/` | Terraform (`bootstrap/` first, then `envs/`) |
| `eval/` | Search-quality dataset, queries and `run_eval.py` |
| `scripts/` | CI helpers (`ci_changes.py`, `check_version_bump.py`), publish script (Sprint 2) |
| `docs/` | Blueprint, delivery plan, CI/CD notes, ADRs |

Plans: [`docs/Khojo_AI_Final_Blueprint_v2.2.md`](docs/Khojo_AI_Final_Blueprint_v2.2.md) (what and why) and [`docs/Khojo_AI_Delivery_Plan.md`](docs/Khojo_AI_Delivery_Plan.md) (phases, sprints, CI/CD).

## Build locally

Needs JDK 17 and Android SDK 36 (`ANDROID_HOME`).

```bash
cd android
./gradlew testDebugUnitTest -PappVersionCode=1
./gradlew assembleDebug         # installable debug build
```

CI logic tests: `python -m unittest discover -s tests/ci`

## Shipping a change

1. Branch → PR. `Checks` must be green (`Required checks` is the only status branch protection needs).
2. If the PR touches the app, add a newer `## x.y.z` entry at the top of `android/app/src/main/assets/changelog.md`.
3. Merge to `main`. From Sprint 2 this publishes the OTA build automatically. OTA is the only release path for everyone.

See [`docs/CI_CD.md`](docs/CI_CD.md).
