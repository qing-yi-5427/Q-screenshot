# Q-screenshot

在 Android 上离线制作真实 3D 手机套壳截图。导入图片，调整机型、配色、角度和光影，保存透明或带背景的 PNG；也可以从其他应用分享一张图片，按默认参数自动生成后继续分享。

**[下载最新版 APK](https://github.com/qing-yi-5427/Q-screenshot/releases/latest/download/Q-screenshot.apk)** · [版本说明与历史下载](https://github.com/qing-yi-5427/Q-screenshot/releases)

本地构建默认版本 **0.8.0**，发布版本以 [Releases](https://github.com/qing-yi-5427/Q-screenshot/releases) 为准。适用于 Android 10 及以上的 arm64 / x86_64 设备。当前提供 Debug 签名测试版，与此前安装的测试版可覆盖升级；也可按下方说明自行构建。

## 实机界面

<p>
  <img src="docs/images/redmi-editor.png" width="320" alt="Redmi K90 Pro Max 真机上的 Q-screenshot 直板模型编辑器">
  <img src="docs/images/fold-editor.png" width="320" alt="同一 Redmi 真机上的 Xiaomi 18 Fold 西野红模型编辑器">
</p>

两张为 **0.7.1 历史实机界面；当前布局与配色已优化**。均在 **Redmi K90 Pro Max / Android 16** 上通过系统 `screencap` 获取，包含当时的实际编辑器控件；屏内图片是原创离线示例。第二张展示的是在 Redmi 上编辑折叠机模型，并非另一台设备截图。详见[截图来源](docs/images/README.md)。

## 功能

- **真实三维模型**：直板主屏、折叠外屏与展开内屏；单指旋转，双指缩放、平移和扭转，支持撤销、重做及草稿恢复。前向旋转限制避免显示背面。
- **可见预览**：底部参数面板展开时，预览随剩余空间缩小；收起恢复大画布。横屏使用侧栏。
- **摄影棚光影**：PBR 机身材质、环境反射、灯光方向、玻璃弱反射与阴影；截图内容使用独立材质保持原色。
- **PNG 导出**：透明、纯色或渐变背景；2048 / 2560 / 3840 长边，支持 2160×3840、3840×2160 等画布输出。预览与导出共用渲染管线。
- **默认参数与外部分享**：保存渲染配置，自动处理其他应用分享来的单张图片，再打开系统分享面板。
- **本地处理**：无需账号，无水印；仅下载机型资源时联网，不申请整库照片读取权限；截图和导出始终在本地处理，不上传。通过系统图片选择器、文件选择器或临时 URI 授权读取用户选定图片。

### 0.8.0 按需机型库

APK 只内置 **Xiaomi 18 Fold**（合拢外屏、展开内屏），另提供 **24款 Xiaomi / REDMI 直板旗舰**按需下载。在「设备」页展开「机型库」选择资源，下载一次后即可离线编辑与导出；已下载机型可直接切换，下载更新为独立操作；模型更新按版本单独保存，已有草稿继续使用原版本，旧版本不会被新包覆盖。截图、照片和导出图片不上传，网络仅用于获取机型索引与资源包。

| 来源 | 机型 |
|---|---|
| 内置 | Xiaomi 18 Fold |
| Xiaomi 按需 | 15、15 Pro、15 Ultra、15S Pro、17、17 Pro、17 Pro Max、17 Ultra、17 Ultra 徕卡版、17 Max、17T、17T Pro、18 Pro、18 Pro Max |
| REDMI 按需 | K80、K80 Pro、K80 至尊版、K90、K90 Pro Max、K90 Max、K90 至尊版、K100、K100 Pro、K100 Pro Max |

每款收录本轮官方页面列出的全部配色名（含Xiaomi 15定制版20种背板×亮银/亮黑边框），共 **142种配色／中框组合**（内置4、下载138）；颜色、金属度和粗糙度是原创视觉估计，不是官方色度测量。已核实的配色厚度差异使用独立模型，框体与背盖分别设置材质，换色不会给截图加滤镜。

几何以官方机身尺寸和屏幕像素为依据，再结合各机官方正面、侧面图估计有效屏区、圆角、中框与孔位按键；**不是原厂 CAD**。未知细节保留说明；Android 全配色加载与默认色正/左右三视图的验收范围见[验证记录](docs/MODEL_LIBRARY_VALIDATION.md)。范围与来源见[机型库](model-library/README.md)、[Xiaomi研究](docs/MODEL_LIBRARY_XIAOMI.md)及[REDMI研究](docs/MODEL_LIBRARY_REDMI.md)。

## 使用

1. 点「＋ 截图」选择图片，在「设备」中选机型、状态和配色。
2. 调整角度、背景、光影与画布；「适配」会按当前机型重新构图。
3. 点顶部「保存」选择 PNG 长边并保存到 `Pictures/Q-screenshot`，随后可分享。

**保存默认参数：** 调好场景后，点底部「画布」面板顶部的「设为默认参数」。它保存机型、配色、姿态、灯光、背景、画布和导出尺寸等配置，不保存原图、URI 或图片尺寸。以后新工程与普通导入使用已保存默认；已有草稿仍恢复自己的设置。可随时再次保存更新默认。

**从其他应用自动套壳：** 在相册或其他应用中分享一张图片 → 选择「Q-screenshot · 图片套壳」→ 等待生成 PNG → 在系统分享面板自行选择接收应用。未设置默认时使用产品默认。此流程生成临时 PNG，不自动保存相册；返回后可再次分享。当前仅支持单张图片，不支持批量分享。

## 构建

要求 Android 10（API 29）及以上；打包 ABI 为 `arm64-v8a` 和 `x86_64`。

已验证的构建环境：JDK 21、Gradle Wrapper 9.8.0、AGP 9.4.1、Kotlin 2.4.20、Android SDK Platform 37、Build Tools 36.0.0。可在 Android Studio 中打开工程、选择 JDK 并配置 SDK；`local.properties` 使用自己的 SDK 位置。

```sh
./gradlew test :app:lintDebug :app:assembleDebug
```

生成 APK：`app/build/outputs/apk/debug/app-debug.apk`。Windows 使用 `gradlew.bat`。

可选的隔离输出构建，仅需 Python 标准库：

```sh
python3 tools/build_android.py --output Q-screenshot-debug.apk
```

普通构建使用仓库自带的 GLB、材质和 Filament AAR，不需要运行 Blender。完整依赖版本与来源见[工具链](docs/TOOLCHAIN.md)。如需运行 Android 测试，仅在授权设备或模拟器连接后执行 `./gradlew :app:connectedDebugAndroidTest`。

## 自动构建与发布

推送严格的 `vMAJOR.MINOR.PATCH` tag（例如 `v0.7.2`）后，GitHub Actions 构建 Debug 签名测试版，并发布固定附件 `Q-screenshot.apk` 与 `SHA256SUMS.txt`。上方「下载最新版 APK」无需随版本修改；重跑旧 tag 不会把最新版指回旧版本。tag 应指向已合并到默认分支、包含工作流的提交。

CI 的版本名来自 tag，版本码为 `major × 1000000 + minor × 1000 + patch`，如 `v0.7.2 → 7002`；同 tag 重跑不变。本地普通构建仍使用源码默认版本。也可在 Actions 手动运行 **Android APK**，输入版本，仅生成可下载 Artifact，不发布 Release。

维护者须配置仓库 Secret `QSCREENSHOT_DEBUG_KEYSTORE_BASE64`，复用现有测试签名；缺失或证书不一致时构建明确失败，不生成随机签名。该测试密钥不进入源码或附件。版本范围、签名与 Runner 依据见[发布说明](docs/RELEASING.md)。

## 技术与边界

Kotlin、Jetpack Compose / Material 3、Google Filament 1.79.0 / gltfio；GLB 模型由原创参数化工具生成，编辑状态驱动预览和离屏导出。系统分享使用独立接收 Activity、私有临时工程与 FileProvider 读取授权，避免覆盖当前编辑草稿。应用 ID 保持 `dev.qingframe`，便于旧版本覆盖升级保留数据。

模型参考官方尺寸、屏幕比例与公开多角度图重建，**不是原厂 CAD**。局部圆角、铰链曲率、PBR 色值与粗糙度为估计，未做实物色号或反射率测量。模型以正面截图为目标，背部结构简化；冠军版联名图案与特殊后摄细节不复刻。厂商照片、Logo 和壁纸未作为模型纹理打包。

0.7.0 的默认参数与外部分享流程已完成 31 项 Android 回归，并用不同 UID 的测试发送器和接收器验证实际 PNG 授权读取；0.7.1 仅更新公开名称与保存目录。实机界面与透明导出另经 Redmi 检查；0.7.2 针对 Xiaomi 18 Fold 外屏短高布局调整，并在该机检查菜单、默认参数及保存分享流程。尚未进行全面机型兼容、峰值性能、功耗或多图批量验证。

## 许可

原创代码与资源采用 [Apache License 2.0](LICENSE)，依赖许可见 [第三方声明](THIRD_PARTY_NOTICES.md)。本项目与 Xiaomi / REDMI 无隶属关系；相关品牌与产品设计权利属于各自权利人。

### 小米机型资料与隔离建模

新增机型或回归模型请先阅读 [可复用资料采集与参数化建模流水线](docs/XIAOMI_DEVICE_PIPELINE.md)。支持官方资料候选采集、来源/置信度台账、隔离 GLB 构建及合同校验；[本轮测试结果](docs/XIAOMI_PIPELINE_TEST_RESULTS.md)。

### 模型构建的安全默认值

`python3 tools/build_assets.py` 默认仅重建Fold到新的 `.cache/pipeline/build-*` 目录，不改APK资源。`--device redmi-k90-pro-max` 同样只生成staging资源；`--profile ... --staging-root ...` 用于库机型的明确输入。需要审查并更新唯一内置Fold时，显式执行 `python3 tools/build_assets.py --update-builtin-fold --staging-root .cache/pipeline/fold-reviewed-new`：先完成staging合同验证，再仅复制Fold两状态与元数据，内置catalog始终只有Fold。可先加 `--dry-run` 查看目标。
