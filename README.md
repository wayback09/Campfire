<p align="center">
  <img src="Campfire.gif" alt="Campfire Animation" width="380" />
</p>

<p align="center">
  <img src="minecraft_title.png" alt="Campfire Title Screen Integration" width="850" />
</p>

<p align="center">
  <b>Free and open-source peer-to-peer world hosting and social mod for Minecraft (Fabric &amp; NeoForge).</b><br/>
  No paid cosmetics • No microtransactions • No VPS or subscription required
</p>

<p align="center">
  <a href="https://enzonic.com/r/CQ3WBGRL" target="_blank" rel="noopener noreferrer">
    <img src="https://img.shields.io/badge/Powered%20by-Enzonic-b2ff00?style=for-the-badge&logoColor=110e0a&labelColor=1a1512" alt="Powered by Enzonic" />
  </a>
</p>

---

## Features

- **P2P World Hosting**: Open any singleplayer world to friends over the internet or LAN. Automatically forwards ports via UPnP and generates compact invite codes (`CAMP-XXXXX-XXXXX`).
- **Friends & Live Presence**: Track friends' real-time in-game status (*In Menu, Singleplayer, Hosting, Multiplayer*) with one-click join or invite actions.
- **Screenshot HUD Toast & Clipboard Copy**

---

## Supported Versions

| Loader | Minecraft | Mod version | Build output |
| --- | --- | --- | --- |
| Fabric | 1.21.11 | `0.1.0+1.21.11` | `mod/fabric/build/libs/Campfire-0.1.0+1.21.11.jar` |
| NeoForge | 1.21.1 | `0.1.0+1.21.1` | `mod/neoforge/build/libs/Campfire-NeoForge-0.1.0+1.21.1.jar` |

The Fabric jar is compiled against exactly the Minecraft version in
`gradle.properties` (`minecraft_version` / `yarn_version`), and its
`fabric.mod.json` declares that version as a dependency. Mixin injections are
declared as non-required, so a future Minecraft patch that moves a method will
disable a single cosmetic feature rather than crash the game.

---

## Building from Source

The build needs **two JDKs**:

| JDK | Why |
| --- | --- |
| **25** | Gradle 9.8.2 / Fabric Loom 1.19 require a JVM 25 runtime. This is the `JAVA_HOME` used to launch Gradle. |
| **21** | The Java toolchain the mod is *compiled* with (Minecraft 1.21.x targets Java 21 bytecode). |

Gradle discovers the Java 21 toolchain automatically. Set `JAVA_HOME` to your
JDK 25 install before building:

```bash
git clone https://github.com/wayback09/Campfire.git
cd Campfire

# Fabric + NeoForge jars, plus the shared-module unit tests
export JAVA_HOME=/path/to/jdk-25
./gradlew :mod:common:test :mod:fabric:build :mod:neoforge:build
```

On Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'
.\gradlew.bat :mod:common:test :mod:fabric:build :mod:neoforge:build
```

Run only the Fabric jar build with `./gradlew :mod:fabric:build`.

### Relay server tests

```bash
cd backend/relay-go
go vet ./...
go test ./...
```

---

## Configuration

Relay and social endpoints are configurable instead of hard-coded. Values are
resolved in this order (first match wins):

1. JVM system property, e.g. `-Dcampfire.relay.host=relay.example.com`
2. `config/campfire.properties` (or `campfire.properties` in the game directory)
3. Built-in defaults

```properties
# campfire.properties (place next to the Minecraft game directory)
campfire.relay_host=relay.enzonic.com
campfire.relay_port=25631
campfire.social_url=https://campfire-social.example.workers.dev
campfire.featured_servers_url=https://example.com/featured_servers.json
```

The same keys can be overridden per launch with JVM system properties, e.g.
`-Dcampfire.relay_host=relay.example.com`.

See [`mod/common/src/main/java/com/wayback09/campfire/CampfireProperties.java`](mod/common/src/main/java/com/wayback09/campfire/CampfireProperties.java)
for how resolution works, and the `PROP_*` constants in
[`RelayClient`](mod/common/src/main/java/com/wayback09/campfire/RelayClient.java),
[`PresenceManager`](mod/common/src/main/java/com/wayback09/campfire/PresenceManager.java) and
[`FeaturedServerStore`](mod/common/src/main/java/com/wayback09/campfire/FeaturedServerStore.java)
for the authoritative key list.

---

## Infrastructure & Sponsorship

Campfire's multi-region tunneling network is proudly **Powered by [Enzonic](https://enzonic.com/r/CQ3WBGRL)** and the **[Enzonic Relay Network](https://relay.enzonic.com/)**.  
- **[Enzonic Cloud](https://enzonic.com/r/CQ3WBGRL)** High-performance Minecraft servers, free tier, and reliable game hosting.
- **[Enzonic Relay](https://relay.enzonic.com/)** Fast, low-latency Minecraft server tunnels across global regions without port forwarding.

---

## License

This project is licensed under the **GNU General Public License v3.0** (GPL-3.0). See [LICENSE](LICENSE) for details.
