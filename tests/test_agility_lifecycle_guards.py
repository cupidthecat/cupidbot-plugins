import pathlib
import unittest


class AgilityLifecycleGuardsTest(unittest.TestCase):
    def test_unsupported_course_path_does_not_sleep_after_warning(self):
        repo_root = pathlib.Path(__file__).resolve().parents[1]
        source = repo_root / "src/main/java/net/runelite/client/plugins/cupidbot/agility/AgilityScript.java"
        text = source.read_text(encoding="utf-8")

        self.assertNotIn(
            "sleep(10000)",
            text,
            "Agility setup failure paths must stop promptly so lifecycle audits can stop the plugin.",
        )


if __name__ == "__main__":
    unittest.main()
