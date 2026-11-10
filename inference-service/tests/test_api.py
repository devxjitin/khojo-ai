import io
import math

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.config import load_lock
from app.encoder import FakeEncoder, build_encoder, feature_vector
from app.main import MAX_IMAGE_BYTES, create_app

LOCK = load_lock()


@pytest.fixture()
def client():
    with TestClient(create_app(encoder=FakeEncoder(LOCK), lock=LOCK)) as test_client:
        yield test_client


def png_bytes(color=(200, 30, 30)) -> bytes:
    buffer = io.BytesIO()
    Image.new("RGB", (32, 32), color).save(buffer, format="PNG")
    return buffer.getvalue()


def test_lock_is_pinned_to_an_exact_revision():
    assert len(LOCK.revision) == 40 and set(LOCK.revision) <= set("0123456789abcdef")
    assert LOCK.dimensions == 768
    assert LOCK.text_max_length == 64


def test_health_reports_the_model(client):
    body = client.get("/healthz").json()
    assert body == {"status": "ok", "encoder": "fake", "modelVersion": LOCK.model_version, "dimensions": 768}


def test_text_vector_has_the_right_size_and_unit_length(client):
    body = client.post("/v1/embeddings/text", json={"text": "beach sunset"}).json()
    assert body["dimensions"] == 768 and len(body["vector"]) == 768
    assert body["modelVersion"] == LOCK.model_version
    assert math.isclose(math.sqrt(sum(v * v for v in body["vector"])), 1.0, abs_tol=1e-4)
    assert math.isclose(body["l2Norm"], 1.0, abs_tol=1e-4)


def test_same_text_gives_the_same_vector_and_case_does_not_matter(client):
    first = client.post("/v1/embeddings/text", json={"text": "Beach Sunset"}).json()["vector"]
    second = client.post("/v1/embeddings/text", json={"text": "beach sunset"}).json()["vector"]
    other = client.post("/v1/embeddings/text", json={"text": "birthday cake"}).json()["vector"]
    assert first == second
    assert first != other


@pytest.mark.parametrize("payload", [{}, {"text": ""}, {"text": "   "}, {"text": "x" * 513}])
def test_bad_text_is_rejected(client, payload):
    assert client.post("/v1/embeddings/text", json=payload).status_code == 422


def test_image_vector_has_the_right_size_and_unit_length(client):
    response = client.post("/v1/embeddings/image", content=png_bytes(), headers={"content-type": "application/octet-stream"})
    body = response.json()
    assert response.status_code == 200
    assert len(body["vector"]) == 768
    assert math.isclose(body["l2Norm"], 1.0, abs_tol=1e-4)


def test_different_images_give_different_vectors(client):
    headers = {"content-type": "application/octet-stream"}
    red = client.post("/v1/embeddings/image", content=png_bytes((255, 0, 0)), headers=headers).json()["vector"]
    blue = client.post("/v1/embeddings/image", content=png_bytes((0, 0, 255)), headers=headers).json()["vector"]
    assert red != blue


def test_a_file_that_is_not_an_image_is_refused(client):
    response = client.post("/v1/embeddings/image", content=b"not an image", headers={"content-type": "application/octet-stream"})
    assert response.status_code == 415


def test_empty_and_oversized_images_are_refused(client):
    headers = {"content-type": "application/octet-stream"}
    assert client.post("/v1/embeddings/image", content=b"", headers=headers).status_code == 422
    assert client.post("/v1/embeddings/image", content=b"0" * (MAX_IMAGE_BYTES + 1), headers=headers).status_code == 413


def test_unknown_encoder_name_fails_loudly():
    with pytest.raises(ValueError):
        build_encoder("nonsense", LOCK)


class _Tensor:
    """Stands in for a torch tensor: detach().cpu().numpy() gives a batch of one vector."""

    def __init__(self, values):
        self._values = values

    def detach(self):
        return self

    def cpu(self):
        return self

    def numpy(self):
        import numpy as np

        return np.array([self._values], dtype=np.float32)


class _Output:
    def __init__(self, pooler_output):
        self.pooler_output = pooler_output


def test_feature_vector_accepts_a_tensor_and_the_newer_output_object():
    values = [3.0, 4.0] + [0.0] * 766
    from_tensor = feature_vector(_Tensor(values), 768)
    from_output = feature_vector(_Output(_Tensor(values)), 768)
    assert from_tensor == from_output
    assert math.isclose(from_tensor[0], 0.6, abs_tol=1e-6) and math.isclose(from_tensor[1], 0.8, abs_tol=1e-6)


def test_feature_vector_refuses_the_wrong_size():
    with pytest.raises(RuntimeError):
        feature_vector(_Tensor([1.0, 2.0]), 768)
