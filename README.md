# Portero Android

Incoming-call client for a Hikvision doorbell exposed through
[portero](https://github.com/gnacho/portero) (Home Assistant + go2rtc backend).

The phone rings like a real call when somebody presses the doorbell, shows the
live video, lets you answer, hang up, reject, open the pedestrian door and talk
back. No push service, no Google services, no VPN: one HTTPS connection to your
own backend.

## How it works

- A foreground service keeps an SSE connection to `GET /api/events` and turns a
  `ringing` state into a full-screen call notification with ringtone and
  vibration.
- The call screen plays the HLS stream from the backend (`/go2rtc/...`) through
  Media3/ExoPlayer.
- Buttons call `POST /api/action` (answer, hangup, reject, open).
- The microphone is captured at 8 kHz, encoded to G.711 µ-law and streamed to
  `POST /api/talk`; the backend forwards it to the doorbell over ISAPI.

## Requirements

- The backend from [gnacho/portero](https://github.com/gnacho/portero), reachable
  over HTTPS, plus a device token configured there.
- Android 10 or newer.

## Install

Download the APK from the latest release and install it (you will have to allow
installing from unknown sources). Open the app once and enter:

- **Server URL**: your backend, for example `https://portero.example.com`
- **Device token**: the token configured in the backend

Two system dialogs matter more than they look:

- **Battery optimisation**: allow the app to run in the background, otherwise
  aggressive OEM ROMs kill the listener and the phone stops ringing.
- **Notifications and full-screen intent**: needed for the incoming-call screen.

## Build

```sh
./gradlew :app:assembleDebug
```

Release builds read `keystore.properties` from the repository root (ignored by
git); without it Gradle produces an unsigned APK, which Android will not install.

## Privacy

No analytics, no ads, no third-party services. The app only talks to the server
URL you configure.

## License

AGPL-3.0-or-later. See `LICENSE`.
