# Campfire libraries (ready-made, no reinventing)

Principle: vanilla + mature Fabric-ecosystem libs. No Essential-owned libs
(Elementa, Vigilance, KDiscordIPC) - keeps the clean-room intact and stays Java.

## GUI (mod)

- `Fabric API` - screens, keybinds, events (`ScreenEvents`,
  `ScreenKeyboardEvents`). Already required.
- `YetAnotherConfigLib (YACL)` - settings menu. Modern, 1.21+ Fabric + NeoForge,
  Java-first. Replaces writing a custom config GUI.
  `dev.isxander:yet-another-config-lib:3.x`
  Declared as a **required** Fabric dependency because the in-game settings
  button and the About screen open a YACL screen.
- `Mod Menu` - optional (`recommends` in `fabric.mod.json`); exposes the Campfire
  settings entry without custom menu code. `com.terraformersmc:modmenu`
- Custom screens (Social, Host/Invite, Pictures): vanilla `Screen` +
  `ClickableWidget` subclasses + Fabric `ScreenEvents`. No UI framework. Theme
  via `docs/theme.md` colors.
- Optional if lists get painful: `OwO Lib` for containers/scroll lists only.
  Skip unless vanilla lists prove too slow.

## JSON

- `CampfireJson` (`mod/common`) - dependency-free RFC-8259-style parser and
  writer with typed getters. Replaced the hand-rolled `indexOf("\"key\":")`
  string splitting that broke on escaped quotes and nested values. This keeps
  `mod:common` testable with plain `javac` and avoids pulling Jackson into the
  mod jar.

## Backend (relay)

- `backend/relay-go` - Go, **stdlib only** (`net`, `bufio`, `crypto/rand`,
  `encoding/binary`). One binary, no runtime deps. TCP splice with an
  application-level control protocol (see `docs/protocol.md`).
- `backend/relay` (Java + Netty) and `backend/social` (JDK `HttpServer`) are
  legacy reference implementations for the hosted-VPS path. They are not used by
  the mod.

## Hosting / tunneling helpers

- `org.bitlet:weupnp` (or `com.dosse.upnp`) - UPnP/NAT-PMP port-open attempt.
  Tiny, client-side, no server. Direct-join path before the relay fallback.
- Relay tunnels: hand-rolled TCP splice; no TURN/ICE lib needed for TCP v1.
  QUIC/ICE is deferred to v2.

## Misc

- Screenshots: vanilla capture + `java.awt` thumbnails. No lib needed.
- Auth: Mojang `authlib` (already in MC, 9.0.75 on 1.21.11). No custom JWT in the
  current build.

## Explicitly NOT used

- Elementa / Vigilance / Essential GUI / KDiscordIPC - Essential-owned,
  Kotlin-heavy, would taint the clean-room. YACL + vanilla covers it faster.
