# AsterMC

Paper staff and moderation plugin targeting Java 21 and Paper API 1.21.4. It uses Adventure/MiniMessage and shaded HikariCP, SQLite JDBC, and MySQL Connector/J.

## Build

```sh
mvn package
```

Copy `target/astermc-staff-1.0.0.jar` to the server's `plugins` directory. The plugin creates `config.yml`, `guis.yml`, `messages.yml`, `punishment-matrix.yml`, and `data.db` in its data folder. SQLite is the default storage backend; MySQL can be selected in `config.yml`.

## Included

- `/staff` (`/modmenu`), `/staffmode` (`/sm`), `/threats` (`/acgui`), `/inspect <player>`, `/punish <player>`, `/report <player> <reason>`, `/mutechat`, `/freezeall`, and `/staffchat` (`/sc`).
- Live staff dashboard metrics (TPS, MSPT, heap use, loaded chunks, staff duty/vanish counts), configurable action items, global chat mute, emergency non-staff freeze, and report queue navigation.
- Paginated online player directory with health, hunger, ping, client brand, vanish/freeze/mute/combat state, and recent anti-cheat flag count. Left-click teleports, right-click inspects, and shift-click toggles freeze.
- Persistent timestamp-ordered reports with staff notifications and GUI teleport/resolve actions.
- Staff loadout snapshot/restore, private vanish, configurable MiniMessage action bar, staff boss bar, freeze controls, and custom inventory-holder menus with persistent action tags.
- Online player inspection, threat list, and warn/mute/kick/ban menu actions.
- HikariCP-backed SQLite/MySQL storage, asynchronous punishment and anti-cheat flag writes, and initial schema creation.
- Optional-plugin presence detection and a `publishFlag(...)` intake boundary for anti-cheat adapters.
- Configurable GrimAC/Vulcan Bukkit event bridges, enabled only when the configured event class exists and exposes recognized player/check/VL accessors.
- LuckPerms primary group, prefix, and weight display; asynchronous `vanished` and `staffmode` metadata-node synchronization.
- Optional Vault permission-group fallback and economy balance display.
- Persistent, transactional offense counters and a data-driven escalation matrix for all configured chat/gameplay categories.
- `/ban`, `/tempban`, `/ipban`, `/tempmute`, `/mute`, and `/punish` category commands routed through the same escalation engine. “Lying to staff” categories require `nexusstaff.admin`.
- `/dupeip` based on a persisted HMAC-SHA256 of the address, never the raw IP. The HMAC secret must remain stable and be shared across servers that share the same database.
- Configurable, asynchronous Discord embeds for moderation, high-VL, and freeze-disconnect events.
- Optional proxy-forwarded staff chat over the configured `nexusstaff:main` plugin channel.

## Integration boundaries

GrimAC and Vulcan event class names and accessors vary by release. Configure `anti-cheat.grim-event-class` and `anti-cheat.vulcan-event-class` for the installed versions; the bridge logs when registration succeeds, and unsupported event shapes require a small version-specific adapter calling `AntiCheatHooks.publishFlag(...)`. Threat scores are in-memory and represent maximum received VL, not a normalized cross-plugin severity.

Proxy blacklist propagation requires a compatible proxy that forwards the configured plugin channel between AsterMC backends, `network.enabled: true`, and the same `network.shared-secret` on every backend. `/ipban` uses the category engine's local timed IP ban for timed outcomes; only the permanent `BLACKLIST` tier propagates network-wide. Custom texture payloads, durable staff snapshots, and punishment history browsing remain unimplemented.

Grant `nexusstaff.dashboard` for `/staff`, `nexusstaff.punish` for matrix actions, `nexusstaff.ipban` for `/ipban`, `nexusstaff.dupeip` for alt lookup, and `nexusstaff.freezeall` for emergency freeze; `nexusstaff.staff` inherits dashboard and punishment permissions. Bukkit player/world/inventory APIs are sampled and updated on the server thread once per second. Moving these calls to an async scheduler is not thread-safe; only storage and webhook I/O run off-thread.

The staff-mode inventory snapshot is held in memory and restored on disable/quit. Do not treat it as crash-safe inventory escrow. Validate behavior on a staging server before granting staff permissions in production.