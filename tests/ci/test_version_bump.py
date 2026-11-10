import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("check_version_bump", Path(__file__).resolve().parents[2] / "scripts/check_version_bump.py")
bump = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bump)

OLD = "# Changelog\n\n## 0.1.0 - 2026-12-14\n\n### Added\n- x\n\n## 0.0.2\n"


class VersionBumpTests(unittest.TestCase):
    def test_top_version_is_the_first_heading(self):
        self.assertEqual((0, 1, 0), bump.top_version(OLD))
        self.assertIsNone(bump.top_version("# Changelog\nnothing here"))

    def test_unchanged_version_is_rejected(self):
        self.assertIn("still 0.1.0", bump.check(OLD, OLD))

    def test_older_version_is_rejected(self):
        self.assertIsNotNone(bump.check(OLD, OLD.replace("0.1.0", "0.0.9")))

    def test_newer_version_passes_and_compares_numbers_not_text(self):
        self.assertIsNone(bump.check(OLD, "## 0.1.1\n" + OLD))
        self.assertIsNone(bump.check("## 1.9.0\n", "## 1.10.0\n## 1.9.0\n"))

    def test_missing_heading_is_rejected(self):
        self.assertIn("no '## x.y.z'", bump.check(OLD, "# Changelog\n"))


if __name__ == "__main__":
    unittest.main()
