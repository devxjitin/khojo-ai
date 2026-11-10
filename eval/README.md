# Eval and model benchmark

Answers one question before we build on it: **does SigLIP 2 So400m beat Base by enough to justify the cost?** (blueprint 7.4, ADR-003)

> **Who does what:** testing and the model decision are the **user's**, done by hand with their own images on their own phone. Claude does not build the dataset or run this benchmark. Default model until the user decides: **SigLIP 2 Base**. No GPU is needed for Base (CPU is fine); a GPU only matters if you later want to try So400m, and the Mumbai GPU quota request is postponed until Sprint 8C. Everything below is an optional tool for when the user wants numbers.

## 1. Build the dataset (you do this; it needs real photos)

Put it in `eval/data/` (gitignored: these are real, consented photos and must never be committed).

| File | Format |
|---|---|
| `eval/data/manifest.csv` | `image_id,path` - one row per photo, `path` relative to `eval/data/` |
| `eval/queries.csv` | `id,language,category,query,relevant_asset_ids` - `language` is `en`, `hi` or `hinglish`; list several correct `image_id`s joined with `\|` |

Target: **1,000 photos** (product photos, objects, nature, receipts/screenshots) and **100+ queries** (about a third each of English, Devanagari Hindi, Roman Hinglish), with categories such as `color`, `clothing_detail`, `ocr_amount`, `ambiguous`. A query row with an empty `relevant_asset_ids` is skipped, so the 5 placeholder rows already in `queries.csv` are harmless. Photos of other people need their consent (and see the face-data notes in blueprint 10A before including any).

## 2. Run the benchmark (optional; Base runs on CPU, So400m wants a GPU)

```bash
pip install -r eval/requirements-benchmark.txt
# pin the exact commits shown on each Hugging Face model page:
python eval/benchmark_siglip.py --name base   --model-id google/siglip2-base-patch16-384   --revision <commit>
python eval/benchmark_siglip.py --name so400m --model-id google/siglip2-so400m-patch14-384 --revision <commit>
# Hinglish approach comparison on the winner:
python eval/benchmark_siglip.py --name base --model-id ... --revision ... --query-mode original
python eval/benchmark_siglip.py --name base --model-id ... --revision ... --query-mode rewrite
```

Repeat once with `--device cpu` to get CPU latency. `--hourly-inr` sets the instance price used for the cost column. Results append to `eval/reports/experiments.csv` (gitignored; columns follow blueprint 19.4), one row per language plus `ALL`.

GPU quota note: this account has 0 vCPU quota for G/VT/P instances in Mumbai (checked 10 Nov 2026). It is not requested yet; do that before Sprint 8C, or run So400m on Colab/another rented GPU if you want to try it earlier.

## 3. Decide

- Choose **So400m only if** Recall@5 is at least 5 points better than Base **and** cost per 1,000 inferences stays sustainable. Otherwise choose Base (cheaper, faster, can later run on the phone).
- Hinglish: keep dual embedding (original + rewrite, merged with RRF) only if it beats `original` alone on the `hinglish` rows.
- Write the result into `docs/adr/ADR-003-model-choice.md`, pin the winner's revision in `inference-service/models.lock`, and create `eval/baseline.json` from the winning run. From then on the CI search-quality gate compares every PR against it.

## Files

| File | Purpose |
|---|---|
| `run_eval.py` | Metrics (Recall@5, MRR@10), baseline comparison used by the CI gate |
| `benchmark_siglip.py` | The model benchmark described above (needs torch; not run in CI) |
| `hinglish.py`, `hinglish_dictionary_v0.json` | Reference Hinglish normalizer and the ~200-word seed dictionary (grow it to ~500 from real seller queries) |
| `test_eval.py` | Unit tests for all of the above (run in CI) |
