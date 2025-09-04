# Kenjinx Android

Kenjinx Android is a custom Android port of Ryujinx with additional features and optimizations for mobile devices.

## Branches

- **kenji-2.0.3** → Stable base version (aligned with Ryujinx 2.0.3)
- **libryujinx_bionic (2.0.4 patch)** → Experimental / extended version with Android-specific patches

## Features

- Game launching via shortcut support
- Full external storage support
- Firmware & keys installer
- Import/export app data
- User interface customizations
- Motion sensor & performance mode controls

## Build Instructions

1. Clone the repository and initialize submodules:
   ```bash
   git clone <repo-url>
   cd KenjinxAndroid
   git submodule update --init --recursive
   ```

2. Open in **Android Studio**.

3. Select desired branch (`kenji-2.0.3` or `libryujinx_bionic`).

4. Build APK:
   - Use `Build > Make Project`
   - Or run: `./gradlew assembleDebug`

## Contribution

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our workflow and coding guidelines.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details.
