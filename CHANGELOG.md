# ActionCut — Changelog & Decision Log

This log records what was built, decisions made, and approaches tried (including
failures) so work is traceable and we avoid repeating problems.

## Format
- **Added** — new features/files
- **Decision** — an architectural/tooling choice and its rationale
- **Tried/Rejected** — an approach that didn't work and why
- **Fixed** — bug fixes

---

## [Unreleased]

### Phase 1 — Core App Structure

**Decision: Multi-module Clean Architecture.**
Split into `app`, `core:*`, and `feature:*` modules. Rationale: enforces
separation of concerns, enables parallel builds, faster incremental compilation,
and prevents feature cross-dependencies. Follows the official "Now in Android"
module conventions.

**Decision: Pluggable `VideoEngine` instead of hard FFmpeg dependency.**
The prompt requests FFmpeg. However, the widely-used `com.arthenica:ffmpeg-kit-*`
artifacts were retired from Maven Central (Jan 2025) and no longer resolve in a
clean build. To keep the build green while honoring the intent, `:core:media`
defines a `VideoEngine` / `VideoExporter` abstraction. The default implementation
uses **AndroidX Media3 Transformer** (fully available, hardware-accelerated).
An `FFmpegVideoEngine` adapter scaffold documents exactly where FFmpeg command
strings plug in, so a self-hosted FFmpegKit (or `mobile-ffmpeg`) build can be
swapped in without touching feature code.

**Decision: Tooling versions.**
- Gradle 8.11.1, AGP 8.7.3, Kotlin 2.0.21 (K2 + Compose compiler plugin)
- compileSdk/targetSdk 35, minSdk 26
- Hilt 2.52, Room 2.6.1, Media3 1.4.1, Navigation Compose 2.8.5, Coil 2.7.0
Rationale: known-good, mutually compatible versions verified against the local
SDK (platforms 35/36, build-tools 35/36, NDK 27, CMake 3.22.1).

**Decision: Gradle Version Catalog (`libs.versions.toml`).**
Centralizes dependency versions for consistency across modules.
