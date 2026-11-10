import importlib.util
from pathlib import Path
import subprocess
import unittest

spec = importlib.util.spec_from_file_location("ci_changes", Path(__file__).resolve().parents[2] / "scripts/ci_changes.py")
changes = importlib.util.module_from_spec(spec)
spec.loader.exec_module(changes)

PR = {"pull_request": {"base": {"sha": "b"}, "head": {"sha": "h"}}}


def only(**true):
    return {name: name in true for name in changes.FLAGS}


class ClassifyTests(unittest.TestCase):
    def test_docs_only_runs_nothing_expensive(self):
        self.assertEqual(only(), changes.classify(["docs/Khojo_AI_Delivery_Plan.md", "README.md"]))

    def test_android_change_publishes_an_apk(self):
        self.assertEqual(only(android=True, publish=True), changes.classify(["android/app/build.gradle.kts"]))

    def test_publish_script_counts_as_android(self):
        self.assertEqual(only(android=True, publish=True), changes.classify(["scripts/publish-update.ps1"]))

    def test_backend_code_runs_backend_and_eval_but_not_publish(self):
        self.assertEqual(only(backend=True, eval=True), changes.classify(["inference-service/app/main.py"]))

    def test_lambda_markdown_is_not_backend(self):
        self.assertEqual(only(), changes.classify(["lambdas/khojo-updates/README.md"]))

    def test_terraform_runs_only_infra(self):
        self.assertEqual(only(infra=True), changes.classify(["infrastructure/envs/prod/main.tf"]))

    def test_android_search_code_triggers_eval_gate(self):
        got = changes.classify(["android/app/src/main/java/com/khojoai/app/search/ResultRanker.kt"])
        self.assertTrue(got["eval"] and got["android"])

    def test_model_lock_triggers_eval(self):
        self.assertTrue(changes.classify(["inference-service/models.lock"])["eval"])

    def test_checks_workflow_change_validates_everything_without_publishing(self):
        self.assertEqual(only(android=True, backend=True, infra=True, eval=True), changes.classify([".github/workflows/checks.yml"]))

    def test_publish_workflow_change_validates_and_publishes(self):
        self.assertEqual(dict.fromkeys(changes.FLAGS, True), changes.classify([".github/workflows/publish-ota.yml"]))


class PlanTests(unittest.TestCase):
    def test_pull_request_uses_three_dot_diff(self):
        seen = {}

        def diff(base, head, triple):
            seen.update(base=base, head=head, triple=triple)
            return ["infrastructure/x.tf"]

        self.assertEqual(only(infra=True), changes.plan("pull_request", PR, diff))
        self.assertEqual(dict(base="b", head="h", triple=True), seen)

    def test_new_branch_push_runs_everything(self):
        event = {"before": "0" * 40, "after": "a" * 40}
        self.assertEqual(dict.fromkeys(changes.FLAGS, True), changes.plan("push", event, lambda *a: []))

    def test_manual_run_runs_everything(self):
        self.assertEqual(dict.fromkeys(changes.FLAGS, True), changes.plan("workflow_dispatch", {}, lambda *a: []))

    def test_diff_failure_fails_safe_to_everything(self):
        def boom(*a):
            raise subprocess.CalledProcessError(1, "git")

        self.assertEqual(dict.fromkeys(changes.FLAGS, True), changes.plan("pull_request", PR, boom))


if __name__ == "__main__":
    unittest.main()
