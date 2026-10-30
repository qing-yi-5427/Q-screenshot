# Architecture

The application is one Android module to keep the build small. Packages enforce the three boundaries:

- `model/ProjectState.kt`: immutable scene state, normalized quaternion, fit/fill/crop UV transform, camera sizing, output limits, coalesced bounded undo history, premultiplied readback conversion. No Compose/Filament dependency.
- `model/ProjectStore.kt`: versioned JSON, relative private source paths, atomic draft replace, selected URI validation/copy, oriented SDR/sRGB decode. `EditorViewModel` exposes StateFlow and keeps changes/history through Activity recreation; private draft restores process death.
- `render/RenderController.kt`: one main-thread Filament Engine. Own AssetLoader/ResourceLoader/provider, GLB scene/root, explicit active screen node, custom material instances, prefiltered original IBL, two direct lights, fixed ground, separate background. TextureView surface and demand-based Choreographer frames are separate from edit state.
- `MainActivity.kt`: adaptive Compose layout, five compact panels, gestures, Photo Picker/SAF, export dialog, MediaStore pending insertion and shared content URI. Debug-only intent scenarios use original fixtures and the same production renderer.

Preview and export call the same scene update function. Export snapshots ProjectState, decodes source up to4096 instead of preview2048, uploads on owner thread, creates target-size RGBA8/depth textures and a separate View. Two spatial-AA frames render outside beginFrame/endFrame through `renderStandaloneView`; target `readPixels` completion keeps Buffer alive, retains verified top-left target rows and unpremultiplies once before Android setPixels. PNG encoding is off the owner thread. GPU submission uses asynchronousEngine.flush; preview frames pause during export so UI events remain available. Temporary target/view/attachments are released; queued editor changes are then applied, including asset changes.

Screen content is independent geometry with unlit color. The original HDR cube is sampled using actual view/normal reflection and Fresnel; reflection0 is an actual shader control. Inner portrait rotates root90° and compensates UVs to preserve upright text. Body uses PBR glTF materials and real geometric bevels. Transparent shadow receiver uses direction-light-only unlit shadowMultiplier; opaque backgrounds are another unlit plane. No photographed fixed highlights or 2D perspective transformations represent the device.

Important limits: output cap3840 long edge, single export queue; no tile renderer, HDR output, continuous hinge animation or video. Runtime native resource peak and physical-device performance remain to be measured; see acceptance evidence instead of deriving performance from architecture.
