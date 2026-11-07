# TiltBoy Keyboard

<img src="docs/images/icon.png" width="96" align="right" alt="TiltBoy icon">

A keyboard for Android game handhelds that you type on with the analog sticks.
The left stick picks a key and the right stick flicks the letter.
It types English and numbers, and Japanese with kana-kanji conversion.

[日本語の説明はこちら](README.ja.md)

Honest note: typing with sticks is slower than a touchscreen. TiltBoy is for the times you'd rather not let go of the controller.

## What it does

- **One move per letter.** Hold the left stick toward a key (or leave it centred), then tilt the right stick toward the letter and let go. You never walk a cursor across a keyboard.
- **Every mode works the same way.** English, numbers and Japanese kana share one layout, one set of buttons and one on-screen guide.
- **Leaves your games alone.** TiltBoy only reads the controller while the keyboard is on screen. Games and emulators get every button as usual.
- **No internet permission.** The app asks for one permission, vibration. Voice input is removed.
- **Japanese with conversion.** Kana-kanji conversion comes from [Sumire Japanese Keyboard](https://github.com/KazumaProject/JapaneseKeyboard), the open-source keyboard TiltBoy is built on.

## Devices

| Device | Android | Status |
|---|---|---|
| Anbernic RG477V | 14 | Tested |
| AYN Thor | 13 | Tested (both screens) |

Other Android handhelds with two analog sticks should work.
On a device whose sticks don't reach the keyboard, TiltBoy switches to a D-pad layout by itself.
If something doesn't work, open TiltBoy → **Try it and check the controller**, reproduce the problem, and attach the log to an issue.

Android 7.0 or newer.

## Install

1. Download `tiltboy-*.apk` from [Releases](../../releases) and open it on the device. Allow installing apps from that source if Android asks.
2. Open **TiltBoy**. Under **Getting started**:
   - **Button layout**: pick the picture that looks like your device (X on top or Y on top).
   - **Set up the keyboard**: turn TiltBoy on, then switch to it.
3. Tap any text field. With the controller connected, the guide shows right away.

New versions install over the old one and keep your settings.
[Obtainium](https://github.com/ImranR98/Obtainium) can watch this repository for updates.

Release APKs are signed with this certificate (SHA-256):
`5e7db81ac8a1e6b72deb222f9d8b46304d382ee909faab45314034a5a7d9b46c`

## Controls

The guide on screen shows every key and button, so there is nothing to memorise.
These are the defaults. Button roles can be changed in **Fine tuning**.

| Control | What it does |
|---|---|
| Left stick | Pick one of the 9 keys. Centred = the middle key |
| Right stick | Tilt toward a letter and let go to type it. Roll the stick along the rim to change your mind before letting go |
| A | Enter |
| B | Delete |
| X | Space |
| Y | Next suggestion (Japanese: convert) |
| L2 (hold) | Shift (Japanese: ゛) |
| L1 / R1 (hold) | Numbers while held (Japanese: ゜ / small kana) |
| R2 (hold) + left stick | Symbols that are not on the keys |
| L3 / R3 (click) | Previous / next mode (kana, English, numbers) |
| D-pad | Move the cursor. While converting: ← → pick a candidate, ↑ ↓ convert more or fewer characters |
| Select | Switch between the guide and the touch keys. Hold to hide the keyboard |
| Start | Emoji list |

- English keys: `abcd` `efgh` `ijkl` / `mnop` `qrst` `uvwx` / `yz'-` `,.?!` `@/:#`. On each key, ← ↑ → ↓ type the 1st, 2nd, 3rd and 4th character.
- Number keys: `1234` `5678` `90.,` / `+-*/` `(=)%` `¥$€&` / `!?'"` `~@_#` `<:>;`
- The digits 1–9 and 0 on the left of the guide can also be tapped.
- The slider at the far left of the guide makes the guide see-through (down to 20 %), so you can read the app behind it.

Japanese is described in [README.ja.md](README.ja.md).

## Building

You need JDK 21 and the Android SDK (platform `android-37`, build-tools `37.0.0`, NDK `29.0.14206865`).

```sh
./gradlew :pad_input:test                  # controller engine tests (plain JVM, fast)
./gradlew :app:assembleLiteFdroidRelease   # app/build/outputs/apk/liteFdroid/release/
```

The release APK is unsigned unless `local.properties` names your own key (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
A build signed with a different key will not install over the official one.

Where things are:

- `pad_input/`: the controller engine in plain Kotlin (stick and button state in, typing actions out) and its unit tests.
- `app/src/main/java/com/kazumaproject/markdownhelperkeyboard/pad/`: the Android side. It reads the controller inside the keyboard, draws the guide and holds TiltBoy's settings screens. `TiltBoyPolicy` pins the Sumire settings TiltBoy does not use.
- `app/src/liteFdroid/`: TiltBoy's app name and icon (the `liteFdroid` build is TiltBoy).
- Everything else is Sumire.

## Credits

- **Sumire Japanese Keyboard** by Kazuma Naka ([KazumaProject/JapaneseKeyboard](https://github.com/KazumaProject/JapaneseKeyboard), MIT). TiltBoy starts from Sumire v1.7.139 (commit `16915fb`). The conversion engine, the dictionaries, the touch keyboard and the emoji list are Sumire's work. TiltBoy is an unofficial fork, so please report TiltBoy problems here, not to Sumire. Sumire's own README is kept as [README.sumire.md](README.sumire.md).
- Dictionaries and other third-party material: [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md), [docs/licenses/](docs/licenses/) and **About → licences** in the app.
- **Related work.** The closest idea is JoyFlick, a research prototype for flick-style Japanese input with two joysticks: Yokoyama, Takakura, Shizuki, "JoyFlick: Japanese Text Entry Using Dual Joysticks for Flick Input Users", INTERACT 2021 ([open version](https://inria.hal.science/hal-04292388)). JoyFlick runs on a PC with a Switch Pro Controller and has no kana-kanji conversion. TiltBoy uses one of its findings: people let go of both sticks at almost the same moment, so the key is fixed when a flick starts, not when it ends.

## License

MIT, like Sumire. See [LICENSE](LICENSE).

TiltBoy is an independent project. It is not affiliated with Nintendo, Anbernic, AYN or the Sumire project.
