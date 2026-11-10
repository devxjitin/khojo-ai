import io
import json
import os
import unittest

os.environ["BUCKET"] = "khojo-test-bucket"
os.environ["BETA_KEY"] = "beta-secret"

import lambda_function as fn  # noqa: E402


class FakeError(Exception):
    def __init__(self, code):
        self.response = {"Error": {"Code": code}}


class FakeS3:
    def __init__(self, latest=None, error=None):
        self.latest, self.error, self.presigned = latest, error, []

    def get_object(self, Bucket, Key):
        assert Bucket == "khojo-test-bucket" and Key == "latest.json"
        if self.error:
            raise self.error
        return {"Body": io.BytesIO(json.dumps(self.latest).encode())}

    def generate_presigned_url(self, op, Params, ExpiresIn):
        self.presigned.append((op, Params, ExpiresIn))
        return f"https://signed.example/{Params['Key']}?exp={ExpiresIn}"


RELEASE = {"versionCode": 100, "versionName": "0.0.2", "sha256": "ab", "sizeBytes": 5, "apkKey": "apk/khojo-100.apk"}


def call(fake, headers=None, path="/v1/releases/latest"):
    fn._client = fake
    base = {"x-beta-key": "beta-secret", "x-install-id": "install-1"}
    base.update(headers or {})
    return fn.lambda_handler({"rawPath": path, "headers": base}, None)


class HandlerTests(unittest.TestCase):
    def test_wrong_or_missing_key_is_rejected(self):
        self.assertEqual(401, call(FakeS3(RELEASE), {"x-beta-key": "nope"})["statusCode"])
        self.assertEqual(401, call(FakeS3(RELEASE), {"x-beta-key": ""})["statusCode"])

    def test_header_names_are_case_insensitive(self):
        fn._client = FakeS3(RELEASE)
        got = fn.lambda_handler({"rawPath": "/v1/releases/latest", "headers": {"X-Beta-Key": "beta-secret"}}, None)
        self.assertEqual(200, got["statusCode"])

    def test_unknown_path_is_404(self):
        self.assertEqual(404, call(FakeS3(RELEASE), path="/other")["statusCode"])

    def test_returns_release_with_presigned_link_and_defaults(self):
        fake = FakeS3(dict(RELEASE))
        got = call(fake)
        body = json.loads(got["body"])
        self.assertEqual(200, got["statusCode"])
        self.assertEqual(100, body["versionCode"])
        self.assertEqual("https://signed.example/apk/khojo-100.apk?exp=1800", body["apkUrl"])
        self.assertEqual(100, body["rolloutPercent"])
        self.assertEqual(0, body["minSupportedVersionCode"])

    def test_nothing_published_is_404(self):
        self.assertEqual(404, call(FakeS3(error=FakeError("NoSuchKey")))["statusCode"])

    def test_other_s3_errors_are_not_hidden(self):
        with self.assertRaises(FakeError):
            call(FakeS3(error=FakeError("AccessDenied")))

    def test_manifest_cannot_point_at_another_object(self):
        bad = dict(RELEASE, apkKey="latest.json")
        self.assertEqual(500, call(FakeS3(bad))["statusCode"])
        bad = dict(RELEASE, apkKey="apk/../secrets.apk")
        self.assertEqual(500, call(FakeS3(bad))["statusCode"])

    def test_zero_percent_rollout_hides_release(self):
        self.assertEqual(404, call(FakeS3(dict(RELEASE, rolloutPercent=0)))["statusCode"])

    def test_partial_rollout_is_stable_per_install(self):
        release = dict(RELEASE, rolloutPercent=50)
        inside = next(i for i in map(str, range(1000)) if fn.rollout_bucket(i) < 50)
        outside = next(i for i in map(str, range(1000)) if fn.rollout_bucket(i) >= 50)
        self.assertEqual(200, call(FakeS3(dict(release)), {"x-install-id": inside})["statusCode"])
        self.assertEqual(404, call(FakeS3(dict(release)), {"x-install-id": outside})["statusCode"])
        self.assertEqual(404, call(FakeS3(dict(release)), {"x-install-id": ""})["statusCode"])

    def test_rollout_buckets_are_roughly_even(self):
        inside = sum(fn.in_rollout(str(i), 20) for i in range(5000))
        self.assertTrue(850 < inside < 1150, inside)


if __name__ == "__main__":
    unittest.main()
