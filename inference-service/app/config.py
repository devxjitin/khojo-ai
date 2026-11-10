"""Reads models.lock: the one place that says which model (and which exact revision) this service runs."""
import json
from dataclasses import dataclass
from pathlib import Path

LOCK_PATH = Path(__file__).resolve().parent.parent / "models.lock"


@dataclass(frozen=True)
class ModelLock:
    model_id: str
    revision: str
    dimensions: int
    model_version: str
    text_max_length: int


def load_lock(path: Path = LOCK_PATH) -> ModelLock:
    data = json.loads(path.read_text(encoding="utf-8"))
    return ModelLock(
        model_id=data["modelId"],
        revision=data["revision"],
        dimensions=int(data["dimensions"]),
        model_version=data["modelVersion"],
        text_max_length=int(data["textMaxLength"]),
    )
