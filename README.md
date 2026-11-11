# NexusStaff Pro

Paper staff and moderation plugin foundation. This build targets Java 21 and Paper API 1.21.4; it uses Adventure/MiniMessage and shaded HikariCP + SQLite JDBC.

## Build

```sh
mvn package
```

Copy `target/nexusstaff-pro-1.0.0.jar` to the server's `plugins` directory. The plugin creates `config.yml`, `guis.yml`, `messages.yml`, and `data.db` in its data folder. SQLite is the default storage backend; MySQL can be selected in `config.yml`.

## Included

- `/staff` (`/modmenu`), `/staffmode` (`/sm`), `/threats` (`/acgui`), `/inspect <player>`, `/punish <player>`, `/report <player> <reason>`, `/mutechat`, `/freezeall`, and `/staffchat` (`/sc`).
- Live staff dashboard metrics (TPS, MSPT, heap use, loaded chunks, staff duty/vanish counts), configurable action items, global chat mute, emergency non-staff freeze, and report queue navigation.
- Paginated online player directory with health, hunger, ping, client brand, vanish/freeze/mute/combat state, and recent anti-cheat flag count. Left-click teleports, right-click inspects, and shift-click toggles freeze.
- Persistent timestamp-ordered reports with staff notifications and GUI teleport/resolve actions.
- Staff loadout snapshot/restore, private vanish, configurable MiniMessage action bar, staff boss bar, freeze controls, and custom inventory-holder menus with persistent action tags.
- Online player inspection, threat list, and warn/mute/kick/ban menu actions.
- HikariCP-backed SQLite/MySQL storage, asynchronous punishment and anti-cheat flag writes, and initial schema creation.
- Optional-plugin presence detection and a `publishFlag(...)` intake boundary for anti-cheat adapters.
- Configurable, asynchronous Discord embeds for moderation, high-VL, and freeze-disconnect events.
- Optional proxy-forwarded staff chat over the configured `nexusstaff:main` plugin channel.

## Integration boundaries

GrimAC and Vulcan are soft dependencies, but this artifact does not compile against either plugin's API and does not register their native flag listeners. Their event contracts vary across releases; a matching adapter must call `AntiCheatHooks.publishFlag(...)`. Until that adapter is installed, threat scores remain empty. Proxy forwarding requires a compatible proxy and this plugin installed/configured on every participating backend; only staff chat is currently forwarded. Custom texture payloads, durable staff snapshots, punishment history browsing, and temporary-ban/time-selector controls remain unimplemented. Mutes and threat scores are currently in-memory, and the threat GUI reports the maximum received VL rather than a cross-plugin normalized score.

Grant `nexusstaff.dashboard` for `/staff` and `nexusstaff.freezeall` for the emergency freeze; `nexusstaff.staff` inherits dashboard access. Bukkit player/world/inventory APIs are sampled and updated on the server thread once per second. Moving these calls to an async scheduler is not thread-safe; only storage and webhook I/O run off-thread.

The staff-mode inventory snapshot is held in memory and restored on disable/quit. Do not treat it as crash-safe inventory escrow. Validate behavior on a staging server before granting staff permissions in production.