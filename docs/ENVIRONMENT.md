# 实测环境

2026-10-29，宿主 macOS / arm64。原始只读检查见 `deliverables/evidence/build-and-test-logs/doctor.json`，可重跑 `python3 tools/doctor.py --json`。

- Temurin OpenJDK 21.0.12；Python 3.14.9。
- Android SDK：`~/Library/Android/sdk`，platforms android-35、36、37.0；build-tools 34.0.0、35.0.0、36.0.0。
- adb 37.0.1 / protocol 1.0.41；Emulator 37.2.12。
- 现有 `Pixel_API_36_ARM64` AVD 正在运行，adb serial `emulator-5554`，已授权 `device`，启动完成，Android 16 / API 36，arm64。
- 模拟 GPU：OpenGL ES 3.1 ANGLE / Vulkan SwiftShader。这个软件模拟结果不能作为小米真机 GPU 性能或色彩验收。
- 初始可用空间约 14 GiB。未找到 PATH Blender 或 `/Applications/Blender.app`。
- 初始目录未初始化 Git 仓库。
- 未检测到授权真机；**未做真机性能验收**。
- 没有安装系统组件、接受新的 SDK 法律条款、修改全局代理或全局 AVD 配置。复用正在运行的模拟器。

项目内 `.venv` 安装 Pillow 12.1.0，用于原创测试图与实际 PNG 数值检查；不修改全局 Python。

后续工具补齐：项目内 `.cache/tools/Blender.app/Contents/MacOS/Blender` 为4.5.0；Filament 1.79.0官方工具在 `tools/bin/filament/bin/`，与AAR一致。最终只读环境记录见 `doctor-final.json`。构建生成文件受到“空格2”重复文件干扰，最终使用 `/private/tmp/qing-frame-build-20261029` 隔离构建目录，成功APK复制至 `deliverables/apk/qing-frame-debug.apk`；并非替换源码或更改全局配置。

## Isolated build output

Sequential rebuilds under Desktop still acquired extra generated `file_paths 2.xml` / `ic_launcher 2.xml` / `data_extraction_rules 2.xml`, while source resources had no such names. Exact external source of the duplicate was not established; no unsupported claim that Android generated these names. A clean rebuild succeeded, but the duplication recurred. Second materially different path isolated all Gradle outputs and project cache under `/private/tmp/qing-frame-build-20261029` / `/private/tmp/qing-frame-gradle-cache-20261029`. That build succeeded in21s, unit7passed/lint0errors, and APK was copied/hash-verified into deliverables. `tools/build_android.py` generalizes this with a per-project system temporary root. No global proxy/security/config changes.

A later isolated build stalled in macOS native filesystem event registration. `jcmd Thread.print` recorded `AbstractNativeFileEventFunctions$NativeFileWatcher.startWatching0` on the Gradle execution worker (`gradle-filesystem-watch-hang.txt`). Only this task's Gradle client/daemon were stopped. Project `org.gradle.vfs.watch=false` plus `--no-daemon --no-watch-fs` recovered compilation (8s). No other task daemon or global setting was changed.

19:51模拟器adbd突然offline，一次 `adb reconnect offline`无效。正常终止失联qemu后，以同AVD和原参数加 `-no-window`重启，未wipe、未改AVD配置；13.5秒启动并恢复授权device。首次锁释放期间SDK出现crashdata consent窗口，未接受，终止该次启动再headless成功。重启后需重新安装App。重启日志在 `deliverables/evidence/build-and-test-logs/emulator-recovery.txt`，运行时仍是模拟器，不是真机。
