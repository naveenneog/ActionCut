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

### Transitions at export (InShot feature set, 4/5 — part 2)

**Added: transitions now render on export.** Previously `transitionToNext` was stored but
never applied. A new `TransitionEffect` (custom `GlEffect`) ramps each clip's leading/trailing
edge so a transition reads across the cut into the next clip:
- **Fade / Dissolve** → fade through black (also the honest fallback for **Slide/Wipe**, which
  would need the neighbouring clip's frames that Media3 Transformer can't composite).
- **Zoom In / Zoom Out** → push toward centre.
- **Blur** → ramps a cheap box blur at the boundary.

The exporter pairs an out-edge on clip A with the in-edge on clip B (from `transitionToNext`),
applied after framing/overlays. The Transition panel notes the export behaviour.

> Honest caveat: true A→B crossfades/dissolves need cross-clip compositing that Transformer
> doesn't support, so these are single-clip edge ramps; best with normal-speed clips.

### Stylized effect shaders (InShot feature set, 4/5 — part 1)

**Added: custom GL shader render path.** New `ShaderEffect` (`GlEffect`) + `ShaderProgram`
(`BaseGlShaderProgram`) run inline GLSL fragment shaders over each frame via Media3's
`GlProgram`, with a uniform contract (`uTexSampler`, `uIntensity`, `uTime`, `uResolution`).
This unblocks the **stylized effects that were previously skipped at export**.

**Added: 8 stylized effects** now render on export — **Glitch** (block displacement +
chroma split), **RGB Split**, **Shake**, **Zoom Pulse**, **Film Grain**, **Light Leak**
(animated screen-blend), **VHS** (wobble + scanlines + noise) and **Pixelate** (mosaic).
Blur variants keep using stock Media3 `GaussianBlur`. Shaders are time-driven via
`presentationTimeUs`, so animated looks move across the render.

**Improved: Effects panel** is now a **toggle** — chips show which effects are applied to
the selected clip and tapping again removes them (`toggleEffect`), with haptics.

> Best-effort, honest caveat: GLSL is verified to compile against Media3's shader contract
> but can only be validated visually on a device. Effects render at **export**; the live
> preview still shows the unprocessed clip (authoritative render happens on export).

### Keyframe animation + speed curves (InShot feature set, 3/5)

**Added: keyframe animation.** New **Keyframe** tool animates an overlay/PiP/text clip's
**position, scale, rotation and opacity** over time. Move/scale a layer, scrub the playhead,
and press **Add keyframe** to capture a pose (re-adding within 50 ms replaces it); **Clear**
removes them. Pure interpolation lives in `core/model/Keyframe.kt` (`Keyframes.propsAt`) so
the **preview** (`PreviewPlayer` overlay + PiP layers) and the **export** path share identical
math. At export the PiP `VideoCompositorSettings.getOverlaySettings(id, presentationTimeUs)`
samples the curve per-frame, so an animated PiP moves/zooms in the rendered file.

**Added: speed ramps.** The **Speed** panel gains ramp presets — *Slow → Fast*, *Fast → Slow*,
*Bullet time*, *Montage 2x* — on top of the constant-speed chips. Stored as `Clip.speedRamp`
and exported via `SpeedChangeEffect(SpeedProvider)`, where a `RampSpeedProvider` samples the
curve into piecewise-constant 100 ms steps (`core/model/SpeedRamp.kt`).

> Best-effort, honest caveat: speed ramps drive the **video** lane; audio is sped uniformly,
> so on long ramps audio may drift. Keyframe export assumes the PiP lane starts at timeline 0.

### Canvas & background + crop (InShot feature set, 2/5)

**Added: canvas fit modes.** New **Canvas** tool sets the project's **Fit / Fill / Stretch**
mode and a **background colour** (swatches). Applied live in the preview (PlayerView
`resizeMode` + canvas colour) and at export (Presentation `LAYOUT_SCALE_TO_FIT` /
`_WITH_CROP` / `STRETCH_TO_FIT`). Stored on `Project.canvas` (serialized).

**Added: Crop tool.** Per-clip crop via left/top/right/bottom inset sliders; exported via
the existing Media3 `Crop` effect.

> Blurred-fill background (vs solid colour) is deferred — it needs a two-pass composite and
> is the one piece I can't verify without a device.


### Picture-in-picture (PiP) — completes the Overlays feature (1/5)

**Added: PiP video layering.** New **PiP** tool picks a video and adds it as a scaled,
cornered overlay (VIDEO clip on the OVERLAY lane). In the **preview** a second muted,
time-synced `ExoPlayer` renders the PiP surface — **draggable to reposition and a corner
handle to resize**. At **export**, the PiP is composited over the main video via
`Composition.setVideoCompositorSettings(...)` with per-input `OverlaySettings` (scale +
anchor from the clip transform); this path is isolated to PiP-present exports so normal
renders are unchanged.


### Overlays — stickers / emoji (InShot feature set, 1/5)

**Added: emoji/sticker overlays.** New **Sticker** tool opens an emoji grid; tapping adds a
sticker overlay at the playhead on an OVERLAY lane. Overlays render **live on the preview
canvas** and are **draggable to reposition** (normalized transform). Exported via Media3
`OverlayEffect` + `TextOverlay` positioned by the clip's transform (added Guava, required
by the `OverlayEffect` API). `TimelineEditor.addClip` adds overlays without rippling.

> PiP (picture-in-picture) video layering — the other half of this feature — is next.


### Fixes & features from device feedback

**Export reliability — runs directly now.** Replaced the WorkManager-driven export with a
direct run (the Media3 exporter already marshals onto the main Looper), collected in the
ViewModel scope. This removes a silent point of failure; failures surface their message.

**Save to Gallery + share.** After a successful render the file is copied into
`Movies/ActionCut` via MediaStore (`MediaSaver` port → `MediaStoreVideoSaver`, scoped on
API 29+, legacy on 26–28) and the emitted `Completed` carries a shareable `content://`
URI. The export screen shows "Saved to your Gallery" with **Share** (works to WhatsApp/
Instagram/etc. via the content URI) and **Done**.

**Detach / extract audio.** New **Detach** tool extracts a video clip's audio onto a
separate AUDIO lane (aligned), muting the original clip — CapCut/InShot-style. Exported
correctly (video audio removed, audio lane mixed).

**Drag-to-move clips.** Long-press a clip to drag it along its lane (`TimelineEditor.moveClip`
wired through the timeline; preview rebuilt on drop). Previously only trim handles worked.

**Audio plays in preview.** Added a second, time-synced `ExoPlayer` for the AUDIO lane, so
added music and extracted audio are now audible in the preview (best-effort sync; aligned
clips from the start play correctly).

**Note:** still can't device-test here, so export robustness is best-effort — if a render
still fails, the on-screen error message now pinpoints why.


### Live preview audio + timeline waveforms

**Added: live preview mute/volume.** `PlayerController` now tracks per-clip volume and
applies it to ExoPlayer on each `onMediaItemTransition` (and via `updateVolumes` on
non-structural edits), so muting/adjusting a clip's audio is reflected in the preview
immediately — not just at export.

**Added: audio waveforms on the timeline.** `WaveformExtractor` decodes a subset of PCM
via `MediaExtractor` + `MediaCodec` (peak per time bucket, cached in an `LruCache`), with
a deterministic synthesized fallback when decoding isn't possible. Audio clips render the
envelope as centered bars on the lane.


### Audio — add music, mute / remove audio, and real export mixing

**Added: in-editor "Audio" tool.** Launches the system audio picker (SAF), takes a
persistable URI permission, resolves the file's duration/type via
`MediaMetadataRetriever` (`ResolveMediaUseCase` → `MediaRepository.resolveMedia`), and
inserts it as an audio clip on a dedicated AUDIO lane at the playhead.

**Added: "Mute" tool.** Toggles the selected clip's audio (volume 0 ⇄ 1) — the way to
remove the original audio from an existing video.

**Fixed: export now actually renders audio.** Previously the Media3 path ignored the
audio lane and per-clip volume. Now:
- the **audio lane is mixed in** as a second `EditedMediaItemSequence` (audio-only items
  via `setRemoveVideo(true)`), so added music/voiceover lands in the output;
- **mute** maps to `EditedMediaItem.setRemoveAudio(true)` (strips the clip's own audio);
- **per-clip volume** maps to a `ChannelMixingAudioProcessor` (mono+stereo matrices
  scaled by the volume), combined with the existing `SonicAudioProcessor` speed sync.

(API signatures — `setRemoveAudio`, `ChannelMixingMatrix.create(in,out).scaleBy()` —
verified via `javap` against media3 1.4.1 before coding.)

**Note:** the preview player still plays the main video lane's audio as-is; mute/volume
are authoritative at export (documented preview limitation).


### v1.0.0 follow-up — Render path, platform presets, release

**Added: real LUT/effects export render path.** `EffectMapper` translates each clip's
editing properties into a Media3 GPU `Effect` chain — rotate (`ScaleAndRotateTransformation`),
crop (`Crop`), brightness/contrast/saturation/warmth (`Brightness`/`Contrast`/
`HslAdjustment`/`RgbAdjustment`), blur (`GaussianBlur`), per-clip speed (`SpeedChangeEffect`
+ `SonicAudioProcessor` to keep audio in sync), and `Presentation` scaling last.
`FilterLuts` generates real 17³ 3D colour cubes per named filter and feeds them to
`SingleColorLut.createFromCube` — the actual LUT render path, no `.cube` assets required.
(Exact Media3 1.4.1 signatures were verified via `javap` before coding.)

**Added: platform export presets.** `ExportPreset` (Instagram Reel/Post/4:5, TikTok,
YouTube, YouTube 4K, Shorts, Mobile HD, Original) pins output aspect + resolution.
`ExportSettings.aspectRatio` override lets one project export to any platform shape; the
exporter crops-to-fit via `Presentation`. Wired through the worker and export UI.

**Added: polished adaptive launcher icon** — violet→mint gradient play mark with a "cut"
split, gradient background, and a monochrome layer for Android 13+ themed icons.

**Added: GitHub Pages landing page** (`docs/index.html`) — branded, responsive, dark-first.

**Decision: release signed with the debug key** so the v1.0.0 APK installs directly
(demo release; swap for an upload keystore for Play distribution).


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

**Decision: `core:common` is a pure-Kotlin (JVM) module.**
Originally an Android library; converted to `kotlin.jvm` so the pure-Kotlin
`core:domain` can reuse `Outcome`/`DispatcherProvider`. Android-only UI helpers live
in `core:designsystem` instead. Keeps the dependency-inversion clean.

### Phase 2 — Media Import & Management

**Added:** `MediaStoreDataSource` (scoped-storage queries over video/image/audio with a
live `ContentObserver`-backed `Flow`), `MediaRepositoryImpl`, runtime-permission-aware
`MediaPickerScreen` (multi-select grid with ordered selection badges, Coil video-frame
thumbnails, duration overlays), and `CreateProjectFromMediaUseCase`.

**Decision: kotlinx.serialization for the timeline.** Added `@Serializable` to the whole
model graph. Room stores project metadata in columns + the timeline as a JSON blob
(`ProjectEntity.timelineJson`), decoded lazily. Doubles as cloud-sync readiness (Phase 9).

### Phase 3 — Timeline Editor

**Added:** `EditorTimeline` — a CapCut-style **fixed center playhead** where the content
scrolls beneath it, so the scroll offset *is* the current time (low-latency scrubbing).
Multi-track lanes, colored clip blocks, tap-to-select, and **drag-to-trim** handles.
`EditorViewModel` owns immutable timeline state with a 50-step **undo/redo** stack.

**Decision: pure `TimelineEditor` engine in domain.** All structural edits (add/insert/
remove-with-ripple/split/trim/move) and property edits are pure functions on immutable
models — fully unit-tested, including speed/reverse source-window mapping.

### Phase 4 — Editing Tools

**Added:** Split, delete, trim, **speed** (presets + source-duration recompute), reverse,
rotate, **volume**, **LUT filters** (catalogue), **adjustments** (brightness/contrast/
saturation/warmth), and **text overlays** — all wired through `TimelineEditor`.

### Phase 5 — Effects & Transitions

**Added:** `Transition`/`TransitionType` (fade/slide/zoom/…) and `VisualEffect`/
`VisualEffectType` (glitch/blur/VHS/…) models with tool-panel pickers. Preview is
approximate; the authoritative render is applied at export.

### Phase 6 — Export Engine

**Added:** `Media3VideoExporter` (default `VideoExporter`) builds a Transformer
`Composition` (clipping + `Presentation` scaling to 480p/720p/1080p/4K), driven on the
main `Looper` so it works from any thread. **Background export** via a `@HiltWorker`
`ExportWorker` + `ExportManager` (observes `WorkInfo` → `ExportState`). Export screen
shows live progress and shares the result via `FileProvider`.

**Decision: WorkManager + Hilt.** `ActionCutApplication` implements
`Configuration.Provider` with `HiltWorkerFactory`; the default WorkManager initializer is
removed in the manifest so on-demand init uses our config.

### Phase 7 — Performance

`ThumbnailLoader` with an `LruCache` (~1/8 heap) + `OPTION_CLOSEST_SYNC` keyframe lookup;
lazy grids/rows everywhere; adaptive timeline ruler tick density; `StateFlow` +
`WhileSubscribed` to avoid wasted work; immutable state for cheap structural sharing.

### Phase 8 — UI/UX

Dark-first Material 3 design system (electric-violet + mint accents), rounded components,
haptic feedback on interactions, editor layout (preview / timeline / contextual tools),
edge-to-edge, adaptive launcher icon.

### Phase 9 — Bonus (stubs)

`CaptionGenerator` port + `StubCaptionGenerator` with a **pluggable config**:
`AzureManagedIdentity` (DefaultAzureCredential) as the intended default, `ApiKey` for
other users, or `OnDevice`. Sticker/template/cloud-sync hooks via the serializable model.

### Validation

- `./gradlew assembleDebug` → green; `app-debug.apk` ≈ 21 MB.
- `./gradlew test` → green. Unit tests cover `TimelineEditor` (incl. reverse split),
  `TimeFormatter`, timeline/project JSON round-trips, and `CreateProjectFromMediaUseCase`.

**Tried/Rejected:**
- `inline` members on the `Outcome` sealed interface → Kotlin forbids inline on virtual
  members; moved to top-level inline extensions.
- `android:authority` on `<provider>` → correct attribute is `android:authorities`.
- Hard FFmpegKit dependency → artifacts retired; used pluggable engine (see Phase 1).

