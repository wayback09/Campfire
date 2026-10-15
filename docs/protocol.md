# Campfire protocol (own design, v1 draft)


- Auth: Mojang UUID + session token -> local JWT (own, not Essential infra).
- Social REST: /friends, /groups, /messages. WS: presence, typing, invites.
- Signaling: host creates ticket (short code) -> guest redeems -> relay slot assigned.
- Relay: both sides open outbound TCP to relay, relay splices bytes. No direct port forward required.
