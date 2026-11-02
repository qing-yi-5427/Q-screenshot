# Q-screenshot 按需前向机型库

应用只内置 Xiaomi 18 Fold。其他机型由 `catalog.json` 对应的独立版本ZIP按需安装；不会把所有GLB塞进APK。范围是近两年大陆官方在售/已发布的 Xiaomi 数字系列与 REDMI K 系列直板旗舰；新增折叠/Flip不在本轮范围。

`source/<id>/profile.json` 是可复现的原创几何输入；`package.json` 是运行时配色/材质元数据；`sources.json`、研究与图像判断台账记录官方事实与估计的边界。官方商品图只缓存于 `.cache`，不得提交或分发。全部GLB不含图像、壁纸、商标纹理或厂商网格。

精度边界：官方机身尺寸、屏像素与配色名有来源；有效屏绝对尺寸、圆角、中框倒角、孔位/按钮和PBR是各机官网图比例估计。前向新模型省略后摄与背面装饰；未核实细节明确未知。三款Xiaomi全等深微曲使用独立盖板边缘adapter；截图内容面保持正确UV和平面以便替换。配色厚度有官方差异的型号使用独立GLB，不全机Z缩放。原验收K90 Pro Max/丹宁直接原字节导出，18Fold内置GLB不重建。

## 包格式（schemaVersion 1）

ZIP根仅含 `package.json` 与 `devices/<id>/device.json`、对应GLB。manifest沿用应用原有screen节点、材质、屏幕毫米、UV、hash与colorwayAsset合同。配色由 `colors[{id,title,backMaterial,thicknessMeters?,materials:{materialName:{srgb:'#RRGGBB',metallic,roughness}},notes}]` 与 `defaultColorwayId` 驱动。包不超过64MiB、解压128MiB、32文件。目录项保存模型版本、最低应用版本8000、固定Release资产URL、ZIP SHA256和字节数。`partial_reconstruction` 与可下载表示有可试用资源，不等于逐机Android外观验收。

## 复现与验证

从干净 clone 安装项目流水线文档指定的 Python 依赖与 Blender 4.5.0 后，在仓库根目录执行以下命令。`BLENDER` 指向本机可执行文件；以下目录均为本次新建，重跑时请换目录，避免覆盖已冻结发布物。

```sh
export BLENDER="$PWD/.cache/tools/Blender.app/Contents/MacOS/Blender"
export PYTHON="$PWD/.cache/tools/qa-python/bin/python"
export REBUILD="$PWD/.cache/library-rebuild-local"
mkdir -p "$REBUILD/packages" "$REBUILD/entries"

# 九款新 Redmi；每款读取 tracked profile/package，保存 ZIP 和索引 descriptor。
"$PYTHON" tools/rebuild_redmi_library.py \
  --staging-root "$REBUILD/redmi" --packages "$REBUILD/packages" \
  --entries "$REBUILD/entries"

# 十四款 Xiaomi；专属入口保留微曲、滚花和各色独立厚度映射。
for profile in model-library/source/xiaomi-*/profile.json; do
  device=$(basename "$(dirname "$profile")")
  "$PYTHON" tools/rebuild_xiaomi_library.py --device "$device" \
    --output "$REBUILD/$device" --blender "$BLENDER"
  version=$("$PYTHON" -c 'import json,sys; print(json.load(open(sys.argv[1]))["modelVersion"])' \
    "model-library/source/$device/package.json")
  "$PYTHON" tools/model_library_pack.py --device "$device" \
    --asset-root "$REBUILD/$device/build/assets" \
    --metadata "model-library/source/$device/package.json" \
    --output "$REBUILD/packages/$device-$version.zip" \
    --entry-output "$REBUILD/entries/$device.json" --release-tag models-2026-11-r3
done

# 已验收 K90 Pro Max 只复制 tracked 原字节基线，绝不重建。
mkdir -p "$REBUILD/baseline/devices/redmi-k90-pro-max"
cp model-library/baselines/redmi-k90-pro-max/device.json \
   model-library/baselines/redmi-k90-pro-max/*.glb \
   "$REBUILD/baseline/devices/redmi-k90-pro-max/"
"$PYTHON" tools/model_library_pack.py --device redmi-k90-pro-max \
  --asset-root "$REBUILD/baseline" \
  --metadata model-library/source/redmi-k90-pro-max/package.json \
  --output "$REBUILD/packages/redmi-k90-pro-max-1.2.0.zip" \
  --entry-output "$REBUILD/entries/redmi-k90-pro-max.json" \
  --release-tag models-2026-11

"$PYTHON" tools/model_library_index.py --entries "$REBUILD/entries" \
  --packages "$REBUILD/packages" --output "$REBUILD/catalog.json"
```

上述过程不依赖已有 `.cache` descriptor；`pack --entry-output` 会保存由实际 ZIP 字节计算的 URL、SHA256、字节数和版本。九款 Redmi 的构建入口固定本轮 `models-2026-11-r3`，Xiaomi 逐包命令显式指定相同 tag；K90 Pro Max 继续引用原 Release。索引入口会重新核对每个 ZIP。生成的本地候选不能直接替换已发布附件：先比较正式 `catalog.json` 中的版本与 SHA，再执行合同、Khronos 与 Android 检查；不同 Blender/依赖环境可能产生不同字节。

构建入口只写新staging目录。打包前核对GLB hash，拒绝图像/纹理、越界路径、错误身份与非法PBR，使用固定ZIP时间戳保证确定性。`tools/model_library_render.py`是显式GLB路径的Blender四视角诊断工具；它输出不是Android截图。新增模型有screen/UV/节点/尺寸合同与Khronos验证，Android实际加载/截图替换由运行时检查，未做的逐机验收明确 `NOT_RUN`。

ZIP位于被Git忽略的 `deliverables/model-library*`；原 Release `models-2026-11`、r2 与 r3 保留独立不可变版本。该tag不匹配APK的v*发布流程。发布后不可替换同版本ZIP；模型升级必须换版本、文件名与hash。不要把官网图、缓存或Blender诊断图冒充真实设备图上传。`previewUrl` 暂为null。

本轮最终模型版本、包 SHA 与模拟器检查范围见 [0.8.0 版本验收](../docs/MODEL_LIBRARY_VALIDATION.md) 及 [机器可读审计](validation-report.json)。包内构建时状态不因外部验收重打包；实际结论由该版本化外部报告绑定。
