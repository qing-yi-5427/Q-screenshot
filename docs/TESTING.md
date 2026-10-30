# 最新0.4测试（2026-10-30）

见`deliverables/xiaomi18-refresh/ACCEPTANCE_REPORT.md`。16逻辑测试；最终079a17项Android0.935s、1真实MIX迁移及4正侧UI；之前7c9raw迁移/保存chooser取消按原hash保留，新包仅4个资产entry变化、dex/manifest相同。最终真机11PNG包含两状态/竖向/透明/双4K，屏幕灰暗修复后白底正常。原色PNG量化均ΔE0.3510，图片/GLB/来源/未知项与APK无旧资源检查均独立归档。以下均为旧任务历史。

# 0.3.0大画布/光影实际验收（2026-10-30）

最新结论见`deliverables/design-refresh/ACCEPTANCE_REPORT.md`。最终c8包12逻辑/14Android通过，模拟器实际保存/chooser返回/4K早期取消、360dp大字/横宽、机型sheet/导入通过；真机<DEVICE_SERIAL>只隔离离屏渲染10+3PNG、颜色/alpha/双4K通过，未自动点按或改手机安全。before/after严格独立于旧模拟器/Redmi历史；首2f布局量化与最终c8复验分开。旧草稿canvas不变由新增instrument回归覆盖。以下为旧任务历史，不能当作最新包重新执行。

# Redmi追加真机验收

新机型与真机结果单独记录于`deliverables/REDMI_K90_PRO_MAX_ACCEPTANCE_REPORT.md`；证据仅在`deliverables/evidence/redmi-k90-pro-max/`，不把旧模拟器结果冒充真机。测试前先新增并验收专属直板模型/材质，再安装新APK。授权真机<DEVICE_SERIAL>，Xiaomi25102RKBEC/REDMI K90 Pro Max，Android16/HyperOS3.0.310.0，Adreno840。初装前dev.qingframe未安装。测试全程显式serial、隔离session，不读个人图库、不清数据、不改系统显示/安全配置。最终6bda实际16场景/25PNG、双4K、10逻辑/13Android、20次合法ActivityManager HOME恢复及force-stop通过；保存/分享/取消因HyperOS INJECT_EVENTS拒绝而blocked，未改安全设置，不记通过。原有下列内容为首版历史。

# 实际测试与证据

## 版本与范围

最终APK为 `deliverables/apk/qing-frame-debug.apk`，SHA-256：
`e13b70946d549119a9802ab5cba3f6da5e83f2aac001937e13a54041fce8da43`。

完整渲染矩阵使用07f版（SHA `07fcee624818d0e9cc49b2331e548bf2132a29bdc2007893421c32aa58987c0a`）。最终e13版只将分享URI改为可观察的Compose状态；渲染、模型、材质、资产未改变，版本/源码/资产证据在 `deliverables/evidence/render-matrix-apk-source-asset-match.json` 等匹配报告。e13另实际运行E01、E06、双向4K及F01，文件在 `deliverables/evidence/final-ui-apk-smoke/`。不得把07f的UI生命周期记录伪称为e13重新执行。

环境为Android 16/API36 arm64模拟器，ANGLE/SwiftShader；**未做真机性能、色彩、耗电或小米图库兼容验收**。

## 结果

| 检查 | 结果与证据 |
|---|---|
| 纯逻辑测试 | 7项通过；`deliverables/evidence/instrumentation/model-tests.xml`及最终构建日志 |
| Android状态/导入审计测试 | 最终e13已实际11项通过；`deliverables/evidence/build-and-test-logs/instrumentation-share-ui-final.txt`（OK11，0.942s）。覆盖往返/迁移/未来版本拒绝、路径、原子保存、有效/损坏导入，以及autosave/撤销/缺源/prune/fill等审计回归；每次绑定版本以该次安装与测试日志为准 |
| E01–E12矩阵 | 07f全部完成，25场景共34张实际Android PNG；`deliverables/renders/android-run.json`、`deliverables/evidence/export-checks.json`、每例logcat |
| 四角与方向 | 实际查看E01/E03/E04，横竖内容正向、四角正确、展开内容连续；不是仅按文件存在判定 |
| E05适配 | 完整显示与填满均实际出图，分别保留比例/进行裁切 |
| E06/E07透明 | RGBA、画布角alpha0、主体不透明区通过；实际查看白/黑合成，无明显矩形接影底板；`alpha-final.json`和`alpha-composites/` |
| E08目标尺寸 | 精确2160×3840及3840×2160；真实RenderTarget读回日志，不是预览放大；e13也复跑两规格 |
| E09反射 | 0/默认/强均实际导出。默认清晰，强档可见变化；`reflection-comparison-final.json` |
| E11替换 | 30次交替后等待最新纹理，导出最后1672×2364内容；实际查看文字尺寸及四角 |
| E12快照 | 导出请求后继续修改场景，仍成功输出请求快照；日志与对应导出保留 |
| S30/S10 | 同引擎30次模型切换后导出成功；同引擎连续10次导出全成功，十图均解码检查通过 |
| F01分配失败 | 第3分配阶段故意失败，同renderer重试成功；e13也实际复跑，日志含EXPECTED allocation failure和COMPLETE |
| 实际系统选图 | 07f由Photo Picker选取原创outer，私有副本1168×1712、推荐folded/fit；`photo-picker-import-draft.json`、`ui-imported.png`。取消选图保持草稿，`photo-picker-check.json` |
| 保存/分享 | e13真实保存成功，保存后不关dialog即出现分享；打开系统chooser并返回，未向任何对象发送。`share-final-check.json`、`final-share-button.xml`、`ui-share-chooser.png`、`share-chooser-activity.txt` |
| 前后台/恢复 | 07f前版20次HOME/返回及force-stop重开，草稿JSON完全相同，无App fatal；`lifecycle-check.json`与前后JSON。e13未重新执行20次，不作额外声明 |
| 窗口/旋转 | 07f前版360/840/1066dp及横屏稳定截图实际查看，草稿保持；`ui-compact-360.png`、`ui-expanded-840.png`、`ui-expanded.png`、`ui-rotated.png`。wm尺寸/密度与旋转已恢复原值；e13未重拍 |
| 取消导出 | **未确认**。做过两条UI驱动路径，进度动画时UIAutomator未产生XML，随后adbd/模拟器连接波动，无法确认取消与MediaStore清理结果。未将设备失联自动认定为App崩溃；停止追测 |
| 其余限制 | 真机性能、真实系统折叠姿态、存储不足模拟等未完成项目不记通过，见最终验收报告 |

## 色彩与内存

正面、反射0、正交E01，利用红色卡边界标定坐标，每块取内部5×5均值。源和输出均按SDR sRGB解码至Lab D65，12块平均ΔE2000 **0.2832**、最大 **0.4857**，满足项目初始平均≤3目标。完整RGB/采样坐标在 `deliverables/evidence/color-samples-final.json`，此结果绑定07f渲染路径。

`verify_exports.py` 中CIEDE2000为独立纯Python实现（kL=kC=kH=1）；通过[Sharma作者提供的34组参考数据](https://hajim.rochester.edu/ece/sites/gsharma/ciede2000/)，最大绝对误差0.0000495；校验记录 `ciede2000-validation.json`。没有复制其MATLAB实现。

每场景采集导出后 `dumpsys meminfo`，PSS范围178089–216769 kB。它不是CPU/GPU峰值，也不能证明所有资源压力下零增长。耗时与每场景PSS在 `deliverables/evidence/render-metrics.json`；不以模拟器数据宣称真机性能达标。

## 实际修复循环

- E04倒置与顶部条影：保留 `portrait-before.png`、`unfolded-shadow-before.png`、`shadow-transparent-before.png`；统一UV/读回/相机方向后实际复查正向。
- 透明边缘重复预乘：`alpha-before.png`、`alpha-comparison.json`保留前后像素；一次解预乘后正常。白/黑合成实际查看。
- VSM设置副本未回写：修复后native blurWidth=18.4，低清白底显示真实柔边，保留 `low-resolution/E07-hard-before.png` 与 `E07-soft-white.png`。
- 首次异步提交未触发回调：保留 `before-logs/async-export-stalled-logcat.txt`；使用官方非等待回调pump后恢复，低清和完整矩阵均实际通过。
- E11测试入口过早导出：原来ready=false被正确拒绝，保留 `before-logs/E11-not-ready-logcat.txt`；等待最新上传后实测通过。
- 分享URI非可观察状态：最终e13修复，保存后即时分享入口与系统chooser已实测。

## 复现工具

```sh
python3 tools/doctor.py --json
python3 -m venv .venv
.venv/bin/python -m pip install Pillow==12.1.0
.venv/bin/python tools/generate_test_images.py --output tests/fixtures
python3 tools/run_android_exports.py E01 E06 --serial emulator-5554
.venv/bin/python tools/verify_exports.py --input deliverables/renders --manifest tests/fixtures/export-contract.json --output deliverables/evidence/export-checks.json --composites deliverables/evidence/alpha-composites
```

三张原创输入分别1168×1712、2364×1672、1672×2364，包含TEST、四角编号、12–32px小字、RGB/CMY色卡、灰阶和棋盘格；无用户私人图片。`tests/fixtures/manifest.json`记录尺寸、哈希和色块坐标。

PNG工具只读取实际文件，检查解码/尺寸/alpha并合成检查底图，不制造待验收导出。数字通过不替代Android来源、视觉和色彩检查。
