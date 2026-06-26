# CupidBot CLI

`cupidbot-cli` is a command-line tool that lets AI agents (and developers) interact with a running CupidBot client in real time. It communicates with the embedded Agent Server plugin over HTTP on localhost, providing access to the game's widget system, inventory, NPCs, objects, banking, walking, dialogues, login, and script lifecycle management.

The CLI lives in the CupidBot client repo at `./cupidbot-cli`. This document is a reference copy for Hub developers.

## Prerequisites

1. The CupidBot client must be running
2. The **Agent Server** plugin must be enabled in the plugin list
3. `curl` must be available on the system (standard on Linux/macOS)

## Quick Start

```bash
# Check if the server is reachable and the player is logged in
./cupidbot-cli state

# See what's in the inventory
./cupidbot-cli inventory

# Find nearby NPCs
./cupidbot-cli npcs --name Guard --distance 15

# Attack the nearest guard
./cupidbot-cli npcs interact "Guard" "Attack"

# Walk somewhere
./cupidbot-cli walk 3222 3218
```

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `CUPIDBOT_HOST` | `127.0.0.1` | Agent Server host |
| `CUPIDBOT_PORT` | `8081` | Agent Server port (must match plugin config) |
| `CUPIDBOT_TIMEOUT` | `30` | Request timeout in seconds |

## Command Reference

### Game State

```bash
./cupidbot-cli state                       # Player info, position, animation
./cupidbot-cli skills                      # All skill levels and XP
./cupidbot-cli skills --name Attack        # Single skill
```

### Widget Inspection

```bash
./cupidbot-cli widgets list                # All visible widget interfaces
./cupidbot-cli widgets search "bank"       # Search widgets by keyword
./cupidbot-cli widgets describe 134 0      # Widget tree under a specific widget
./cupidbot-cli widgets click 134 42        # Click widget by ID
./cupidbot-cli widgets click --text "Toggle"  # Click by text
```

### Inventory

```bash
./cupidbot-cli inventory                               # List items
./cupidbot-cli inventory interact "Lobster" "Eat"      # Use/eat/equip
./cupidbot-cli inventory drop "Logs" --all             # Drop items
```

### NPCs

```bash
./cupidbot-cli npcs --name "Banker" --distance 10      # Query nearby
./cupidbot-cli npcs interact "Banker" "Bank"           # Interact
```

### Objects

```bash
./cupidbot-cli objects --name "Oak tree" --distance 10  # Query nearby
./cupidbot-cli objects interact "Oak tree" "Chop down"  # Interact
```

### Ground Items

```bash
./cupidbot-cli ground-items --name "Dragon bones"       # Query nearby
./cupidbot-cli ground-items pickup "Dragon bones"        # Pick up
```

### Walking

```bash
./cupidbot-cli walk 3222 3218              # Walk to coordinates
./cupidbot-cli walk 3222 3218 1            # With plane (upstairs)
```

### Banking

```bash
./cupidbot-cli bank                        # Bank status
./cupidbot-cli bank open                   # Open nearest bank
./cupidbot-cli bank close                  # Close bank
./cupidbot-cli bank deposit-all            # Deposit everything
./cupidbot-cli bank deposit "Logs"         # Deposit specific item
./cupidbot-cli bank withdraw "Pure essence" 28  # Withdraw items
```

### Dialogue

```bash
./cupidbot-cli dialogue                    # Dialogue state
./cupidbot-cli dialogue continue           # Click continue
./cupidbot-cli dialogue select "Buy sword" # Select option
```

### Login

`POST /login` **blocks by default** until login succeeds or fails, returning a definitive result. No polling needed.

```bash
./cupidbot-cli login                       # Login status + error detection
./cupidbot-cli login now                   # Trigger login (blocks until result)
./cupidbot-cli login now --world 360       # Login to specific world
./cupidbot-cli login wait --timeout 60     # CLI-side poll (alternative to blocking POST)
```

**HTTP API parameters** for `POST /login`:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `world` | number | - | Target world (omit for profile default) |
| `wait` | boolean | `true` | Block until login resolves. Set `false` for fire-and-forget. |
| `timeout` | number | `30` | Max seconds to wait (max 120) |

**Success:**
```json
{"success": true, "message": "Login successful", "currentWorld": 360}
```

**Failure (non-member on members world):**
```json
{"success": false, "message": "Login failed: Non-member account cannot login to members world", "loginIndex": 34, "loginError": "Non-member account cannot login to members world"}
```

**Timeout:**
```json
{"success": false, "message": "Login timed out after 30s", "currentWorld": 360}
```

Login error detection via `loginIndex` / `loginError` (on `GET /login` status and in failure responses):

| loginIndex | loginError |
|------------|------------|
| 3 | Authentication failed - invalid credentials |
| 4 | Invalid credentials |
| 14 | Account is banned |
| 24 | Disconnected from server |
| 34 | Non-member account cannot login to members world |

| HTTP Status | Meaning |
|-------------|---------|
| 200 | Login successful (or already logged in) |
| 400 | Login rejected (no profile, not on login screen) |
| 401 | Login failed (auth failure, banned, non-member, or timeout) |
| 409 | Login attempt already in progress |

**Auto-dismiss on retry:** If login fails (e.g., non-member on members world), the game error dialog is automatically dismissed on the next login call. You can immediately retry with a different world — no manual intervention needed:

```bash
./cupidbot-cli login now --world 461    # fails: non-member
./cupidbot-cli login now --world 383    # error dismissed, retries on F2P world
```

### Script Lifecycle

```bash
./cupidbot-cli scripts                     # List all plugins
./cupidbot-cli scripts start --class "com.hub.MyPlugin"  # Start by class
./cupidbot-cli scripts start --name "Example"             # Start by name
./cupidbot-cli scripts stop --class "com.hub.MyPlugin"    # Stop
./cupidbot-cli scripts status --class "com.hub.MyPlugin"  # Status + runtime
./cupidbot-cli scripts results --class "com.hub.MyPlugin" # Get test results
```

## Hub Testing Workflow

The typical flow for automated Hub testing:

```bash
# 1. Wait for Agent Server to be ready
# 2. Login
./cupidbot-cli login wait --timeout 60 --world 360

# 3. Start the script under test
./cupidbot-cli scripts start --class "net.runelite.client.plugins.cupidbot.myplugin.MyPlugin"

# 4. Poll status while running
./cupidbot-cli scripts status --class "net.runelite.client.plugins.cupidbot.myplugin.MyPlugin"

# 5. Retrieve results
./cupidbot-cli scripts results --class "net.runelite.client.plugins.cupidbot.myplugin.MyPlugin"

# 6. Stop the script
./cupidbot-cli scripts stop --class "net.runelite.client.plugins.cupidbot.myplugin.MyPlugin"
```

See `docs/SCRIPT_LIFECYCLE_API.md` for the full HTTP API and Java-side result submission.

## Full HTTP API

For the complete HTTP endpoint reference, handler architecture, and server internals, see `docs/AGENT_SERVER.md` in the CupidBot client repo.
