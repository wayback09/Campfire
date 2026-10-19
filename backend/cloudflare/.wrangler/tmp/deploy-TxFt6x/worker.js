var __defProp = Object.defineProperty;
var __name = (target, value) => __defProp(target, "name", { value, configurable: true });

// worker.js
var worker_default = {
  async fetch(request, env) {
    const url = new URL(request.url);
    const method = request.method.toUpperCase();
    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Content-Type": "application/json"
    };
    if (method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders });
    }
    if (url.pathname === "/health") {
      return json({ ok: true, service: "campfire-social", version: "1.0.0" }, corsHeaders);
    }
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
    if (url.pathname === "/presence" && method === "POST") {
      try {
        const body = await request.json();
        const username = (body.username || "").trim();
        if (!username) return json({ error: "Missing username" }, corsHeaders, 400);
        const presence = {
          username,
          status: body.status || "MENU",
          // MENU, SINGLEPLAYER, HOSTING, OFFLINE
          worldName: body.worldName || "",
          inviteCode: body.inviteCode || "",
          updatedAt: Date.now()
        };
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
          if (data && Date.now() - data.updatedAt < 9e4) {
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
          await env.CAMPFIRE_KV.put(`invite:${to}`, JSON.stringify(invite), { expirationTtl: 900 });
        }
        return json({ ok: true, message: `Invite sent to ${to}` }, corsHeaders);
      } catch (err) {
        return json({ error: err.message }, corsHeaders, 500);
      }
    }
    if (url.pathname === "/invites/poll" && method === "GET") {
      const username = (url.searchParams.get("username") || "").trim().toLowerCase();
      if (!username) return json({ invite: null }, corsHeaders);
      if (env.CAMPFIRE_KV) {
        const key = `invite:${username}`;
        const invite = await env.CAMPFIRE_KV.get(key, "json");
        if (invite) {
          await env.CAMPFIRE_KV.delete(key);
          return json({ invite }, corsHeaders);
        }
      }
      return json({ invite: null }, corsHeaders);
    }
    if (url.pathname === "/tickets" && method === "POST") {
      const code = Math.random().toString(36).substring(2, 8).toUpperCase();
      const slot = 1e4 + Math.abs(hashString(code)) % 2e4;
      if (env.CAMPFIRE_KV) {
        await env.CAMPFIRE_KV.put(`ticket:${code}`, String(slot), { expirationTtl: 3600 });
      }
      return json({ code, relaySlot: slot }, corsHeaders);
    }
    if (url.pathname.startsWith("/tickets/") && method === "GET") {
      const code = url.pathname.substring("/tickets/".length).toUpperCase();
      let slot = null;
      if (env.CAMPFIRE_KV) {
        slot = await env.CAMPFIRE_KV.get(`ticket:${code}`);
      }
      if (!slot) return json({ error: "Ticket expired or not found" }, corsHeaders, 404);
      return json({ code, relaySlot: parseInt(slot, 10) }, corsHeaders);
    }
    if (url.pathname === "/telemetry/event" && method === "POST") {
      try {
        const body = await request.json();
        const installId = (body.installId || "").trim();
        const event = (body.event || "unknown").trim();
        const modVersion = (body.modVersion || "unknown").trim();
        const mcVersion = (body.mcVersion || "unknown").trim();
        const loader = (body.loader || "fabric").trim().toLowerCase();
        const os = (body.os || "unknown").trim().toLowerCase();
        if (!installId) return json({ error: "Missing installId" }, corsHeaders, 400);
        if (env.CAMPFIRE_KV) {
          const today = (/* @__PURE__ */ new Date()).toISOString().slice(0, 10);
          const statsKey = `telemetry:day:${today}`;
          let dayStats = await env.CAMPFIRE_KV.get(statsKey, "json") || {
            date: today,
            totalEvents: 0,
            launches: 0,
            hosts: 0,
            joins: 0,
            loaders: {},
            mcVersions: {},
            osList: {},
            uniqueInstalls: []
          };
          dayStats.totalEvents++;
          if (event === "launch") dayStats.launches++;
          if (event === "host_world") dayStats.hosts++;
          if (event === "join_world") dayStats.joins++;
          dayStats.loaders[loader] = (dayStats.loaders[loader] || 0) + 1;
          dayStats.mcVersions[mcVersion] = (dayStats.mcVersions[mcVersion] || 0) + 1;
          dayStats.osList[os] = (dayStats.osList[os] || 0) + 1;
          if (!dayStats.uniqueInstalls.includes(installId)) {
            if (dayStats.uniqueInstalls.length < 5e3) {
              dayStats.uniqueInstalls.push(installId);
            }
          }
          await env.CAMPFIRE_KV.put(statsKey, JSON.stringify(dayStats), { expirationTtl: 86400 * 60 });
          const globalKey = "telemetry:totals";
          let totals = await env.CAMPFIRE_KV.get(globalKey, "json") || { totalLaunches: 0, totalHosts: 0, totalJoins: 0 };
          if (event === "launch") totals.totalLaunches = (totals.totalLaunches || 0) + 1;
          if (event === "host_world") totals.totalHosts = (totals.totalHosts || 0) + 1;
          if (event === "join_world") totals.totalJoins = (totals.totalJoins || 0) + 1;
          await env.CAMPFIRE_KV.put(globalKey, JSON.stringify(totals));
        }
        return json({ ok: true }, corsHeaders);
      } catch (err) {
        return json({ error: err.message }, corsHeaders, 500);
      }
    }
    if (url.pathname === "/telemetry/stats" && method === "GET") {
      const today = (/* @__PURE__ */ new Date()).toISOString().slice(0, 10);
      let dayStats = null;
      let totals = null;
      if (env.CAMPFIRE_KV) {
        dayStats = await env.CAMPFIRE_KV.get(`telemetry:day:${today}`, "json");
        totals = await env.CAMPFIRE_KV.get("telemetry:totals", "json");
      }
      const activeToday = dayStats ? dayStats.uniqueInstalls ? dayStats.uniqueInstalls.length : 0 : 0;
      const responseData = {
        date: today,
        activePlayersToday: activeToday,
        todayLaunches: dayStats?.launches || 0,
        todayHosts: dayStats?.hosts || 0,
        todayJoins: dayStats?.joins || 0,
        loaders: dayStats?.loaders || {},
        mcVersions: dayStats?.mcVersions || {},
        operatingSystems: dayStats?.osList || {},
        lifetimeTotals: totals || { totalLaunches: 0, totalHosts: 0, totalJoins: 0 }
      };
      if (url.searchParams.get("format") === "html") {
        return new Response(renderTelemetryHtml(responseData), {
          headers: { "Content-Type": "text/html; charset=utf-8", "Access-Control-Allow-Origin": "*" }
        });
      }
      return json(responseData, corsHeaders);
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
__name(json, "json");
function hashString(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) - hash + str.charCodeAt(i);
    hash |= 0;
  }
  return hash;
}
__name(hashString, "hashString");
function renderTelemetryHtml(stats) {
  const formatDict = /* @__PURE__ */ __name((dict) => Object.entries(dict || {}).map(([k, v]) => `<li><b>${k}</b>: <span class="badge">${v}</span></li>`).join("") || "<li>None recorded yet</li>", "formatDict");
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>\u{1F525} Campfire Telemetry Dashboard</title>
  <style>
    :root {
      --bg: #121316;
      --card-bg: #1c1d22;
      --accent: #FF7A29;
      --text: #f0f0f4;
      --subtext: #9ba1b0;
      --border: rgba(255, 255, 255, 0.08);
    }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      background: var(--bg);
      color: var(--text);
      margin: 0;
      padding: 30px 20px;
      display: flex;
      justify-content: center;
    }
    .container {
      max-width: 900px;
      width: 100%;
    }
    h1 {
      font-size: 26px;
      color: var(--accent);
      margin-bottom: 6px;
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .date-badge {
      font-size: 14px;
      color: var(--subtext);
      margin-bottom: 24px;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
      margin-bottom: 28px;
    }
    .card {
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 20px;
    }
    .metric {
      font-size: 32px;
      font-weight: 700;
      color: #fff;
      margin: 6px 0 0 0;
    }
    .label {
      font-size: 13px;
      color: var(--subtext);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .sections {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }
    @media (max-width: 650px) {
      .sections { grid-template-columns: 1fr; }
    }
    ul {
      list-style: none;
      padding: 0;
      margin: 10px 0 0 0;
    }
    li {
      padding: 8px 0;
      border-bottom: 1px solid var(--border);
      display: flex;
      justify-content: space-between;
      font-size: 14px;
    }
    li:last-child { border-bottom: none; }
    .badge {
      background: rgba(255, 122, 41, 0.15);
      color: var(--accent);
      padding: 2px 8px;
      border-radius: 6px;
      font-weight: 600;
    }
  </style>
</head>
<body>
  <div class="container">
    <h1>\u{1F525} Campfire Telemetry</h1>
    <div class="date-badge">Stats for: <b>${stats.date}</b> \u2022 Refresh for live updates</div>

    <div class="grid">
      <div class="card">
        <div class="label">Active Players (Today)</div>
        <div class="metric">${stats.activePlayersToday}</div>
      </div>
      <div class="card">
        <div class="label">Launches (Today / All Time)</div>
        <div class="metric">${stats.todayLaunches} <span style="font-size:16px;color:var(--subtext)">/ ${stats.lifetimeTotals.totalLaunches || 0}</span></div>
      </div>
      <div class="card">
        <div class="label">Worlds Hosted (Today / All)</div>
        <div class="metric">${stats.todayHosts} <span style="font-size:16px;color:var(--subtext)">/ ${stats.lifetimeTotals.totalHosts || 0}</span></div>
      </div>
      <div class="card">
        <div class="label">Invites Accepted (Today)</div>
        <div class="metric">${stats.todayJoins}</div>
      </div>
    </div>

    <div class="sections">
      <div class="card">
        <div class="label">Minecraft Versions</div>
        <ul>${formatDict(stats.mcVersions)}</ul>
      </div>
      <div class="card">
        <div class="label">Mod Loaders</div>
        <ul>${formatDict(stats.loaders)}</ul>
      </div>
      <div class="card">
        <div class="label">Operating Systems</div>
        <ul>${formatDict(stats.operatingSystems)}</ul>
      </div>
      <div class="card">
        <div class="label">Privacy & Transparency</div>
        <p style="font-size:13px;color:var(--subtext);line-height:1.5;margin-top:10px;">
          All metrics are strictly anonymous (UUID install ID, OS, game version). No usernames, IPs, or personal identifiers are stored.
        </p>
      </div>
    </div>
  </div>
</body>
</html>`;
}
__name(renderTelemetryHtml, "renderTelemetryHtml");
export {
  worker_default as default
};
//# sourceMappingURL=worker.js.map
