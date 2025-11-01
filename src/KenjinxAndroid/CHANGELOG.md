# Changelog

## 2.0.5l — 2025-11-01

### Added
- **Emulation Service**: Keeps the Game running in the Background.

## 2.0.5k — 2025-10-28

### Added
- **Virtual Controller**: Added Controller Scale Slider.

## 2.0.5j — 2025-10-27

### Added
- **Minor Performance Optimizations**: Credits to TechDunk.

### Fixed
- **Tablets/Foldable**: Fixed Gamescreen for Tablets and Foldables.

## 2.0.5i — 2025-10-27

### Added
- **Nightly Patches**: added some nightly patches from Kenji-NX. All Credits to KeatonTheBot, LotP, Coxxs, GreemDev, Xam
- **DLCs/Updates**: Autoload title updates and dlc from selected folder. Credits to Jochem Kuipers.

## 2.0.5h — 2025-10-27

### Added
- **Virtual Controller**: Added 6 new Layouts for Virtual Controllers, selectable in Input Settings.
- **Rendering**: Added an option to diasable Threaded Rendering. This reduces Performance, but fixes crashes in some Games.

## 2.0.5g — 2025-10-24

### Fixed
- **Save Manager**: Fixed broken import/export.

## 2.0.5f — 2025-10-23

### Added
- **Save Manager**: Added a import/export function for saves as .zip. Compatible with Eden.

## 2.0.5e — 2025-10-23

### Added
- **Cheats**: Added a Cheat Import function.
- **Mods**: Added a Mod Manager with import/delete.

## 2.0.5d — 2025-10-23

### Fixed
- **XBox Controllers**: Fixed L2 / R2 not working.

## 2.0.5c — 2025-10-23

### Fixed
- **Crashes**: Fixed some crashes.

## 2.0.5b — 2025-10-23

### Added
- **Cheat** Suport: Added a Cheat Manager by Long pressing a Game.
  - Cheat need to be Placed in files/mods/contents/<TITLEID>/cheats/<BUILDID>.txt
  - <TITLEID> and <BUILDID> need to be in Uppercase.

## 2.0.5a — 2025-10-09

### Added
- **Missing functions**: Added Missing functions to Kenji-NX 2.0.5.
  - Amiibo support.
  - Homescreen shortcuts

## 2.0.4.1g — 2025-10-02

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
