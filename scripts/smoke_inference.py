"""Smoke test for a deployed inference service (staging Function URL, AWS_IAM auth).

Usage: python scripts/smoke_inference.py <function-url>
Needs AWS credentials in the environment (the deploy role) and: pip install boto3 pillow.
Checks what blueprint 7.2 and the delivery plan promise: right vector size, unit length, same text gives the same
vector, a bad image is refused, and the URL refuses callers who do not sign the request.
"""
import io
import json
import math
import sys
import urllib.error
import urllib.request

import boto3
from botocore.auth import SigV4Auth
from botocore.awsrequest import AWSRequest

EXPECTED_DIMENSIONS = 768
# The first request after a cold start loads the model, so be patient.
TIMEOUT_SECONDS = 150


def call(url, method="GET", body=None, content_type="application/json", sign=True):
    headers = {"content-type": content_type} if body is not None else {}
    if sign:
        credentials = boto3.Session().get_credentials().get_frozen_credentials()
        request = AWSRequest(method=method, url=url, data=body, headers=headers)
        SigV4Auth(credentials, "lambda", boto3.Session().region_name or "ap-south-1").add_auth(request)
        headers = dict(request.headers)
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=body, headers=headers, method=method), timeout=TIMEOUT_SECONDS) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()


def check(condition, message):
    print(("PASS " if condition else "FAIL ") + message)
    if not condition:
        raise SystemExit(1)


def ok_json(status, body, what):
    """Fail with the real status and body instead of a confusing JSON error."""
    check(status == 200, f"{what} answers 200 (got {status}: {body[:200]!r})")
    return json.loads(body)


def png_bytes():
    from PIL import Image

    buffer = io.BytesIO()
    Image.new("RGB", (48, 48), (20, 120, 220)).save(buffer, format="PNG")
    return buffer.getvalue()


def main(base):
    base = base.rstrip("/")

    status, _ = call(base + "/healthz", sign=False)
    check(status in (401, 403), f"unsigned request is refused (got {status})")

    status, body = call(base + "/healthz")
    health = ok_json(status, body, "healthz")
    check(health["encoder"] == "siglip", f"healthz answers with the real model: {health}")
    check(health["dimensions"] == EXPECTED_DIMENSIONS, "healthz reports 768 dimensions")

    payload = json.dumps({"text": "Beach Sunset"}).encode()
    status, body = call(base + "/v1/embeddings/text", "POST", payload)
    first = ok_json(status, body, "text embedding")
    check(len(first["vector"]) == EXPECTED_DIMENSIONS, "text vector has 768 numbers")
    check(math.isclose(math.sqrt(sum(v * v for v in first["vector"])), 1.0, abs_tol=1e-3), "text vector has unit length")

    status, body = call(base + "/v1/embeddings/text", "POST", json.dumps({"text": "beach sunset"}).encode())
    second = ok_json(status, body, "second text embedding")
    cosine = sum(a * b for a, b in zip(first["vector"], second["vector"]))
    check(cosine > 0.999, f"same text in another case gives the same vector (cosine {cosine:.5f})")

    status, body = call(base + "/v1/embeddings/image", "POST", png_bytes(), "application/octet-stream")
    image = ok_json(status, body, "image embedding")
    check(len(image["vector"]) == EXPECTED_DIMENSIONS, "image vector has 768 numbers")
    check(image["modelVersion"] == first["modelVersion"], "text and image use the same modelVersion")

    status, _ = call(base + "/v1/embeddings/image", "POST", b"not an image", "application/octet-stream")
    check(status == 415, f"a file that is not an image is refused (got {status})")

    print("Smoke test passed.")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])
