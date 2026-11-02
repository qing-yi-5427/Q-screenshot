# 小米机型资料与建模流水线

这套工具以 **研究候选 → Agent 核对 → 参数 profile → 隔离 Blender 构建 → 现有合同验证** 为流程。不是单图自动恢复 CAD。联网只发生在开发工具中，Android 无新增联网能力。

## 安全地复现当前机型

Python 3.12、Blender 4.5.3 LTS 已实际验证。`BLENDER` 指向可执行文件；Pillow 版本沿用 `tools/requirements-qa.txt`。可选 Khronos：

```sh
npm install --prefix .cache/tools/gltf-validator gltf-validator@2.0.0-dev.3.10 --ignore-scripts
export BLENDER=/absolute/path/to/blender
python tools/xiaomi_pipeline.py replay --device xiaomi-18-fold --output .cache/pipeline/fold-profile
python tools/build_assets.py --profile .cache/pipeline/fold-profile/profile.json --staging-root .cache/pipeline/fold-build
python tools/xiaomi_pipeline.py replay --device redmi-k90-pro-max --output .cache/pipeline/redmi-profile
python tools/build_assets.py --profile .cache/pipeline/redmi-profile/profile.json --staging-root .cache/pipeline/redmi-build
```

每次用新的输出目录；已有目录拒绝覆盖。`--dry-run` 不需要 Blender，不创建产物。`--profile` 显式进入 staging 分支，不运行旧入口的 runtime 写入、配色同步或 catalog 发布步骤。**旧式 `build_assets.py --device ...` 已改为默认新staging，不再写当前资源。无参数默认构建Fold；显式 `--update-builtin-fold` 才会在staging验证后更新唯一内置Fold两状态及元数据，拒绝红米或自定义profile内置更新。**

复现范围为 K90 默认 portrait、Fold folded/unfolded 三份主模型；不重新生产丹宁派生款。0.8.0运行时只含Fold两份GLB，`python tools/validate_assets.py --all` 验证当前内置资源；原K90主/丹宁两份原字节在 `model-library/baselines`，按需包的完整合同验证使用对应staging assets目录。本次同时修复了旧验证器将丹宁误当独立 state、错误报告多余 GLB 的问题。

输出：

- `input-profile.json`：实际构建输入、已有资料台账及输入哈希。
- `assets/devices/...`：沿用 schemaVersion=1 的 device.json、GLB、catalog；不含商品图片。
- `blender.log`、`validation.log`、`evidence/`：可核查的过程记录。
- `build-report.json`：产物哈希、与当前资源的逐字节/语义比较、Blender 版本和验证状态。

语义比较仅忽略 `asset.generator`，JSON 其他字段和整个 BIN 块都必须完全一致。不同 Blender 版本出现真正几何差异时会明确显示 false，不按“外观看起来相似”放行。Khronos 未安装显示 NOT_RUN，不冒充通过。结构通过不代表 Android 或外观验收。

## 接入下一款手机：Agent 执行步骤

1. 在 `.cache/pipeline/` 新建研究 config。提供 slug 与正式名称别名；名称、尺寸都不得从其他机型复制。未提供 pages 时，工具探测 mi.com 产品/参数页及仓库已研究的公开 mishop wrapper 路由，随后跟随已读 HTML/JS 中的产品资源链接。路由猜测仅是检索候选，HTTP 200 不证明机型身份。
2. 执行 collect。读失败记录；若自动路由无法发现该地区产品，Agent 使用输出的 `searchQueries` 搜索公开官方页面，把实际找到的 URL 放入 pages 后重跑。工具没有付费搜索 API，也不会执行厂商 JS、访问账户接口、绕验证码或尝试无限爬取。
3. 对照正文、产品专属资源路径和配色/状态上下文确认身份。必要时用正常公开浏览器 DOM/资源快照补充动态页面，记录方法。没有数值的页面壳层不能作为尺寸证据。
4. 执行 images，Agent 查看联系表及必要原图，再用 review-images 写入按 SHA256 绑定的视角判断。剔除图标、装饰线、Logo；保留无法确定的 unknown。默认仅抓取首批 12 张候选，数量不足时 Agent 可从 research.json 筛选相关图片生成新的研究副本再取图；不要让用户替 Agent 找图。
5. 用 resolve 选择字段证据。补充屏幕有效区域、圆角等估计的方法说明。明确版本/配色、屏幕状态及像素宽高方向，再编译 profile。
6. staging 构建、合同/Khronos 验证；对新机型执行正面、左右斜角、侧面视觉检查及 Android Filament 实际截图替换/导出检查。通过后再在单独变更中接入 App 的 Kotlin DeviceCatalog/ColorwayCatalog；staging catalog 不是自动完成 App 注册。

config 示例（示例名称与 slug 需替换，不是已核实机型）：

```json
{
  "deviceId": "xiaomi-example-phone",
  "aliases": ["Xiaomi Example Phone", "小米示例手机"],
  "pages": [{"url": "https://www.mi.com/prod/xiaomi-example-phone/specs", "state": "portrait", "variant": "standard-white"}]
}
```

pages 中 state/variant 是 Agent 已检查页面适用范围后的显式输入；页面同时列多状态/多版本时省略，不要给整个页面强加一个版本。明确的“展开/闭合”尺寸后缀会提取为候选状态；内外屏对角也会分开。未明确像素方向时不自动倒置数值。

```sh
python tools/xiaomi_pipeline.py collect --config tools/device_pipeline/xiaomi-18-fold.json --output .cache/pipeline/research-run --max-pages 5
python tools/xiaomi_pipeline.py images --research .cache/pipeline/research-run/research.json --output .cache/pipeline/images-run
python tools/xiaomi_pipeline.py review-images --index .cache/pipeline/images-run/image-index.json --annotations .cache/pipeline/view-observations.json --output .cache/pipeline/views.json
python tools/xiaomi_pipeline.py resolve --research .cache/pipeline/research-run/research.json --selections .cache/pipeline/selections.json --output .cache/pipeline/resolved.json
```

`selections.json` 用研究数组的零起始 candidateIndices 指定证据：

```json
{
  "width_mm": {
    "candidateIndices": [0],
    "identityEvidence": "实际正文/产品资源路径证明这是目标机型；写明核查位置",
    "scopeEvidence": "标准白色直板版，不含镜头凸起；写明核查位置"
  }
}
```

同一字段/状态/版本有不同数值会阻止 resolve。只有读完来源后补充 `conflictResolution` 解释适用范围才能继续；禁止为通过检查随意写理由。选择的多条证据自身数值冲突仍会拒绝。不同字段或不同 scope 不能拼在一起；合成离线 fixture 不能升为 official。

`view-observations.json`：键为实际下载图片的 SHA256，值含 `view`、0–1 的 `confidenceScore`、`notes`。view 支持 front、front_folded、front_unfolded、back、side、detail、decoration、unknown。来源哈希不可匹配、缺少理由或置信度越界会拒绝；视角判断只标 visual_observation，无法赋予再分发许可。

## 将新抓取的证据接回已有机型

`reconcile` 只接受已 resolve 的、能映射到 geometry.json 的官方数值，逐项与现有参数比对。数值不一致就停止，不改已验收参数；同值证据接入新 profile，其他屏幕边界/材质估计仍保留原标记。

```sh
python tools/xiaomi_pipeline.py reconcile --profile .cache/pipeline/fold-profile/profile.json --research .cache/pipeline/resolved.json --output .cache/pipeline/fold-refreshed
python tools/build_assets.py --profile .cache/pipeline/fold-refreshed/profile.json --staging-root .cache/pipeline/fold-refreshed-build
```

Fold 的 resolve 字段名使用 `folded.width_mm`、`unfolded.width_mm` 等几何路径；共同 height 可以引用同一个正文候选，scopeEvidence 解释两状态通用。仅选择能映射的六个机身参数；像素数、材质等仍保留在研究台账，不当毫米参数直接写入几何。

## 新直板机参数编译

`compile-slab` 读取 resolved.json 的字段：width_mm、height_mm、thickness_mm、display_pixels（模型竖向 [宽,高]）、corner_radius_mm、screen_corner_radius_mm，以及 screen_height_mm 或 diagonal_inches。研究字段结构继续沿用 value/confidence/sourceIds/notes，新增数值 confidenceScore 只代表工具/Agent 的判断强度，**不是统计测量误差**。

Agent 把不公开的圆角等补成 `estimated`，例如：

```json
{"value": 8.0, "confidence": "estimated", "confidenceScore": 0.5,
 "notes": "示例值，必须用目标机型正视图估算后替换；写明图片哈希、比例与方法"}
```

不能用示例值宣称真实机型精度。official 必须有来源及证据；measured/estimated 必须有方法说明。完整可运行的 **合成测试输入** 见 `tests/fixtures/device_pipeline/slab-research.json`：

```sh
python tools/xiaomi_pipeline.py compile-slab --research tests/fixtures/device_pipeline/slab-research.json --output .cache/pipeline/slab-profile
python tools/build_assets.py --profile .cache/pipeline/slab-profile/profile.json --staging-root .cache/pipeline/slab-build
```

由对角和像素比求有效宽高一律标 estimated。几何超过机身、非有限值、负值、比例不符、manifest 不一致都阻止构建，不静默缩放或以 null 当 0。

通用 `slab-front` 复用 `build_device.py` 的 shape/screen/material/cv，生成厚度、中框、背部遮挡、屏幕边框与独立屏幕面；默认中性 PBR，不虚构官方颜色测量。屏幕 +Z、米制、顶部 UV=0，材质为 screen_content。它**暂不包含新机型自拍孔、按键/端口、曲屏或后摄**，manifest 明示未验收。Agent 需要基于目标图片补充专属部件再验收。

新的折叠结构不可把 xiaomi18-fold 专属脚本换名当成已还原。新增小型 adapter 复用已有 shell、boundary、content、hinge 等工具并保留新参数来源，按相同 profile/staging/validator 接入；本轮没有实现任意折叠机拓扑自动推断。

## 采集能力与异常处理

| 情况 | 工具行为 / Agent 后续动作 |
|---|---|
| 官方正文完整 | 有界抓取、SHA256/时间/MIME/最终 URL、原文片段；只产生候选，Agent 负责身份/范围确认 |
| HTML 懒加载、srcset、CSS、JSON 图片 | 提取公开 URL；支持小米 imgPath.prod + imageFDSPath 的已观察字面量拼接，不执行 JS |
| 配色 | 提取显式配色文本与 colorName 字面量列表；不把 CSS color 误认商品配色，也不从色名生成“官方 PBR” |
| 页面壳层/脚本无法静态解析 | 失败/缺字段保留；Agent 读取公开 DOM，不能把搜索摘要或 HTTP 200 当参数 |
| 缺厚度/有效屏幕参数 | 停止对应模型编译，Agent 补可追踪估计或保留 unknown；不自动套用前一机型 |
| 图片角度不足 | 仍可生成标记近似的正面原型；侧面、铰链、孔位未核实，不能生产验收 |
| 多配色厚度冲突 | 按版本分开；冲突默认阻断，必须记录范围取舍 |
| 403/登录/验证码/超限 | 记录错误，不绕过、不无限重试；人工/Agent 改用另一公开来源 |
| 许可证未知 | 只在本地参考缓存中取图，保留 not_verified；构建器从不读取图缓存，验证器拒绝 GLB images/textures |
| 导出器版本变化 | 记录版本与两个哈希；仅 exporter metadata 不同可报告语义一致，真正几何差异必须调查 |

参考图片、缓存、联系表不得提交、打包进 APK 或放入模型交付目录。原始照片不能作固定高光材质；颜色/粗糙度是原创估计。源码里保留 URL 与哈希台账即可。

## 测试

```sh
python -m unittest discover -s tools -p 'test_xiaomi_pipeline.py' -v
Q_PIPELINE_BLENDER="$BLENDER" python -m unittest discover -s tools -p 'test_xiaomi_pipeline.py' -v
```

第一条离线单元测试，真实 Blender 集成测试明确 SKIP。第二条实际构建两款机型各两次、比较哈希和现有模型语义、生成合成新直板机，并确认 app/assets-source 所有文件哈希不变。fixture 使用原创文本，无厂商图片。实际本轮结果见 `XIAOMI_PIPELINE_TEST_RESULTS.md`。

## 按需库迁移后的验收基线

0.8.0仅内置18Fold；原K90 Pro Max主/丹宁GLB与device.json原字节保留于 `model-library/baselines/redmi-k90-pro-max`，不会进入APK。profile/research/colorways仍来自 `assets-source/devices/redmi-k90-pro-max`。staging比较器先读内置asset，缺失时从此源基线回退；不将“现在APK没有K90”解释为不需要原资源比较。真实Blender集成测试包含此基线的全文件hash保护，删除APK资源后仍执行两次replay与语义比较。
