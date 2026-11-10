"""Reference Hinglish query normalizer (Python). The production version is Kotlin (QueryNormalizer.kt, Sprint 5);
this one exists so the benchmark can measure original vs rewrite vs dual-embedding retrieval before that is built.

Strategy (blueprint 7.3): dictionary rewrite to English, grammar words dropped; the original query is always kept and
both are embedded, then merged with Reciprocal Rank Fusion.
"""
import json
import re
from pathlib import Path

DEFAULT_DICTIONARY = Path(__file__).resolve().parent / "hinglish_dictionary_v0.json"
# Split on whitespace and punctuation, NOT with \w: Devanagari vowel signs are not "word characters" and would cut words apart.
_SPLIT = re.compile(r"[\s,.;:!?\"'()\[\]{}|/\\-]+")


def load_dictionary(path=DEFAULT_DICTIONARY):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    return data["map"], set(data["stopwords"])


def tokenize(text):
    return [t for t in _SPLIT.split(text.lower()) if t]


def normalize(query, mapping, stopwords):
    """Returns {"original": ..., "rewrite": ...}. The rewrite falls back to the original when nothing is left."""
    kept = []
    for token in tokenize(query):
        if token in mapping:
            kept.append(mapping[token])
        elif token in stopwords:
            continue
        else:
            kept.append(token)
    rewrite = " ".join(kept).strip()
    return {"original": query, "rewrite": rewrite or query}


def rrf(rankings, k=60):
    """Reciprocal Rank Fusion of several ranked id lists (best first). Returns ids best first.

    Raw scores from different queries or models are never compared directly; only ranks are (blueprint 10.1).
    """
    scores = {}
    for ranking in rankings:
        for position, item in enumerate(ranking, start=1):
            scores[item] = scores.get(item, 0.0) + 1.0 / (k + position)
    return sorted(scores, key=lambda item: (-scores[item], str(item)))
