# Campfire

Clean-room social + world-hosting mod for Minecraft 1.21.x - 26.x (Fabric first, NeoForge later).

Author: wayback09

No Essential code or assets used. No coins, no cosmetics store.

## Layout

- `mod/common/` - platform-independent logic (Java)
- `mod/fabric/` - Fabric entry, screens, hooks
- `mod/neoforge/` - NeoForge stub (phase 4)
- `backend/social/` - local Netty + SQLite friends/DM/groups/presence (Option B)
- `backend/relay/` - local TCP relay for world hosting
- `docs/` - spec, protocol, theme
