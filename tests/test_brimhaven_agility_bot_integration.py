import pathlib
import unittest


class BrimhavenAgilityBotIntegrationTest(unittest.TestCase):
    def setUp(self):
        self.repo_root = pathlib.Path(__file__).resolve().parents[1]
        self.plugin_dir = self.repo_root / (
            "src/main/java/net/runelite/client/plugins/cupidbot/brimhavenagilitybot"
        )

    def test_descriptor_uses_literal_version_for_hub_metadata(self):
        plugin_source = self.plugin_dir / "BrimhavenAgilityBotPlugin.java"
        text = plugin_source.read_text(encoding="utf-8")

        self.assertIn('version = "1.0.1"', text)
        self.assertIn('VERSION = "1.0.1"', text)
        self.assertIn('minClientVersion = "2.6.12"', text)

    def test_travel_config_exposes_lumbridge_fallback(self):
        config_source = self.plugin_dir / "BrimhavenAgilityBotConfig.java"
        text = config_source.read_text(encoding="utf-8")

        self.assertIn("travelSection", text)
        self.assertIn("useLumbridgeHomeTeleport", text)
        self.assertIn("lumbridgeTeleportDistance", text)

    def test_layout_resource_lives_in_plugin_resource_package(self):
        layout = self.repo_root / (
            "src/main/resources/net/runelite/client/plugins/cupidbot/"
            "brimhavenagilitybot/arena_layout.txt"
        )

        self.assertTrue(layout.exists(), "arena_layout.txt must be copied into the plugin resource package")
        self.assertIn("00l10", layout.read_text(encoding="utf-8"))

    def test_script_loop_has_no_long_blocking_sleeps(self):
        script_source = self.plugin_dir / "BrimhavenAgilityBotScript.java"
        text = script_source.read_text(encoding="utf-8")

        self.assertNotIn("sleep(10000)", text)
        self.assertNotIn("Thread.sleep", text)

    def test_script_uses_travel_route_helper_instead_of_direct_brimhaven_walk(self):
        script_source = self.plugin_dir / "BrimhavenAgilityBotScript.java"
        text = script_source.read_text(encoding="utf-8")

        self.assertIn("BrimhavenTravelRoute.nextStep", text)
        self.assertIn("TAKE_BOAT_TO_MUSA_POINT", text)
        self.assertNotIn("Rs2Walker.walkTo(BrimhavenAgilityBotPlugin.ARENA_ENTRANCE_POINT, 4);", text)


if __name__ == "__main__":
    unittest.main()
