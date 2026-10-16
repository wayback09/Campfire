/**
 * Campfire Social & Signaling Backend for Cloudflare Workers
 * Free Tier: 100,000 req/day + 10M KV reads/month.
 *
 * Provides:
 *  - Real-time player presence (Online, Offline, Hosting, Menu)
 *  - One-click Friend Invites with offline delivery queue
 *  - Player lookup via official Mojang Profiles API
 *  - P2P relay connection tickets
 */

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const method = request.method.toUpperCase();

    // CORS headers for all responses
    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Content-Type": "application/json"
    };

    if (method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders });
    }

    // 1. Health check
    if (url.pathname === "/health") {
      return json({ ok: true, service: "campfire-social", version: "1.0.0" }, corsHeaders);
    }

    // 2. Player Search / Validation via Mojang API
    // GET /friends/search?username=<name>
    if (url.pathname === "/friends/search" && method === "GET") {
      const username = url.searchParams.get("username");
      if (!username) return json({ error: "Missing username" }, corsHeaders, 400);

      try {
        const mojangRes = await fetch(`https://api.mojang.com/users/profiles/minecraft/${encodeURIComponent(username)}`, {
          headers: {
            "User-Agent": "Campfire-Mod/1.0 (wayback09)"
          }
        });
        if (mojangRes.status === 204 || mojangRes.status === 404) {
          return json({ found: false, error: "Player does not exist on Minecraft" }, corsHeaders, 404);
        }
        if (!mojangRes.ok) {
          return json({ found: false, error: "Mojang API unavailable" }, corsHeaders, 502);
        }
        const data = await mojangRes.json();
        return json({
          found: true,
          username: data.name,
          uuid: data.id,
          avatarUrl: `https://mc-heads.net/avatar/${data.id}/64`
        }, corsHeaders);
      } catch (err) {
        return json({ error: err.message }, corsHeaders, 500);
      }
    }

    // 3. Update Player Presence (Heartbeat)
    // POST /presence
    // Body: { username, status, worldName, inviteCode }
    if (url.pathname === "/presence" && method === "POST") {
      try {
        const body = await request.json();
        const username = (body.username || "").trim();
        if (!username) return json({ error: "Missing username" }, corsHeaders, 400);

        const presence = {
          username: username,
          status: body.status || "MENU", // MENU, SINGLEPLAYER, HOSTING, OFFLINE
          worldName: body.worldName || "",
          inviteCode: body.inviteCode || "",
          updatedAt: Date.now()
        };

        // Cache in KV with a 90-second expiration (if no heartbeat sent, marked offline)
        if (env.CAMPFIRE_KV) {
          await env.CAMPFIRE_KV.put(
            `presence:${username.toLowerCase()}`,
            JSON.stringify(presence),
            { expirationTtl: 120 }
          );
        }

        return json({ ok: true, presence }, corsHeaders);
      } catch (err) {
        return json({ error: err.message }, corsHeaders, 500);
      }
    }

    // 4. Batch Query Friends Presence
    // POST /presence/query
    // Body: { usernames: ["player1", "player2"] }
    if (url.pathname === "/presence/query" && method === "POST") {
      try {
        const body = await request.json();
        const usernames = body.usernames || [];
        const results = [];

        for (const name of usernames) {
          const key = `presence:${name.toLowerCase().trim()}`;
          let data = null;
          if (env.CAMPFIRE_KV) {
            data = await env.CAMPFIRE_KV.get(key, "json");
          }

          if (data && (Date.now() - data.updatedAt < 90000)) {
            results.push(data);
          } else {
            results.push({
              username: name,
              status: "OFFLINE",
              worldName: "",
              inviteCode: "",
              updatedAt: data ? data.updatedAt : 0
            });
          }
        }

        return json({ results }, corsHeaders);
      } catch (err) {
        return json({ error: err.message }, corsHeaders, 500);
      }
    }

    // 5. Send Invite to a Friend (Online or Queued Offline)
    // POST /invites/send
    // Body: { from, to, code, world }
    if (url.pathname === "/invites/send" && method === "POST") {
      try {
        const body = await request.json();
        const to = (body.to || "").trim().toLowerCase();
        const from = (body.from || "").trim();
        const code = (body.code || "").trim();
        const world = (body.world || "").trim();

        if (!to || !from || !code) {
          return json({ error: "Missing required fields (to, from, code)" }, corsHeaders, 400);
        }

        const invite = {
          from,
          code,
          world,
          sentAt: Date.now()
        };

        if (env.CAMPFIRE_KV) {
          // Queued for up to 15 minutes
          await env.CAMPFIRE_KV.put(`invite:${to}`, JSON.stringify(invite), { expirationTtl: 900 });
        }

        return json({ ok: true, message: `Invite sent to ${to}` }, corsHeaders);
      } catch (err) {
        return json({ error: err.message }, corsHeaders, 500);
      }
    }

    // 6. Poll for Pending Invites
    // GET /invites/poll?username=<name>
    if (url.pathname === "/invites/poll" && method === "GET") {
      const username = (url.searchParams.get("username") || "").trim().toLowerCase();
      if (!username) return json({ invite: null }, corsHeaders);

      if (env.CAMPFIRE_KV) {
        const key = `invite:${username}`;
        const invite = await env.CAMPFIRE_KV.get(key, "json");
        if (invite) {
          // Delete once delivered so it only pops once
          await env.CAMPFIRE_KV.delete(key);
          return json({ invite }, corsHeaders);
        }
      }

      return json({ invite: null }, corsHeaders);
    }

    // 7. Relay room tickets
    // POST /tickets -> generates random 6-digit room code
    if (url.pathname === "/tickets" && method === "POST") {
      const code = Math.random().toString(36).substring(2, 8).toUpperCase();
      const slot = 10000 + (Math.abs(hashString(code)) % 20000);
      if (env.CAMPFIRE_KV) {
        await env.CAMPFIRE_KV.put(`ticket:${code}`, String(slot), { expirationTtl: 3600 });
      }
      return json({ code, relaySlot: slot }, corsHeaders);
    }

    // GET /tickets/:code -> lookup slot
    if (url.pathname.startsWith("/tickets/") && method === "GET") {
      const code = url.pathname.substring("/tickets/".length).toUpperCase();
      let slot = null;
      if (env.CAMPFIRE_KV) {
        slot = await env.CAMPFIRE_KV.get(`ticket:${code}`);
      }
      if (!slot) return json({ error: "Ticket expired or not found" }, corsHeaders, 404);
      return json({ code, relaySlot: parseInt(slot, 10) }, corsHeaders);
    }

    return json({ error: "Not found" }, corsHeaders, 404);
  }
};

function json(data, headers, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers
  });
}

function hashString(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) - hash + str.charCodeAt(i);
    hash |= 0;
  }
  return hash;
}
