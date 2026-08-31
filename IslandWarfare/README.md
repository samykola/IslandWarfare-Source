# IslandWarfare

A clan-based island survival + raid/war game mode for **Paper 1.21.x** (Java 21).

Clans build and upgrade their own island (pasted from WorldEdit schematics), all
sharing one world connected by bridges. The server cycles through
**PREPARATION → WAR → COOLDOWN** phases; during WAR, clans can raid enemy
islands for loot and clan-vs-clan PvP is enabled.

---

## 1. Requirements

| Requirement | Notes |
|---|---|
| Paper (or a Paper fork) | 1.21.x, `api-version: '1.21'` |
| Java | 21 |
| WorldEdit **or** FastAsyncWorldEdit | **Required.** Used to paste island schematics. Install it as a normal plugin on your server (not a build dependency of the server jar) |
| Vault | Optional, **not required**. The economy is fully self-contained on `Clan` balances. Vault is only listed as a soft dependency in case you later want to bridge clan balances into a global player economy. |

No other external services or plugins are required. Storage is local YAML
files under `plugins/IslandWarfare/` by default (SQLite can be added later —
see `storage.type` in `config.yml` and the `StorageService` interface).

---

## 2. Building the plugin

This project is a standard Maven project.

```bash
mvn clean package
```

The built jar will be at `target/IslandWarfare-1.0.0.jar`.

> **Note:** Building requires internet access so Maven can download the
> Paper API, WorldEdit API, and VaultAPI dependencies (all marked
> `provided`, meaning they are **not** bundled into the final jar — they
> must already be present on the server via the Paper server jar and the
> WorldEdit plugin). If you're building in an offline/sandboxed
> environment, point Maven at a local mirror or run the build on a machine
> with normal internet access.

---

## 3. Installation

1. Build the plugin (see above) or use the pre-built jar you were given.
2. Install **WorldEdit** (or FastAsyncWorldEdit) on your Paper server if it
   isn't already installed.
3. Copy `IslandWarfare-1.0.0.jar` into your server's `plugins/` folder.
4. Start the server once so the plugin generates its default files:
   ```
   plugins/IslandWarfare/
   ├── config.yml
   ├── schematics/
   │   └── README.txt
   ├── clans.yml
   ├── islands.yml
   └── raid.yml
   ```
5. Stop the server (or use `/iw admin reload` after editing config).

### Setting up the island world

By default the plugin looks for (and auto-creates, if missing) a world
named `islandwarfare_world` (configurable under `world.name` in
`config.yml`). You can point it at an existing world instead by changing
that value before first start.

### Adding your island schematics

1. Build your island template(s) in any world using WorldEdit.
2. `//copy` the build, then `//schem save island-level-1` (repeat per level).
3. Copy each resulting `.schem` file from
   `plugins/WorldEdit/schematics/` into `plugins/IslandWarfare/schematics/`,
   named exactly as configured in `config.yml`:
   ```
   island-level-1.schem
   island-level-2.schem
   island-level-3.schem
   island-level-4.schem
   island-level-5.schem
   ```
   See `plugins/IslandWarfare/schematics/README.txt` (also included in this
   project at `src/main/resources/schematics/README.txt`) for paste-origin
   tips.

### Creating your first island

Islands are normally created automatically when a clan is formed:

```
/clan create MyClan MYC
```

This creates the clan, assigns it a free grid slot (no overlap with other
islands, checked in-memory — no world scanning), and pastes
`island-level-1.schem` there. As an admin you can also force-create one:

```
/iw admin island create MyClan 1
```

---

## 4. Configuring the game mode

Everything gameplay-relevant lives in `config.yml`:

- `island.levels.<n>.size / schematic / upgrade-cost / max-members` — one
  entry per level, add as many levels as you like.
- `island.grid-spacing`, `island.min-gap` — controls island placement/overlap.
- `bridge.*` — whether bridges (any block outside every island) can be
  broken, and whether that's restricted to WAR phase / specific clans.
- `clan.*` — name/tag length limits, starting balance, default permissions
  per role.
- `raid.preparation-hours`, `raid.war-hours`, `raid.cooldown-hours` — global
  phase cycle timing.
- `raid.raid-zone-radius`, `raid.rules.*` — where and what raiding allows.
- `raid.loot.*` — loot toggles and percentage taken from the defender.
- `raid.combat.friendly-fire` — same-clan PvP toggle.
- `economy.shop` — material → sell price map for `/shop`.
- `gui.*`, `scoreboard.*`, `messages.*` — all player-facing text/UI.

Run `/iw admin reload` after editing to apply changes without a restart.

---

## 5. Commands & Permissions

| Command | Permission | Description |
|---|---|---|
| `/island` | `islandwarfare.island` | Opens the island GUI |
| `/island info` | same | Shows island stats |
| `/island home` | same | Teleport to your island (cooldown configurable) |
| `/island sethome` | same | Set your island's home point |
| `/island upgrade` | same | Upgrade your island to the next level |
| `/clan` | `islandwarfare.clan` | Opens the clan GUI |
| `/clan create <name> <tag>` | same | Create a clan (and its starting island) |
| `/clan invite <player>` | same | Invite a player (officer/leader) |
| `/clan join <name>` | same | Accept a pending invite |
| `/clan leave` | same | Leave your clan (leaders must transfer/disband first) |
| `/clan kick <player>` | same | Kick a member (officer/leader) |
| `/clan promote <player>` | same | Promote member → officer (leader only) |
| `/clan demote <player>` | same | Demote officer → member (leader only) |
| `/clan info` | same | Show clan stats |
| `/clan members` | same | Opens the members GUI |
| `/clan permissions [player]` | same | Opens the per-member permission GUI |
| `/raid` | `islandwarfare.raid` | Opens the raid GUI |
| `/raid attack <clan>` | same | Start raiding an enemy island (WAR phase only) |
| `/raid info` | same | Shows current phase and raid status |
| `/shop` | `islandwarfare.shop` | Opens the sell-shop GUI |
| `/iw admin island create/delete/setlevel/info` | `islandwarfare.admin` | Manual island management |
| `/iw admin reload` | same | Reload config.yml |
| `/iw admin raid start/stop/status` | same | Force the global raid phase |
| `/iw admin clan info/setbalance` | same | Inspect/edit a clan |
| `/iw admin debug` | same | Dump internal counters/state |

Permission nodes:

- `islandwarfare.island`, `islandwarfare.clan`, `islandwarfare.raid`,
  `islandwarfare.shop` — default `true` (all players)
- `islandwarfare.admin` — default `op`
- `islandwarfare.bypass.protection` — default `op`, bypasses all island
  protection and raid-zone checks
- `islandwarfare.*` — grants everything above

Per-player **island permissions** (`BUILD`, `BREAK`, `CONTAINER`, `PICKUP`,
`USE`, `INTERACT`) are managed separately, in-game, through
`/clan permissions <player>` or the Clan → Members → (click a player) GUI
flow — these are not Bukkit permission nodes, they're per-clan-member flags
stored with the clan.

---

## 6. Architecture

```
src/main/java/com/islandwarfare/
├── IslandWarfare.java       - main plugin class, wires everything together
├── clan/                    - Clan, ClanMember, roles, permission flags, ClanManager
├── island/                  - Island, IslandManager (registry + placement),
│                              IslandHomeService, IslandUpgradeService
├── protection/               - ProtectionListener (build/break/container/pickup/
│                              interact permission gate + bridge rules)
├── raid/                     - RaidPhase, ActiveRaid, RaidManager (phase cycle,
│                              attacks, loot)
├── economy/                  - EconomyService (clan-balance based, shop pricing)
├── worldedit/                 - SchematicService (schematic load/cache/paste)
├── gui/                      - Island/Clan/Members/Permissions/Shop/Raid GUIs
│                              + a single GuiListener routing all clicks
├── commands/                  - /island, /clan, /raid, /shop, /iw admin
├── storage/                   - StorageService interface + YamlStorageService
├── scoreboard/                 - ScoreboardManager (per-player sidebar)
├── leaderboard/                - LeaderboardManager (computed on demand)
├── listeners/                  - PlayerListener (join/quit/respawn),
│                              CombatListener (PvP rules)
├── config/                    - ConfigManager (typed config.yml accessor)
└── utils/                     - MessageUtil, ItemBuilder
```

Design notes / performance:

- Island placement uses an expanding spiral over a fixed grid, checked only
  against islands already in memory — never a world scan.
- Schematic clipboards are cached after first load, so pasting the same
  level template for many clans doesn't re-parse the file from disk.
- The raid phase clock and scoreboard both run on lightweight periodic
  scheduler ticks (timestamp comparisons / a single pass over online
  players), not per-tick loops over all players or all islands.
- Storage autosaves asynchronously on an interval plus on shutdown.

## 7. Known MVP simplifications (documented, easy to extend)

- **Raid "win" condition**: the first playable version grants the
  configured loot percentage as soon as `/raid attack` succeeds against an
  island during WAR phase (see the comment in `RaidManager`), rather than
  requiring a full combat/objective win condition. The `ActiveRaid` /
  `RaidManager` split was written so a proper win-condition (e.g. "core"
  destruction, kill count) can be plugged in later without touching the
  rest of the plugin.
- **Storage** is YAML by default (zero dependencies). The `StorageService`
  interface is ready for a SQLite implementation to be dropped in for very
  large clan counts.
- **Bridges** are simply "any block outside every registered island
  bounding box" rather than a separate registry of bridge regions — this
  matches the described map shape (islands connected by open terrain/
  bridges) without needing hand-drawn bridge regions for the first version.
