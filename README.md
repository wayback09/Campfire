<p align="center">
  <img src="Campfire.gif" alt="Campfire Animation" width="380" />
</p>

<h1 align="center">Campfire</h1>

<p align="center">
  <b>Free and open-source peer-to-peer world hosting and social mod for Minecraft (Fabric 1.21.x).</b><br/>
  No paid cosmetics • No microtransactions • No VPS or subscription required
</p>

<p align="center">
  <img src="minecraft_title.png" alt="Campfire Title Screen Integration" width="850" />
</p>

---

## Features

- **P2P World Hosting**: Open any singleplayer world to friends over the internet or LAN. Automatically forwards ports via UPnP and generates compact invite codes (`CAMP-XXXXX-XXXXX`).
- **Friends & Live Presence**: Track friends' real-time in-game status (*In Menu, Singleplayer, Hosting, Multiplayer*) with Mojang username verification and one-click join or invite actions powered by a serverless Cloudflare Worker backend.
- **Featured Partner Servers**: Native injection of vetted partner servers (e.g. `IslandersMC.net`) at the top of the multiplayer list, with built-in protection against accidental deletion or modification.
- **Screenshot HUD Toast & Clipboard Copy**: In-game toast upon taking a screenshot with one-click **Copy Image** directly to the OS clipboard (ready to paste in Discord) or **View** in the full gallery.
- **Integrated Sidebar**: Sleek right-hand sidebar accessible from both the Title Screen and the in-game Pause Menu.

---

## Building from Source

Requires **Java 21** (e.g. Eclipse Adoptium Temurin 21).

```bash
# Clone the repository
git clone https://github.com/wayback09/Campfire.git
cd Campfire

# Build the Fabric mod JAR
./gradlew :mod:fabric:build
```

The compiled mod JAR is placed in `mod/fabric/build/libs/` as:
`Campfire-<version>+<mc_version>.jar`

---

## License

This project is licensed under the **GNU General Public License v3.0** (GPL-3.0). See [LICENSE](LICENSE) for details.
