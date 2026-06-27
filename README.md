# CupidBot Hub

CupidBot Hub contains the local plugin source and build system for CupidBot. It imports the legacy hub plugins into the `net.runelite.client.plugins.cupidbot` namespace, builds each plugin as a local jar, and writes a local `plugins.json` manifest for the CupidBot client.

## Network Boundary

The hub does not publish runtime download URLs. `plugins.json` uses local jar names such as `PestControlPlugin.jar`, and the CupidBot client reads those jars from `~/.runelite/cupidbot-plugins`.

Local plugins may use networking for their own features. Examples include Discord webhook delivery, OpenAI chatbot calls, third-party shooting-star providers, and third-party Tears of Guthix world lookup. The local-only boundary applies to the CupidBot core client and loader, not to plugin feature code.

## Plugin Status

CupidBot Hub is still a work in progress. The goal is to make the imported local plugins build, install, and run from local jars, but not every plugin is expected to work perfectly yet. Some plugins may stop themselves when requirements are missing, depend on account/location setup, or still need CupidBot compatibility fixes.

If you find a broken plugin, open an issue with the plugin name, the action you tried, your config/setup, steps to reproduce, and any relevant lines from `/tmp/cupidbot.log`, `build/reports/cupidbot-plugin-audit.json`, or the launcher log.

## Requirements

- Java 11 for hub plugin compilation
- A local CupidBot client jar built from `../cupidbot`

## Build All Plugins

```bash
JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew clean build generatePluginsJson copyPluginDocs \
  -PcupidbotClientPath=/home/frank/micro-client-custom/cupidbot/runelite-client/build/libs/cupidbot-2.6.11.jar
```

Outputs:

- `build/libs/<InternalName>-<version>.jar`
- `public/docs/plugins.json`
- copied docs under `public/docs/plugins/` where plugin docs exist

## Install Into CupidBot

```bash
scripts/install-cupidbot-local-plugins.sh
```

The helper installs:

- `~/.runelite/cupidbot-plugins/plugins.json`
- `~/.runelite/cupidbot-plugins/<InternalName>.jar`
- optional plugin docs copied from `public/docs/plugins`

## Test Local Plugins

Run the plugin harness for static lifecycle-risk checks:

```bash
scripts/test-cupidbot-plugins.sh static --allow-findings
```

When CupidBot is running with the Agent Server enabled, smoke-test plugin start/stop behavior and parse the client log:

```bash
scripts/test-cupidbot-plugins.sh lifecycle \
  --plugin AutoCookingPlugin \
  --run-seconds 2 \
  --log-file /tmp/cupidbot.log \
  --allow-findings
```

See [docs/PLUGIN_TEST_HARNESS.md](docs/PLUGIN_TEST_HARNESS.md) for full usage, reports, exit codes, and limitations.

To audit every enabled plugin, use the guarded all-plugin mode:

```bash
scripts/test-cupidbot-plugins.sh audit-all \
  --live-account-ok \
  --run-seconds 2 \
  --log-file /tmp/cupidbot.log \
  --report build/reports/cupidbot-plugin-audit.json \
  --allow-findings
```

## Build One Plugin

```bash
JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew build generatePluginsJson \
  -PpluginList=PestControlPlugin \
  -PcupidbotClientPath=/home/frank/micro-client-custom/cupidbot/runelite-client/build/libs/cupidbot-2.6.11.jar
```

## Plugin Layout

Source:

```text
src/main/java/net/runelite/client/plugins/cupidbot/<pluginname>/
```

Resources:

```text
src/main/resources/net/runelite/client/plugins/cupidbot/<pluginname>/
```

Common files:

- `<PluginName>Plugin.java`
- `<PluginName>Script.java`
- `<PluginName>Config.java`
- `<PluginName>Overlay.java`
- `dependencies.txt` for extra Maven dependencies
- `docs/README.md` and `docs/assets/` for local hub documentation

## Descriptor Rules

Every plugin needs a `@PluginDescriptor` with:

- `name`
- `version`
- `minClientVersion`
- `enabledByDefault`
- `isExternal`

Use relative resource paths for local icons/cards:

```java
iconUrl = "PestControlPlugin/assets/icon.png",
cardUrl = "PestControlPlugin/assets/card.png"
```

Do not add remote jar download URLs for CupidBot local plugins. Plugin feature networking is allowed when it lives in the plugin source and is visible to users through plugin configuration or docs.
