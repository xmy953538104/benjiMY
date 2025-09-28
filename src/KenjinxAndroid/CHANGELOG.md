# Changelog

## 2.0.4.1g — 2025-09-28

### Added
- **Overlay-Menu-Button** Position Dropdown and Opacity Slider (Settings → User Interface)

## 2.0.4.1f — 2025-09-28

### Added
- **Stretch to Fullscreen** toggle in Graphics Settings

## 2.0.4.1e — 2025-09-26

### Added
- **Amiibo** Support:
    - On Homescreen press the folder button on the bottom right to load 5 Amiibo files on Quickslots 1-5.
    - Press the Overlay button in-Game and use the Quickslots 1-5 to load the Amiibo file in-Game.
- **Timezone**: The Android-Device Timezone is now used instead of a fixed UTC

### Fixed
- **Orientation preference**: fixed an issue where SensorLandscape doesn't work

## 2.0.4.1d — 2025-09-11

### Added
- x075 Resolution setting

### Fixed
- **Virtual controller**: removed old implementation of **L3** and **R3** (doubletab+hold of the stick).

## 2.0.4.1c — 2025-09-07

### Fixed
- **Title-IDs** fixed bloating of the titleid_map.ndjson in the save game folder.

## 2.0.4.1b — 2025-09-07

### Added
- Home-screen **Shortcut creation** directly from the game’s long-press bottom sheet  
  (choose **Custom icon** or **App icon**; uses the same grid artwork).
- **Language & Region** selection in Settings → System.
- **Orientation preference** in Settings → User Interface  
  (Sensor / SensorLandscape / SensorPortrait).
- Save data: groundwork for **save files mapped by item IDs**.

### Fixed
- **On-screen keyboard** focus & visibility handling.
- **Loading screen**: progress reporting from native side → UI overlay.
- **L3/R3**: full support on physical controllers.
- **Virtual controller**: separate buttons for **L3** and **R3**.

### Notes
- Many more updates planned.
- App renamed to **Benji-SC** (formerly Kenjinx Android fork).
