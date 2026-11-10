"""Search-quality evaluation (skeleton).

Sprint 1: loads queries.csv + labels and reports metrics once an index exists.
Until a real model run is wired in (Sprint 2 benchmark / Sprint 4 service), --quick passes when no
baseline.json exists yet, so the CI gate is switched on but cannot block anything prematurely.

Metrics:
  Recall@5  - a relevant item appears in the top 5
  MRR@10    - reciprocal rank of the first relevant item within the top 10
"""
import argparse
import csv
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent


def recall_at_k(ranked, relevant, k=5):
    return 1.0 if any(item in relevant for item in ranked[:k]) else 0.0


def mrr_at_k(ranked, relevant, k=10):
    for position, item in enumerate(ranked[:k], start=1):
        if item in relevant:
            return 1.0 / position
    return 0.0


def evaluate(results):
    """results: list of (ranked_ids, relevant_ids). Returns averaged metrics."""
    if not results:
        return {"recall_at_5": 0.0, "mrr_at_10": 0.0, "queries": 0}
    n = len(results)
    return {
        "recall_at_5": sum(recall_at_k(r, rel) for r, rel in results) / n,
        "mrr_at_10": sum(mrr_at_k(r, rel) for r, rel in results) / n,
        "queries": n,
    }


def parse_relevant(cell):
    """queries.csv stores several correct answers in one cell, separated by |."""
    return {part.strip() for part in (cell or "").split("|") if part.strip()}


def percentile(values, pct):
    """Nearest-rank percentile (pct 0-100) of a list of numbers; None for an empty list."""
    if not values:
        return None
    ordered = sorted(values)
    rank = max(1, -(-len(ordered) * pct // 100))  # ceil(n * pct / 100)
    return ordered[int(rank) - 1]


def load_queries(path):
    with open(path, encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def compare(current, baseline, max_drop):
    """Returns a list of failure messages (empty = ok)."""
    failures = []
    for metric in ("recall_at_5", "mrr_at_10"):
        if current[metric] < baseline[metric] - max_drop:
            failures.append(f"{metric} dropped from {baseline[metric]:.3f} to {current[metric]:.3f} (allowed drop {max_drop})")
    return failures


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--quick", action="store_true", help="small cached set for PR checks")
    parser.add_argument("--baseline", default=str(HERE / "baseline.json"))
    parser.add_argument("--max-drop", type=float, default=0.02)
    parser.add_argument("--queries", default=str(HERE / "queries.csv"))
    args = parser.parse_args(argv)

    queries = load_queries(args.queries) if Path(args.queries).exists() else []
    print(f"Loaded {len(queries)} queries from {args.queries}")

    baseline_path = Path(args.baseline)
    if not baseline_path.exists():
        print("No baseline.json yet (created after the Sprint 2 benchmark); gate passes.")
        return 0

    # Real model results are wired in by the benchmark sprint; fail loudly rather than pass silently once a baseline exists.
    print("baseline.json exists but no results runner is wired in yet.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
