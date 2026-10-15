# Campfire libraries (ready-made, no reinventing)

Principle: vanilla + mature Fabric-ecosystem libs. No Essential-owned libs
(Elementa, Vigilance, KDiscordIPC) — keeps clean-room intact and stays Java.

## GUI (mod)
- `Fabric API` — screens, keybinds, events. You already need it.
- `YetAnotherConfigLib (YACL)` — settings menu. Modern, 1.21+ Fabric+NeoForge,
  Java-first. Replaces writing own config GUI.
  `dev.isxander:yet-another-config-lib:3.x`
- `Mod Menu` — exposes Campfire settings entry, no custom menu code.
  `com.terraformersmc:modmenu`
- Custom screens (Social, Host/Invite, Pictures): vanilla `Screen` + `ButtonWidget`
  + Fabric `ScreenEvents`. No UI framework. Theme via `docs/theme.md` colors.
- Optional if lists get painful: `OwO Lib` for containers/scroll lists only.
  Skip unless vanilla lists prove too slow.

## Backend (local, Option B)
- `Javalin 6` — REST + WebSocket signaling/social in one jar, pure Java.
- `sqlite-jdbc + HikariCP` — friends/groups/messages/presence store, file-based.
- `Jackson` — JSON for protocol (matches `docs/protocol.md`).
- `SLF4J + Logback` — logging.
- `Netty (MC's version)` — TCP relay splice, no extra dep in mod.

## Hosting / tunneling helpers
- `org.bitlet:weupnp` (or `com.dosse.upnp`) — UPnP/NAT-PMP open-port attempt.
  Tiny, client-side, no server. Direct-join path before relay fallback.
- Relay itself: hand-rolled ~150 lines on Netty `Bootstrap/ServerBootstrap`
  channel-relay. No TURN lib needed for TCP v1. Defer QUIC/ICE to v2.

## Misc
- `com.jagrosh:DiscordIPC` — Discord presence. Pure Java, not Essential-owned.
  Drop if it slows v1; Discord is optional.
- Screenshots: vanilla capture + `java.awt` thumbnails. No lib needed.
- Auth: Mojang `authlib` (already in MC) + own JWT via `com.auth0:java-jwt`.

## Explicitly NOT used
- Elementa / Vigilance / Essential GUI / KDiscordIPC — Essential-owned,
  Kotlin-heavy, would taint clean-room. YACL + vanilla covers it faster.
