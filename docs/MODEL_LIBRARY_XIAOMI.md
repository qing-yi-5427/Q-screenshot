# Xiaomi 直板旗舰机型资料与资源

核查日期：2026-11-02。按用户选择，仅增加直板机；既有 Xiaomi 18 Fold 继续内置，不增加 MIX Flip 或其他新折叠机。[小米官方开售活动](https://www.mi.com/article/detail/a58b2a65ac38.html)记录15/15 Pro于2024-10-29开始新品开售，为系列边界候选，随完整15系列保留；本清单不表示所有机型都在严格滚动两年内发布。

每款均实际抓取 mi-fds 小米公开 mishop wrapper 及其中引用的产品专属 specs/overview entry；身份根据专属产品 slug、根 class/prodName、对应尺寸与主屏正文核对，未使用官网通用导航或 HTTP 200 证明身份。官方正文 URL、最终 entry URL、SHA256、原文片段及图像观察写入每款 `model-library/source/<id>/research.json` 和 `sources.json`。官方图片仅存在忽略目录，不随 GLB 或安装包发布。

## 已有官方主体和参数的机型

尺寸顺序为高×宽×厚，单位 mm；像素统一为竖屏[宽,高]。表中厚度为默认版本，差异见下一节。

| 机型/官方参数入口 | 机身尺寸 | 主屏像素 | 正面结构 |
|---|---|---|---|
| [Xiaomi 15](https://www.mi.com/prod/xiaomi-15/specs) | 152.3×71.2×8.08 | 1200×2670 | 直屏、居中圆孔、四曲包裹中框 |
| [Xiaomi 15 Pro](https://www.mi.com/prod/xiaomi-15-pro/specs) | 161.3×75.3×8.35 | 1440×3200 | 全等深微曲盖板、居中圆孔、四曲中框 |
| [Xiaomi 15 Ultra](https://www.mi.com/prod/xiaomi-15-ultra/specs) | 161.3×75.3×9.35 | 1440×3200 | 全等深微曲盖板、居中圆孔、四曲中框 |
| [Xiaomi 15S Pro](https://www.mi.com/prod/xiaomi-15s-pro/specs) | 161.3×75.3×8.33 | 1440×3200 | 全等深微曲盖板、居中圆孔、四曲中框 |
| [Xiaomi 17](https://www.mi.com/prod/xiaomi-17/specs) | 151.1×71.8×8.06 | 1220×2656 | 直屏、较大圆角、居中圆孔 |
| [Xiaomi 17 Pro](https://www.mi.com/prod/xiaomi-17-pro/specs) | 151.1×71.8×8.00 | 1220×2656 | 直屏、较大圆角、居中圆孔 |
| [Xiaomi 17 Pro Max](https://www.mi.com/prod/xiaomi-17-pro-max/specs) | 162.9×77.6×8.00 | 1200×2608 | 大直屏、居中圆孔 |
| [Xiaomi 17 Ultra](https://www.mi.com/prod/xiaomi-17-ultra/specs) | 162.9×77.6×8.29 | 1200×2608 | 官方明确四等边直屏、居中圆孔 |
| [Xiaomi 17 Max](https://www.mi.com/prod/xiaomi-17-max/specs) | 162.9×77.6×8.20 | 1200×2608 | 官方 FAQ 明确直屏、居中圆孔 |
| [Xiaomi 18 Pro](https://www.mi.com/prod/xiaomi-18-pro/specs) | 151.8×72×8.46 | 1120×2436 | 6.4 英寸直屏、大圆角、居中圆孔 |
| [Xiaomi 18 Pro Max](https://www.mi.com/prod/xiaomi-18-pro-max/specs) | 163.39×77.6×8.39 | 1208×2624 | 6.9 英寸直屏、大圆角、居中圆孔 |
| [Xiaomi 17T](https://www.mi.com/prod/xiaomi-17t/specs) | 157.63×75.19×8.17 | 1268×2756 | 大陆版6.59英寸柔性直屏、居中圆孔 |
| [Xiaomi 17T Pro](https://www.mi.com/prod/xiaomi-17t-pro/specs) | 162.28×77.51×8.25 | 1280×2772 | 大陆版6.83英寸柔性直屏、居中圆孔 |
| [Xiaomi 17 Ultra 徕卡版](https://www.mi.com/prod/xiaomi-17-ultra-byleica/specs) | 162.9×77.6×8.32 | 1200×2608 | 独立滚花中框、居中圆孔；黑/米白8.46mm |

17/18 Pro 的参数页同时列背屏像素，研究台账保留原文，但上表和模型只选第一组明确主屏数据。17 Ultra 大陆版与全球版重量/电池不同，本轮只取大陆专属 entry，未拼接地区规格。

未找到可确认主体与规格的 Xiaomi 18 标准版、18 Ultra，不生成资源。猜测 wrapper 返回 404 或搜索无结果只能记录“未核实”，不推断品牌已取消该产品。Flip 资料初步读取后按用户范围停止，未产生新折叠资源。

## 官方配色名称和厚度范围

| 机型 | 参数页实际列出的配色/版本 | 厚度差异 |
|---|---|---|
| 15 | 丁香紫、浅草绿、黑色、白色、亮银版；钻石限定版冰川白/大象灰/橙色 | 常规8.08，亮银8.48，钻石8.36 |
| 15 Pro | 云杉绿、岩石灰、白色、亮银版 | 常规8.35，亮银8.73 |
| 15 Ultra | 白色、黑色、松柏绿、经典黑银、樱花粉、微风蓝、鸢尾紫、金棕色 | 白黑9.35，其余9.48 |
| 15S Pro | 远空蓝、小米龙鳞纤维版 | 8.33 |
| 17 | 黑色、白色、雪山粉、冰融蓝 | 8.06 |
| 17 Pro/Pro Max | 黑色、白色、冷烟紫、森野绿 | 8.00 |
| 17 Ultra | 黑色、白色、冷烟紫、星空绿 | 8.29 |
| 17 Max | 白色、晴空蓝、像素黑 | 玻璃8.20，像素黑玻纤8.15 |
| 18 Pro | 黑色、白色、冰桃粉、星河蓝、透明版 | 前三玻纤8.46；后两玻璃8.56 |
| 18 Pro Max | 黑色、白色、冰桃粉、星河蓝、透明版 | 前三玻纤8.39；后两玻璃8.47 |
| 17T | 黑色、冰莓紫、幻彩白 | 大陆版8.17 |
| 17T Pro | 黑色、星云紫、午夜蓝 | 大陆版8.25 |
| 17 Ultra 徕卡版 | 黑银色(非卫星)、黑色、米白色 | 黑银8.32，黑/米白8.46 |

配色名为官方名称；RGB/金属度/粗糙度均为原创视觉估计，不称官方光学测量。纯 PBR 前侧配色省略钻石镶嵌、龙鳞/透明内部结构、背板图案和标志，不能当作这些特别版的完整工业复刻。未在当前专属参数正文中读取的其他历史定制组合不臆造加入。

不同厚度是真正重新生成机身和正面位置的派生 GLB；manifest 按 colorwayAssets、colorwayAssetHashes、colorwayBodySizeMeters 绑定。相同厚度的不同配色可使用相同字节，但采用各自唯一资源路径，以满足现有合同验证。

## 几何方法和限制

每款 profile 使用各自官方机身尺寸、主屏像素方向和已查看图片哈希；按各自正面图/官方尺寸线稿比例估计有效区域、圆角、前摄与侧键。15 系外圆角约6.2–6.8mm、17系约9.4–10.7mm、18Pro约12mm；这些数字是可追踪估计，**不是公开 CAD 尺寸**。主屏高度采用逐机图像估计并保留原始像素宽高比，不能同时把宣传图透视和名义对角线视为精确测量。

共享正面构造增加居中前摄圆孔外观和独立侧键，不包含背摄。15 Pro/Ultra/15S Pro 使用专属 `xiaomi-library` adapter：12 环等深微曲盖板边缘与圆润框，内容面保持可替换平面；不声称恢复真实发光层曲率或玻璃折射。其余使用带各自几何参数和正侧细节的共享直屏构造。端口本轮保留 unknown，不补不存在的洞孔。

已逐款查看官方联系表/必要原图与本地 Blender 正面、左斜、右斜、73°侧面视图，确认孔位位于前面、侧键独立、无表面贯穿或脱离，厚度/圆角在视图可见。Blender 中性工作台图仅用于检查几何，**不是 Android Filament 输出或材质验收**。局部精确误差、真机外观、各色真实反射仍未认证。

## 再生成和验证

例：

```sh
.cache/tools/qa-python/bin/python tools/rebuild_xiaomi_library.py \
  --device xiaomi-18-pro \
  --output .cache/model-library-staging/new-xiaomi-18-pro \
  --blender .cache/tools/Blender.app/Contents/MacOS/Blender
```

必须用新 staging 目录。读取已审核 source profile/package，生成主模型及所有不同厚度版本，并对最终合并 manifest 执行合同验证；不覆盖 app/assets-source。

本次 Darwin arm64 / Python3.14.9 / Blender4.5.0，11款主模型与7套额外厚度（共18套独立几何）已实际构建，逐构建合同通过；合并各色映射后11款均再次 `validate_assets.py --all` 通过。47个原 app/src/main/assets 与 assets-source 文件哈希前后相同。随后复用本地已安装 Khronos，对最终27个实际打包GLB（同厚度按色独立路径）逐一验证，0 errors / 0 warnings。没有官方图片、纹理或私密数据打包。

缓存证据 `.cache/model-library-research/xiaomi/`；可交付 staging `.cache/model-library-staging/xiaomi-*/build/assets/`；逐款构建报告、最终合同记录和四视图在各 staging 目录，顶层汇总 `.cache/model-library-staging/xiaomi-summary.json`。Android下载、缓存安装、Filament替图/导出与发布由应用/发布任务另外验收；本资料不冒称其通过。

## 完整性复查补项（2026-11-02）

[官方商品22423](https://www.mi.com/shop/buy/detail?cfrom=search&product_id=22423)主体与参数链接证明“17 Ultra 徕卡版”为独立版本，先前普通Ultra四配色没有涵盖它。本轮补独立`xiaomi-17-ultra-byleica`，依据它自己参数页重建8.32/8.46mm版本，不能仅给普通Ultra换色。已实际查看专属overview的`984-new.png`侧框近照和`8379-new.png`滚花特写；原创单网格侧框细条凸起避开按键，0.65mm节距/0.18mm宽/0.04mm凸起与范围是估计，不宣称原厂精加工尺寸。滚花以真实几何表达，不嵌厂商纹理；徕卡铭文/红标和背部变焦环按正面范围省略。

17T和17T Pro大陆产品专属wrapper/entry均读取成功，有各自本地规格、配色正文及正面图，所以收录；没有拿国际产品页尺寸作为国行官方事实。当前小米手机导航列18 Pro/Pro Max/Fold，没有18基础版；`xiaomi-18` wrapper探测404、官方站内搜索无可确认主体，结论仅为“截至核查时当前目录未发现18标准版”，不证明历史或未来绝不存在。

新增三款完成真实Blender构建、最终各色资源合同及Khronos（5份GLB均0错误0警告）与12张四视角目视。至此Xiaomi直板14款、22套独立厚度几何、最终按色路径32份GLB、56张几何视图。新增包/条目均位于原交付位置。host命令改名为`tools/rebuild_xiaomi_library.py`，避免与Blender adapter同名导入冲突；改名后微曲15Pro也实际重建并通过合同/Khronos，未覆盖已有ZIP或运行时文件。Android逐机替图/导出仍待应用任务验证。

## Xiaomi 15 定制配色补全（资源包1.0.1）

完整性复查确认：原1.0.0只包含标准/亮银/钻石三组8色，遗漏了仍可读的[小米15定制版官方专页](https://www.mi.com/prod/xiaomi-15-custom-version)及[定制版参数页](https://www.mi.com/prod/xiaomi-15-custom-version/specs)。原小米15概述本身也明确链接这张定制页，不能因标准spec页没有列出便视为不存在。

定制专页明确20款背板色、2款高亮中框色及“铝合金PVD工艺高亮中框”；参数页逐项列出20个名字，并独立给出152.3×71.2×8.08mm、2670×1200主屏像素。数值与普通玻璃版相同是两个官方规格正文一致的结果，没有自行假定额外厚度。

20个背板名字：薰衣草紫、大地灰、奶油蓝、卡其色、糖果绿、嫩芽黄、杏仁奶白、宝蓝色、荧光红、奶油黄、孔雀绿、冰晶蓝、树莓粉、牛油果绿、胭脂红、冷杉绿、宝石红、龙胆蓝、珊瑚橙、海葵紫。每个都可配亮银色或亮黑色中框，构成40组合；“新春献礼”胭脂红属于这20名之一，当前已核正文不支持把它重复计算成第21种颜色。

本轮实际查看官方参数页3组商品图及独立侧框近照、亮银/亮黑框色小图。两框在正面外沿和侧面有明显可见差异，并且是高亮表面，因此这次补项不只是背盖颜色。40组合的数字PBR均为原创视觉估计，未复制网页文字的CSS色值当作制造商反射率，未嵌入商品图；背盖基材未从现有文字独立核实，保持未知而不擅称某类玻璃。

1.0.1保留原8配色对象及既有5份GLB字节不变，新增40个明确“背板名 · 中框色”的选项（全48）。实际重建后合同和Khronos5份均通过、0错误0警告。1.0.0原ZIP和原Release附件均不改写；1.0.1使用独立`models-2026-11-r2/xiaomi-15-1.0.1.zip`，旧pin可继续读取1.0.0。Android48配色与旧新版并存验证由应用任务增量执行，本节不抢先声称通过。

定制专页entry SHA256：overview `57e9194a0e1fae1f02628a5972d0e2c27c644c603dfa609eee5532f715332485`，specs `eb4c5af2ee9ade2a856c58c8a9787e2b83bfdc37093ec7e6d84b7f549b20ba2f`；完整URL和已查看图片哈希已加入source research/sources台账。
