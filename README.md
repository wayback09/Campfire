# Campfire

Free and open-source peer-to-peer world hosting and social mod for Minecraft (Fabric 1.21.8).

No paid cosmetics, no microtransactions, and no VPS or dedicated server required.

## Features

- **P2P World Hosting**: Open any singleplayer world to friends over the internet or LAN. Automatically forwards ports via UPnP and generates compact invite codes (`CAMP-XXXXX-XXXXX`).
- **Direct Join**: Connect directly using an invite code or standard IP:port address.
- **In-Game Screenshot Gallery**: Browse `.minecraft/screenshots` in-game with preview rendering, Previous/Next buttons, and arrow-key controls.
- **Social / Friends**: Add and remove friends saved locally in `campfire/friends.json`.
- **Sidebar Integration**: Integrated right-hand sidebar accessible from both the Title Screen and the Pause Menu.

## Building from Source

Requires **Java 21**.

```bash
# Clone the repository
git clone https://github.com/wayback09/Campfire.git
cd Campfire

# Build the mod JAR
./gradlew build
```

Built JAR is placed in `mod/fabric/build/libs/` as `Campfire-<version>+<mc_version>.jar`.

## License

This project is licensed under the **GNU General Public License v3.0** (GPL-3.0). See [LICENSE](LICENSE) for details.
