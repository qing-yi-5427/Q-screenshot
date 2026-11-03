# 按需机型库格式（schemaVersion 1）

App 0.8.0/code8000 仅内置 Xiaomi 18 Fold 的 device.json、GLB 与四款配色 metadata。APK 的 `library/catalog.json` 只含列表 metadata；其他模型须由用户点选后下载。开发资料、参考图、脚本不会下载到 App。

索引入口固定为 `https://raw.githubusercontent.com/qing-yi-5427/Q-screenshot/main/model-library/catalog.json`。顶层含 `schemaVersion:1`、`updatedAt` 和 `devices` 数组（≤100）。每项含：

- `id`、`name`、`states`、`defaultState`、`notes`。
- `modelVersion`、`minAppVersionCode`。
- `packageUrl`、`packageSha256`（小写64位）、`packageBytes`。
- `previewUrl`（可空；本版不自动下载图片）。
- `downloadable` 与 `verificationStatus`：仅已完成包可设 downloadable=true；generic_approximation 不提供下载。partial_reconstruction 的具体未核实项和 Android 验收状态须在 notes/validation 如实注明。

包初始 URL 仅允许本仓库 `https://github.com/qing-yi-5427/Q-screenshot/releases/download/models-<版本>/<文件>.zip`；禁止其他仓库、私有上传、任意 URL、HTTP、账户信息、特殊端口、查询或路径转义。只接受该入口返回的 HTTPS `release-assets.githubusercontent.com` 跳转，最多5跳。索引本身不跟随跳转。

ZIP 根含 `package.json` 和 `devices/<id>/device.json`、manifest 引用的 `.glb`。package.json 含 schemaVersion/id/name/modelVersion/states/defaultState/notes/defaultColorwayId/colors。colors 限1–64项，每项为 `{id,title,backMaterial,thicknessMeters?,materials:{材质名:{srgb:'#RRGGBB',metallic,roughness}},notes}`。数值必须有限、范围合法。默认配色必须存在。device.json 沿用当前 schemaVersion1 合同，包含主模型 assetHash 及色版 colorwayAssets/colorwayAssetHashes。

安装先校验包长度和 SHA256，再检查 ZIP 中央目录、路径和文件类型。限制：ZIP≤64MiB，展开≤128MiB，≤32条目，JSON≤1MiB/文件、GLB≤32MiB/文件；拒绝软链接、特殊文件、路径穿越、重复路径、未声明文件、图片/代码/外部纹理。包与索引的 ID/version/states/defaultState 必须一致，GLB 独立hash、屏幕节点、材质与元数据须一致。完成后重命名临时目录并原子切换 active 指针；取消、网络错误、校验失败不修改已发布旧版本。

缓存为 App 私有目录 `model-library/<id>/<modelVersion>_<zipSHA>/`。版本目录只读且不会被更新原地覆盖；旧版本保留供草稿、Undo 和正在导出的快照使用。同一 modelVersion 不接受不同 SHA。总缓存上限384MiB，本版不自动删除历史版本。网络请求为单任务，用户可取消；已安装版本离线可用，内置 Fold 无联网依赖。

ProjectState 新增可空 modelVersion。旧草稿/默认保留 deviceId、stateId、colorwayId、源图及构图；缺包不转换为 Fold、不丢弃字段。未知合法 ID/状态先保留，等待 metadata。显式选机只有资源就绪后才提交状态。外部分享缺默认机型时保留独立私有 source，显示“下载并继续/重试/取消”，不默默换设备。渲染与导出锁定不可变版本快照。

内置 Fold 首次使用时也复制并校验到不可变版本归档；后续 APK 的 Fold 版本更新不会覆盖旧草稿所 pin 的字节。当前 Fold active 使用当前 APK 版本，旧归档继续可用。若私有目录无法归档，当前内置资源仍可直接使用。

下载取消按调用者 owner 隔离；编辑器取消不会取消分享任务，等待资源但尚未发起下载的分享取消也不会影响编辑器。实际网络失败进入可重试错误，不误作用户取消；界面使用中文连接/下载提示，详细异常只记日志。单任务队列不会自动下载全库。

机型列表区分“使用已下载版本”和“下载新版本”。断网可直接切换到已安装版本；新版本安装完成也不会偷偷改动当前场景。显式 pin 的版本缺 metadata 时保留原 stateId/colorwayId，不借用其他版本的状态去规范化。不存在配色或模型时等待补资源，不能静默替换。

库包是数据和原创重建模型。加载/导出通过不表示尺寸、材质或所有细节达到 CAD 精度；资源 notes/validation 与外部同 SHA Android 验收记录共同说明范围。发布同版本后不得换字节，校准必须升 modelVersion 并保留旧包。

相机正面约束与默认示例尺寸通过纯版本化几何表共享安装manifest的bodySizeMeters/inputSizeHints；色版厚度单独叠加。指定版本缺失时不借用Fold或新版几何改旧pose。仅内置demo可按已安装直板像素比裁切，真实图和旧构图不改。
