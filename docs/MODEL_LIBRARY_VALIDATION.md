# 0.8.0 机型库版本验收

2026-11-02，本轮本地最终包审计及 Android 模拟器渲染验收通过。正式 [catalog](../model-library/catalog.json) 与 [validation-report](../model-library/validation-report.json) 绑定模型版本、ZIP SHA256、字节数和外部证据摘要；ZIP 内构建时的 `pending` / `NOT_RUN` 文字保留原样，不代表本次版本化外部检查尚未执行。

资源范围为唯一内置 Xiaomi 18 Fold，加 24 款按需直板旗舰，共 142 种配色／中框组合（内置 4、下载 138）。本轮 r3 升级 23 个包：Xiaomi 15 为 1.0.2，其余新模型为 1.0.1；原 REDMI K90 Pro Max 1.2.0 及独立丹宁 GLB 保持原字节与原 Release URL。旧附件不替换。

实测环境为 Android API 36 ARM64 模拟器中的真实 Filament 输出，并非每款实体手机。最终 24 个 ZIP 的 SHA/版本与 138 个配色／中框组合均核对通过，完成 256×320 透明导出及可见区域/角部 alpha 检查；这项检查不等于精细外观或色准验证。每款代表色另实际输出正面、左斜、右斜共 72 张 1638×2048 PNG，Redmi 与 Xiaomi 分别独立目视，未发现新增阻断问题。另查看白色 Xiaomi 18 Pro 三视图。原始证据位于被 Git 忽略的 `deliverables/model-library/android/`：

- `final-android-evidence.json`：24 包版本/SHA、138 组合结果、72 张图的 PNG SHA。
- `filament-final-r3.txt`：最终运行 `OK (1 test)`，1216.921 秒。
- `independent-final-redmi-review.json`：9 款新 Redmi 27 图及原 K90 Pro Max 3 图。
- `xiaomi-r3-independent-visual.json`：14 款 Xiaomi 42 图及白色 18 Pro 补充视图。

新模型已将有效显示面、等宽黑色遮罩与外围金属前唇分层；有效区保留各自来源推算及屏像素比例，遮罩值根据各款官方图片估计，未使用统一放大屏幕的方法。微曲与徕卡滚花使用专属几何。官方机身尺寸、屏像素、配色名有来源；有效屏尺寸、圆角、孔位、倒角和 PBR 仍是明确记录的图片估计，不是厂商 CAD、实物测量或色准承诺。代表色目视不代表每种颜色均经过 2048 精细目视。Blender 诊断图不计入上述 Android 输出。

r3 发布后的公开 HTTPS 与 Android 实际下载验证均已通过：主机匿名 HTTPS 下载全部 23 个新 ZIP，共 8,160,580 字节，逐包核对大小、SHA256、身份、版本及 manifest 内 GLB 哈希，并匹配上述 Android 渲染所用 SHA。Release 索引、公开 raw 索引与源提交 `d8e9dd1` 一致；索引 SHA256 为 `f80bb00c7d75ea00fc737985d1389d93894d1a67555aa5018e33af21f5b3d611`。主机报告为 `.cache/model-library-review/public-https-r3/report.json`。原 K90 Pro Max 目录项与 URL 未变，主机复用此前完整 HTTPS 字节证据。

Android 模拟器另从正式公开索引实际 HTTPS 下载全部 24 包，14.957 秒通过；`https-final-r3.txt` 与 `https-final-r3-results.json` 记录逐包 SHA/版本，均匹配正式索引。先前 `https-direct-timeout.txt` 的直连超时及 `https-proxy-index-and-24-packages.txt` 的既有 CONNECT 代理下 TLS 成功属于 r2 历史记录，不能当作无代理网络普遍可达的保证。

已发布 `validation-report.json` 是发布前冻结的审计快照，保留其中当时的 r3 网络待验证状态，SHA256 仍为 `42cd886ae50b1ac1d2c8b7488a6512536a98857bde2c5c7dbca00d3957e3f269`；本节追加发布后结论，不修改已发布快照、索引或 ZIP。

截图和用户图片始终本地处理；分发包不含官方商品图、厂商网格、纹理或外部图像引用。模型包的安全合同和运行时功能测试细节见 [机型库协议](MODEL_LIBRARY_FORMAT.md)、[任务记录](TASKS.md) 及对应 Android 测试报告。
