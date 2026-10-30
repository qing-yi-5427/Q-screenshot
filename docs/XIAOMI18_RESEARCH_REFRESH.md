# Xiaomi 18 Fold：官方补证与专属模型

本轮已突破原先仅能读导航壳层的限制，**正式机型、主要尺寸、像素规格、材质家族与多视角外观均取得产品专属官方正文和实际图片支持**。新增独立 `xiaomi-18-fold` 两状态资源，未将 `wide-fold` 改名，未借用 MIX Fold4 的长机身、四摄模组或未知参数。

模型仍标 `partial_reconstruction`：没有工厂CAD；局部圆角/铰链/按键/孔位/相机尺寸和光学参数为可追踪估计。官方核实字段不与估计字段混用。

## 官方正文获取链

实际打开 [官方概述](https://www.mi.com/prod/xiaomi-18-fold) 与 [参数页](https://www.mi.com/prod/xiaomi-18-fold/specs)，站点HTML引用 `prod-template/index.2595d681.js`。沿该已实读脚本 `mounted()` 根据当前 pathname 构造的公开mishop内容路由获取wrapper，再沿wrapper原样引用的entry地址读取：

- [参数wrapper](https://mishop-pc.cnbj1.mi-fds.com/mishop-pc/production/mishop_pc/src/pages/xiaomi-18-fold/specs/index.html) → [产品专属参数正文](https://mishop-pc.cnbj1.mi-fds.com/mishop-pc/production/mishop_pc/xiaomi-18-fold/specs/entry-1792032063000.js)
- [概述wrapper](https://mishop-pc.cnbj1.mi-fds.com/mishop-pc/production/mishop_pc/src/pages/xiaomi-18-fold/index.html) → [产品专属概述/FAQ正文](https://mishop-pc.cnbj1.mi-fds.com/mishop-pc/production/mishop_pc/xiaomi-18-fold/entry-1792032063000.js)

正文明确产品名、成套尺寸、配色、材质与专属图片base；这些不是搜索摘要。没有使用商城product_id或标题作为充分身份依据，没有访问账号、验证码或私人接口。第三方新闻未用于提升尺寸置信度；任何返回花洒等错误商品的商城记录均不能覆盖这组专属正文。

## 核实结果

| 字段 | 官方实际正文 | 模型处理 |
|---|---|---|
| 长度 | 117.8mm | 两状态相同、official |
| 宽度 | 闭合83.6mm、展开163.8mm | 两状态分别保存、official |
| 厚度 | 闭合10.68mm、展开5.02mm | 主体尺寸official；本项目清单不包含相机凸起 |
| 重量 | 219g | 只做身份比对，未参与几何 |
| 外屏 | 5.38英寸；1712×1168；概述明确竖向1168×1712 | 比例1168/1712，official |
| 内屏 | 7.58英寸；2364×1672 | 横向2364/1672，official；竖构图旋转模型/映射由App处理 |
| 配色 | 陶瓷特别版（粉）、暖金白、黑、西野红 | 当前选择暖金白普通版，不混粉陶瓷材质 |
| 普通版背板 | 龙晶玻璃3.0 | 原创非金属暖白PBR；磨砂感粗糙度是视觉近似，非BRDF实测 |
| 特别版背板 | 氮化硅陶瓷 | 没有加入当前普通版模型 |
| 中框 | 航天级7系铝合金 | 原创金属PBR，沿用当前高光家族，非镀铬盒子 |

有效显示矩形绝对毫米尺寸仍为 **estimated**：由官方标称对角与像素比通过 `height=diagonal_mm/sqrt(1+aspect²)`、`width=height×aspect` 推导，得到外屏约 **77.013836×112.883294mm**、内屏约 **157.189194×111.176114mm**。标称对角、圆角可见面积、像素排列与厂商测量方式不等于工厂有效区域CAD，不能把这些计算结果标成official毫米尺寸。图片维持完整UV比例，不拉伸用户截图填壳。

所有字段与估计项在 `assets-source/devices/xiaomi-18-fold/research.json` / `geometry.json`。本轮台账替换为专属来源，原混合台账另存 `deliverables/xiaomi18-refresh/research/prior-source-ledger.json`，不涂改历史研究结论。

## 已实际查看的多视角

官方原图及其派生联系表只在 `.cache/references/xiaomi-18-fold-refresh/`。联系表 `contact-sheet.png` 顺序见 `contact-index.json`，未进入GLB或APK，也未复制进交付模型。每项URL、SHA256、MIME、时间、用途和许可未知标记在sources.json；广告图中人物可能为厂商标注AI生成，不能把人物照片或宣传透视当工业测量。

- [暖金白背面](https://cdn.cnbj1.fds.api.mi-img.com/product-images/new-product-images/xiaomi-18-fold_37057/images/1310.png)：金属外圈跑道、黑内岛、三镜头+闪光，无大圆镜头突出内岛。
- [外屏正面](https://cdn.cnbj1.fds.api.mi-img.com/product-images/new-product-images/xiaomi-18-fold_37057/images/1314.png)：铰链边更直、小R；外侧更大圆角，正面上中自拍孔。
- [闭合与展开正面](https://cdn.cnbj1.fds.api.mi-img.com/product-images/new-product-images/xiaomi-18-fold_19052/images/7811.png)：标示5.38/7.58、展开内屏上右自拍孔、中心铰链接缝。
- [侧面/圆角/跑道特写](https://cdn.cnbj1.fds.api.mi-img.com/product-images/new-product-images/xiaomi-18-fold_19052/images/7814.png)：两层机身、细黑合缝、侧面按键、金属边缘与跑道台阶。
- [另一张明确内屏半开图](https://cdn.cnbj1.fds.api.mi-img.com/product-images/new-product-images/xiaomi-18-fold_19052/images/7837.png)：来源为“超级像素屏幕”段image_246，明确显示内屏孔在右上角，是与7811独立的方位交叉核对。
- [陶瓷侧/底部图](https://cdn.cnbj1.fds.api.mi-img.com/product-images/new-product-images/xiaomi-18-fold_19052/images/1047.png)：只辅助理解接口/层叠，不把其粉陶瓷外观、未知版本尺寸混入暖金白。

**孔位审查更正：** specs的1329.png早期备注误称“展开线稿”。实际其前后文为image_39/5.38数字，图的平直铰链边与侧键方向符合整体横转90°的外屏；故“右中孔”不能当作展开内屏孔。已对照7811与7837实际展开图，维持模型内屏右上孔；没有因这次审查交换或改写GLB。台账已改正图用途，保留过程说明，避免下轮复发。

## 专属几何与材质（最终front-focused范围）

用户随后明确：套壳不需要背面。默认生产器已切换为 **front-focused solid3D**，相机/Deco/背部外屏细节停止验收并从GLB移除。先前已查到的背部厂商资料只作为研究记录，不代表最终模型包含这些结构。

`tools/blender/build_xiaomi18_fold.py` 是新专属组装器，只复用原创坐标/材质基本函数，没有调用旧generic装配；专属参数仍为官方117.8mm短宽机身。

- 显式非对称圆角：靠铰链小R、外侧大R；屏幕边界和用户原图UV完整保留。
- 两层机身按5.02mm半壳与10.68mm闭合总厚组织，保留黑色合缝；展开是两独立半壳与中部可见铰链，屏幕连续。
- 背面只保留闭合遮挡/投影用实体和平整两半分区；没有相机镜头、Deco、背部外屏材质节点或相关纹理。
- 侧键/可见边框/铰链保留实体倒角与金属高光。微小底部端口仍是薄实体，去掉亚像素昂贵倒角，未将整机降为2D。
- 金属0.95/roughness0.21、暖白背板metal0/roughness0.37，PBR为原创调校；光学自拍孔是几何遮挡，不染色截图。

没有Logo、徕卡印字、官方壁纸、商品图片或固定高光贴图。没有改Redmi、UI或摄影棚IBL。

简化实际量化：

| 状态 | 简化前tri/renderable/bytes | 最终tri/renderable/bytes | 三角形 / bytes减少 |
|---|---|---|---|
| folded | 29,304 / 18 / 809,452 | 16,552 / 11 / 458,576 | 43.5% / 43.3% |
| unfolded | 30,644 / 19 / 840,816 | 16,552 / 11 / 456,864 | 46.0% / 45.7% |

详见 `front-focused-reduction.json`。最初微小端口合计12,060三角，去除不可见微倒角后明显下降。主实体/屏幕/可见倒角没有改变。最终活动屏幕位置、法线、UV、索引相对初版实际逐字节一致，见 `reproducibility.json`。

## 生产、验证与交付边界

```sh
.cache/tools/Blender.app/Contents/MacOS/Blender -b --python tools/blender/build_xiaomi18_fold.py -- folded
.cache/tools/Blender.app/Contents/MacOS/Blender -b --python tools/blender/build_xiaomi18_fold.py -- unfolded
python3 assets-source/devices/xiaomi-18-fold/validate.py
.cache/tools/Blender.app/Contents/MacOS/Blender -b --python assets-source/devices/xiaomi-18-fold/render_diagnostics.py -- xiaomi-18-fold folded
.cache/tools/Blender.app/Contents/MacOS/Blender -b --python assets-source/devices/xiaomi-18-fold/render_diagnostics.py -- xiaomi-18-fold unfolded
```

GLB米制、+X右/+Y上/+Z正面；节点 `screen_outer_content` / `screen_inner_content`、独立 `screen_content` 材质，顶部UV为0。每状态只有一个活动内容材质；最终展开背面没有外屏节点或多余screenBinding。

两状态已经实际经Khronos validator2.0.0-dev.3.10校验，均0错误/0警告。实际二进制逐顶点核验+Z法线、top-leftUV、有效屏幕唯一性、节点合同、正数米制和无vendor图。独立.cache重生成，与包内二进制字节一致。原图不随包的检查来自GLB没有images引用，不用口头声明代替检查。

按用户新范围，最终只输出正面、左右斜角、俯仰、侧面六角度及wire/normal诊断，不再投入背图。最终路径 `deliverables/xiaomi18-refresh/evidence/front-focused/{folded,unfolded}/`。初次完整背部研究阶段已生成的旧七角度位于原evidence/{folded,unfolded}，属于历史研究版，不是当前front-focused模型。已实际查看正面/斜面/侧面与法线诊断，检查中部屏幕连续、非对称R、合缝与孔位。**这些是Blender诊断，不能冒充Android或真机导出。** Android样张由主实现单独生成验证。未使用宣传透视图计算虚假2%轮廓误差，工厂CAD精度未验收。

最终模型与清单副本在 `deliverables/xiaomi18-refresh/models/`；所有实际合同、Khronos、hash/再生结果在evidence内。删除MIX运行入口、迁移旧草稿和catalog整合由主实现负责，本资产任务不修改Android逻辑或旧机移除范围。

最终front-focused SHA256：folded `52d08e2af8bb2dd398a674a8e25cd5a046cf5725e0f4614138e5b6f91fa0e725`；unfolded `36df0069c7ea5df1a4ce202e1d1e6b428e86d107dde23c4c5b04896ce5b557b3`。assetVersion1.1.0，独立再生字节一致。


## 斜角灰屏单变量排查：0.20mm前向间距试版

真实Android发现正交白色正常、斜角整片均匀灰，不能把出图标记为视觉通过。实际GLB原content与整块不透明bezel前表面仅25μm（folded）/15μm（unfolded）间距；机身前方没有额外cover玻璃。透视深度精度/MSAA覆盖是待证假设，不是已确定根因。

经授权只将活动content与自拍孔同时向+Z移动0.20mm，显式参数 `screen_front_clearance_mm=.20`；不是修改曝光、亮度或新shader，也不宣称这是厂商物理玻璃厚度。试版间距变为225/215μm，孔保持在屏前50/40μm。所有XY、UV、法线、索引和材质字节未变，仅这两个节点POSITION的Z变化；真实厚度/屏幕比例合同不改。

包内试版assetVersion1.1.1：folded `00369576a7d74f7c0c42a23d83b99b0671e0b880c46ca8052249882755227823`；unfolded `40dff77bb73f6c728468d84287bbac32acfd9f080ebf51b6f9c5c3c622d41bcc`。结构/Khronos均0错误0警告、独立再生字节一致，仅证明资产有效；实际X02/X09是否恢复白色待主实现真机A/B。若不恢复，不能把深度间距列为根因。之前Blender前侧诊断属于1.1.0版本，本试版不重复绘制大批诊断。

原版GLB、device、geometry与生产脚本保留 `deliverables/xiaomi18-refresh/evidence/spacing-020/before/`；新delta检查、Khronos、再生报告同在spacing-020目录。
