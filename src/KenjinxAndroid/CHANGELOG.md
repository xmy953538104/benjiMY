# 📑 Changelog

All notable changes to **Kenji-SC** will be documented in this file.  
This project follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) and uses semantic versioning.

---

## [Unreleased]
- Minor UI refinements
- Further improvements to shortcut handling

---

## [2.0.3] - 2025-09-04
### Added
- Initial fork of **Kenji-NX** → **Kenji-SC** ("ShortCut").
- Integrated **shortcut creation** directly into the emulator app (no external helper required).
- Added **"Shortcut Guide"** in the settings menu.
- Default game folder is suggested automatically when creating shortcuts.

### Changed
- App renamed to **Kenji-SC** with unique package name (`org.kenjinxsc.android`) to allow parallel installation with original **Kenji-NX**.
- UI updated with an extra button on the home screen for shortcut creation.

### Fixed
- Orientation workaround during Android shortcut creation (forces portrait mode temporarily).
- Shortcut creation now restores orientation properly after confirmation.

---

## [1.0.0] - 2025-08-XX
### Added
- Standalone **KenjiLauncher** released for creating Android home screen shortcuts for Kenji-NX games.
- First proof-of-concept before integration into the main emulator app.

---
