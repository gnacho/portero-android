# Portero

**Your doorbell, on your phone, without anybody's cloud.**

Portero is an Android client for a Hikvision video doorbell wired through Home
Assistant and go2rtc. When somebody presses the button, the phone rings like a
real call: live video, answer, open the door and talk back.

No account. No vendor app. No push service. One HTTPS connection to a backend you
run yourself.

[![Latest release](https://img.shields.io/github/v/release/gnacho/portero-android?label=release)](https://github.com/gnacho/portero-android/releases/latest)
[![License](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)
![Android 10+](https://img.shields.io/badge/android-10%2B-green)

## The trade-off, up front

Most doorbell apps sell you a feature list and take the cloud in exchange.
Portero goes the other way: it is small in scope, keeps everything on your
network and tells you exactly what it does not do.

| | Portero | Typical vendor app |
|---|---|---|
| Account required | No | Yes |
| Video through a vendor cloud | No | Yes |
| Push service (FCM, ntfy, UnifiedPush) | None, one held connection | Vendor push |
| Works offline from the internet | If your backend is reachable, yes | Only through their cloud |
| Analytics or tracking | None | Usually |
| Two-way audio | Yes, G.711 over ISAPI | Yes |
| Cloud recordings, subscriptions, AI detection | **No** | Often |
| Multiple users, sharing | **No** | Yes |
| iOS | **No** | Usually |

If you want a managed service with cloud clips, this is not it. If you want the
doorbell to ring on your phone and nothing else to leave your house, it is.

## What it does

- Rings like a phone call, with a full-screen incoming-call screen while the
  device is locked.
- Live video with the doorbell audio in the call screen.
- Answer, hang up and open the pedestrian door.
- Talk back from the phone.
- Remembers the settings and reconnects after a reboot.

## What it does not do

- No cloud recording, no snapshots gallery, no motion notifications.
- No multi-user sharing, no guest access.
- No iOS app.
- No background magic without a reachable backend: the app keeps one
  connection open; if the backend is down, the phone stays silent.

## How it works

```
doorbell --ISAPI-- backend (Go) --HTTPS/SSE-- this app
                      |
                    go2rtc --HLS-- this app
```

- A foreground service holds a Server-Sent Events connection to
  `GET /api/events`. A `ringing` state becomes the incoming-call notification.
- Video is HLS from go2rtc, proxied by the backend, played with Media3.
- Buttons call `POST /api/action`; answer and cancel go straight to the doorbell
  over ISAPI because the Home Assistant add-on errors on some units.
- The microphone is captured at 8 kHz, encoded to G.711 µ-law and streamed to
  `POST /api/talk`, which forwards it to the doorbell.

The backend lives at [gnacho/portero](https://github.com/gnacho/portero).

## Install

1. Download the APK from the [latest release](https://github.com/gnacho/portero-android/releases/latest).
2. Allow installing from unknown sources when Android asks.
3. Open the app and enter the server URL and a device token.

Two system settings make the difference between "works" and "why did it stop
ringing":

- **Battery**: allow the app to run without restrictions. Aggressive OEM ROMs
  kill the listener otherwise.
- **Full-screen notifications**: needed for the incoming-call screen. On
  Android 14+ you may have to grant it in the app settings.

## Requirements

- A reachable backend ([gnacho/portero](https://github.com/gnacho/portero)) with a
  device token, reachable over HTTPS.
- Android 10 or newer.

## Build

```sh
./gradlew :app:assembleDebug
```

Release builds read `keystore.properties` from the repository root (ignored by
git). Without it Gradle produces an unsigned APK, which Android will not install.

## Privacy

The app talks only to the server URL you configure. No analytics, no ads, no
third-party SDKs, no Google services.

## License

AGPL-3.0-or-later. See [LICENSE](LICENSE).
