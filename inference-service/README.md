# inference-service

Turns text and photos into vectors with the pinned SigLIP 2 checkpoint (`models.lock`). Sprint 4A: the API and the Docker image only. Nothing is deployed yet.

| Endpoint | In | Out |
|---|---|---|
| `GET /healthz` | | status, encoder, `modelVersion`, dimensions |
| `POST /v1/embeddings/text` | `{"text": "..."}` (1 to 512 characters) | `modelVersion`, `dimensions`, `vector` (unit length), `l2Norm` |
| `POST /v1/embeddings/image` | raw image bytes (up to 10 MB) | same |

Text is lower-cased and padded to `max_length=64`, as in `eval/benchmark_siglip.py`, so the service and the benchmark agree. Wrong file type is 415, empty input is 422, too big is 413.

There is no sign-in here on purpose: the activation-key and licence check arrive in Sprint 4B, in front of this service. Do not expose it to the internet before that.

## Run the tests (no model download)

```bash
python -m pip install -r requirements-dev.txt
ruff check .
python -m pytest -q
```

Tests and CI use `KHOJO_ENCODER=fake`, a stand-in that gives the right sizes and unit-length vectors but is not a real model.

## Run the real model

```bash
docker build -t khojo-inference inference-service
docker run -p 8080:8080 khojo-inference
```

The image downloads the exact revision from `models.lock` while it builds and runs offline afterwards. Changing the model means a new `modelVersion` and a full reindex on every phone (ADR-003).
