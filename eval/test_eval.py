import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import benchmark_siglip as bench  # noqa: E402
import hinglish  # noqa: E402
import run_eval  # noqa: E402


class MetricTests(unittest.TestCase):
    def test_recall_and_mrr(self):
        self.assertEqual(1.0, run_eval.recall_at_k(["a", "b", "c", "d", "e", "x"], {"e"}))
        self.assertEqual(0.0, run_eval.recall_at_k(["a", "b", "c", "d", "e", "x"], {"x"}))
        self.assertEqual(0.5, run_eval.mrr_at_k(["a", "x"], {"x"}))
        self.assertEqual(0.0, run_eval.mrr_at_k(["a"] * 10 + ["x"], {"x"}))

    def test_evaluate_averages(self):
        got = run_eval.evaluate([(["a"], {"a"}), (["b"], {"zzz"})])
        self.assertEqual(0.5, got["recall_at_5"])
        self.assertEqual(2, got["queries"])

    def test_compare_flags_only_drops_beyond_tolerance(self):
        base = {"recall_at_5": 0.90, "mrr_at_10": 0.80}
        self.assertEqual([], run_eval.compare({"recall_at_5": 0.885, "mrr_at_10": 0.80}, base, 0.02))
        self.assertEqual(1, len(run_eval.compare({"recall_at_5": 0.85, "mrr_at_10": 0.80}, base, 0.02)))

    def test_parse_relevant_and_percentile(self):
        self.assertEqual({"a1", "b2"}, run_eval.parse_relevant(" a1 | b2 |"))
        self.assertEqual(set(), run_eval.parse_relevant(None))
        self.assertEqual(95, run_eval.percentile(list(range(1, 101)), 95))
        self.assertEqual(50, run_eval.percentile(list(range(1, 101)), 50))
        self.assertIsNone(run_eval.percentile([], 95))

    def test_ranking_and_per_language_summary(self):
        self.assertEqual(["b", "c", "a"], bench.ranked_ids([0.1, 0.9, 0.5], ["a", "b", "c"]))
        got = bench.summarize([("en", ["a"], {"a"}), ("hi", ["x"], {"a"})])
        self.assertEqual(0.5, got["ALL"]["recall_at_5"])
        self.assertEqual(1.0, got["en"]["recall_at_5"])
        self.assertEqual(0.0, got["hi"]["recall_at_5"])


class HinglishTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mapping, cls.stop = hinglish.load_dictionary()

    def rewrite(self, query):
        return hinglish.normalize(query, self.mapping, self.stop)["rewrite"]

    def test_examples_from_the_blueprint(self):
        self.assertEqual("red saree golden border", self.rewrite("laal saree golden border wali"))
        self.assertEqual("tree", self.rewrite("ped wali photo"))
        self.assertEqual("black kurti golden embroidery", self.rewrite("kali kurti sunehri kadhai wali photo dikhao"))

    def test_devanagari_words_are_not_split(self):
        self.assertEqual("red saree", self.rewrite("लाल साड़ी"))
        self.assertEqual("red saree", self.rewrite("लाल साड़ी की फोटो"))

    def test_unknown_words_pass_through_and_original_is_kept(self):
        out = hinglish.normalize("Meesho banarasi lehnga", self.mapping, self.stop)
        self.assertEqual("meesho banarasi lehenga", out["rewrite"])
        self.assertEqual("Meesho banarasi lehnga", out["original"])

    def test_all_stopwords_falls_back_to_original(self):
        self.assertEqual("mera wali photo", self.rewrite("mera wali photo"))

    def test_rrf_prefers_items_ranked_well_by_both(self):
        self.assertEqual("b", hinglish.rrf([["b", "a", "c"], ["b", "c", "a"]])[0])
        self.assertEqual("x", hinglish.rrf([["x", "y"], ["z", "x"]])[0])  # second in one list, first in the other beats singles


class DictionaryTests(unittest.TestCase):
    def test_no_duplicate_keys_and_clean_format(self):
        def reject_duplicates(pairs):
            keys = [k for k, _ in pairs]
            dupes = {k for k in keys if keys.count(k) > 1}
            self.assertFalse(dupes, f"duplicate keys: {dupes}")
            return dict(pairs)

        path = Path(__file__).resolve().parent / "hinglish_dictionary_v0.json"
        data = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=reject_duplicates)
        for key, value in data["map"].items():
            self.assertEqual(key, key.lower().strip(), key)
            self.assertTrue(value.strip(), key)
        self.assertEqual(len(data["stopwords"]), len(set(data["stopwords"])), "duplicate stopwords")
        self.assertFalse(set(data["map"]) & set(data["stopwords"]), "a word cannot be both mapped and dropped")


if __name__ == "__main__":
    unittest.main()
