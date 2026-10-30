# REDMI K90 Pro Max：官方证据与原创资产

正式机型 **REDMI K90 Pro Max**，独立直板机 `redmi-k90-pro-max`，单状态 `portrait`。这不是折叠机变体，模型没有铰链或第二半机身。当前模型为官方资料支持的外观重建（`partial_reconstruction`），未公开的局部毫米尺寸和光学参数均估计。

## 实际读取的官方资料

仅使用两条实质官方资料路径，未由新闻或搜索摘要写入硬件参数：

1. [小米官方参数入口](https://www.mi.com/prod/redmi-k90-pro-max/specs) → 页面实际引用的 `prod-template/index.2595d681.js` → 其 `mounted()` 根据 pathname 加载的公开 [参数 wrapper](https://mishop-pc.cnbj1.mi-fds.com/mishop-pc/production/mishop_pc/src/pages/redmi-k90-pro-max/specs/index.html) → wrapper原样引用的 [正文 entry JS](https://mishop-pc.cnbj1.mi-fds.com/mishop-pc/production/mishop_pc/redmi-k90-pro-max/specs/entry-1778678001254.js)。实际读取正文数值与图片引用。
2. 同一官方公开页面内容路由的 [概述 wrapper](https://mishop-pc.cnbj1.mi-fds.com/mishop-pc/production/mishop_pc/src/pages/redmi-k90-pro-max/index.html) → [概述正文](https://mishop-pc.cnbj1.mi-fds.com/mishop-pc/production/mishop_pc/redmi-k90-pro-max/entry-1778678001254.js)。实际读取材质FAQ、设计文案和图片URL，再下载并查看正面、背面、侧斜细节与结构线稿。

所有来源/图片的实际URL、抓取时间、缓存路径、SHA256、MIME及身份依据在 `assets-source/devices/redmi-k90-pro-max/sources.json`。官方原图仅在 `.cache/references/redmi-k90-pro-max/`，不进入GLB/APK/交付模型。外观重建不等于已取得公开发布商标或外观授权；本次无Logo、Bose字样或官方壁纸。

| 项目 | 官方实际正文 | 当前建模处理 |
|---|---|---|
| 标准黑/流金白 | 长163.33、宽77.82、厚7.9mm；218g | 选择标准流金白，机身米制0.07782×0.16333×0.0079；官方厚度不含镜头凸起 |
| 丹宁 | 厚8.3mm、220g | 没有混用该厚度或丹宁纹理 |
| 显示 | 6.9英寸OLED直屏、2608×1200像素 | portrait宽高比1200/2608；不由测试截图臆造规格 |
| 有效屏幕毫米 | 官方未给 | 高158.8mm估计，宽73.0674847mm由该估计高度乘官方像素比；绝对有效尺寸仍estimated |
| 机身材质 | FAQ确认高强度6系航空铝中框、玻纤与Tech-Denim背盖家族；龙晶玻璃 | 流金白使用玻纤色感的原创非金属PBR，铝中框/Deco使用原创金属PBR；金属度、粗糙度、IOR并非厂商实测 |
| 相机/声音外观 | 实际图显示火山口金属平台、三摄、辅助传感器圆盘、独立背部低音单元及微孔 | 专属坡面平台、三摄含左下方形潜望窗、右下辅助传感器阵列、右侧圆形低音单元三圈微孔；镜头/平台/孔位绝对尺寸估计 |
| 前/侧/端面 | 实际图与线稿显示中置自拍孔、侧按键、Type-C、SIM、上/下扬声器及麦克风 | 单独几何体与小组件合并，保留正侧背结构；不复制图上印字 |

可视参考：[官方正面](https://cdn.cnbj1.fds.api.mi-img.com/product-images/new-product-images/redmi-k90-pro-max_90396/images/32430.png)、[官方背面细节](https://cdn.cnbj1.fds.api.mi-img.com/product-images/new-product-images/redmi-k90-pro-max_90396/images/32431.png)、[金属平台/侧边/低音单元细节](https://cdn.cnbj1.fds.api.mi-img.com/product-images/new-product-images/redmi-k90-pro-max_90396/images/25443.png)、[官方结构示意](https://cdn.cnbj1.fds.api.mi-img.com/product-images/new-product-images/redmi-k90-pro-max_55474/images/3321.png)。均已实际查看，示意图不假称工厂CAD。

## 生产与合同

```sh
.cache/tools/Blender.app/Contents/MacOS/Blender -b --python tools/blender/build_redmi_k90.py
python3 assets-source/devices/redmi-k90-pro-max/validate.py
.cache/tools/Blender.app/Contents/MacOS/Blender -b --python assets-source/devices/redmi-k90-pro-max/render_diagnostics.py -- redmi-k90-pro-max portrait
```

Blender4.5.0与现项目工具链一致。`build_redmi_k90.py` 是独立直板组装器，仅复用原创圆角网格/材质/坐标转换基础函数，没有调用折叠机组装函数。所有几何原创，PBR无第三方图像输入。参数在专属 `geometry.json`，语义合同在专属 `device.json`。

- 包内路径 `devices/redmi-k90-pro-max/portrait.glb`。
- 活动屏幕 `screen_main_content`，独立材质 `screen_content`，无其他活动屏幕；玻璃反射由应用运行时控制。
- 右手坐标，+X右/+Y上/+Z正面，原点机身中心；GLB单位米。
- 屏幕UV逐顶点满足 `(x/width+.5, .5-y/height)`，前法线全部+Z。
- 正面中置挖孔属于外观遮挡层；不向用户截图增加状态栏/Logo。

## 实际验证与视觉迭代

模型与证据路径：`app/src/main/assets/devices/redmi-k90-pro-max/`、`deliverables/models/redmi-k90-pro-max/`、`deliverables/evidence/redmi-k90-pro-max/model-*`。

实际执行Khronos Validator2.0.0-dev.3.10：0errors、0warnings；1条INFO为屏幕UV在占位材质下未引用，运行时替换屏幕材质需要此UV，属于有意保留。实际GLB合同检查同时核验节点唯一、单位、UV、+Z法线、禁止vendor图片、无hinge/secondary节点。报告包含真实三角形数、包围盒和SHA256。

已经实际查看官方图和模型正面、左斜、背面、侧面与法线图。初版发现玻璃/背盖叠层使非相机厚度略超7.9mm，并且浅色棚灯下低音单元辨识度偏弱；修正玻璃/背盖偏移、降低光学黑面反射、调整低音网罩金属色及微孔半径后重新生成。保留修正前背面图 `model-before-back.png`，标准七视角、wire及normal诊断均重新输出。这里全部是**Blender模型诊断，不是Android/真机导出**。

当前约82k三角形、17renderable、2.1MiB；主要预算用于机身/相机平台/镜环倒角与216个真实几何微孔，低于100k硬性说明阈值，但高于首轮20–60k目标，待真机测量后可按需减小隐藏面的倒角采样。没有据此宣称手机性能通过。官方参考有透视且不具备统一标定，不虚报轮廓2%误差或CAD精度。

最终GLB另在独立 `.cache/redmi-regenerated.glb` 实际再生，SHA256与包内文件字节一致；见 `model-reproducibility.json` / `model-rebuild.log`。最终SHA256 `05caf867b8619875b699f0b883139aeb904d26f981e08153fdc5aee4a9efc929`。
