#!/usr/bin/env python3
from __future__ import annotations

import argparse
import dataclasses
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Iterable


BROAD_CATCH_TYPES = {"Exception", "RuntimeException", "Throwable"}
CATCH_RE = re.compile(r"catch\s*\(\s*(?:final\s+)?(?P<types>[^)]+?)\s+(?P<var>[A-Za-z_$][\w$]*)\s*\)\s*\{")
PACKAGE_RE = re.compile(r"^\s*package\s+([A-Za-z_$][\w.$]*)\s*;", re.MULTILINE)
CLASS_RE = re.compile(r"\b(?:public\s+)?(?:final\s+)?class\s+([A-Za-z_$][\w$]*)\b")
IMAGE_RESOURCE_RE = re.compile(r"ImageUtil\s*\.\s*loadImageResource\s*\(\s*(?P<class>(?:[A-Za-z_$][\w$]*\.class|getClass\(\)))\s*,\s*\"(?P<path>[^\"]+)\"")
LOG_THREAD_RE = re.compile(r"\[([^\]]+)]")
LOG_PREFIX_RE = re.compile(r"^\d{4}-\d{2}-\d{2} .+ \[(?P<thread>[^\]]+)]\s+(?P<level>[A-Z]+)\s+(?P<logger>\S+)\s+-\s+(?P<message>.*)$")
EXCEPTION_LINE_RE = re.compile(r"^(?:Caused by:\s+)?(?:[\w$]+\.)+(?:[\w$]+)(?::\s+.*)?$")
AGENT_COMMANDS = {"static", "lifecycle", "logs", "all", "audit-all"}
AUTH_HEADER = "X-Agent-Token"
HARD_FINDING_CODES = {
    "lifecycle-start-failed",
    "lifecycle-stop-failed",
    "lifecycle-status-failed",
    "log-plugin-start-failed",
    "log-uncaught-exception",
    "log-client-shutdown",
    "log-client-thread-task-exception",
    "log-error-exception-stack",
}


@dataclasses.dataclass
class Finding:
    severity: str
    code: str
    message: str
    file: str | None = None
    line: int | None = None
    plugin: str | None = None
    class_name: str | None = None


@dataclasses.dataclass
class PluginTarget:
    internal_name: str
    name: str
    class_name: str
    disabled: bool = False


@dataclasses.dataclass
class LifecycleResult:
    plugin: str
    class_name: str
    started: bool
    stopped: bool
    status: str | None = None
    error: str | None = None


@dataclasses.dataclass
class AuditPluginResult:
    plugin: str
    class_name: str
    started: bool
    stopped: bool
    status: str | None
    duration_ms: int
    log_start_offset: int
    log_end_offset: int
    hard_failure: bool
    findings: list[Finding]


def default_repo_root() -> Path:
    return Path(__file__).resolve().parents[1]


def load_manifest(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        raise FileNotFoundError(f"Plugin manifest not found: {path}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(data, list):
        return data
    if isinstance(data, dict) and isinstance(data.get("plugins"), list):
        return data["plugins"]
    raise ValueError(f"Unsupported plugin manifest shape: {path}")


def _java_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def _line_number(text: str, index: int) -> int:
    return text.count("\n", 0, index) + 1


def _simple_type_name(raw_type: str) -> str:
    raw_type = raw_type.strip()
    raw_type = raw_type.replace("final ", "")
    raw_type = raw_type.split("<", 1)[0].strip()
    return raw_type.rsplit(".", 1)[-1]


def _is_broad_catch(caught_types: str) -> bool:
    return any(_simple_type_name(part) in BROAD_CATCH_TYPES for part in caught_types.split("|"))


def _extract_brace_block(text: str, open_brace_index: int) -> str:
    depth = 0
    for index in range(open_brace_index, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
            continue
        if char == "}":
            depth -= 1
            if depth == 0:
                return text[open_brace_index + 1:index]
    return text[open_brace_index + 1:]


def _has_interruption_guard(block: str, var_name: str) -> bool:
    escaped = re.escape(var_name)
    guard_patterns = [
        rf"\bScript\s*\.\s*isInterruption\s*\(\s*{escaped}\s*\)",
        rf"\bisInterruption\s*\(\s*{escaped}\s*\)",
        rf"\bshould[A-Za-z0-9_$]*Suppress[A-Za-z0-9_$]*\s*\(\s*{escaped}\s*\)",
        rf"\b{escaped}\s+instanceof\s+InterruptedException\b",
    ]
    return any(re.search(pattern, block) for pattern in guard_patterns)


def _logs_or_rethrows_exception(block: str, var_name: str) -> bool:
    escaped = re.escape(var_name)
    patterns = [
        rf"\b(?:log|logger|LOGGER)\s*\.\s*(?:warn|error)\s*\([^;]*[,(\s]\s*{escaped}\s*[,)]",
        rf"\b(?:CupidBot|Microbot)\s*\.\s*logStackTrace\s*\([^;]*[,(\s]\s*{escaped}\s*[,)]",
        rf"\b{escaped}\s*\.\s*printStackTrace\s*\(",
        rf"\bthrow\s+{escaped}\s*;",
        rf"\bthrow\s+new\s+[A-Za-z_$][\w$]*\s*\([^;]*[,(\s]\s*{escaped}\s*[,)]",
    ]
    return any(re.search(pattern, block, re.DOTALL) for pattern in patterns)


def _is_lifecycle_source(path: Path, text: str) -> bool:
    if path.name.endswith("Script.java"):
        return True
    lifecycle_markers = (
        " extends Script",
        "scheduleWithFixedDelay",
        "scheduleAtFixedRate",
        "ClientThread.invoke",
        "runOnClientThread",
    )
    return any(marker in text for marker in lifecycle_markers)


def find_shutdown_interrupt_findings(source_root: Path) -> list[Finding]:
    findings: list[Finding] = []
    if not source_root.exists():
        raise FileNotFoundError(f"Source root not found: {source_root}")

    for path in sorted(source_root.rglob("*.java")):
        text = _java_text(path)
        if "catch" not in text or not _is_lifecycle_source(path, text):
            continue
        for match in CATCH_RE.finditer(text):
            caught_types = match.group("types")
            var_name = match.group("var")
            if not _is_broad_catch(caught_types):
                continue
            block = _extract_brace_block(text, match.end() - 1)
            if _has_interruption_guard(block, var_name):
                continue
            if not _logs_or_rethrows_exception(block, var_name):
                continue
            findings.append(
                Finding(
                    severity="WARN",
                    code="shutdown-interrupt-logged",
                    file=str(path),
                    line=_line_number(text, match.start()),
                    message=(
                        f"Broad catch ({caught_types.strip()} {var_name}) logs or rethrows the exception "
                        "without Script.isInterruption guard; shutdown can look like a crash."
                    ),
                )
            )
    return findings


def _resolve_image_resource_path(
    java_path: Path,
    source_root: Path,
    resource_root: Path,
    resource_path: str,
    class_ref: str,
    class_index: dict[str, str],
) -> Path:
    if resource_path.startswith("/"):
        return resource_root / resource_path.lstrip("/")

    normalized = resource_path

    if class_ref != "getClass()":
        simple_class = class_ref.removesuffix(".class")
        class_name = class_index.get(simple_class)
        if class_name:
            return resource_root / Path(class_name.replace(".", "/")).parent / normalized

    package_dir = java_path.relative_to(source_root).parent
    return resource_root / package_dir / normalized


def find_missing_image_resource_findings(source_root: Path, resource_root: Path) -> list[Finding]:
    findings: list[Finding] = []
    if not source_root.exists():
        raise FileNotFoundError(f"Source root not found: {source_root}")

    class_index = _index_java_classes(source_root)

    for path in sorted(source_root.rglob("*.java")):
        text = _java_text(path)
        if "ImageUtil.loadImageResource" not in text:
            continue
        for match in IMAGE_RESOURCE_RE.finditer(text):
            resource_path = match.group("path")
            class_ref = match.group("class")
            resolved = _resolve_image_resource_path(path, source_root, resource_root, resource_path, class_ref, class_index)
            normalized = resource_path.strip("/")
            if normalized == "docs/assets/icon.jpg" or "/docs/assets/" in f"/{normalized}":
                findings.append(
                    Finding(
                        severity="WARN",
                        code="unbundled-image-resource",
                        file=str(path),
                        line=_line_number(text, match.start()),
                        message=(
                            f"Image resource '{resource_path}' points at docs assets. "
                            "Docs resources are not bundled into local plugin jars; use a bundled resource or generated fallback."
                        ),
                    )
                )
                continue
            if resolved.exists():
                continue
            findings.append(
                Finding(
                    severity="WARN",
                    code="missing-image-resource",
                    file=str(path),
                    line=_line_number(text, match.start()),
                    message=f"Image resource '{resource_path}' does not exist at {resolved}.",
                )
            )
    return findings


def _index_java_classes(source_root: Path) -> dict[str, str]:
    classes: dict[str, str] = {}
    if not source_root.exists():
        return classes
    for path in sorted(source_root.rglob("*.java")):
        text = _java_text(path)
        package_match = PACKAGE_RE.search(text)
        if not package_match:
            continue
        package_name = package_match.group(1)
        for class_match in CLASS_RE.finditer(text):
            simple_name = class_match.group(1)
            classes.setdefault(simple_name, f"{package_name}.{simple_name}")
    return classes


def discover_plugin_targets(
    source_root: Path,
    manifest: Iterable[dict[str, Any]],
    include_disabled: bool = False,
    filters: Iterable[str] | None = None,
) -> list[PluginTarget]:
    class_index = _index_java_classes(source_root)
    wanted = {value.lower() for value in filters or []}
    targets: list[PluginTarget] = []

    for plugin in manifest:
        internal_name = str(plugin.get("internalName") or "").strip()
        name = str(plugin.get("name") or internal_name).strip()
        if not internal_name:
            continue
        disabled = bool(plugin.get("disable", False))
        if disabled and not include_disabled:
            continue
        class_name = str(plugin.get("className") or class_index.get(internal_name) or "").strip()
        if not class_name:
            continue
        if wanted:
            candidates = {internal_name.lower(), name.lower(), class_name.lower()}
            if not any(value in candidate for value in wanted for candidate in candidates):
                continue
        targets.append(PluginTarget(internal_name=internal_name, name=name, class_name=class_name, disabled=disabled))
    return targets


def parse_log_findings(log_text: str) -> list[Finding]:
    findings: list[Finding] = []
    lines = log_text.splitlines()
    last_interrupt_line = -100

    for index, line in enumerate(lines, start=1):
        thread_match = LOG_THREAD_RE.search(line)
        thread = thread_match.group(1) if thread_match else "unknown-thread"
        severity = "ERROR" if " ERROR " in line else "WARN"

        if "Interrupted waiting for client thread" in line:
            if index - last_interrupt_line > 12:
                findings.append(
                    Finding(
                        severity=severity,
                        code="log-shutdown-interrupt-stack",
                        line=index,
                        message=(
                            f"{thread}: shutdown interrupt was logged. This usually means a script was stopped "
                            "while waiting for ClientThread and should suppress Script.isInterruption."
                        ),
                    )
                )
                last_interrupt_line = index
            continue

        if "Exception during task execution" in line:
            findings.append(
                Finding(
                    severity="ERROR",
                    code="log-client-thread-task-exception",
                    line=index,
                    message=f"{thread}: ClientThread task raised an exception during plugin execution.",
                )
            )
            continue

        if "Failed to start plugin" in line or "Unable to start plugin" in line:
            findings.append(
                Finding(
                    severity="ERROR",
                    code="log-plugin-start-failed",
                    line=index,
                    message=line.strip(),
                )
            )
            continue

        if "[WebWalk] exit | r=interrupted-exception" in line:
            findings.append(
                Finding(
                    severity="WARN",
                    code="log-webwalk-interrupted",
                    line=index,
                    message=(
                        f"{thread}: WebWalk pathfinder was interrupted. This is usually expected when the "
                        "audit stops a plugin mid-walk, but repeated occurrences during normal runtime need review."
                    ),
                )
            )
            continue

        if "Uncaught exception:" in line:
            findings.append(
                Finding(
                    severity="ERROR",
                    code="log-uncaught-exception",
                    line=index,
                    message=f"{thread}: RuneLite reported an uncaught exception.",
                )
            )
            continue

        if "Client shutdown detected" in line:
            findings.append(
                Finding(
                    severity="WARN",
                    code="log-client-shutdown",
                    line=index,
                    message=line.strip(),
                )
            )
            continue

        if " ERROR " in line:
            exception_line = None
            for following in lines[index:index + 3]:
                if not following.strip():
                    continue
                if LOG_PREFIX_RE.match(following):
                    break
                if EXCEPTION_LINE_RE.match(following.strip()):
                    exception_line = following.strip()
                    break
            if exception_line and "InterruptedException" not in exception_line and "Interrupted waiting for client thread" not in exception_line:
                findings.append(
                    Finding(
                        severity="ERROR",
                        code="log-error-exception-stack",
                        line=index,
                        message=f"{thread}: ERROR log emitted exception stack: {exception_line}",
                    )
                )
                continue

        if "Error during overlay rendering" in line:
            findings.append(
                Finding(
                    severity="WARN",
                    code="log-overlay-render-error",
                    line=index,
                    message=line.strip(),
                )
            )
            continue

        if "Hash mismatch for plugin" in line or "Plugin hash verification failed" in line:
            findings.append(
                Finding(
                    severity="WARN",
                    code="log-plugin-hash-mismatch",
                    line=index,
                    message=line.strip(),
                )
            )
    return findings


def resolve_agent_token(agent_token: str | None = None, token_file: Path | None = None) -> str | None:
    if agent_token:
        return agent_token.strip()

    env_token = os.environ.get("CUPIDBOT_TOKEN", "").strip()
    if env_token:
        return env_token

    token_file_value = token_file or Path(os.environ.get("CUPIDBOT_TOKEN_FILE", "~/.runelite/.agent-token"))
    token_path = token_file_value.expanduser()
    if token_path.exists():
        token = token_path.read_text(encoding="utf-8", errors="replace").strip()
        return token or None
    return None


def _http_json(
    base_url: str,
    method: str,
    path: str,
    payload: dict[str, Any] | None = None,
    timeout: float = 10.0,
    token: str | None = None,
) -> tuple[int, Any]:
    url = urllib.parse.urljoin(base_url.rstrip("/") + "/", path.lstrip("/"))
    data = None
    headers = {"Accept": "application/json"}
    if token:
        headers[AUTH_HEADER] = token
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read().decode("utf-8", errors="replace")
            return response.status, _decode_json_or_text(body)
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        return error.code, _decode_json_or_text(body)


def _decode_json_or_text(body: str) -> Any:
    if not body:
        return {}
    try:
        return json.loads(body)
    except json.JSONDecodeError:
        return body


def _response_ok(status: int, body: Any) -> bool:
    if status < 200 or status >= 300:
        return False
    if isinstance(body, dict) and body.get("success") is False:
        return False
    return True


def _body_error(body: Any) -> str:
    if isinstance(body, dict):
        for key in ("error", "message", "status"):
            value = body.get(key)
            if value:
                return str(value)
        return json.dumps(body, sort_keys=True)
    return str(body)


def _plugin_not_running(status: int, body: Any) -> bool:
    return 400 <= status < 500 and "plugin is not running" in _body_error(body).lower()


def _plugin_already_running(status: int, body: Any) -> bool:
    return 400 <= status < 500 and "plugin is already running" in _body_error(body).lower()


def run_lifecycle_targets(
    targets: Iterable[PluginTarget],
    base_url: str,
    run_seconds: float,
    request_timeout: float,
    token: str | None,
) -> tuple[list[LifecycleResult], list[Finding]]:
    results: list[LifecycleResult] = []
    findings: list[Finding] = []

    try:
        status, body = _http_json(base_url, "GET", "/scripts", timeout=request_timeout, token=token)
    except OSError as error:
        raise RuntimeError(f"Agent Server is not reachable at {base_url}: {error}") from error
    if not _response_ok(status, body):
        hint = ""
        if status == 404:
            hint = (
                " Agent Server returns 404 when the X-Agent-Token header is missing or wrong; "
                "check ~/.runelite/.agent-token, CUPIDBOT_TOKEN, or --agent-token-file."
            )
        raise RuntimeError(f"Agent Server /scripts failed with HTTP {status}: {_body_error(body)}.{hint}")

    for target in targets:
        start_status, start_body = _http_json(
            base_url,
            "POST",
            "/scripts/start",
            {"className": target.class_name},
            timeout=request_timeout,
            token=token,
        )
        started = _response_ok(start_status, start_body) or _plugin_already_running(start_status, start_body)
        if not started:
            findings.append(
                Finding(
                    severity="ERROR",
                    code="lifecycle-start-failed",
                    plugin=target.internal_name,
                    class_name=target.class_name,
                    message=f"Start failed with HTTP {start_status}: {_body_error(start_body)}",
                )
            )

        if run_seconds > 0:
            time.sleep(run_seconds)

        status_text = None
        if started:
            query = urllib.parse.urlencode({"className": target.class_name})
            check_status, check_body = _http_json(
                base_url,
                "GET",
                f"/scripts/status?{query}",
                timeout=request_timeout,
                token=token,
            )
            if isinstance(check_body, dict):
                status_text = str(check_body.get("status") or check_body.get("state") or "")
            if _plugin_not_running(check_status, check_body):
                status_text = "STOPPED"
                stopped = True
            elif not _response_ok(check_status, check_body):
                findings.append(
                    Finding(
                        severity="ERROR",
                        code="lifecycle-status-failed",
                        plugin=target.internal_name,
                        class_name=target.class_name,
                        message=f"Status failed with HTTP {check_status}: {_body_error(check_body)}",
                    )
                )

        if not stopped:
            stop_status, stop_body = _http_json(
                base_url,
                "POST",
                "/scripts/stop",
                {"className": target.class_name},
                timeout=request_timeout,
                token=token,
            )
            stopped = _response_ok(stop_status, stop_body) or _plugin_not_running(stop_status, stop_body)
            if not stopped:
                findings.append(
                    Finding(
                        severity="ERROR",
                        code="lifecycle-stop-failed",
                        plugin=target.internal_name,
                        class_name=target.class_name,
                        message=f"Stop failed with HTTP {stop_status}: {_body_error(stop_body)}",
                    )
                )

        results.append(
            LifecycleResult(
                plugin=target.internal_name,
                class_name=target.class_name,
                started=started,
                stopped=stopped,
                status=status_text,
                error=None if started and stopped else "lifecycle request failed",
            )
        )
    return results, findings


def _format_location(finding: Finding) -> str:
    if finding.file:
        location = finding.file
        if finding.line:
            location += f":{finding.line}"
        return location
    if finding.plugin:
        return finding.plugin
    if finding.line:
        return f"log:{finding.line}"
    return "-"


def print_findings(title: str, findings: list[Finding]) -> None:
    print(f"{title}: {len(findings)} finding(s)")
    for finding in findings:
        print(f"{finding.severity} {finding.code} {_format_location(finding)}")
        print(f"  {finding.message}")


def write_report(
    path: Path,
    findings: list[Finding],
    targets: list[PluginTarget] | None = None,
    lifecycle: list[LifecycleResult] | None = None,
    audit: list[AuditPluginResult] | None = None,
) -> None:
    first_hard_failure = next((result.plugin for result in audit or [] if result.hard_failure), None)
    report = {
        "summary": {
            "findings": len(findings),
            "targets": len(targets or []),
            "lifecycleResults": len(lifecycle or []),
            "auditResults": len(audit or []),
            "firstHardFailure": first_hard_failure,
        },
        "findings": [dataclasses.asdict(finding) for finding in findings],
        "targets": [dataclasses.asdict(target) for target in targets or []],
        "lifecycleResults": [dataclasses.asdict(result) for result in lifecycle or []],
        "auditResults": [dataclasses.asdict(result) for result in audit or []],
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def _add_common_arguments(parser: argparse.ArgumentParser) -> None:
    repo_root = default_repo_root()
    parser.add_argument("--repo-root", type=Path, default=repo_root)
    parser.add_argument("--source-root", type=Path)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--plugin", action="append", default=[], help="Filter by internal name, display name, or class name. May be repeated.")
    parser.add_argument("--include-disabled", action="store_true")
    parser.add_argument("--report", type=Path, help="Write a JSON report.")
    parser.add_argument("--allow-findings", action="store_true", help="Return exit code 0 even when findings are reported.")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="CupidBot local plugin static and lifecycle test harness.",
    )
    subparsers = parser.add_subparsers(dest="command")

    static_parser = subparsers.add_parser("static", help="Scan plugin source for shutdown/lifecycle risks.")
    _add_common_arguments(static_parser)

    lifecycle_parser = subparsers.add_parser("lifecycle", help="Start/stop plugins through a running Agent Server.")
    _add_common_arguments(lifecycle_parser)
    lifecycle_parser.add_argument("--agent-base-url", default="http://127.0.0.1:8081")
    lifecycle_parser.add_argument("--agent-token", help="Agent Server token. Defaults to CUPIDBOT_TOKEN or the token file.")
    lifecycle_parser.add_argument("--agent-token-file", type=Path, help="Agent Server token file. Defaults to CUPIDBOT_TOKEN_FILE or ~/.runelite/.agent-token.")
    lifecycle_parser.add_argument("--all-plugins", action="store_true", help="Intentionally start/stop every enabled manifest plugin. Dangerous on live accounts.")
    lifecycle_parser.add_argument("--run-seconds", type=float, default=2.0)
    lifecycle_parser.add_argument("--request-timeout", type=float, default=10.0)
    lifecycle_parser.add_argument("--log-file", type=Path, help="Parse new log lines written during lifecycle testing.")

    audit_parser = subparsers.add_parser("audit-all", help="Safely audit every enabled plugin one at a time.")
    _add_common_arguments(audit_parser)
    audit_parser.add_argument("--agent-base-url", default="http://127.0.0.1:8081")
    audit_parser.add_argument("--agent-token", help="Agent Server token. Defaults to CUPIDBOT_TOKEN or the token file.")
    audit_parser.add_argument("--agent-token-file", type=Path, help="Agent Server token file. Defaults to CUPIDBOT_TOKEN_FILE or ~/.runelite/.agent-token.")
    audit_parser.add_argument("--live-account-ok", action="store_true", help="Required when the Agent Server reports loggedIn=true.")
    audit_parser.add_argument("--resume-from", help="Resume at this internal name, display name, or class name.")
    audit_parser.add_argument("--continue-on-failure", action="store_true", help="Continue after hard findings instead of stopping at the first one.")
    audit_parser.add_argument("--run-seconds", type=float, default=2.0)
    audit_parser.add_argument("--post-stop-settle-seconds", type=float, default=0.25)
    audit_parser.add_argument("--request-timeout", type=float, default=10.0)
    audit_parser.add_argument("--log-file", type=Path, help="Parse new log lines after each plugin.")

    logs_parser = subparsers.add_parser("logs", help="Scan an existing CupidBot log file.")
    logs_parser.add_argument("--log-file", type=Path, required=True)
    logs_parser.add_argument("--report", type=Path)
    logs_parser.add_argument("--allow-findings", action="store_true")

    all_parser = subparsers.add_parser("all", help="Run static scan and Agent Server lifecycle testing.")
    _add_common_arguments(all_parser)
    all_parser.add_argument("--agent-base-url", default="http://127.0.0.1:8081")
    all_parser.add_argument("--agent-token", help="Agent Server token. Defaults to CUPIDBOT_TOKEN or the token file.")
    all_parser.add_argument("--agent-token-file", type=Path, help="Agent Server token file. Defaults to CUPIDBOT_TOKEN_FILE or ~/.runelite/.agent-token.")
    all_parser.add_argument("--all-plugins", action="store_true", help="Intentionally start/stop every enabled manifest plugin. Dangerous on live accounts.")
    all_parser.add_argument("--run-seconds", type=float, default=2.0)
    all_parser.add_argument("--request-timeout", type=float, default=10.0)
    all_parser.add_argument("--log-file", type=Path)

    return parser


def _normalize_paths(args: argparse.Namespace) -> None:
    if hasattr(args, "repo_root"):
        args.repo_root = args.repo_root.resolve()
        if args.source_root is None:
            args.source_root = args.repo_root / "src/main/java"
        if args.manifest is None:
            args.manifest = args.repo_root / "public/docs/plugins.json"
        args.source_root = args.source_root.resolve()
        args.manifest = args.manifest.resolve()


def _manifest_targets(args: argparse.Namespace) -> list[PluginTarget]:
    manifest = load_manifest(args.manifest)
    return discover_plugin_targets(args.source_root, manifest, args.include_disabled, args.plugin)


def _target_key(value: str) -> str:
    return value.strip().lower()


def _target_matches(target: PluginTarget, value: str) -> bool:
    needle = _target_key(value)
    return needle in {
        _target_key(target.internal_name),
        _target_key(target.name),
        _target_key(target.class_name),
    }


def resume_targets(targets: list[PluginTarget], resume_from: str | None) -> list[PluginTarget]:
    if not resume_from:
        return targets
    for index, target in enumerate(targets):
        if _target_matches(target, resume_from):
            return targets[index:]
    raise RuntimeError(f"Could not find --resume-from target '{resume_from}' in manifest targets.")


def ensure_lifecycle_scope(filters: Iterable[str] | None, all_plugins: bool) -> None:
    if all_plugins or filters:
        return
    raise RuntimeError(
        "Lifecycle mode starts real plugins in the live client. Pass --plugin <name> to test a narrow target, "
        "or pass --all-plugins when you intentionally want to start/stop every enabled manifest plugin."
    )


def ensure_live_account_audit_allowed(state_body: Any, live_account_ok: bool) -> None:
    if isinstance(state_body, dict) and state_body.get("loggedIn") is True and not live_account_ok:
        raise RuntimeError(
            "Agent Server reports loggedIn=true on a live account. audit-all starts real plugins; rerun with --live-account-ok "
            "only when this account and location are safe for live plugin startup tests."
        )


def has_hard_findings(findings: Iterable[Finding]) -> bool:
    return any(finding.severity == "ERROR" or finding.code in HARD_FINDING_CODES for finding in findings)


def should_stop_audit(findings: Iterable[Finding], continue_on_failure: bool) -> bool:
    return has_hard_findings(findings) and not continue_on_failure


def _read_log_delta(path: Path | None, offset: int) -> str:
    if path is None or not path.exists():
        return ""
    with path.open("r", encoding="utf-8", errors="replace") as handle:
        handle.seek(offset)
        return handle.read()


def _log_offset(path: Path | None) -> int:
    if path is None or not path.exists():
        return 0
    return path.stat().st_size


def run_static(args: argparse.Namespace) -> tuple[list[Finding], list[PluginTarget], list[LifecycleResult]]:
    findings = find_shutdown_interrupt_findings(args.source_root)
    resource_root = args.repo_root / "src/main/resources"
    findings.extend(find_missing_image_resource_findings(args.source_root, resource_root))
    print_findings("Static scan", findings)
    return findings, [], []


def run_logs(args: argparse.Namespace) -> tuple[list[Finding], list[PluginTarget], list[LifecycleResult]]:
    log_text = args.log_file.read_text(encoding="utf-8", errors="replace")
    findings = parse_log_findings(log_text)
    print_findings("Log scan", findings)
    return findings, [], []


def _tag_findings(findings: list[Finding], target: PluginTarget) -> list[Finding]:
    tagged: list[Finding] = []
    for finding in findings:
        tagged.append(
            Finding(
                severity=finding.severity,
                code=finding.code,
                message=finding.message,
                file=finding.file,
                line=finding.line,
                plugin=finding.plugin or target.internal_name,
                class_name=finding.class_name or target.class_name,
            )
        )
    return tagged


def _agent_get(base_url: str, path: str, request_timeout: float, token: str | None) -> Any:
    try:
        status, body = _http_json(base_url, "GET", path, timeout=request_timeout, token=token)
    except OSError as error:
        raise RuntimeError(f"Agent Server is not reachable at {base_url}: {error}") from error
    if not _response_ok(status, body):
        hint = ""
        if status == 404:
            hint = (
                " Agent Server returns 404 when the X-Agent-Token header is missing or wrong; "
                "check ~/.runelite/.agent-token, CUPIDBOT_TOKEN, or --agent-token-file."
            )
        raise RuntimeError(f"Agent Server {path} failed with HTTP {status}: {_body_error(body)}.{hint}")
    return body


def _lifecycle_transport_finding(code: str, target: PluginTarget, action: str, error: OSError) -> Finding:
    return Finding(
        severity="ERROR",
        code=code,
        plugin=target.internal_name,
        class_name=target.class_name,
        message=f"{action} request failed: {error}",
    )


def run_audit_targets(
    targets: list[PluginTarget],
    base_url: str,
    run_seconds: float,
    post_stop_settle_seconds: float,
    request_timeout: float,
    token: str | None,
    log_file: Path | None,
    continue_on_failure: bool,
    report_path: Path | None = None,
) -> tuple[list[AuditPluginResult], list[Finding]]:
    _agent_get(base_url, "/scripts", request_timeout, token)
    audit_results: list[AuditPluginResult] = []
    all_findings: list[Finding] = []

    for index, target in enumerate(targets, start=1):
        print(f"Audit {index}/{len(targets)}: starting {target.internal_name}")
        started_at = time.monotonic()
        log_start_offset = _log_offset(log_file)
        plugin_findings: list[Finding] = []
        started = False
        stopped = False
        status_text = None
        agent_lost = False

        try:
            start_status, start_body = _http_json(
                base_url,
                "POST",
                "/scripts/start",
                {"className": target.class_name},
                timeout=request_timeout,
                token=token,
            )
            started = _response_ok(start_status, start_body) or _plugin_already_running(start_status, start_body)
        except OSError as error:
            start_status = 0
            start_body = str(error)
            agent_lost = True
            plugin_findings.append(_lifecycle_transport_finding("lifecycle-start-failed", target, "Start", error))

        if not started and start_status:
            plugin_findings.append(
                Finding(
                    severity="ERROR",
                    code="lifecycle-start-failed",
                    plugin=target.internal_name,
                    class_name=target.class_name,
                    message=f"Start failed with HTTP {start_status}: {_body_error(start_body)}",
                )
            )

        if started and run_seconds > 0:
            time.sleep(run_seconds)

        if started:
            query = urllib.parse.urlencode({"className": target.class_name})
            try:
                check_status, check_body = _http_json(
                    base_url,
                    "GET",
                    f"/scripts/status?{query}",
                    timeout=request_timeout,
                    token=token,
                )
                if isinstance(check_body, dict):
                    status_text = str(check_body.get("status") or check_body.get("state") or "")
            except OSError as error:
                check_status = 0
                check_body = str(error)
                agent_lost = True
                plugin_findings.append(_lifecycle_transport_finding("lifecycle-status-failed", target, "Status", error))
            if check_status and _plugin_not_running(check_status, check_body):
                status_text = "STOPPED"
                stopped = True
            elif check_status and not _response_ok(check_status, check_body):
                plugin_findings.append(
                    Finding(
                        severity="ERROR",
                        code="lifecycle-status-failed",
                        plugin=target.internal_name,
                        class_name=target.class_name,
                        message=f"Status failed with HTTP {check_status}: {_body_error(check_body)}",
                    )
                )

        if not stopped:
            try:
                stop_status, stop_body = _http_json(
                    base_url,
                    "POST",
                    "/scripts/stop",
                    {"className": target.class_name},
                    timeout=request_timeout,
                    token=token,
                )
                stopped = _response_ok(stop_status, stop_body) or _plugin_not_running(stop_status, stop_body)
            except OSError as error:
                stop_status = 0
                stop_body = str(error)
                agent_lost = True
                plugin_findings.append(_lifecycle_transport_finding("lifecycle-stop-failed", target, "Stop", error))
            if not stopped and stop_status:
                plugin_findings.append(
                    Finding(
                        severity="ERROR",
                        code="lifecycle-stop-failed",
                        plugin=target.internal_name,
                        class_name=target.class_name,
                        message=f"Stop failed with HTTP {stop_status}: {_body_error(stop_body)}",
                    )
                )

        if post_stop_settle_seconds > 0:
            time.sleep(post_stop_settle_seconds)
        log_end_offset = _log_offset(log_file)
        log_text = _read_log_delta(log_file, log_start_offset)
        if log_text:
            plugin_findings.extend(_tag_findings(parse_log_findings(log_text), target))

        hard_failure = has_hard_findings(plugin_findings)
        duration_ms = int((time.monotonic() - started_at) * 1000)
        result = AuditPluginResult(
            plugin=target.internal_name,
            class_name=target.class_name,
            started=started,
            stopped=stopped,
            status=status_text,
            duration_ms=duration_ms,
            log_start_offset=log_start_offset,
            log_end_offset=log_end_offset,
            hard_failure=hard_failure,
            findings=plugin_findings,
        )
        audit_results.append(result)
        all_findings.extend(plugin_findings)

        if plugin_findings:
            print_findings(f"Audit findings for {target.internal_name}", plugin_findings)
        else:
            print(f"Audit {target.internal_name}: no findings")

        if report_path:
            write_report(report_path, all_findings, targets, audit=audit_results)
            print(f"Wrote report: {report_path}")

        if agent_lost:
            print(f"Audit stopped at {target.internal_name} because the Agent Server became unreachable.")
            break

        if should_stop_audit(plugin_findings, continue_on_failure):
            print(f"Audit stopped at {target.internal_name} after hard finding(s). Use --resume-from {target.internal_name} after fixing, or --continue-on-failure.")
            break

    return audit_results, all_findings


def run_audit(args: argparse.Namespace) -> tuple[list[Finding], list[PluginTarget], list[LifecycleResult]]:
    token = resolve_agent_token(args.agent_token, args.agent_token_file)
    state_body = _agent_get(args.agent_base_url, "/state", args.request_timeout, token)
    ensure_live_account_audit_allowed(state_body, args.live_account_ok)

    targets = _manifest_targets(args)
    targets = resume_targets(targets, args.resume_from)
    if not targets:
        raise RuntimeError("No plugin targets matched the manifest/source filters.")

    audit_results, findings = run_audit_targets(
        targets,
        args.agent_base_url,
        args.run_seconds,
        args.post_stop_settle_seconds,
        args.request_timeout,
        token,
        args.log_file,
        args.continue_on_failure,
        args.report,
    )
    args.audit_results = audit_results
    print(f"Audit: tested {len(audit_results)}/{len(targets)} plugin(s)")
    print_findings("Audit total", findings)
    return findings, targets, []


def run_lifecycle(args: argparse.Namespace, include_static: bool = False) -> tuple[list[Finding], list[PluginTarget], list[LifecycleResult]]:
    findings: list[Finding] = []
    lifecycle_log_findings: list[Finding] = []
    if include_static:
        static_findings = find_shutdown_interrupt_findings(args.source_root)
        findings.extend(static_findings)
        print_findings("Static scan", static_findings)

    ensure_lifecycle_scope(args.plugin, args.all_plugins)
    targets = _manifest_targets(args)
    if not targets:
        raise RuntimeError("No plugin targets matched the manifest/source filters.")

    log_offset = _log_offset(args.log_file)
    token = resolve_agent_token(args.agent_token, args.agent_token_file)
    lifecycle_results, lifecycle_findings = run_lifecycle_targets(
        targets,
        args.agent_base_url,
        args.run_seconds,
        args.request_timeout,
        token,
    )
    findings.extend(lifecycle_findings)
    lifecycle_log_findings.extend(lifecycle_findings)
    log_text = _read_log_delta(args.log_file, log_offset)
    if log_text:
        log_findings = parse_log_findings(log_text)
        findings.extend(log_findings)
        lifecycle_log_findings.extend(log_findings)

    print(f"Lifecycle: tested {len(lifecycle_results)} plugin(s)")
    print_findings("Lifecycle/log scan", lifecycle_log_findings)
    return findings, targets, lifecycle_results


def main(argv: list[str] | None = None) -> int:
    argv = list(argv if argv is not None else sys.argv[1:])
    if not argv or (argv[0] not in AGENT_COMMANDS and argv[0] not in {"-h", "--help"}):
        argv.insert(0, "static")

    parser = build_parser()
    args = parser.parse_args(argv)
    _normalize_paths(args)

    try:
        if args.command == "static":
            findings, targets, lifecycle = run_static(args)
        elif args.command == "logs":
            findings, targets, lifecycle = run_logs(args)
        elif args.command == "lifecycle":
            findings, targets, lifecycle = run_lifecycle(args)
        elif args.command == "audit-all":
            findings, targets, lifecycle = run_audit(args)
        elif args.command == "all":
            findings, targets, lifecycle = run_lifecycle(args, include_static=True)
        else:
            parser.error(f"Unsupported command: {args.command}")
    except (FileNotFoundError, RuntimeError, ValueError, OSError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2

    if getattr(args, "report", None):
        write_report(args.report, findings, targets, lifecycle, getattr(args, "audit_results", None))
        print(f"Wrote report: {args.report}")

    if findings and not getattr(args, "allow_findings", False):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
