# 摄影棚反射与REDMI PBR改进

已只查看用户指定参考4：`用户提供的界面参考图（未随源码分发）`，没有扫描图库、复制或打包该图。参考用于观察清楚截图、亮银边缘、黑色反射条与材质分层，不抽取其中手机、截图、UI或品牌素材。

## 先分清“变亮”和“更像金属”

旧摄影棚实际HDR积分平均辐射0.438574、峰2.1875。新版采用完全原创中性条形柔光箱与黑卡间隙，整体乘0.52归一化后，实际文件平均0.435253（比旧低约0.76%）、峰6.21875。不是只提高总体亮度：更多能量集中在局部白条，其余方向接近黑卡，使金属表面产生明暗反射边。

HDR所有像素R=G=B，没有彩色摄影棚光污染。新内容为1024×512线性Radiance RGBE，Filament1.79.0 cmgen预计算256×256六面IBL；IBL约2.0MiB，skybox约1.5MiB。KTX结构实查六面、256宽高与mip；独立路径重复构建与包内全部字节一致。

原创源与完整参数在 `tools/build_render_assets.py`，没有第三方HDRI或新依赖。脚本保留首次before环境，不会在反复迭代时覆盖。

```sh
python3 tools/build_render_assets.py --environment-only
# 可独立输出，不改APK输入：
python3 tools/build_render_assets.py --environment-only --output-assets .cache/design-refresh-repro-assets --evidence-dir .cache/design-refresh-repro-evidence
```

`--environment-only` 不重编材料包；普通全量命令仍以已固定matc79编译mobile/OpenGL。

## 只改材质，保持模型与截图合同

在主实现明确授权后，REDMI PBR版本升级为1.1.0：

| 材质 | 当前线性baseColor | 金属度 / 粗糙度 | 目的 |
|---|---|---|---|
| 铝中框/镜环 | 0.72,0.74,0.76 | 0.95 / 0.21 | 清楚银色反射条；较锐但保留粗糙度 |
| 金属Deco | 0.58,0.58,0.55 | 0.90 / 0.26 | 与边框分层，略柔和 |
| 流金白玻纤背盖 | 0.70,0.685,0.64 | 0 / 0.42 | 保留非金属漫反射，不当作镀银背盖 |
| 镜头光学涂层 | 0.008,0.014,0.020 | 0 / 0.12 | 非金属低粗糙度玻璃光学层；外围黑层独立 |

这些是原创视觉近似，不是厂商测量BRDF。值已写入专属geometry.json的material_parameters，并由生产脚本读取；没有改任何机身尺寸或局部几何。

旧新GLB的每个几何accessor（位置、法线、UV、索引等）实际逐字节相同；节点名称与屏幕合同不变。屏幕材质 `screen_content` 与UV完全不变，不把屏幕改成普通受光PBR。当前GLB SHA256：`4d4422eed3129c07749f8845d95b76f8e59d160b655766920f69d7d17f067134`。source/app device与app catalog对应hash已统一。独立再生GLB字节一致，Khronos0错误/0警告。

## 运行时建议与验收边界

主实现初始采用方向主灯32k、补光6k、IBL22k，作为原65k/16k/26k的替代起点。避免直接光压过环境反射条；如果机身仍过亮，优先降低直射强度，不改全场曝光以免影响截图unlit颜色。屏幕unlit、linear tone mapper与原图色彩链路保持原样，由主实现实际Android图验收。

环境旋转时应看到金属高光沿实体边缘迁移。反射强度为0时截图仍清楚；高光不能当作固定渐变贴图。白棚、冷灰、深色预设建议共用中性反射源，通过环境方向、直射/补光比例与背景区分，强彩灯应谨慎且不得通过全局调色染截图。

`deliverables/design-refresh/assets/studio-{before,after}-{front,back}.png` 是**Blender诊断，不是Android输出**：使用同一当前PBR模型、同一相机与小面积直接光，只换旧/新HDR；可看到银边由均匀灰变成更强的亮边和黑卡反射。屏幕为相同纯色unlit占位；不能据此宣称截图色卡或真机性能通过。复现实验脚本在 `deliverables/design-refresh/assets/diagnostics.py`。

旧环境和旧REDMI模型只另存至新 `deliverables/design-refresh/{assets,models}/before/`；没有覆盖历史deliverables模型/证据。当前新模型副本、几何不变报告、Khronos和再生报告在 `deliverables/design-refresh/models/`。

## 最终Android实测选择

最终c8包使用默认环境90度、主32k/补6k/**IBL16k**（22k仅首候选），降低白高光并保留银框/暗带/按键轮廓。新工程与R12使用弱shadow0.16/softness0.85。0/75/90/105/180度低清真实对照和2048×2560最终hero位于`deliverables/design-refresh/`；局部高光仍可饱和，不夸作照片级。旧草稿参数原样保留，完整新默认可显式重置场景（保留截图）。
