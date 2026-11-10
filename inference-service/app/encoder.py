"""Turns text and images into unit-length vectors. Text and images use the same checkpoint (blueprint 7.2)."""
import hashlib
import io
import threading
from typing import Protocol

import numpy as np
from PIL import Image

from app.config import ModelLock


class Encoder(Protocol):
    name: str

    def encode_text(self, text: str) -> list[float]: ...

    def encode_image(self, data: bytes) -> list[float]: ...


def normalize(vector: np.ndarray) -> list[float]:
    norm = float(np.linalg.norm(vector))
    if norm == 0.0:
        raise ValueError("The model returned a zero vector.")
    return (vector / norm).astype(np.float32).tolist()


def feature_vector(features, dimensions: int) -> list[float]:
    """Newer transformers return an output object (the vector is its pooler_output); older ones return the tensor."""
    tensor = features if hasattr(features, "detach") else features.pooler_output
    vector = tensor.detach().cpu().numpy()[0]
    if vector.shape[0] != dimensions:
        raise RuntimeError(f"Model gave {vector.shape[0]} numbers, models.lock says {dimensions}.")
    return normalize(vector)


def open_image(data: bytes) -> Image.Image:
    """Raises ValueError for anything that is not a readable image."""
    try:
        image = Image.open(io.BytesIO(data))
        image.load()
    except Exception as error:
        raise ValueError("The file is not a readable image.") from error
    return image.convert("RGB")


class FakeEncoder:
    """Same input gives the same vector, with the right size and length. For tests and CI: no model download."""

    name = "fake"

    def __init__(self, lock: ModelLock):
        self._dimensions = lock.dimensions

    def _vector(self, seed_bytes: bytes) -> list[float]:
        seed = int.from_bytes(hashlib.sha256(seed_bytes).digest()[:4], "big")
        return normalize(np.random.default_rng(seed).standard_normal(self._dimensions))

    def encode_text(self, text: str) -> list[float]:
        return self._vector(b"text:" + text.lower().encode("utf-8"))

    def encode_image(self, data: bytes) -> list[float]:
        open_image(data)
        return self._vector(b"image:" + data)


class SiglipEncoder:
    """The real SigLIP 2 model, loaded once at start from the pinned revision."""

    name = "siglip"

    def __init__(self, lock: ModelLock):
        import torch
        from transformers import AutoModel, AutoProcessor

        self._torch = torch
        self._lock = lock
        self._processor = AutoProcessor.from_pretrained(lock.model_id, revision=lock.revision)
        self._model = AutoModel.from_pretrained(lock.model_id, revision=lock.revision).eval()
        # One request at a time through the model: predictable memory on a small CPU instance.
        self._gate = threading.Lock()

    def encode_text(self, text: str) -> list[float]:
        inputs = self._processor(
            text=[text.lower()], padding="max_length", max_length=self._lock.text_max_length,
            truncation=True, return_tensors="pt",
        )
        with self._gate, self._torch.inference_mode():
            return feature_vector(self._model.get_text_features(**inputs), self._lock.dimensions)

    def encode_image(self, data: bytes) -> list[float]:
        image = open_image(data)
        inputs = self._processor(images=image, return_tensors="pt")
        with self._gate, self._torch.inference_mode():
            return feature_vector(self._model.get_image_features(**inputs), self._lock.dimensions)


def build_encoder(kind: str, lock: ModelLock) -> Encoder:
    if kind == "fake":
        return FakeEncoder(lock)
    if kind == "siglip":
        return SiglipEncoder(lock)
    raise ValueError(f"Unknown KHOJO_ENCODER '{kind}' (use 'siglip' or 'fake').")
