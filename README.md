# ActionCut 🎬

[![CI](https://github.com/naveenneog/ActionCut/actions/workflows/ci.yml/badge.svg)](https://github.com/naveenneog/ActionCut/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/naveenneog/ActionCut?color=7C5CFF)](https://github.com/naveenneog/ActionCut/releases/latest)
[![Pages](https://img.shields.io/badge/demo-live-00E5C0)](https://naveenneog.github.io/ActionCut/)

A blazing-fast, modern Android video editor inspired by CapCut — built with Jetpack
Compose, Clean Architecture, and a hardware-accelerated Media3 pipeline.

> Status: builds a debug APK (~21 MB) on Android SDK 35 / JDK 17. Core editing, media
> import, multi-track timeline, tools, and background export are implemented end-to-end.

## Highlights
- **CapCut-style timeline** — fixed center playhead, content scrolls beneath it for
  low-latency scrubbing; multi-track lanes, tap-to-select, drag-to-trim.
- **Real editing engine** — pure, unit-tested `TimelineEditor` (split / trim / ripple
  delete / move / speed / reverse), with 50-step undo/redo.
- **Tools** — speed, volume, LUT filters, color adjustments, text overlays, transitions,
  effects, rotate, reverse.
- **Background export** — Media3 Transformer rendering (480p–4K) via a Hilt `WorkManager`
  worker with live progress and `FileProvider` sharing.
- **Dark-first design system** — Material 3, electric-violet/mint palette, haptics.

## Tech stack
Kotlin · Jetpack Compose · Material 3 · MVVM + Clean Architecture · Hilt · Room ·
AndroidX Media3 (ExoPlayer + Transformer) · WorkManager · Coil · kotlinx.serialization.

## Module structure
```
app/                      Application, MainActivity, NavHost, Home screen
core/
  common/      (jvm)      Outcome, DispatcherProvider, TimeFormatter, Ids
  model/       (jvm)      Serializable domain models (Timeline, Clip, Project, …)
  domain/      (jvm)      Repository ports, use cases, pure TimelineEditor engine
  designsystem/(android)  Theme, components, haptics
  data/        (android)  Room DB + MediaStore data source + repositories
  media/       (android)  ExoPlayer controller, Media3 exporter, FFmpeg scaffold
feature/
  media/                  Media import & picker
  editor/                 Timeline editor (preview + timeline + tools)
  export/                 Export settings, progress, worker
```

## Build & run
Requires the Android SDK (platforms 35, build-tools 35) and JDK 17.

```bash
# Build the debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Install on a connected device/emulator
./gradlew installDebug
```

`local.properties` must point at your SDK (`sdk.dir=...`). It is git-ignored.

## Video engine note
The brief requests FFmpeg, but the `com.arthenica:ffmpeg-kit-*` artifacts were retired
from Maven Central. ActionCut therefore defines a pluggable `VideoExporter` port; the
default implementation uses **Media3 Transformer** (available + hardware-accelerated).
`FFmpegCommandBuilder` produces the exact FFmpeg argument list, and `FFmpegVideoEngine`
is a drop-in adapter — swap the Hilt binding in `MediaModule` to enable a self-hosted
FFmpeg build.

## Roadmap
See [`CHANGELOG.md`](CHANGELOG.md) for the full per-phase decision log. Bonus features
(auto-captions, stickers, templates, cloud sync) are scaffolded behind interfaces; the
caption port is Azure-`DefaultAzureCredential`-pluggable.
