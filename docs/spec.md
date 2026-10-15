# Campfire spec (v1, Option B, no VPS)


## Scope
- Fabric 1.21.x first, NeoForge later.
- Friends + DM/groups, invites/presence, screenshots (local-only v1).
- World hosting: LAN + loopback first, UPnP direct second, local TCP relay third.
- Out of scope: coins, cosmetics store, bundles, gifting, sales, emote shop.

## Backend (no VPS)
- `backend/social`: Java + Netty WebSocket + SQLite, runs on localhost.
- `backend/relay`: Java + Netty TCP splice relay, runs on localhost.
- Migrate same jars to hosted VPS later (Fly/Oracle/Hetzner).
