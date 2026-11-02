# 0.7.6 蓝白分组 UI（完成，2026-11-02）

- 按用户 iOS 设计指南重做控件层：中性背景/白分组/单蓝accent；矢量顶栏与五tab、按内容高度的分组Inspector、姿态图形tile、细轨圆thumb Slider、外置标签inset输入。保留传统/阔屏大布局及所有回调，不改Renderer/模型/材质/scene算法。
- 最终26逻辑测试、构建、lint通过（0错12警告）；最终Android核心单项1/1通过15.955s。模拟器425×623/1.5字号/412×915完成，五tab/必要滚动scene不变、新Slider实拖+Undo恢复、保存弹框检查通过。关键导航/姿态/预览动作实际触区≥48dp、间隔8dp。
- 最终蓝UI APK `deliverables/ui-polish/apk/Q-screenshot-ui-polish-debug.apk`，0.7.6/code7006，SHA256 `65fdcc54febe134c2dc7a9c6e67ffb10590a98c457c8c59d380f9c2cb70cfbdd`；最终截图在`final-emulator`，报告明确区分否决的绿色候选与UIAutomator大字空root重试。仅模拟器，无真机实测声明。
- 正常草稿hash与默认存在性不变，模拟器尺寸/密度/字号/旋转已恢复读回，最后请求正常入口后停止读取/采图。未提交、推送或发布。

# 0.7.5 按钮间距与手势优先变换（完成，2026-11-01）

- 相邻按钮统一6dp布局间距：预设2×2、模型/配色、通用选项、顶栏、预览操作和底部类别；保持48dp触控、原有Material设计语言。大字截图按钮单行，不缩字体。
- 变换默认仅手势提示/四预设/更多设置；五个精确调节、相机、清晰适配和重置收进同区展开内容。开关/切tab不改scene，预览手势、Renderer/材质/模型/构图/导出不改。
- 构建/26逻辑测试/lint通过；最终Android核心单项1/1通过11.265s，真实展开滚达/状态不变/拖动pose变化。425及1.5字号点击bounds实测最小间隙6.18dp，相关目标均≥48dp；传统412×915上下布局短验通过，模拟器设置已恢复。截图与范围见忽略目录`deliverables/spacing-transform/REPORT.md`。
- 最终0.7.5/code7005 APK `deliverables/spacing-transform/apk/Q-screenshot-gestures-debug.apk`，SHA256 `ead5d9b12c53bb0df1a854626aac7a8e2f44d4ae5dde205ff2407e85b2c25dfd`；真机已覆盖安装同hash。用户再次解锁后真机隔离补验通过：默认/更多/四预设间距、全项滚达、开关及tab/滚动scene不变、真实预览拖动pose改变，正常draft/default hash不变；timeout已恢复60000ms并读回，已回正常无test extras入口，此后不再采图/读取正常内容。未提交、推送或发布。

# 0.7.4 阔屏并排工作台（完成，2026-11-01）

- 完整窗口宽≥600dp，或宽≥400dp且高/宽≤1.65使用左预览/右约152dp独立滚动参数栏；传统长竖屏保持原布局。沿用现有Material设计语言，系统insets/不对称圆角动态避让，全屏仅改变UI。
- 按用户最新要求移除横放预设入口，保留手动旋转与内屏横向构图；左前/右前改为适度仰视，真机确认底部中框可见。旧草稿、默认与Renderer/模型/材质/导出算法不重置、不改动。
- 26逻辑测试、构建/lint通过；传统3项Android检查、候选宽屏3项通过，最终包真实侧栏触摸/状态不变/全屏返回单项复验通过。最终1.5字号单行导入与稳定横屏完整模型已目视；早期横屏截图为旋转过渡态，scene未变，未通过fit掩盖。
- 真机隔离会话保存PNG/分享chooser返回、左右仰视与最终布局烟测完成，正常草稿/默认不变；timeout已恢复60000ms并读回，最终已回正常启动入口。模拟器设置亦全部恢复。
- 最终APK位于忽略目录`deliverables/wide-workspace/apk/Q-screenshot-workspace-debug.apk`，0.7.4/code7004，SHA256 `e4f77609dac9c99e983fccb81a9529c74917595150262da86e6d85a081540e95`，已安装base.apk同hash。范围与候选/最终证据见本轮`REPORT.md`。无待测试项，未提交、推送或发布。

# 0.7.3 边缘质量（2026-10-31）

- MSAA4保留，受预算限制的最多1.5倍边长超采样，预览2MP/导出增加采样8MP，低内存/压力退回原尺寸；不改模型/材质/黑环/构图。
- 最终a095 APK真机同态A/B、透明黑白合成、2160×3840、实际UI取消后同controller保存通过；24逻辑测试/lint通过。候选ad8单项Android质量回归通过，范围详见本轮简报。
- 普通草稿/默认不变，超时已恢复60000并读回；交付位于忽略目录deliverables/edge-quality，未提交或发布。

# 0.7.2 Xiaomi 18 Fold 短外屏（2026-10-31）

- 实机1168×1712/440dpi（约425×623dp）短高响应式：收起态缩放移到变换，紧凑占位面板；只变viewport。
- 用户手动安装0.7.2/code7002，installed SHA与交付一致；21逻辑测试与1项短高布局Android实测通过。真机五tab/滚动末项/默认保存/真实PNG保存/分享chooser返回完成，正常草稿与默认保留；timeout恢复60000ms并读回。
- 证据与APK位于忽略目录 deliverables/fold-cover-fit；未发布本轮版本。

# 0.7 默认参数与外部分享（2026-10-31）

最终APK `deliverables/defaults-share/apk/qing-frame-defaults-share-debug.apk`；SHA`5722e868e9ec1c53e8f5c625eeaa9953f8751893643a79b4179671d315b9f21d`；runtime`2fe32482118281056af7707cf6f8f5a33d0c99bf`。

- 画布面板顶部保存默认；剔除源图URI/path/w/h，原子持久化更新，新工程/正常导入应用，已有草稿优先不覆盖。
- 独立ShareActivity/任务VM接单content URI，独立私有source session，ready后Filament离屏出PNG，FileProvider/read grant真实系统分享；冷/热/旋转防重/忙/取消/无权限/错误恢复。PNG在后续任务清理7天旧缓存，可再次分享，私有源及旧遗留目录按范围清理。
- 另一UID发送和chooser接收均实读，西野红/3:4/2048→1536×2048；编辑草稿前后字节一致。45a9实际31/31通过，最终5722实际31/31通过15.61s，另一UID接收PNG/授权真实读取通过，见本轮报告与verification/final。手机最终已装hash一致，颜色/alpha烟测通过，不清数据或改安全。
- 仅单图，用户自行选择分享目标，不存相册/不发送联系人；未重复旧长矩阵。全部完成，无待运行测试。

# 0.6 官方配色、可见预览面板、铰链（2026-10-30）

最终APK `deliverables/colors-panels/apk/qing-frame-colors-panels-debug.apk`；SHA`ca47c24473c78e8ad729d27f2391bc67e74ddf7f355e1c007f51d7e3047d3976`，runtime`39737b31b055a6801fc60a9c0ea8238469861bc3`。

- 两机官网fresh正文/图核实各4款，框/背/Deco分PBR，截图/黑环不染色；丹宁8.3mm受控独立资源，陶瓷特别版背材区分，冠军联名背纹/精细后摄不加入前向范围。
- 五tab非modal占位面板，开/关仅变viewport，保持zoom/pose/canvas；预览仍可拖动。设备说明移出紧凑面板；大字横屏侧栏自适应320dp。
- 极限hinge原平面定位修复为浅弧软帽，不改bbox/屏/黑环/旋转范围；3邻帧/常用侧角实际Android通过，局部截面估计非CAD。
- 21逻辑、b5实际26 Android，8色数值颜色/透明、UI选择undo/恢复/保存chooser取消均通过；ca47仅字号侧栏变化，模型/renderer资产不变，真机烟测与b5原图同hash。ca47最终26/26通过15.186s，大字横屏五标签完整/预览无重叠，设置已恢复；权威结果见本轮报告及release-final。所有本轮工作完成，无待运行测试。

# 0.5 黑环与旋转修复（完成，2026-10-30）

最终0.5.0 APK `deliverables/bezel-rotation-fix/apk/qing-frame-bezel-rotation-debug.apk`；SHA256 `6e723e53b3ac1356fe44af66b75da1c964c8eb68b2254cd89ec2f639fccf7861`；runtime commit `03d6ea17c61ffc31cc163050bc1728dd53ee2532`。

- 官方原图复核后区分content/0.85mm等距黑环/金属/铰链；真实Android闭合四边13/13/13/14px、展开9/9/9/9px，圆角几何24射线0.8468–0.8500mm。非CAD估计，正面PBR仍深灰如实记录。
- yaw±60/pitch±45及实际相机front-dot安全余量统一覆盖状态/手势/恢复/预设/undo/preview/export；背向旧草稿原子备份、一次说明，源图与构图保留。
- 最终21逻辑测试、19Android测试、真实UI越界回拖与旧rear恢复通过；最终真机正面/斜角/内屏横竖/极限透明/颜色通过，已装sha一致。未重复双4K与完整UI长矩阵，历史证据不改称最终实测。
- 完整结果与限制：`deliverables/bezel-rotation-fix/ACCEPTANCE_REPORT.md`。无待运行测试。

# Xiaomi18Fold前向资源刷新（2026-10-30，完成）

最终0.4 APK `deliverables/xiaomi18-refresh/apk/qing-frame-xiaomi18-debug.apk`，SHA079a42ecacaab8327d6e2fb61fa13cb370f2442385ad7f8714425130eddd431a；runtime commit `ab01d96dbcc5d377c09eea4867450ed622ab76b3`。权威结论 `deliverables/xiaomi18-refresh/ACCEPTANCE_REPORT.md`。

- 官方正文/多视角已实读，117.8高、83.6/163.8宽、10.68/5.02厚；像素外1168×1712/内2364×1672，暖金白普通玻璃版；有效屏幕矩形/局部参数仍estimated。
- 专属两状态/正侧优化；背相机/Deco/非活动外屏按用户要求移除，16,552tri/11renderables，约457KB；Khronos/UV/米制/独立再生通过。
- MIX Fold4/通用wide从runtime和生产配置移除，APK仅18Fold+Redmi；旧两id先canonical再白名单，备份/原子写/一次提示并保留字段与源。
- 3个现成软件固定commit研究与许可分离；新增既有正面preset的正交/清屏/fit原创组合，不照搬iPhone资产/UV。
- 7c候选9实际迁移+全UI/保存chooser取消通过；079a仅屏/孔Z+0.20mm修复灰屏，dex/manifest相同；最终17测试和1真实MIX迁移+4正侧UI复验通过，源码16逻辑测试通过。
- 最终真机11PNG，横竖内屏/外屏正侧/透明/双4K均过；三原图12块色卡均ΔE0.3510，alpha全角0；手机仅显式serial/独立xiaomi18-*，不清数据/读个人图库/改安全。
- 灰屏A/B原图/旧APK保留，最终白ROI由131/131/135恢复243/245/249。报告准确区分候选/最终及未验背面/fps/峰值边界。无待运行测试。

# 编辑工作台与光影改版（2026-10-30，完成）

最新APK `deliverables/design-refresh/apk/qing-frame-design-refresh-debug.apk`，0.3.0，SHA256 `c8ac31b17bb12d7f634d34631c1ed47303d064d76291ae4e20df947c2e7d3b58`；runtime commit `11c22c9e3a047d37d8f6935e7ef6498595850f4f`。最终结果/边界见 `deliverables/design-refresh/ACCEPTANCE_REPORT.md`，旧历史保留。

- UI：浅中性大预览+紧凑工具/胶囊tab/临时sheet，48dp触控；状态栏深字、友好自动比例、大字caret同排；手机高601→1287px（首候选布局量化，最终另实看）。
- 构图：新工程按设备取约0.543画布；旋转8角点fit，preview/export同函数；旧草稿canvas不变，显式适配才重新取比例/缩放/平移。
- 光影：原创中性条窗IBL，Redmi PBR1.1.0，几何/UV不变；最终新默认90°环境、32k主/6k补/16kIBL、弱短柔影。
- 验证：12逻辑/14最终模拟器Android测试；360dp/1.5字/横宽、sheet旧模型切换、Picker取消+自有URI导入；最终c8实际4K取消、2048保存、即时分享chooser返回均通过，未发送人。
- 真机：<DEVICE_SERIAL>只显式ADB隔离design-*，10PNG+3反射图、颜色均ΔE0.3510、透明、双4K566/550ms；安装hash一致，无清数据/个人图库/安全设置变化；手机仍不能自动点按，UI结论严格绑定模拟器。
- 交付：独立before/after/原始图/报告/SHA，不再有待运行长矩阵。局部白高光、背面简化/估计PBR、无fps/峰值/耗电测量的边界已如实记录。

# Redmi K90 Pro Max追加任务（2026-10-29）

已按用户顺序完成专属单portrait直板几何/流金白PBR/清单/机型入口/旧草稿兼容，先构建核验资源，再安装真机。最终APK `deliverables/apk/qing-frame-redmi-k90-pro-max-debug.apk`，SHA256 `6bda9cdd32ef491753810474d5c201c24a23870ad67eedd6237ed698aac6cb2a`；runtime源码commit `31400ec30cbc22909683e674caace8836cb9928b`。报告 `deliverables/REDMI_K90_PRO_MAX_ACCEPTANCE_REPORT.md` 为追加权威结论。

| 追加项 | 实际结果 |
|---|---|
| 官方核实与原创模型 | 完成；163.33×77.82×7.9mm、1200×2608、材质家族；局部estimated，partial_reconstruction |
| 资产验证 | 完成；82,062三角形/17renderable/2.1MiB；Khronos0错0警告，独立重生成同hash |
| APK与兼容 | 完成；原6个模型/清单文件不变，新增直板状态/选择入口；10逻辑单测，lint0错7警告，签名/16KB结构检查通过 |
| 新机型真机矩阵 | 完成；最终6bda在<DEVICE_SERIAL>实际16场景/25PNG，双向4K、透明、反射、30换图/30模型切换/连续10导出、快照与原创hero |
| 真实GPU问题修复 | 完成；首392空alpha1/255时域噪声，最终exportView禁dither，低清与完整透明复测四角0，三底亲看 |
| 真机回归与导入/恢复 | 完成；最终13项Android测试1.135s；自有URI生产导入1200×2608；20次合法HOME重开+force-stop，draft相同 |
| 保存/分享/取消UI | blocked未执行；HyperOS INJECT_EVENTS拒绝、NotificationShade焦点。未改安全设置、未读取通知/图库，无伪通过 |
| 数据保护与交付 | 完成；始终显式serial和隔离session，初装无旧包，最终正常project仍不存在，installed SHA与交付一致。旧模拟器证据保留 |

无待运行长矩阵。仅UI三项需用户交互/合适系统授权后续补验，不能把环境权限阻塞写成已通过或App失败。未测fps/功耗/发热/峰值，详见限制。

# 历史首版任务台账（未覆盖）

# Final task ledger

Final runtime source commit: `f189045221d5df9b08c44c106f6fb7a03a96940f`.
Final APK: `deliverables/apk/qing-frame-debug.apk`, SHA256 `e13b70946d549119a9802ab5cba3f6da5e83f2aac001937e13a54041fce8da43`.
Read `deliverables/ACCEPTANCE_REPORT.md` and `KNOWN_LIMITATIONS.md` for the authoritative result. No further tests or changes are pending in this run.

| ID | Result | Evidence |
|---|---|---|
| P00 | done | ENVIRONMENT.md / TOOLCHAIN.md, successful isolated no-watch build |
| P10 | done with target limitation | Official MIX4 full dimensions verified; Xiaomi18Fold remains clearly generic fallback |
| P11/P12 | done | Four final GLBs,0Khronos errors/warnings; independently regenerated byte-identically |
| P20/P21/P22 | done | Filament main owner; lifecycle; three original input sizes; quaternion and actual upright UV/portrait |
| P23/P24 | done with validation limits | Real PBR/IBL/reflection, native VSM softness; true target-size/offscreen PNG and transparent alpha; exact双4K |
| P30 | core done | Import,fit/fill,crop,gesture,5panels,MediaStore save,final immediate share chooser; adaptive window evidence. Cancellation UI not_verified due device instability |
| P31 | done | Atomic draft,40-step history, stale-save prevention, private source pruning and missing-source failure;11Android regressions passed on final APK |
| P40 | done with hardware limits | Rendering commit17146c9/APK07f:25scenarios/34PNG includingS30/S10; final UI APK e13:11instrumentation +E01/E06/double4K/F01 +save/share actual smoke. Same renderer/model/assets by byte comparison |
| P50 | done | Actual debug APK, source commits, models, real renders, reports, checksums; no publication/push |

Resolved issues and actual failures are retained in build-and-test-logs and visual before/after evidence. App/model/material/asset source matched55 committed files; only one URI observer line differs from the complete rendering-regression commit. Exact hash scopes are explicit in the acceptance report.

Future priorities (not current unfinished execution): official Xiaomi18Fold evidence/model, physical HyperOS/GPU/performance/16KB runtime, low-memory/storage/EXIF matrix, asset-version migrations and UI resource localization. No physical device was authorized; do not claim true-device acceptance. Do not restart a long loop merely to erase a documented environment limitation.
