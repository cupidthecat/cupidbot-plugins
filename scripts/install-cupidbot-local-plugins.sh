#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
target_dir="${CUPIDBOT_PLUGIN_DIR:-$HOME/.runelite/cupidbot-plugins}"
manifest="$repo_root/public/docs/plugins.json"
jar_dir="$repo_root/build/libs"

if [[ ! -f "$manifest" ]]; then
  echo "Missing manifest: $manifest" >&2
  echo "Build it first with: JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew build generatePluginsJson -PcupidbotClientPath=/absolute/path/to/cupidbot.jar" >&2
  exit 1
fi

if [[ ! -d "$jar_dir" ]]; then
  echo "Missing plugin jar directory: $jar_dir" >&2
  exit 1
fi

mkdir -p "$target_dir"
install -m 0644 "$manifest" "$target_dir/plugins.json"

python3 - "$manifest" "$jar_dir" "$target_dir" <<'PY'
import json
import shutil
import sys
from pathlib import Path

manifest = Path(sys.argv[1])
jar_dir = Path(sys.argv[2])
target_dir = Path(sys.argv[3])

plugins = json.loads(manifest.read_text(encoding="utf-8"))
missing = []
installed = 0

for plugin in plugins:
    internal = plugin["internalName"]
    version = plugin["version"]
    source = jar_dir / f"{internal}-{version}.jar"
    target = target_dir / f"{internal}.jar"
    if not source.is_file():
        missing.append(str(source))
        continue
    shutil.copy2(source, target)
    installed += 1

if missing:
    print("Missing plugin jars:", file=sys.stderr)
    for item in missing:
        print(f"  {item}", file=sys.stderr)
    sys.exit(1)

print(f"Installed {installed} local CupidBot plugin jars into {target_dir}")
PY

python3 - "$manifest" <<'PY'
import json
import re
import sys
from pathlib import Path

manifest = Path(sys.argv[1])
plugins = {
    plugin["internalName"]: f"{plugin['version']}\\:{plugin['sha256']}"
    for plugin in json.loads(manifest.read_text(encoding="utf-8"))
    if plugin.get("internalName") and plugin.get("version") and plugin.get("sha256")
}

profile_dir = Path.home() / ".runelite" / "cupidbot-profiles"
if not profile_dir.is_dir():
    raise SystemExit(0)

line_pattern = re.compile(r"^(cupidbotPluginVersions\.plugin\.([^=]+)=)(.*)$")
updated = 0
for profile in sorted(profile_dir.glob("*.properties")):
    lines = profile.read_text(encoding="utf-8").splitlines(keepends=True)
    changed = False
    new_lines = []
    for line in lines:
        newline = "\n" if line.endswith("\n") else ""
        body = line[:-1] if newline else line
        match = line_pattern.match(body)
        if match and match.group(2) in plugins:
            replacement = f"{match.group(1)}{plugins[match.group(2)]}{newline}"
            if replacement != line:
                line = replacement
                changed = True
        new_lines.append(line)

    if changed:
        profile.write_text("".join(new_lines), encoding="utf-8")
        updated += 1

if updated:
    print(f"Updated local plugin version hashes in {updated} CupidBot profile(s)")
PY

if [[ -d "$repo_root/public/docs/plugins" ]]; then
  rm -rf "$target_dir/plugins"
  cp -R "$repo_root/public/docs/plugins" "$target_dir/plugins"
fi

echo "Installed local manifest: $target_dir/plugins.json"
