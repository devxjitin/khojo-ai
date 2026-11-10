"""Classify complete PR/push diffs without skipping required workflows.

Outputs (all "true"/"false"): android, backend, infra, eval, publish.
Ported from ultron-android and extended for Khojo's backend, Terraform and eval areas.
"""
import json
import os
from pathlib import Path
import subprocess

FLAGS = ("android", "backend", "infra", "eval", "publish")


def classify(paths):
    result = dict.fromkeys(FLAGS, False)
    for path in paths:
        if path.startswith("android/") or path == "scripts/publish-update.ps1":
            result.update(android=True, publish=True)
        if path.startswith(("inference-service/", "lambdas/")) and not path.endswith(".md"):
            result["backend"] = True
        if path.startswith("infrastructure/"):
            result["infra"] = True
        # Anything that can change search quality must pass the eval gate.
        if path.startswith(("eval/", "inference-service/")) or "/search/" in path or path.endswith("models.lock"):
            result["eval"] = True
        if path in (".github/workflows/checks.yml", "scripts/ci_changes.py") or path.startswith("tests/ci/"):
            result.update(android=True, backend=True, infra=True, eval=True)
        if path == ".github/workflows/publish-ota.yml" or path.startswith(".github/actions/"):
            result.update(android=True, backend=True, infra=True, eval=True, publish=True)
    return result


def plan(event_name, event, diff):
    if event_name == "pull_request":
        base, head, triple = event["pull_request"]["base"]["sha"], event["pull_request"]["head"]["sha"], True
    elif event_name == "push" and event.get("before", "").strip("0"):
        base, head, triple = event["before"], event["after"], False
    else:
        return dict.fromkeys(FLAGS, True)
    try:
        return classify(diff(base, head, triple))
    except subprocess.CalledProcessError:
        return dict.fromkeys(FLAGS, True)


def git_diff(base, head, triple):
    raw = subprocess.check_output(["git", "diff", "--no-renames", "--name-only", "-z", base + ("..." if triple else "..") + head])
    return [name.decode("utf-8", errors="surrogateescape") for name in raw.split(b"\0") if name]


def main():
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    result = plan(os.environ["GITHUB_EVENT_NAME"], event, git_diff)
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        for name, enabled in result.items():
            output.write(f"{name}={str(enabled).lower()}\n")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
