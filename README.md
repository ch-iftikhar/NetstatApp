# NetstatApp

A lightweight, fully open-source network monitor for Android — no closed dependencies, no bloat, no data leaving your device. Built entirely in Kotlin, backed by a local Room database, and designed to sit quietly in your notification shade telling you exactly what your connection is doing, second by second.

## Features

### Live monitoring
- Real-time upload/download speed, polled every second via `TrafficStats`
- Persistent foreground notification showing live ↓/↑ rates and session totals, with a dynamically rendered icon that reflects current throughput
- Separate tracking of mobile vs. Wi‑Fi data, computed from delta sampling between polls

### Usage history
- **Daily usage dashboard** — full current-month calendar view with per-day mobile/Wi‑Fi breakdown, backed by Room and updated live via Kotlin Flow
- **Week and month totals**, computed on the fly from the daily usage table
- **Speed log** — rolling log of the last 200 samples (rx/tx rate and cumulative totals), viewable in a dedicated screen with live status updates

### Reliability
- **Boots with your phone** — a `BroadcastReceiver` restarts the monitoring service automatically after device reboot, no manual relaunch needed
- Runs as a proper Android foreground service (`dataSync` type) so it survives background restrictions
- Automatic log pruning — entries older than 7 days are purged on service start, keeping the local database lean

### Privacy by design
- 100% local — all data lives in an on-device Room/SQLite database, nothing is transmitted anywhere
- Minimal permission footprint: usage stats access, foreground service, notifications, boot receiver, and battery-optimization exemption — nothing beyond what monitoring requires

## Known Issues

- **Export/Import buttons are currently non-functional.** The Data Tools screen's layout defines Export, Export TXT, and Import buttons, but no click handlers are wired up yet in `DataToolsActivity.kt`. Tapping them does nothing right now.
- **Attribution gap for notification icon assets.** The icon rendering logic is adapted from another GPLv3 project ("DataMonitor"), per an in-code comment referencing a `NOTICE.md` that doesn't yet exist in this repo. A proper `NOTICE.md` and `LICENSE` should be added to stay compliant before wider distribution.

## Requirements

- JDK 17+
- Android SDK (compileSdk 34, minSdk 26, targetSdk 34)
- Gradle Wrapper (included — no separate Gradle install needed)

## Building

```bash
git clone https://github.com/ch-iftikhar/NetstatApp.git
cd NetstatApp
./gradlew assembleDebug
```

APK output:

```
app/build/outputs/apk/debug/app-debug.apk
```

Release build (requires a signing config in `app/build.gradle`):

```bash
./gradlew assembleRelease
```

## Installing

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

On first launch, you'll be prompted to grant **Usage Access** (required to read network statistics) and asked to exempt the app from battery optimization, so background monitoring stays reliable.

## Tech stack

- **Kotlin**, AppCompat + classic Views (no Compose)
- **Room** for local persistence, queried reactively via **Kotlin Flow**
- **Kotlin Coroutines** for background work
- **TrafficStats** API for network byte counters
- Foreground `Service` + `BroadcastReceiver` for always-on monitoring

## Project structure

```
app/src/main/java/com/yos/netstatapp/
  MainActivity.kt           # Dashboard: nav drawer, pinned "today" stats, daily usage list
  MonitorService.kt         # Foreground service: polling, notification, DB writes
  SpeedLogActivity.kt       # Live status + rolling speed log
  DailyUsageActivity.kt     # Full daily usage history view
  DataToolsActivity.kt      # Export/import screen (buttons not yet wired)
  AboutActivity.kt          # App info, GitHub link, contact
  BootReceiver.kt           # Restarts monitoring after reboot
  AppDatabase.kt            # Room database setup
  NetworkLog.kt / NetworkLogDao.kt       # Rolling speed-sample log
  DailyUsage.kt / DailyUsageDao.kt       # Per-day usage aggregation
  NotificationIconRenderer.kt            # Dynamic notification icon
  Formatters.kt              # Byte/rate formatting helpers
  NetworkDataBus.kt          # In-app live update broadcasting
  PermissionUtils.kt         # Usage access + battery optimization helpers
```

## License

No license specified yet — add one (and the `NOTICE.md` noted above) before distributing widely.
