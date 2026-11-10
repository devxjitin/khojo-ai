"""SigLIP 2 benchmark: Base vs So400m on your own photos and queries (Sprint 2C / blueprint 7.4).

Run on a GPU box (g4dn.xlarge, paid from AWS credits) and, separately, on CPU to compare latency:

    pip install -r eval/requirements-benchmark.txt
    python eval/benchmark_siglip.py --name base   --model-id google/siglip2-base-patch16-384   --revision <commit>
    python eval/benchmark_siglip.py --name so400m --model-id google/siglip2-so400m-patch14-384 --revision <commit>

Inputs (not committed; see eval/README.md):
    eval/data/manifest.csv  columns: image_id,path      (path relative to eval/data/)
    eval/queries.csv        columns: id,language,category,query,relevant_asset_ids   (ids joined with |)

The same checkpoint encodes images and text (blueprint 7.2). Text uses padding="max_length", max_length=64 and lowercase,
as the SigLIP model docs describe; vectors are L2-normalised. Appends one row per language plus ALL to the experiment log.
"""
import argparse
import csv
import statistics
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import hinglish  # noqa: E402
import run_eval  # noqa: E402

LOG_COLUMNS = [
    "experiment_id", "model_revision", "preprocess_revision", "dataset_version", "query_mode",
    "query_language", "query_category", "queries", "recall_at_5", "mrr_at_10",
    "image_index_p50_ms", "text_query_p95_ms", "memory_peak_mb", "cost_per_1000_queries_inr", "notes",
]


def ranked_ids(scores, ids):
    """ids sorted by score, best first."""
    return [ids[i] for i in sorted(range(len(ids)), key=lambda i: -scores[i])]


def summarize(rows):
    """rows: list of (language, ranked_ids, relevant_ids). Returns {language or "ALL": metrics}."""
    out = {"ALL": run_eval.evaluate([(r, rel) for _, r, rel in rows])}
    for language in sorted({lang for lang, _, _ in rows}):
        out[language] = run_eval.evaluate([(r, rel) for lang, r, rel in rows if lang == language])
    return out


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--name", required=True, help="short label, e.g. base or so400m")
    ap.add_argument("--model-id", required=True)
    ap.add_argument("--revision", default="main", help="pin the exact commit hash for a real run")
    ap.add_argument("--manifest", default=str(HERE / "data" / "manifest.csv"))
    ap.add_argument("--queries", default=str(HERE / "queries.csv"))
    ap.add_argument("--dataset-version", default="v1")
    ap.add_argument("--query-mode", choices=["original", "rewrite", "dual"], default="dual")
    ap.add_argument("--batch-size", type=int, default=16)
    ap.add_argument("--device", default="auto")
    ap.add_argument("--hourly-inr", type=float, default=55.0, help="instance price per hour in INR, for the cost column")
    ap.add_argument("--log", default=str(HERE / "reports" / "experiments.csv"))
    args = ap.parse_args(argv)

    import torch
    from PIL import Image
    from transformers import AutoModel, AutoProcessor

    device = ("cuda" if torch.cuda.is_available() else "cpu") if args.device == "auto" else args.device
    if args.revision == "main":
        print("WARNING: --revision is 'main'; pin the commit hash so the result is reproducible.", file=sys.stderr)
    processor = AutoProcessor.from_pretrained(args.model_id, revision=args.revision)
    model = AutoModel.from_pretrained(args.model_id, revision=args.revision).to(device).eval()
    if device == "cuda":
        torch.cuda.reset_peak_memory_stats()

    data_dir = Path(args.manifest).parent
    with open(args.manifest, encoding="utf-8", newline="") as handle:
        manifest = list(csv.DictReader(handle))
    ids = [row["image_id"] for row in manifest]

    image_vectors, per_image_ms = [], []
    with torch.no_grad():
        for start in range(0, len(manifest), args.batch_size):
            batch = manifest[start:start + args.batch_size]
            images = [Image.open(data_dir / row["path"]).convert("RGB") for row in batch]
            inputs = processor(images=images, return_tensors="pt").to(device)
            t0 = time.perf_counter()
            vec = model.get_image_features(**inputs)
            if device == "cuda":
                torch.cuda.synchronize()
            per_image_ms.append((time.perf_counter() - t0) * 1000 / len(batch))
            image_vectors.append(torch.nn.functional.normalize(vec, dim=-1).cpu())
    matrix = torch.cat(image_vectors)  # (images, dim)

    mapping, stopwords = hinglish.load_dictionary()
    queries = run_eval.load_queries(args.queries)
    text_ms, results = [], []

    def encode(text):
        inputs = processor(text=[text.lower()], padding="max_length", max_length=64, truncation=True, return_tensors="pt").to(device)
        t0 = time.perf_counter()
        with torch.no_grad():
            vec = model.get_text_features(**inputs)
        if device == "cuda":
            torch.cuda.synchronize()
        text_ms.append((time.perf_counter() - t0) * 1000)
        return torch.nn.functional.normalize(vec, dim=-1).cpu()[0]

    for q in queries:
        relevant = run_eval.parse_relevant(q.get("relevant_asset_ids"))
        if not relevant:
            continue  # unlabeled placeholder rows are skipped
        forms = hinglish.normalize(q["query"], mapping, stopwords)
        texts = {
            "original": [forms["original"]],
            "rewrite": [forms["rewrite"]],
            "dual": [forms["original"], forms["rewrite"]],
        }[args.query_mode]
        rankings = [ranked_ids((matrix @ encode(t)).tolist(), ids) for t in texts]
        results.append((q["language"], hinglish.rrf(rankings) if len(rankings) > 1 else rankings[0], relevant))

    if not results:
        sys.exit("No labeled queries found: fill relevant_asset_ids in queries.csv first.")

    memory_mb = round(torch.cuda.max_memory_allocated() / 2**20) if device == "cuda" else ""
    p95 = run_eval.percentile(text_ms, 95)
    cost = round(args.hourly_inr * (1000 * statistics.fmean(text_ms) / 3.6e6), 4)
    summary = summarize(results)

    log = Path(args.log)
    log.parent.mkdir(parents=True, exist_ok=True)
    new_file = not log.exists()
    with open(log, "a", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=LOG_COLUMNS)
        if new_file:
            writer.writeheader()
        for language, metrics in summary.items():
            writer.writerow({
                "experiment_id": f"{args.name}-{int(time.time())}",
                "model_revision": args.revision,
                "preprocess_revision": "hf-processor-default",
                "dataset_version": args.dataset_version,
                "query_mode": args.query_mode,
                "query_language": language,
                "query_category": "ALL",
                "queries": metrics["queries"],
                "recall_at_5": round(metrics["recall_at_5"], 4),
                "mrr_at_10": round(metrics["mrr_at_10"], 4),
                "image_index_p50_ms": round(run_eval.percentile(per_image_ms, 50), 1),
                "text_query_p95_ms": round(p95, 1),
                "memory_peak_mb": memory_mb,
                "cost_per_1000_queries_inr": cost,
                "notes": f"device={device}",
            })
            print(f"{language:9s} n={metrics['queries']:4d}  Recall@5={metrics['recall_at_5']:.3f}  MRR@10={metrics['mrr_at_10']:.3f}")
    print(f"image p50 {run_eval.percentile(per_image_ms, 50):.1f} ms/img, text p95 {p95:.1f} ms, logged to {log}")


if __name__ == "__main__":
    main()
