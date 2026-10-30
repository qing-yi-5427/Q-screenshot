# 最新现成软件研究（2026-10-30）

本轮已实际固定commit并读phone-mockup-studio、screenshot-studio、PhoneMockup代码/许可证，具体比较和采纳边界见[源码研究](EXISTING_MOCKUP_RESEARCH.md)。没有下载/复用其模型或实现；当前前向精简模型/正面清晰预设为原创实施。下文“未研究这些项目”属于首版历史，不代替本轮结果。

# 资产相关开源复用

本文件只记录资产生产；Android 引擎依赖由应用实现文档记录。

| 工具 / 来源 | 固定版本、许可证与实际使用 | 修改与分发 |
|---|---|---|
| [Blender](https://www.blender.org/) | 官方 4.5.0 arm64，build `8cb6b388974a`；GPL-3.0-or-later，发行包 `Blender.app/Contents/Resources/text/license`，并见[官方许可证](https://www.blender.org/about/license/)；使用 bpy mesh API、BEVEL/WEIGHTED_NORMAL、内置 `bpy.ops.export_scene.gltf` 与 Cycles 渲染。官方 DMG 与哈希见 RESEARCH.md | 未移植 Blender 源码，不打入 APK；生成器原创，没有第三方模型输入。输出物许可与工具自身分开；[官方 FAQ](https://www.blender.org/support/faq/) 不代表小米商标/外观授权。 |
| [Khronos glTF-Validator](https://github.com/KhronosGroup/glTF-Validator) | npm `gltf-validator@2.0.0-dev.3.10`，包 `LICENSE` 为 Apache-2.0；读取包 `README.md` 的 `validateBytes` API，调用 `index.js`，版本固定在项目缓存包锁 | 未修改；仅开发验证，不打入 APK。报告记录实际版本与结果。 |
| [Khronos glTF 2.0](https://github.com/KhronosGroup/glTF/tree/main/specification/2.0) | 只参考右手坐标、米制、UV 以及 GLB accessor 结构，未复制网格或示例资产 | 原创合同验证 Python 读取实际二进制 accessor，不依赖专有格式。 |

`tools/blender/*.py`、几何 JSON、GLB、原创摄影棚 HDR 是本项目原创。没有复制 phone-mockup-studio / screenshot-studio / PhoneMockup 的模型或代码，也没有把 iPhone 模型改名为小米；因此不伪造这些仓库的研究 commit。小米官方图只做本地参考，重建不代表已获得公开发布外观或商标许可，默认不包含 Logo、官方壁纸和品牌字体。

可复现工具安装：官方 Blender URL `https://download.blender.org/release/Blender4.5/blender-4.5.0-macos-arm64.dmg`；validator 命令 `npm install --prefix .cache/tools/gltf-validator gltf-validator@2.0.0-dev.3.10 --ignore-scripts --no-audit --no-fund`。工具不属于应用运行依赖，不要求 App 联网。

## Android rendering source research

Google Filament tag `v1.79.0`, short commit `a854ad6`, Apache2.0. Actually read tag sources: `android/filament-utils-android/.../ModelViewer.kt` (asset loader ownership, surface lifecycle, direct lights), `KTX1Loader.kt` (precomputed IBL), `android/filament-android/.../{Renderer,View,Texture,RenderTarget,MaterialInstance,Camera,LightManager,ColorGrading}.java` (offscreen/readback/alpha/projection/material API), `android/gltfio-android/.../{AssetLoader,ResourceLoader}.java` (GLB resources), `shaders/src/{surface_shading_unlit,surface_getters}.fs` (unlit color and normal availability). Downloaded sources remain ignored cache, no copied sample Activity. Implemented own RenderController, screen UV shader, export View/RenderTarget and state flow; same main-thread ownership and matching tool strategy. Exact AARs/digests in TOOLCHAIN.md; upstream Apache license retained at root and notices in THIRD_PARTY_NOTICES.md.

Other referenced phone-mockup repositories were not incorporated. Direct Filament was sufficient; we do not claim sample code or assets were audited/reused when they were not.
