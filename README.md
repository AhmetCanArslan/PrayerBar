# PrayerBar

Android app that shows the next prayer time and a live countdown right in the status bar, by rewriting the carrier label through [Shizuku](https://shizuku.rikka.app/).

> **Proof of concept.** Built to see whether this is feasible; expect rough edges.

## Features

- Countdown in the status bar (carrier label or icon)
- Home screen widget, Quick Settings tile and notification
- Customizable text templates
- Offline prayer time calculation, with manual or automatic location

## Requirements

- Android 11+
- Shizuku, for the status bar features

## Build

```sh
./gradlew assembleDebug
```
