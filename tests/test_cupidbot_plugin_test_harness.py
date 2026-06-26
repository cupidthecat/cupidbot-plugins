import importlib.util
import os
import sys
import textwrap
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "cupidbot_plugin_test_harness.py"
spec = importlib.util.spec_from_file_location("cupidbot_plugin_test_harness", SCRIPT_PATH)
harness = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = harness
spec.loader.exec_module(harness)


class StaticScanTest(unittest.TestCase):
    def test_flags_broad_catch_that_logs_shutdown_interrupt(self):
        with TemporaryDirectory() as tmp:
            source_root = Path(tmp)
            java_file = source_root / "net/runelite/client/plugins/cupidbot/foo/FooScript.java"
            java_file.parent.mkdir(parents=True)
            java_file.write_text(
                textwrap.dedent(
                    """
                    package net.runelite.client.plugins.cupidbot.foo;

                    class FooScript extends Script {
                        void tick() {
                            try {
                                walk();
                            } catch (RuntimeException ex) {
                                log.warn("tick failed", ex);
                            }
                        }
                    }
                    """
                ).strip()
                + "\n",
                encoding="utf-8",
            )

            findings = harness.find_shutdown_interrupt_findings(source_root)

        self.assertEqual(1, len(findings))
        self.assertEqual("shutdown-interrupt-logged", findings[0].code)
        self.assertEqual("FooScript.java", Path(findings[0].file).name)
        self.assertIn("RuntimeException ex", findings[0].message)

    def test_allows_broad_catch_with_script_interruption_guard(self):
        with TemporaryDirectory() as tmp:
            source_root = Path(tmp)
            java_file = source_root / "net/runelite/client/plugins/cupidbot/foo/FooScript.java"
            java_file.parent.mkdir(parents=True)
            java_file.write_text(
                textwrap.dedent(
                    """
                    package net.runelite.client.plugins.cupidbot.foo;

                    class FooScript extends Script {
                        void tick() {
                            try {
                                walk();
                            } catch (RuntimeException ex) {
                                if (Script.isInterruption(ex)) {
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                                log.warn("tick failed", ex);
                            }
                        }
                    }
                    """
                ).strip()
                + "\n",
                encoding="utf-8",
            )

            findings = harness.find_shutdown_interrupt_findings(source_root)

        self.assertEqual([], findings)

    def test_skips_non_lifecycle_overlay_catches(self):
        with TemporaryDirectory() as tmp:
            source_root = Path(tmp)
            java_file = source_root / "net/runelite/client/plugins/cupidbot/foo/FooOverlay.java"
            java_file.parent.mkdir(parents=True)
            java_file.write_text(
                textwrap.dedent(
                    """
                    package net.runelite.client.plugins.cupidbot.foo;

                    class FooOverlay {
                        void render() {
                            try {
                                draw();
                            } catch (Exception ex) {
                                log.warn("render failed", ex);
                            }
                        }
                    }
                    """
                ).strip()
                + "\n",
                encoding="utf-8",
            )

            findings = harness.find_shutdown_interrupt_findings(source_root)

        self.assertEqual([], findings)

    def test_flags_missing_image_resource_reference(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            source_root = root / "src/main/java"
            resource_root = root / "src/main/resources"
            java_file = source_root / "net/runelite/client/plugins/cupidbot/foo/FooOverlay.java"
            java_file.parent.mkdir(parents=True)
            java_file.write_text(
                textwrap.dedent(
                    """
                    package net.runelite.client.plugins.cupidbot.foo;

                    class FooOverlay {
                        void render() {
                            ImageUtil.loadImageResource(FooPlugin.class, "/net/runelite/client/plugins/cupidbot/foo/missing.png");
                        }
                    }
                    """
                ).strip()
                + "\n",
                encoding="utf-8",
            )

            findings = harness.find_missing_image_resource_findings(source_root, resource_root)

        self.assertEqual(1, len(findings))
        self.assertEqual("missing-image-resource", findings[0].code)
        self.assertIn("missing.png", findings[0].message)

    def test_allows_existing_image_resource_reference(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            source_root = root / "src/main/java"
            resource_root = root / "src/main/resources"
            java_file = source_root / "net/runelite/client/plugins/cupidbot/foo/FooOverlay.java"
            java_file.parent.mkdir(parents=True)
            java_file.write_text(
                textwrap.dedent(
                    """
                    package net.runelite.client.plugins.cupidbot.foo;

                    class FooOverlay {
                        void render() {
                            ImageUtil.loadImageResource(FooPlugin.class, "icon.png");
                        }
                    }
                    """
                ).strip()
                + "\n",
                encoding="utf-8",
            )
            image_file = resource_root / "net/runelite/client/plugins/cupidbot/foo/icon.png"
            image_file.parent.mkdir(parents=True)
            image_file.write_bytes(b"not a real png but path exists")

            findings = harness.find_missing_image_resource_findings(source_root, resource_root)

        self.assertEqual([], findings)

    def test_resolves_image_resource_against_referenced_class_package(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            source_root = root / "src/main/java"
            resource_root = root / "src/main/resources"
            plugin_file = source_root / "net/runelite/client/plugins/cupidbot/foo/FooPlugin.java"
            plugin_file.parent.mkdir(parents=True)
            plugin_file.write_text(
                "package net.runelite.client.plugins.cupidbot.foo;\npublic class FooPlugin {}\n",
                encoding="utf-8",
            )
            helper_file = source_root / "net/runelite/client/plugins/cupidbot/foo/model/FooHeader.java"
            helper_file.parent.mkdir(parents=True)
            helper_file.write_text(
                textwrap.dedent(
                    """
                    package net.runelite.client.plugins.cupidbot.foo.model;

                    class FooHeader {
                        void render() {
                            ImageUtil.loadImageResource(FooPlugin.class, "arrow.png");
                        }
                    }
                    """
                ).strip()
                + "\n",
                encoding="utf-8",
            )
            image_file = resource_root / "net/runelite/client/plugins/cupidbot/foo/arrow.png"
            image_file.parent.mkdir(parents=True)
            image_file.write_bytes(b"not a real png but path exists")

            findings = harness.find_missing_image_resource_findings(source_root, resource_root)

        self.assertEqual([], findings)

    def test_flags_docs_image_resource_even_when_source_file_exists(self):
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            source_root = root / "src/main/java"
            resource_root = root / "src/main/resources"
            java_file = source_root / "net/runelite/client/plugins/cupidbot/foo/FooPlugin.java"
            java_file.parent.mkdir(parents=True)
            java_file.write_text(
                textwrap.dedent(
                    """
                    package net.runelite.client.plugins.cupidbot.foo;

                    class FooPlugin {
                        void startUp() {
                            ImageUtil.loadImageResource(getClass(), "docs/assets/icon.jpg");
                        }
                    }
                    """
                ).strip()
                + "\n",
                encoding="utf-8",
            )
            image_file = resource_root / "net/runelite/client/plugins/cupidbot/foo/docs/assets/icon.jpg"
            image_file.parent.mkdir(parents=True)
            image_file.write_bytes(b"not a real jpg but path exists")

            findings = harness.find_missing_image_resource_findings(source_root, resource_root)

        self.assertEqual(1, len(findings))
        self.assertEqual("unbundled-image-resource", findings[0].code)
        self.assertIn("docs/assets/icon.jpg", findings[0].message)


class PluginDiscoveryTest(unittest.TestCase):
    def test_discovers_manifest_plugin_class_from_source_package(self):
        with TemporaryDirectory() as tmp:
            source_root = Path(tmp)
            plugin_file = source_root / "net/runelite/client/plugins/cupidbot/foo/FooPlugin.java"
            plugin_file.parent.mkdir(parents=True)
            plugin_file.write_text(
                textwrap.dedent(
                    """
                    package net.runelite.client.plugins.cupidbot.foo;

                    public class FooPlugin extends Plugin {
                    }
                    """
                ).strip()
                + "\n",
                encoding="utf-8",
            )
            disabled_file = source_root / "net/runelite/client/plugins/cupidbot/bar/DisabledPlugin.java"
            disabled_file.parent.mkdir(parents=True)
            disabled_file.write_text(
                textwrap.dedent(
                    """
                    package net.runelite.client.plugins.cupidbot.bar;

                    public class DisabledPlugin extends Plugin {
                    }
                    """
                ).strip()
                + "\n",
                encoding="utf-8",
            )
            manifest = [
                {"internalName": "FooPlugin", "name": "Foo", "disable": False},
                {"internalName": "DisabledPlugin", "name": "Disabled", "disable": True},
            ]

            targets = harness.discover_plugin_targets(source_root, manifest, include_disabled=False)

        self.assertEqual(1, len(targets))
        self.assertEqual("FooPlugin", targets[0].internal_name)
        self.assertEqual("net.runelite.client.plugins.cupidbot.foo.FooPlugin", targets[0].class_name)


class LogParseTest(unittest.TestCase):
    def test_flags_interrupted_waiting_for_client_thread_stack(self):
        log_text = textwrap.dedent(
            """
            2026-06-25 17:34:24 EDT [TheMessScript-1] WARN  n.r.c.p.cupidbot.mess.TheMessScript - tick failed: Interrupted waiting for client thread
            java.lang.RuntimeException: Interrupted waiting for client thread
                at net.runelite.client.callback.ClientThread.invoke(ClientThread.java:149)
            Caused by: java.lang.InterruptedException: null
            """
        ).strip()

        findings = harness.parse_log_findings(log_text)

        self.assertEqual(1, len(findings))
        self.assertEqual("log-shutdown-interrupt-stack", findings[0].code)
        self.assertIn("TheMessScript-1", findings[0].message)

    def test_flags_plugin_start_failure_uncaught_exception_and_shutdown(self):
        log_text = textwrap.dedent(
            """
            2026-06-25 18:19:48 EDT [AWT-EventQueue-0] ERROR n.r.c.p.c.a.handler.ScriptHandler - Failed to start plugin DiscordPlugin
            2026-06-25 18:40:42 EDT [AWT-EventQueue-0] ERROR n.r.client.plugins.PluginManager - Unable to start plugin DiscordPlugin
            2026-06-25 18:19:48 EDT [AWT-EventQueue-0] ERROR net.runelite.client.RuneLite - Uncaught exception:
            2026-06-25 18:23:53 EDT [AWT-EventQueue-0] INFO  n.r.c.p.c.e.CupidBotPluginManager - Client shutdown detected, stopping all CupidBot plugins
            """
        ).strip()

        findings = harness.parse_log_findings(log_text)

        self.assertEqual(
            ["log-plugin-start-failed", "log-plugin-start-failed", "log-uncaught-exception", "log-client-shutdown"],
            [finding.code for finding in findings],
        )

    def test_flags_generic_error_exception_stack(self):
        log_text = textwrap.dedent(
            """
            2026-06-25 18:48:59 EDT [PVirewatchScript-1] ERROR n.r.client.plugins.cupidbot.CupidBot - PVirewatchScript
            java.lang.NullPointerException: Cannot invoke "net.runelite.api.coords.WorldArea.contains(net.runelite.api.coords.WorldPoint)" because "plugin.fightArea" is null
                at net.runelite.client.plugins.cupidbot.virewatch.PVirewatchScript.lambda$run$3(PVirewatchScript.java:22)
            """
        ).strip()

        findings = harness.parse_log_findings(log_text)

        self.assertEqual(["log-error-exception-stack"], [finding.code for finding in findings])
        self.assertIn("PVirewatchScript-1", findings[0].message)
        self.assertIn("NullPointerException", findings[0].message)

    def test_flags_webwalk_interrupted_exit_as_warning(self):
        log_text = textwrap.dedent(
            """
            2026-06-25 18:43:29 EDT [AIOCamdozScript-1] INFO  n.r.c.p.c.util.walker.WebWalkLog - [WebWalk] interrupt | pathfinder interrupted (397)
            2026-06-25 18:43:29 EDT [AIOCamdozScript-1] WARN  n.r.c.p.c.util.walker.WebWalkLog - [WebWalk] exit | r=interrupted-exception nullCur=true mismatch=false intr=true goal=WorldPoint(x=2978, y=5798, plane=0)
            """
        ).strip()

        findings = harness.parse_log_findings(log_text)

        self.assertEqual(["log-webwalk-interrupted"], [finding.code for finding in findings])
        self.assertEqual("WARN", findings[0].severity)


class AgentTokenTest(unittest.TestCase):
    def test_resolves_token_from_explicit_argument_first(self):
        with TemporaryDirectory() as tmp:
            token_file = Path(tmp) / "token"
            token_file.write_text("file-token\n", encoding="utf-8")

            token = harness.resolve_agent_token("argument-token", token_file)

        self.assertEqual("argument-token", token)

    def test_resolves_token_from_env_before_file(self):
        old_token = os.environ.get("CUPIDBOT_TOKEN")
        try:
            os.environ["CUPIDBOT_TOKEN"] = "env-token"
            with TemporaryDirectory() as tmp:
                token_file = Path(tmp) / "token"
                token_file.write_text("file-token\n", encoding="utf-8")

                token = harness.resolve_agent_token(None, token_file)
        finally:
            if old_token is None:
                os.environ.pop("CUPIDBOT_TOKEN", None)
            else:
                os.environ["CUPIDBOT_TOKEN"] = old_token

        self.assertEqual("env-token", token)

    def test_resolves_token_from_file(self):
        old_token = os.environ.get("CUPIDBOT_TOKEN")
        try:
            os.environ.pop("CUPIDBOT_TOKEN", None)
            with TemporaryDirectory() as tmp:
                token_file = Path(tmp) / "token"
                token_file.write_text("file-token\n", encoding="utf-8")

                token = harness.resolve_agent_token(None, token_file)
        finally:
            if old_token is not None:
                os.environ["CUPIDBOT_TOKEN"] = old_token

        self.assertEqual("file-token", token)


class LifecycleScopeTest(unittest.TestCase):
    def test_rejects_unfiltered_lifecycle_scope(self):
        with self.assertRaisesRegex(RuntimeError, "starts real plugins"):
            harness.ensure_lifecycle_scope([], False)

    def test_allows_filtered_lifecycle_scope(self):
        harness.ensure_lifecycle_scope(["CupidBotDashboardPlusPlugin"], False)

    def test_allows_explicit_all_plugin_scope(self):
        harness.ensure_lifecycle_scope([], True)


class AuditAllTest(unittest.TestCase):
    def test_rejects_logged_in_state_without_live_account_opt_in(self):
        with self.assertRaisesRegex(RuntimeError, "live account"):
            harness.ensure_live_account_audit_allowed({"loggedIn": True}, False)

    def test_allows_logged_in_state_with_live_account_opt_in(self):
        harness.ensure_live_account_audit_allowed({"loggedIn": True}, True)

    def test_resume_targets_starts_at_requested_plugin(self):
        targets = [
            harness.PluginTarget("OnePlugin", "One", "pkg.OnePlugin"),
            harness.PluginTarget("TwoPlugin", "Two", "pkg.TwoPlugin"),
            harness.PluginTarget("ThreePlugin", "Three", "pkg.ThreePlugin"),
        ]

        resumed = harness.resume_targets(targets, "TwoPlugin")

        self.assertEqual(["TwoPlugin", "ThreePlugin"], [target.internal_name for target in resumed])

    def test_resume_targets_accepts_class_name_match(self):
        targets = [
            harness.PluginTarget("OnePlugin", "One", "pkg.OnePlugin"),
            harness.PluginTarget("TwoPlugin", "Two", "pkg.TwoPlugin"),
        ]

        resumed = harness.resume_targets(targets, "pkg.TwoPlugin")

        self.assertEqual(["TwoPlugin"], [target.internal_name for target in resumed])

    def test_resume_targets_rejects_unknown_plugin(self):
        targets = [harness.PluginTarget("OnePlugin", "One", "pkg.OnePlugin")]

        with self.assertRaisesRegex(RuntimeError, "resume-from"):
            harness.resume_targets(targets, "MissingPlugin")

    def test_hard_findings_stop_fail_fast_audit(self):
        findings = [
            harness.Finding("WARN", "log-shutdown-interrupt-stack", "shutdown noise"),
            harness.Finding("ERROR", "log-plugin-start-failed", "start failed"),
        ]

        self.assertTrue(harness.has_hard_findings(findings))
        self.assertTrue(harness.should_stop_audit(findings, continue_on_failure=False))
        self.assertFalse(harness.should_stop_audit(findings, continue_on_failure=True))

    def test_audit_records_result_when_agent_disappears_during_stop(self):
        old_http_json = harness._http_json
        requests = []

        def fake_http_json(base_url, method, path, payload=None, timeout=10.0, token=None):
            requests.append((method, path))
            if method == "POST" and path == "/scripts/stop":
                raise OSError("connection refused")
            if method == "GET" and path == "/scripts/status?className=pkg.OnePlugin":
                return 200, {"status": "RUNNING"}
            return 200, {"success": True}

        try:
            harness._http_json = fake_http_json
            with TemporaryDirectory() as tmp:
                report_path = Path(tmp) / "audit.json"
                targets = [harness.PluginTarget("OnePlugin", "One", "pkg.OnePlugin")]

                results, findings = harness.run_audit_targets(
                    targets,
                    "http://127.0.0.1:8081",
                    run_seconds=0,
                    post_stop_settle_seconds=0,
                    request_timeout=1,
                    token=None,
                    log_file=None,
                    continue_on_failure=False,
                    report_path=report_path,
                )
                report_exists = report_path.exists()
        finally:
            harness._http_json = old_http_json

        self.assertEqual(
            [
                ("GET", "/scripts"),
                ("POST", "/scripts/start"),
                ("GET", "/scripts/status?className=pkg.OnePlugin"),
                ("POST", "/scripts/stop"),
            ],
            requests,
        )
        self.assertEqual(1, len(results))
        self.assertFalse(results[0].stopped)
        self.assertTrue(results[0].hard_failure)
        self.assertEqual(["lifecycle-stop-failed"], [finding.code for finding in findings])
        self.assertTrue(report_exists)

    def test_audit_stops_on_agent_loss_even_when_continuing_on_failures(self):
        old_http_json = harness._http_json
        requests = []

        def fake_http_json(base_url, method, path, payload=None, timeout=10.0, token=None):
            requests.append((method, path))
            if method == "POST" and path == "/scripts/stop":
                raise OSError("connection refused")
            if method == "GET" and path == "/scripts/status?className=pkg.OnePlugin":
                return 200, {"status": "RUNNING"}
            return 200, {"success": True}

        try:
            harness._http_json = fake_http_json
            targets = [
                harness.PluginTarget("OnePlugin", "One", "pkg.OnePlugin"),
                harness.PluginTarget("TwoPlugin", "Two", "pkg.TwoPlugin"),
            ]

            results, findings = harness.run_audit_targets(
                targets,
                "http://127.0.0.1:8081",
                run_seconds=0,
                post_stop_settle_seconds=0,
                request_timeout=1,
                token=None,
                log_file=None,
                continue_on_failure=True,
            )
        finally:
            harness._http_json = old_http_json

        self.assertEqual(1, len(results))
        self.assertEqual(["OnePlugin"], [result.plugin for result in results])
        self.assertEqual(["lifecycle-stop-failed"], [finding.code for finding in findings])
        self.assertEqual(
            [
                ("GET", "/scripts"),
                ("POST", "/scripts/start"),
                ("GET", "/scripts/status?className=pkg.OnePlugin"),
                ("POST", "/scripts/stop"),
            ],
            requests,
        )

    def test_audit_treats_not_running_stop_as_already_stopped(self):
        old_http_json = harness._http_json
        requests = []

        def fake_http_json(base_url, method, path, payload=None, timeout=10.0, token=None):
            requests.append((method, path))
            if method == "POST" and path == "/scripts/stop":
                return 400, {"error": "Plugin is not running"}
            if method == "GET" and path == "/scripts/status?className=pkg.OnePlugin":
                return 200, {"status": "STOPPED"}
            return 200, {"success": True}

        try:
            harness._http_json = fake_http_json
            targets = [harness.PluginTarget("OnePlugin", "One", "pkg.OnePlugin")]

            results, findings = harness.run_audit_targets(
                targets,
                "http://127.0.0.1:8081",
                run_seconds=0,
                post_stop_settle_seconds=0,
                request_timeout=1,
                token=None,
                log_file=None,
                continue_on_failure=False,
            )
        finally:
            harness._http_json = old_http_json

        self.assertEqual(
            [
                ("GET", "/scripts"),
                ("POST", "/scripts/start"),
                ("GET", "/scripts/status?className=pkg.OnePlugin"),
                ("POST", "/scripts/stop"),
            ],
            requests,
        )
        self.assertEqual(1, len(results))
        self.assertTrue(results[0].stopped)
        self.assertFalse(results[0].hard_failure)
        self.assertEqual([], [finding.code for finding in findings])

    def test_audit_treats_already_running_start_as_started(self):
        old_http_json = harness._http_json
        requests = []

        def fake_http_json(base_url, method, path, payload=None, timeout=10.0, token=None):
            requests.append((method, path))
            if method == "POST" and path == "/scripts/start":
                return 400, {"error": "Plugin is already running"}
            if method == "GET" and path == "/scripts/status?className=pkg.OnePlugin":
                return 200, {"status": "RUNNING"}
            return 200, {"success": True}

        try:
            harness._http_json = fake_http_json
            targets = [harness.PluginTarget("OnePlugin", "One", "pkg.OnePlugin")]

            results, findings = harness.run_audit_targets(
                targets,
                "http://127.0.0.1:8081",
                run_seconds=0,
                post_stop_settle_seconds=0,
                request_timeout=1,
                token=None,
                log_file=None,
                continue_on_failure=False,
            )
        finally:
            harness._http_json = old_http_json

        self.assertEqual(
            [
                ("GET", "/scripts"),
                ("POST", "/scripts/start"),
                ("GET", "/scripts/status?className=pkg.OnePlugin"),
                ("POST", "/scripts/stop"),
            ],
            requests,
        )
        self.assertEqual(1, len(results))
        self.assertTrue(results[0].started)
        self.assertTrue(results[0].stopped)
        self.assertFalse(results[0].hard_failure)
        self.assertEqual([], [finding.code for finding in findings])


if __name__ == "__main__":
    unittest.main()
