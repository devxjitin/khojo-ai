# ADR-003: Image/text model choice (SigLIP 2 Base vs So400m)

- **Status:** Proposed - decided after the Sprint 2 benchmark (`eval/README.md`)
- **Context:** Search quality is the product. So400m (1,152-d, ~4.5 GB) may be more accurate than Base (768-d, ~1.5 GB) but costs more, is slower on CPU, and cannot run on a phone. Base can later run on-device (INT8), which removes cloud indexing cost.
- **Decision rule (blueprint 7.4):** pick So400m only if Recall@5 improves by at least 5 points over Base on our own photos and queries (English, Hindi and Hinglish) **and** cost per 1,000 inferences is sustainable. Otherwise pick Base.
- **Result (fill in after the run):**

| | Base | So400m |
|---|---|---|
| Recall@5 (ALL / en / hi / hinglish) | | |
| MRR@10 | | |
| Image index p50 (GPU / CPU) | | |
| Text query p95 (GPU / CPU) | | |
| Peak memory | | |
| INR per 1,000 images / queries | | |
| Hinglish: original vs rewrite vs dual | | |

- **Decision:** _pending_
- **Pinned:** model revision + checksum in `inference-service/models.lock`; vector dimension (768 or 1,152) in the ObjectBox schema. Changing the model later means a full reindex (a new `modelVersion`).
- **Consequences:** (fill in)
