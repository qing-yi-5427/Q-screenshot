# 最新0.4研究补证（2026-10-30）

本轮已取得并实读Xiaomi18Fold官方专属正文和多角度图，已生成专属前向两状态资源，MIX Fold4从当前应用移除。最新依据、估计边界、官方图片误分类纠正在[刷新研究](XIAOMI18_RESEARCH_REFRESH.md)，下文只保留首轮历史，不能把当时未核实当作现状。

# 机型与资产研究（2026-10-29）

本轮采用路径 C，已生成两个独立机型、各两个状态。**宽比例折叠机 · 近似模型不冒充 Xiaomi 18 Fold。** MIX Fold 4 是第二机型，现已补齐官方长宽厚与屏幕像素比例，并按官方背图修正主要相机特征；有效屏幕毫米尺寸和细节仍估计，标记 `partial_reconstruction`。当前不是精确 CAD 或全外观验收。

实际打开的来源：

- [Xiaomi 18 Fold 官方参数入口](https://www.mi.com/prod/xiaomi-18-fold/specs)：HTML 的 `$GLOBAL_PAGE_INFO` 明确产品名称、商品 ID 25299，以及 5.38/7.58 英寸 SEO 描述；可读正文只有导航/站点壳层，没有长宽厚。不能用它声明官方毫米尺寸。
- [官方概述入口](https://www.mi.com/prod/xiaomi-18-fold)：实际打开后同样只有动态壳层。商城旧 product_id=1230808907 重定向到 25299，product_id=20335 打开无主体，均不作为规格证据。
- [官方导航缩略图](https://cdn.cnbj1.fds.api.mi-img.com/mi-mall/4c88499fe66e8661b0fa9c1c629757f0.png)：从上述实际 HTML `img[data-src]`、`alt="Xiaomi 18 Fold"` 提取并下载，实际查看为西野红前后并列渲染图（320×220）。能辨认横向跑道相机、三镜头、宽短机身，但分辨率和视角不足以测量厚度、铰链和其他多视角。台账 `verifiedProductIdentity=false` 表示仅导航关联，不等于产品不存在。
- [MIX Fold 4 官方参数入口](https://www.mi.com/prod/xiaomi-mix-fold-4/specs)：实际打开正文同样只有动态壳层，故长宽数据不能标成官方。
- [小米 2024 Q2 官方投资者演示文稿](https://ir.mi.com/system/files-encrypted/nasdaq_kms/assets/2024/08/21/6-09-16/Xiaomi%20Corp_24Q2_ER_ENG_v22_upload.pdf)：实际用 web PDF 阅读器打开，第 31 页确认 MIX Fold 4 产品身份、展开 4.59mm、合拢 9.47mm、226g 和四摄描述。此处只提取建模需要的厚度依据。直接下载英文和官方 static-files 两路径均 403，保留失败记录，不虚构本地 PDF / hash。

检索还出现第三方给出的 Xiaomi 18 Fold 117.8×83.6×10.68mm 和 MIX Fold 4 159.4×73.1mm 等数值，但本轮未以实际官方正文复核，因此不升级为官方规格，也不把它们混进近似机身份。未使用社区用户帖子作为厂商证据。浏览器正常动态加载补充路径因 Work 设置关闭本地浏览器而不可用；没有绕过限制。

## 参数置信度

`assets-source/devices/*/research.json` 逐字段保存分类。近似机所有尺寸均 `estimated`，是明确的测试几何假设，并非从用户截图像素换算出的硬件尺寸。MIX Fold 4 的159.37mm机身长、143.3/73.1mm宽、4.59/9.47mm厚均已由后续官方正文核实为 `official`；屏幕绝对有效毫米尺寸仍 `estimated`，比例按官方像素校准。两机有效屏幕矩形、圆角、倒角、相机凸起、镜头尺寸、按键、天线、铰链均为估计。

近似机 folded 84×120×10.8mm，unfolded 166.5×120×5.2mm；屏幕分别 79×115.8mm 和 161.4×114.2mm。近似机展开宽度与两半合拢结构相容；屏幕长宽接近兼容输入，但运行时必须仍按 fit/fill 保持原图比例。MIX Fold 4 参数以独立 JSON 保存，不能作为宽比例机同名变体。

## 生产与真实验证

生成器为原创 `tools/blender/build_device.py`，Blender **4.5.0 arm64**，构建哈希 `8cb6b388974a`。官方 DMG SHA256 `f03e5ae7813db62c8664176b62638e12c1413cd9d59a883bb7bfb67cbaa8c52e`，与下载的官方 sha256 文件一致。安装仅在项目 `.cache/tools/Blender.app`，未修改系统设置或接受新条款。

```sh
python3 tools/build_assets.py --all-states
python3 tools/validate_assets.py --all
.cache/tools/Blender.app/Contents/MacOS/Blender -b --python tools/blender/render_reference_views.py -- wide-fold folded
```

初版同参数第二次实际重建四个 GLB 的 SHA256 完全一致（`model-reproducibility.json`）；该记录早于后续MIX校准，最终MIX布局调整后未重复重建做二进制一致性比较。四个 GLB 已真实生成，包含实体双半机身、中框倒角、背板、铰链、按键、USB 口、天线断点及独立相机模组与镜头。当前各模型约0.94–1.20MiB：宽比例机36,976三角形/18 renderable，MIX Fold4校准版47,180三角形/23 renderable。米制，+X右/+Y上/+Z正面。屏幕材质 `screen_content`；folded 节点 `screen_outer_content`，unfolded 节点 `screen_inner_content`。内容面连续，UV 顶部 v=0；逐顶点实际读取 GLB 检查，法线 +Z。

Khronos `gltf-validator@2.0.0-dev.3.10` 实际执行：四个 GLB 各 0 errors / 0 warnings。完整报告在 `deliverables/evidence/model-khronos-*.json`，合同检查在 `model-contract-validation.json`。屏幕的 GLB 材质只是占位，Android 应用须替换为 unlit；玻璃反射层由运行时受控实现，清单 glassNode=null，不假称已有独立玻璃。

七个标准角度与 wire 诊断实际在 Blender 渲染到 `deliverables/evidence/model-<device>-<state>/`。这些是模型诊断，不是 Android 导出。首次查看发现镜头盖平滑法线和过亮测试灯光使背面产生扇形高光及曝光过亮；修正盖面平直法线并降低测试棚灯功率后重新生成，前后图与说明在 `model-before-after.png` / `model-visual-review.md`。当前未达到官方轮廓数值误差验收，因为没有可标定、多视角原图，不报告虚假 2% 误差。

官方原图只在 `.cache/references/`；没有入 GLB、APK 或 deliverables。原创测试环境 `assets-source/environments/studio.hdr` 由 `tools/blender/build_environment.py` 生成，无第三方 HDRI 输入。其 Filament 最终观感以 Android 实测为准。

## 官方静态正文补证（2026-10-29 追加；冻结现有运行资产）

本次只研究和修改构建别名，**没有修改 GLB、geometry.json、device.json 或 catalog.json**，以保持当前 Android 测试矩阵与资产 hash 一致。前面的“当时只有厚度可核实”是首轮状态；本节为随后补充的新证据。

实质路径共两条：第一条旧地址 `https://www.mi.com/xiaomi-mix-fold-4/specs` 实际返回 404 模板；第二条沿当前官方页面已引用的脚本读取其公开静态正文资源，成功。没有登录、绕过限制或依赖新闻补规格。

在 `product/prod-template/index.2595d681.js` 的 Vue `mounted()` 实际代码中，页面将当前 pathname 去掉 `/prod`，从官方 CDN `mishop-pc.cnbj1.mi-fds.com` 加载对应 `src/pages/.../index.html`。对 MIX Fold 4 路径实际下载该 wrapper，再沿其中原样引用的 [官方正文 JS](https://mishop-pc.cnbj1.mi-fds.com/mishop-pc/production/mishop_pc/xiaomi-mix-fold-4/specs/entry-1774514297940.js) 读取完整正文，而非猜测 CDN 文件名或将导航摘要当参数。

已核实厂商参数：

| 项目 | 官方实际正文 | 现有冻结模型与差距 |
|---|---|---|
| 机身长 | 159.37mm | 当前159.4mm，高0.03mm；下轮重建可修正 |
| 折叠 / 展开宽 | 73.1 / 143.3mm | 当前数值一致，来源分类待下一轮统一更新 |
| 折叠 / 展开厚 | 9.47 / 4.59mm | 当前数值一致；正文说明厚度不含屏幕边框和相机Deco |
| 外屏 | 6.56英寸，2520×1080 | 像素宽高比1080/2520≈0.428571；当前有效矩形68/154≈0.441558，约偏宽3.03%，未在本轮改资产 |
| 内屏 | 7.98英寸，2488×2224 | 按竖向宽高比2224/2488≈0.893891；当前137.7/154≈0.894156，约差0.03% |

屏幕分辨率比例可支持显示区域比例研究，但不能单独证明具体毫米有效矩形，且官方注明对角尺寸/圆角可见区域存在差别。没有由用户测试截图推导机身规格。

正文自身的 `imgPath` 与实际 render 引用提供了 [黑色机背图](https://cdn.cnbj1.fds.api.mi-img.com/product-images/xiaomi-mix-fold-4_333615/specs/images/4943.png) 和 [展开背面线稿](https://cdn.cnbj1.fds.api.mi-img.com/product-images/xiaomi-mix-fold-4_333615/specs/images/5497.png)，均实际下载并查看。机背图具有四镜头位置、右侧徕卡/闪光区域、右下潜望镜方形细节、相机Deco下缘微曲轮廓；展开线稿显示两半机身与铰链及镜头细节。当前参数化模型用矩形圆角Deco和四个相同圆形镜头近似，未重建这些专属细节；不能因为新增资料就宣称现有几何已达外观验收。

台账新增 `mix-cdn-specs-wrapper` / `mix-cdn-specs-body` / `mix-official-back-render` / `mix-official-unfolded-line-diagram`，均有实际 URL、缓存路径、SHA256。这些特定产品正文与实际图像的身份核实为 true；先前纯导航/壳层来源仍为 false。**Xiaomi 18 Fold 的研究与近似标记保持不变**，本次只补 MIX Fold 4 证据。

兼容 CLI 别名已支持 `--device xiaomi-18-fold` → `wide-fold`，执行时明确提示近似机；完整命令见 `tools/blender/README.md`。只运行了别名的 `--dry-run` 检查，没有触发资产重建。


## MIX Fold 4 校准落地（最新状态）

随后收到仅重建MIX Fold4的授权，已将上节官方尺寸和像素比写入参数与清单，重新生成两状态GLB并统一刷新assetHash。宽比例机两个GLB与重建前基线逐文件SHA256一致，证据 `deliverables/evidence/model-wide-unchanged.json`；其机身和屏幕数值没有修改。

MIX Fold4当前机身高159.37mm；折叠/展开宽73.1/143.3mm，厚9.47/4.59mm。外屏有效高仍估计154mm，宽按1080/2520推得66mm；内屏有效高仍估计154mm，宽按2224/2488推得约137.659mm。这些绝对有效屏幕尺寸为estimated，官方像素比本身有实际正文依据。

模型相机布局现为靠左两列四镜位，右下圆形外框内有方形潜望光学窗口，右侧增加闪光灯区域，Deco下缘采用轻微曲线/不对称抬升而非平直矩形。所有局部毫米尺寸仍估计，没有复制徕卡标识或小米Logo。两状态节点名、坐标和屏幕独立材质合同未改变。当前每状态47,180三角形、23个renderable，约1.20MiB。

新GLB经Khronos validator实际执行仍0 errors/0 warnings；逐顶点+Z法线/UV和屏幕尺寸清单检查通过。已重绘MIX Fold4两状态的七视角与wire诊断；Blender模型诊断不能代替Android新增样张，需由应用重新打包后补验。


## 当前最终资产再生一致性复验

审计时将四个最终模型从当前参数/脚本重新生成到独立 `.cache/model-repro-current/`，没有写入包内模型。实际比较四个再生GLB与当前包内GLB的SHA256：全部字节一致；同时确认包内文件复验前后hash不变。证据为 `deliverables/evidence/model-reproducibility-current.json` 与 `model-current-rebuild.log`。此项补齐前述最终MIX布局调整后未复验的限制，仅证明再生成一致性，不替代Android成片或材质验收。
