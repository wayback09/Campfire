# Campfire protocol

Campfire uses two independent transports. Both are plain TCP so a vanilla
Minecraft client can be bridged without any mod installed on the guest side.

## 1. Relay tunnel (`backend/relay-go`)

The relay is a single Go binary. It multiplexes several connection kinds on one
port by peeking at the first bytes of every accepted connection.

| First bytes | Meaning |
| --- | --- |
| `CAMPFIRE_HOST` | A Campfire host registering a world |
| `CAMPFIRE_PIPE` | The host opening the data pipe for a specific guest |
| `CAMPFIRE_JOIN` | A Campfire mod guest requesting a world |
| `GET ` / `HEAD ` | HTTP health check (uptime pingers) |
| anything else | A vanilla Minecraft client; the Minecraft handshake is parsed |

Control lines are newline-terminated ASCII. Responses are newline-terminated.

### Host registration

```
client -> relay:  CAMPFIRE_HOST\n              # relay picks a free code
client -> relay:  CAMPFIRE_HOST WXYZ\n         # request a specific code
relay -> client:  OK WXYZ\n                    # 4 chars, from A-Z2-9 (no I/O/0/1)
relay -> client:  ERROR <reason>\n             # e.g. room capacity reached
```

An existing session keeps the connection alive. Liveness is a simple
application-level ping: the host sends `PING\n` on the control channel and the
relay replies `PONG\n`.

### Guest join

```
guest -> relay:   CAMPFIRE_JOIN WXYZ\n
relay -> host:    NEW_GUEST <guestID>\n        # host learns a guest is waiting
host  -> relay:   CAMPFIRE_PIPE <guestID>\n    # host opens the data pipe
relay -> guest:   OK CONNECTED\n               # then bytes are spliced verbatim
relay -> guest:   ERROR World not found or offline\n
```

`<guestID>` is an opaque token (`<unix-nanos>_<random>`). The relay pairs the
pipe with the waiting guest within a timeout; if the host never opens a pipe the
guest is dropped with a timeout.

### Vanilla guest path

A guest without the mod connects to the relay and sends a normal Minecraft
handshake whose `serverAddress` is `CAMP-XXXX` (or `XXXX`, optionally
`CAMP-XXXX.host`). The relay extracts the code, asks the host for a pipe, and
splices the client's byte stream into the host's local Minecraft server.

### Invite codes

P2P invite codes are generated offline in the mod (`InviteCode`):

- `CAMP-XXXXX-XXXXX` — IPv4 address (4 bytes) + port (2 bytes) encoded as
  Base32 without `0`, `1`, `I`, `O`, then split 5-5.
- A `CAMP-`-prefixed string that does not decode is rejected rather than being
  reinterpreted as a hostname.
- `host:port` and bare `host` are also accepted for direct joins (port defaults
  to 25565).

## 2. Social / presence API (`backend/cloudflare/worker.js`)

Cloudflare Worker backed by a KV namespace (`CAMPFIRE_KV`). Routes:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/health` | Liveness probe |
| `GET` | `/friends/search?username=` | Look up a Minecraft account by name |
| `POST` | `/presence` | Publish `{username, status, worldName, inviteCode}` |
| `POST` | `/presence/query` | Batch-read several friends' presence |
| `POST` | `/invites/send` | Queue an invite for an offline friend |
| `GET` | `/invites/poll?username=` | Drain that friend's invite queue |
| `POST` | `/tickets` | Create a short-lived relay ticket |
| `GET` | `/tickets/<code>` | Redeem a ticket |
| `POST` | `/telemetry/event` | Record a client telemetry event |
| `GET` | `/telemetry/stats` | Aggregate stats (`&format=html` for a page) |

`status` is one of `MENU`, `SINGLEPLAYER`, `HOSTING`, `MULTIPLAYER`.

The base URL is configurable via `campfire.social_url` (see README); the default
points at the deployed Enzonic worker.

> **Known limitation:** the presence, invite and telemetry endpoints are
> unauthenticated. Anyone who knows a username can publish or read presence for
> it. Do not treat the social API as trustworthy for identity-sensitive
> decisions until a signed-request scheme is added.
