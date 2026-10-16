# Cloudflare Setup Guide for Campfire Social & Invites

This guide sets up your free, zero-cost Cloudflare Worker to power:
- **Player Search & Discovery** (via official Mojang API)
- **Live Friend Presence** (Online, In Menu, Singleplayer, Hosting)
- **One-Click World Invites** (Instant toasts + Offline Delivery Queue)
- **P2P Relay Connection Tickets**

Cloudflare's Free Tier includes:
- **100,000 requests/day**
- **10,000,000 KV reads/month**
- Free global HTTPS and DDoS protection

---

## 3-Minute Setup (Web Dashboard - No Terminal Needed)

### Step 1: Create a KV Namespace (for storing presence and invites)
1. Log in to your [Cloudflare Dashboard](https://dash.cloudflare.com/).
2. In the left sidebar, click **Storage & Databases** -> **KV**.
3. Click **Create Namespace**.
4. Name it: `CAMPFIRE_KV` and click **Add**.

### Step 2: Create the Worker
1. In the left sidebar, click **Compute (Workers) -> Workers & Pages**.
2. Click **Create** -> **Create Worker**.
3. Name it: `campfire-social` and click **Deploy**.
4. Once deployed, click **Edit Code**.
5. Replace the template code with the full contents of [`backend/cloudflare/worker.js`](file:///c:/Users/temit/Desktop/Campfire/backend/cloudflare/worker.js).
6. Click **Deploy** in the top right.

### Step 3: Bind the KV Namespace to the Worker
1. Back in your `campfire-social` worker page, go to **Settings** -> **Bindings**.
2. Click **Add** -> **KV Namespace**.
3. Fill in:
   - **Variable name**: `CAMPFIRE_KV` (must match exactly)
   - **KV namespace**: select `CAMPFIRE_KV` from the dropdown
4. Click **Deploy** or **Save**.

### Step 4: Get Your Worker URL
1. On your worker's overview page, copy the public URL:
   `https://campfire-social.<your-subdomain>.workers.dev`
2. Test it in your browser: `https://campfire-social.<your-subdomain>.workers.dev/health`
   You should see: `{"ok":true,"service":"campfire-social","version":"1.0.0"}`

---

## Connecting the Campfire Mod to Your Cloudflare Worker

In your Minecraft installation folder (`.minecraft/campfire/`):
Open (or create) `campfire.properties` and add:
```properties
social_url=https://campfire-social.<your-subdomain>.workers.dev
```
Or launch Minecraft with the JVM argument:
```
-Dcampfire.social_url=https://campfire-social.<your-subdomain>.workers.dev
```

*(If not specified, Campfire defaults to `http://127.0.0.1:8090` for local dev testing via `SocialServer.java`)*
