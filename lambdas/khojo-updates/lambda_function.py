"""Tells the Khojo beta app whether a newer build exists and hands out a short-lived download link for it.

GET /v1/releases/latest   (HTTP API v2 event)
  Headers: x-beta-key (shared beta key), x-install-id (random id the app makes once per install)
  200 -> contents of s3://BUCKET/latest.json plus `apkUrl`, a presigned link to the APK (valid 30 minutes)
  404 -> nothing published yet, or this install is outside the staged rollout (the app treats both as "up to date")
  401 -> missing or wrong beta key

The bucket stays private. The beta key is a stopgap for the pilot: it is compiled into the beta APK, so treat it as
"keeps casual visitors out", not as a secret. Cognito sign-in replaces it in Sprint 7.

latest.json fields: versionCode, versionName, notes, sha256, sizeBytes, apkKey, sourceCommit, publishedAt,
  rolloutPercent (0-100, default 100), minSupportedVersionCode (default 0; older apps must update).

Env vars: BUCKET, BETA_KEY
"""
import hashlib
import hmac
import json
import os
import re

APK_KEY = re.compile(r"^apk/khojo-\d+\.apk$")
LINK_SECONDS = 1800
_client = None


def _s3():
    """Created on first use so tests can run without boto3 installed."""
    global _client
    if _client is None:
        import boto3
        from botocore.config import Config

        _client = boto3.client("s3", region_name=os.environ.get("AWS_REGION", "ap-south-1"), config=Config(signature_version="s3v4"))
    return _client


def _resp(status, body):
    return {"statusCode": status, "headers": {"content-type": "application/json", "cache-control": "no-store"}, "body": json.dumps(body)}


def rollout_bucket(install_id):
    """Stable 0-99 bucket for an install, so the same phone is always in or out of a staged rollout."""
    return int(hashlib.sha256(install_id.encode("utf-8")).hexdigest()[:8], 16) % 100


def in_rollout(install_id, percent):
    if percent >= 100:
        return True
    if percent <= 0 or not install_id:
        return False
    return rollout_bucket(install_id) < percent


def _authorized(headers):
    expected = os.environ.get("BETA_KEY", "")
    given = headers.get("x-beta-key", "")
    return bool(expected) and hmac.compare_digest(given.encode("utf-8"), expected.encode("utf-8"))


def _read_latest(bucket):
    try:
        body = _s3().get_object(Bucket=bucket, Key="latest.json")["Body"].read()
    except Exception as error:  # botocore ClientError without importing botocore at module load
        code = getattr(error, "response", {}).get("Error", {}).get("Code")
        if code in ("NoSuchKey", "404"):
            return None
        raise
    return json.loads(body)


def lambda_handler(event, context):
    if event.get("rawPath") != "/v1/releases/latest":
        return _resp(404, {"error": "not found"})
    headers = {str(k).lower(): v for k, v in (event.get("headers") or {}).items()}
    if not _authorized(headers):
        return _resp(401, {"error": "unauthorized"})

    info = _read_latest(os.environ["BUCKET"])
    if info is None:
        return _resp(404, {"error": "no release published yet"})
    if not APK_KEY.match(str(info.get("apkKey", ""))):
        return _resp(500, {"error": "invalid release manifest"})

    percent = int(info.get("rolloutPercent", 100))
    if not in_rollout(headers.get("x-install-id", ""), percent):
        return _resp(404, {"error": "no release for this install yet"})

    info.setdefault("rolloutPercent", 100)
    info.setdefault("minSupportedVersionCode", 0)
    info["apkUrl"] = _s3().generate_presigned_url(
        "get_object", Params={"Bucket": os.environ["BUCKET"], "Key": info["apkKey"]}, ExpiresIn=LINK_SECONDS
    )
    return _resp(200, info)
