# Campfire spec (v1, Option B, no VPS)

## Status

| Area | State |
| --- | --- |
| Fabric mod | Implemented, targets **Minecraft 1.21.11** (Yarn `1.21.11+build.6`, Loader 0.19.5, Fabric API 0.141.6) |
| NeoForge mod | Implemented, targets **Minecraft 1.21.1** / NeoForge 21.1.172 |
| Shared code | `mod:common` — pure Java, no Minecraft classes, unit tested |
| Relay | `backend/relay-go`, single Go binary, stdlib only |
| Social API | `backend/cloudflare`, single-file Worker + KV |

## Scope

- Friends, invites and presence; screenshots are local-only in v1.
- World hosting: LAN, then UPnP direct, then TCP relay.
- Out of scope: coins, cosmetics store, bundles, gifting, sales, emote shop.

## Layout

- `mod/common` — config (`CampfireProperties`), JSON (`CampfireJson`), invite
  codes (`InviteCode`), relay client, presence, telemetry, theme tokens.
- `mod/fabric` — Fabric entrypoints, screens, mixins, YACL settings.
- `mod/neoforge` — NeoForge entrypoint and screens for the 1.21.1 line.
- `backend/relay-go` — relay tunnel (see `docs/protocol.md`).
- `backend/cloudflare` — social/presence Worker.
- `backend/social`, `backend/relay` — legacy Java reference servers, kept for
  the hosted-VPS path.

## Configuration

Relay and social endpoints are not hard-coded at call sites. Resolution order:

1. JVM system property (`-Dcampfire.relay_host=...`)
2. `campfire.properties` in the game directory
3. Built-in default

## Compatibility policy

- Each loader jar is compiled against exactly one Minecraft version and its
  metadata declares that version (`~<version>` for Fabric).
- Mixin injections are declared with `defaultRequire: 0`: if a Minecraft patch
  moves a method, the affected cosmetic feature is skipped and an error is
  logged instead of the client crashing.
- Bumping the Fabric Minecraft version requires bumping `yarn_version` to the
  matching Yarn build in `gradle.properties`.
