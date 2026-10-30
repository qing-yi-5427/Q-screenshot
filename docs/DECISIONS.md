# Implementation decisions

- Main thread owns all Filament objects. Background workers only decode/read files or encode PNG. Incrementing image generation guards stale decode results.
- Preview and export derive from immutable ProjectState. Quaternion is persisted; sliders derive Euler values. Gesture and slider changes merge into one history checkpoint;40 undo states.
- Photo Picker falls back to SAF. Import copies only selected image to private storage, capped48MiB/120MP, content-addressed by SHA256. ImageDecoder handles orientation and SDR/sRGB conversion. No gallery-wide or INTERNET permissions.
- GLB scene graph uses explicit named active screen node from runtime manifest, primitive count exactly1. Missing node is a surfaced error.
- Screen shader is unlit, sRGB texture sampled once. Fit/fill and crop use UV transform. Inner portrait rotates the device root90° and compensates screen coordinates so text stays upright. Reflection uses actual view vector, surface normal and original studio cubemap, controlled to zero for clarity tests.
- Filament1.79.0 official Android AARs are vendored with matching1.79.0 matc/cmgen, because release artifacts were available while Maven Central1.79 was absent. See TOOLCHAIN.md.
- Export creates separate target-sized RGBA8 / depth textures and a View, renders2 stable spatial AA frames, reads the actual render target via Filament, then encodes PNG in background. Preview pixels are never enlarged into export.
- Maximum selected long edge3840 and maximum14,745,600 pixels; one export at a time. Preview decoding cap2048, export cap4096, source file preserved.
- Two static model states per device; no continuous hinge deformation. Target18Fold identity/specification remain unverified, so wide-fold is visibly approximate. MIX Fold4 is a separate partial reconstruction.
- New app code and original scripts Apache2.0; official reference images local cache only, original test screens and lighting packed.

- A world-axis and TextureView comparison exposed an initial pair of cancelling Y flips. The final shader first converts gltfio runtime V to the declared top-left screen coordinate, performs portrait mapping there, and samples the screenshot once. RenderTarget readback already has top-left row order in the locked backend; no extra row flip. Old failure images are evidence, not final accepted output.
- Draft writes cancel obsolete delayed jobs and use a generation check plus Mutex; undo/redo save immediately. Missing private source never silently turns into a demo. New imports reset to fit. Private source pruning runs only after serialized imports and preserves current/undo/redo references.
- Native export allocation is registered one object at a time under a common cleanup guard. Debug F01 injects a staged failure and retries the same renderer. Activity cancellation remains active through PNG encoding up to the MediaStore pending-item commit.
- Original ground geometry has real bounded extents, avoiding a 5m shadow-receiver bound around a 0.12m device. Directional VSM filtering exposes a user softness control (assign the Vsm value, because its Java getter returns a copy); fixed slightly elevated photographic camera and key light keep the projection beneath the subject.

- Export uses asynchronous Engine.flush after readPixels submission; preview frames pause until its callback so the main/UI thread can process progress, cancellation and queued edits. Resource ownership persists until callback. Close during export requests cancellation and defers native teardown.

- A plain asynchronous flush was experimentally insufficient: the target completed GPU work but its deferred callback was not purged. Locked Filament `filament/src/details/Engine.cpp` confirms that `flushAndWait(timeout)` calls driver.purge after the wait. The final owner Handler polls `flushAndWait(0)` every50ms during export, avoiding GPU waits while dispatching callbacks; low target completed1.676s after immediate submission. The failed candidate and its log are retained as diagnostic evidence, never a delivered build.
