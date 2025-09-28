<h1 align="center">
  <br>
  <img src="https://git.ryujinx.app/BeZide/kenji-sc/-/raw/libryujinx_bionic/distribution/misc/Logo_benji.png" alt="Benji-SC">
  <br>


# Benji-SC

Benji-SC is an Kenji-NX fork focused on quality-of-life improvements around shortcuts, inputs, and UI.  
Goal: jump into your game faster, with fewer rough edges.

> **Note:** Keys and firmware are **not** included.

---

## Version 2.0.4.1g – Highlights

- **Overlay-Menu-Button** Position Dropdown and Opacity Slider (Settings → User Interface)
- **Stretch to Fullscreen** toggle (Settings → Graphics)
- **Amiibo** support
    - On Homescreen press the folder button on the bottom right to load 5 Amiibo files on Quickslots 1-5.
    - Press the Overlay button in-Game and use the Quickslots 1-5 to load the Amiibo file in-Game.
- **Timezone** games are using the Android-Device Timezone instead of UTC
- **Settings** added x0.75 Resolution setting.
- **Home-screen shortcuts** from the game’s long-press bottom sheet  
  (choose **Custom icon** or **App icon** – uses the same grid artwork).
- **On-screen keyboard**: focus/visibility fixes.
- **Loading screen**: native progress piped to the UI overlay.
- **L3/R3** support for **physical controllers**.
- **Virtual controller**: separate **L3** and **R3** buttons. Removed old implementation of **L3** and **R3** (doubletab+hold of the stick).
- **Language & Region** selection (Settings → System).
- **Screen orientation** preference (Settings → User Interface):  
  **Sensor**, **SensorLandscape**, **SensorPortrait**.
- Save files: groundwork for **item-ID based** mapping.

---

## Quick Start

1. **Install Keys & Firmware**  
   *Settings → User Interface → Install Keys / Install Firmware*
2. **Add Game Folder**  
   *Settings → User Interface → Add Game Folder*
3. (Optional) **Create a Home-Screen Shortcut**  
   Long-press a game → bottom sheet → **Create shortcut** → choose **Custom icon** or **App icon**, edit name, confirm the Android “Add to Home screen” sheet.

---

## Shortcuts

- Create from the game’s **long-press** bottom sheet.
- **Custom icon** (pick an image) or **App icon** (uses the grid artwork).
- **Editable name** before pinning.
- Deep-link integration: shortcuts start the game directly (no extra picker).

---

## Screen Orientation

- Configure under *Settings → User Interface → Screen Orientation*:
  - **Sensor** (rotates with device),
  - **SensorLandscape**,
  - **SensorPortrait**.
- In Sensor mode, the app re-queries the surface and resizes safely to avoid stretching.

---

## Import App Data (ZIP)

The ZIP can contain one or more of the following top-level folders:

bis/
games/
profiles/
system/

- Import via *Settings → User Interface → Import App Data*.
- Save-data mapping by item IDs is prepared; future updates will improve cross-install portability.

---

## Build

- Open in **Android Studio** (recent version recommended).
- Use the project’s configured Gradle & SDKs.
- Run on a device (Android 10+ recommended).

---

## Troubleshooting

- If the shortcut only opens the app (not the game), ensure you created it via the in-app **Create shortcut** action (long-press on the game) so deep-linking is embedded correctly.
- If rotation looks off in **Sensor** mode, verify the orientation setting and try again; the app resizes/re-queries surfaces to prevent stretching.

---

## Credits

- Kenji-NX / Ryujinx and all upstream contributors.
- [LibHac](https://github.com/Thealexbarney/LibHac) is used for our file-system.
- [AmiiboAPI](https://www.amiiboapi.com) is used in our Amiibo emulation.
- [ldn_mitm](https://github.com/spacemeowx2/ldn_mitm) is used for one of our available multiplayer modes.
- [ShellLink](https://github.com/securifybv/ShellLink) is used for Windows shortcut generation.
- Community testers and feedback.

---

## License

See the license information in this repository and upstream.
