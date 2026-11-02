# 小米机型流水线：实际测试记录

日期：2026-11-02。基线：`788f8fa592bb75fe0c6784af6737f27fdcc4f712`。
Linux x86_64 / Python 3.12.14 / Blender 4.5.3 LTS / Khronos glTF Validator 2.0.0-dev.3.10。

Blender 便携包通过官方下载 SHA256 校验：
`975c58fcb244273838534bba771e64ad87739216b0f9b39a888531a49a72d845`。
没有安装系统驱动、接受新许可、推送远程或修改应用模型。

## 结果

| 检查 | 实际结果 |
|---|---|
| 新增流水线单元与真实 Blender 集成测试 | 14/14 通过，包含两款机型各构建两次、合成新机型构建 |
| 原有 CI version 测试 | 6/6 通过 |
| Fold folded/unfolded 与当前资源比较 | BIN 完全一致；JSON 除 asset.generator 外完全一致 |
| REDMI K90 默认 portrait 与当前资源比较 | BIN 完全一致；JSON 除 asset.generator 外完全一致 |
| 同环境重复构建 | 三份主模型分别两次 SHA256 相同 |
| staging 三主模型 + 合成新直板模型 | 合同通过；Khronos 每份 0 errors / 0 warnings |
| 原运行时四份 GLB（含丹宁） | 合同与 Khronos 通过，未修改文件 |
| 官方采集样本 | 5 个公开页面/脚本读取成功，9 个参数候选，20 个图片候选，3 个色名候选，无页面抓取失败 |
| 新证据接入 | 6 个 Fold 机身尺寸 resolve 后与现有 geometry 匹配，reconcile profile 再生成两状态，语义一致 |
| 图片采集首批 | 12 次尝试，11 张成功解码并留 SHA256，1 个 404 保留错误；默认许可 not_verified |
| Agent 参考图审查 | 查看联系表；区分外屏/背部/装饰/未知，按下载哈希保存判断；当前批次没有足够内屏/侧面证据 |
| 已验收资源保护 | 集成测试前后 app/src/main/assets 与 assets-source 所有文件哈希相同；git 差异无运行时资源变化 |
| Python 编译、git diff --check | 通过 |
| Android 真机/模拟器、Filament 成片验收 | 本轮未运行 |
| 新直板原型外观验收 | 未运行；只有合同与 glTF 验证，仍属未验收近似模型 |

采集候选需要 Agent 确认，数量不等于已核实参数数量。官方图片首批含装饰/品牌条幅，已经在视觉观察中明确排除。联系表、官方图片、缓存正文没有进入代码补丁或 APK。

官方样本来源使用现有机型台账中的 mi.com 产品页与公开 mishop wrapper/entry。字段：共同长度117.8mm、展开/闭合宽度163.8/83.6mm、厚度5.02/10.68mm；9个候选还含两组屏幕像素与两组对角。本轮仅将6个机身参数接入新的 profile；未重新认证屏幕有效毫米区域、PBR、孔位等已有估计。

## 文件哈希与差异说明

| 模型 | 已验收 SHA256 | 本轮重建 SHA256 |
|---|---|---|
| Fold folded | `604c8bb98d092dd4d833d8e86e282765bd332671c1c2ef1fa5066ecdd065bba9` | `ff8bc34b3115cf6decac112b80e0984837fb727ee52b6270468dd33b2d95b7a4` |
| Fold unfolded | `7302125c9273210594c5f24f2d24a28beb704d189027497b9389d4285205b16a` | `e4a9745ae22eb5a779cc8357053b8a5959424041c3d619a8f9155f680fa13ca0` |
| K90 portrait | `4d4422eed3129c07749f8845d95b76f8e59d160b655766920f69d7d17f067134` | `f4b67517a41d182198282bc837dfab73fdb515c9333ada3b42db324906a20b1e` |

原文件 `Khronos glTF Blender I/O v4.5.47`，本轮 `v4.5.48`。没有修改原文件的 generator 来伪装字节复现，也没有覆盖已验收资产。比较算法只排除这个字段，其他 JSON 和 BIN 必须完全一致。

## 本轮发现并修复的问题

- 同一 Blender 进程连续构建两状态时，删除对象不会删除旧材质，会出现 screen_content.001；现在每状态恢复空 factory scene，合同验证覆盖此问题。
- 原验证器把丹宁 GLB 当成额外状态，无法通过 --all；现在按 modelPath/colorwayAssets 绑定模型并验证对应哈希。
- CSS color 曾进入配色候选；现在只识别明确配色文本/小米 colorName 字面量数组。
- 仅匹配第一个 mm 数值会漏闭合尺寸；现在分别提取明确的展开/闭合标记。
- 小米商品图像 URL 拼接不能靠正则逐段当完整地址；现在识别明确 literal prod base/imageFDSPath/images 拼接，并仍按实际下载结果记录错误。

## 可重复执行

见 [使用说明](XIAOMI_DEVICE_PIPELINE.md)。详细机器报告在同目录 `XIAOMI_PIPELINE_TEST_EVIDENCE.json`；本地运行日志与原始结果位于 `.cache/pipeline/`。新机器需要重新运行联网采集，不能把本次缓存成功视为未来官方链接永久有效。

后续必要工作按机型决定：更多有效视角、Agent 视觉核对、专属孔位与结构、配色PBR调校、App catalog注册与Android成片验证。工具负责消除采集台账、参数校验、构建/验证/复现的重复劳动，不宣称能够自动恢复任意新机型的真实三维结构。
