# Q-screenshot 按需前向机型库

应用只内置 Xiaomi 18 Fold。其他机型由 `catalog.json` 对应的独立版本ZIP按需安装；不会把所有GLB塞进APK。范围是近两年大陆官方在售/已发布的 Xiaomi 数字系列与 REDMI K 系列直板旗舰；新增折叠/Flip不在本轮范围。

`source/<id>/profile.json` 是可复现的原创几何输入；`package.json` 是运行时配色/材质元数据；`sources.json`、研究与图像判断台账记录官方事实与估计的边界。官方商品图只缓存于 `.cache`，不得提交或分发。全部GLB不含图像、壁纸、商标纹理或厂商网格。

精度边界：官方机身尺寸、屏像素与配色名有来源；有效屏绝对尺寸、圆角、中框倒角、孔位/按钮和PBR是各机官网图比例估计。前向新模型省略后摄与背面装饰；未核实细节明确未知。三款Xiaomi全等深微曲使用独立盖板边缘adapter；截图内容面保持正确UV和平面以便替换。配色厚度有官方差异的型号使用独立GLB，不全机Z缩放。原验收K90 Pro Max/丹宁直接原字节导出，18Fold内置GLB不重建。

## 包格式（schemaVersion 1）

ZIP根仅含 `package.json` 与 `devices/<id>/device.json`、对应GLB。manifest沿用应用原有screen节点、材质、屏幕毫米、UV、hash与colorwayAsset合同。配色由 `colors[{id,title,backMaterial,thicknessMeters?,materials:{materialName:{srgb:'#RRGGBB',metallic,roughness}},notes}]` 与 `defaultColorwayId` 驱动。包不超过64MiB、解压128MiB、32文件。目录项保存模型版本、最低应用版本8000、固定Release资产URL、ZIP SHA256和字节数。`partial_reconstruction` 与可下载表示有可试用资源，不等于逐机Android外观验收。

## 复现与验证

```sh
BLENDER=.cache/tools/Blender.app/Contents/MacOS/Blender \
.cache/tools/qa-python/bin/python tools/build_assets.py \
  --profile model-library/source/redmi-k80/profile.json \
  --staging-root .cache/model-library-staging/redmi-k80-fresh
python3 tools/model_library_pack.py --device redmi-k80 \
  --asset-root .cache/model-library-staging/redmi-k80-fresh/assets \
  --metadata model-library/source/redmi-k80/package.json \
  --output deliverables/model-library/redmi-k80-1.0.0.zip
python3 -m unittest discover -s tools -p 'test_model_library.py' -v
python3 tools/model_library_index.py
```

构建入口只写新staging目录。打包前核对GLB hash，拒绝图像/纹理、越界路径、错误身份与非法PBR，使用固定ZIP时间戳保证确定性。`tools/model_library_render.py`是显式GLB路径的Blender四视角诊断工具；它输出不是Android截图。新增模型有screen/UV/节点/尺寸合同与Khronos验证，Android实际加载/截图替换由运行时检查，未做的逐机验收明确 `NOT_RUN`。

ZIP位于被Git忽略的 `deliverables/model-library`，由仓库独立Release `models-2026-11` 分发。该tag不匹配APK的v*发布流程。发布后不可替换同版本ZIP；模型升级必须换版本、文件名与hash。不要把官网图、缓存或Blender诊断图冒充真实设备图上传。`previewUrl` 暂为null。
