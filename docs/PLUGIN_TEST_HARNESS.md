# CupidBot Plugin Test Harness

`scripts/test-cupidbot-plugins.sh` runs local checks against the CupidBot Hub plugin source and, when a client is already running, against the Agent Server lifecycle API.

The harness has four modes:

- `static`: scans Java source for broad script catches that log or rethrow shutdown interrupts without a `Script.isInterruption(...)` guard.
- `logs`: scans a CupidBot log file for lifecycle symptoms such as `Interrupted waiting for client thread`, ClientThread task exceptions, and local plugin hash mismatches.
- `lifecycle`: starts and stops plugins through `http://127.0.0.1:8081/scripts` and optionally parses new lines from the client log.
- `audit-all`: starts and stops enabled plugins one at a time, records per-plugin log deltas, and stops on the first hard failure by default.

## Static Scan

Run an exploratory scan and write a report:

```bash
scripts/test-cupidbot-plugins.sh static \
  --allow-findings \
  --report build/reports/cupidbot-plugin-static.json
```

Run it as a failing gate:

```bash
scripts/test-cupidbot-plugins.sh static
```

Exit code `1` means findings were reported. Add `--allow-findings` when you want a report without failing the shell command.

## Log Scan

Scan the current local log:

```bash
scripts/test-cupidbot-plugins.sh logs \
  --log-file /tmp/cupidbot.log \
  --allow-findings \
  --report build/reports/cupidbot-plugin-log.json
```

This is useful immediately after manually toggling plugins. It catches shutdown interrupt stack traces like:

```text
Interrupted waiting for client thread
```

Those usually mean a script was stopped while blocked on `ClientThread.invoke(...)` and the catch block should treat it as expected shutdown.

## Lifecycle Smoke Test

Prerequisites:

- Build and install local plugins.
- Start CupidBot with the Agent Server plugin enabled.
- Keep the Agent Server auth token at `~/.runelite/.agent-token`, or pass it with `CUPIDBOT_TOKEN` / `--agent-token`.
- Send client output to a stable log file if you want log parsing.

Example:

```bash
scripts/install-cupidbot-local-plugins.sh
java -jar ~/.cupidbot/cupidbot-2.6.11.jar > /tmp/cupidbot.log 2>&1
```

In another shell, test one plugin:

```bash
scripts/test-cupidbot-plugins.sh lifecycle \
  --plugin AutoCookingPlugin \
  --run-seconds 2 \
  --log-file /tmp/cupidbot.log \
  --allow-findings \
  --report build/reports/cupidbot-plugin-lifecycle.json
```

Lifecycle mode refuses to run without a `--plugin` filter. This prevents accidentally starting every gameplay plugin on a live account.

If you intentionally want to test every enabled plugin in `public/docs/plugins.json`, use `audit-all` instead of plain lifecycle mode:

```bash
scripts/test-cupidbot-plugins.sh audit-all \
  --live-account-ok \
  --run-seconds 2 \
  --log-file /tmp/cupidbot.log \
  --report build/reports/cupidbot-plugin-audit.json \
  --allow-findings
```

`audit-all` stops after the first hard finding. Fix the reported plugin, reinstall its jar, then resume from that plugin:

```bash
scripts/test-cupidbot-plugins.sh audit-all \
  --live-account-ok \
  --resume-from DiscordPlugin \
  --run-seconds 2 \
  --log-file /tmp/cupidbot.log \
  --report build/reports/cupidbot-plugin-audit.json \
  --allow-findings
```

To keep going after hard findings and collect a broader report:

```bash
scripts/test-cupidbot-plugins.sh audit-all \
  --live-account-ok \
  --continue-on-failure \
  --run-seconds 2 \
  --log-file /tmp/cupidbot.log \
  --report build/reports/cupidbot-plugin-audit.json \
  --allow-findings
```

Run static and lifecycle checks together:

```bash
scripts/test-cupidbot-plugins.sh all \
  --plugin CupidBotDashboardPlusPlugin \
  --run-seconds 2 \
  --log-file /tmp/cupidbot.log \
  --allow-findings
```

## Filters

`--plugin` can match an internal name, display name, or fully qualified class name. It may be repeated:

```bash
scripts/test-cupidbot-plugins.sh lifecycle \
  --plugin AutoMiningPlugin \
  --plugin TheMessPlugin \
  --run-seconds 2 \
  --log-file /tmp/cupidbot.log
```

Disabled plugins are skipped by default. Add `--include-disabled` when you intentionally want to test them.

## Agent Server Auth

The Agent Server returns `404 Not Found` for missing or incorrect auth, so a 404 from `/scripts` usually means the harness did not send the token.

Token lookup order:

- `--agent-token`
- `CUPIDBOT_TOKEN`
- `--agent-token-file`
- `CUPIDBOT_TOKEN_FILE`
- `~/.runelite/.agent-token`

Examples:

```bash
scripts/test-cupidbot-plugins.sh lifecycle \
  --plugin CupidBotDashboardPlusPlugin \
  --agent-token-file ~/.runelite/.agent-token \
  --run-seconds 1

CUPIDBOT_TOKEN="$(cat ~/.runelite/.agent-token)" \
  scripts/test-cupidbot-plugins.sh lifecycle --plugin CupidBotDashboardPlusPlugin
```

## Exit Codes

- `0`: no findings, or findings were allowed with `--allow-findings`.
- `1`: findings were reported.
- `2`: harness setup failed, such as a missing manifest or unreachable Agent Server.

## Limits

This harness is a fast stability screen. It finds common lifecycle bugs such as noisy shutdown exceptions, failed start/stop calls, hash mismatch logs, plugin startup failures, uncaught exceptions, overlay render errors, and client shutdown. It does not prove that a plugin can complete its in-game task. For that, add a scenario test that logs in, moves the account to the required location, sets plugin config, and watches the expected game state change.
