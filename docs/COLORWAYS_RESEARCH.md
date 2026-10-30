# 官方配色与原创PBR分区研究

2026-10-30重新实读两款机型官网及其公开产品专属CDN正文。网页导航标题不充当正文依据；CDN条目通过现有官方wrapper引用链定位。研究仅新增本文和各设备`colorways.json`，没有修改GLB、生成器、运行时、截图材质或环境。

正式配色名称、正文明确的材料家族为official。以下RGB与metallic/roughness全部为原创视觉估算：参考图有棚灯、渐变、高光、色彩处理，不是校色卡、原厂色号、BRDF测量或CAD。sRGB8只描述建议输入色；传入Filament linear色参数前应恰好做一次sRGB→linear转换。尤其不能把背板色整体乘到前框或截图上。

## REDMI K90 Pro Max

[官网](https://www.mi.com/prod/redmi-k90-pro-max)的[规格专属正文](https://mishop-pc.cnbj1.mi-fds.com/mishop-pc/production/mishop_pc/redmi-k90-pro-max/specs/entry-1778678001254.js)明确列出四款；[概览专属正文](https://mishop-pc.cnbj1.mi-fds.com/mishop-pc/production/mishop_pc/redmi-k90-pro-max/entry-1778678001254.js)FAQ确认冠军版白色、6系铝中框及玻纤/Tech-Denim背板家族，且冠军版后摄设计、背部图案不同。旧research中excludedVariants仅表示当时默认流金白模型的范围，不代表配色不存在。

实际下载并查看[官方四款同图](https://cdn.cnbj1.fds.api.mi-img.com/product-images/new-product-images/redmi-k90-pro-max_55474/images/3074.png)：从左依次黑、流金白、丹宁、冠军白。丹宁蓝背配银灰框；冠军白背配深色Deco及亮银/深灰反射边，不能凭背板蓝色捏造蓝框。小圆图1809/1818/1822/1813仅是配色swatch，不误称完整正面图。缓存位于`.cache/references/colorways/`，不进APK。

|稳定id|官网正式名|框体sRGB8估计|背板sRGB8估计|材料与几何差异|
|---|---|---|---|---|
|flowing-gold-white|流金白|222,223,220|229,226,216|标准玻纤背；7.9mm/218g|
|black|黑色|63,66,69|39,41,44|标准玻纤背；7.9mm/218g|
|denim|丹宁色|184,190,198|64,85,118|Tech-Denim背；8.3mm/220g；纯色PBR不等于织纹复刻|
|champion-white|K90 Pro Max 冠军版|156,158,158|226,226,219|FAQ描述白色；特殊后摄/图纹不加入当前前向套壳；精确背板基材和该版本独立厚度未在正文单独给出|

标准厚度段只明确黑色/流金白及丹宁差异，因此不把冠军版7.9mm写成独立核实值。若运行时只做前向配色，可以复用已有前屏轮廓并明确后壳简化；丹宁不应默默标成原厂7.9mm。全root Zscale8.3/7.9也会缩放屏幕安全间距/镜头凸起，优先显式机身深度描述或受控的body/rear适配；本任务未实施任何缩放。

当前实际GLB分区（从包内材质表实读）：

|区域|精确材质名|建议|
|---|---|---|
|框体/按键/相关铝件|k90_aircraft_aluminum|每色frame；metallic.95、roughness.21|
|相机平台|k90_sculpted_aluminum_deco|独立deco色；metallic.90、roughness.26；冠军版黑平台不染白|
|简化背盖|k90_warm_white_glass_fiber|每色back；metallic0；玻纤roughness.42、丹宁.52（纹理仍简化）|

`screen_content`、`k90_bezel`、`k90_optical_black`、`k90_optical_coating`、`k90_flash_diffuser`、`k90_woofer_brushed_mesh`全部保持独立，不随整机颜色覆盖。冠军版商标、盾牌、Y纹、壁纸均不复制，不增复杂背部资源。

## Xiaomi 18 Fold

[官网](https://www.mi.com/prod/xiaomi-18-fold)的[规格专属正文](https://mishop-pc.cnbj1.mi-fds.com/mishop-pc/production/mishop_pc/xiaomi-18-fold/specs/entry-1792032063000.js)列出陶瓷特别版、暖金白、黑色、西野红；[概览专属正文](https://mishop-pc.cnbj1.mi-fds.com/mishop-pc/production/mishop_pc/xiaomi-18-fold/entry-1792032063000.js)确认前三普通款龙晶玻璃3.0背板，特别版氮化硅陶瓷。特别版视觉为淡粉，官方描述灵感来自淡粉釉；选择器正式名称保留「陶瓷特别版」，不自行改成「粉色」。7系铝框材料与现有已核实研究一致。

本轮实际逐张查看官方规格图[陶瓷1311](https://cdn.cnbj1.fds.api.mi-img.com/product-images/new-product-images/xiaomi-18-fold_37057/images/1311.png)、[暖金白1310](https://cdn.cnbj1.fds.api.mi-img.com/product-images/new-product-images/xiaomi-18-fold_37057/images/1310.png)、[黑色1312](https://cdn.cnbj1.fds.api.mi-img.com/product-images/new-product-images/xiaomi-18-fold_37057/images/1312.png)、[西野红1313](https://cdn.cnbj1.fds.api.mi-img.com/product-images/new-product-images/xiaomi-18-fold_37057/images/1313.png)。图有背部反射渐变，框色与背色分别估计；不把背板最暗处当固有色。官方原图只在.cache。

|稳定id|官网正式名|框体sRGB8估计|背板sRGB8估计|材料差异|
|---|---|---|---|---|
|warm-gold-white|暖金白|221,219,210|237,232,222|暖银铝框；龙晶玻璃3.0背|
|black|黑色|64,65,67|32,33,34|枪灰铝框；龙晶玻璃3.0背|
|xiye-red|西野红|170,29,46|118,21,41|红色框与深红背独立；龙晶玻璃3.0背|
|ceramic-special|陶瓷特别版|215,182,175|232,203,196|暖玫瑰框；氮化硅陶瓷背；非普通玻璃|

折/开10.68/5.02mm是官方产品通用尺寸段；没有取得每色独立CAD差值，不声称陶瓷版后板厚度已单独校验。当前前向套壳继续保留简化背部遮挡体，不增镜头/Deco/厂商图片。

|区域|当前实际GLB精确材质名|建议|
|---|---|---|
|侧框/铰链/铝件|x18_7series_warm_silver|每色frame，metallic.95、roughness.21|
|真实正面铝face|x18_front_satin_aluminum|独立frontAluminum（此轮建议同色frame），metallic.95、roughness.38，受光satin而非unlit银线|
|简化背板|x18_warm_gold_white_AG_glass|每色back，metallic0；普通玻璃roughness.37、陶瓷.24（均估计）|

`x18_optical_black`与`screen_content`不可跟随配色染色。0.85mm黑mask是上一轮官方图视觉校准估算，绝非官方毫米标注；本轮不改其几何、225µm渲染间距或屏幕UV。

## 交付及边界

两份`assets-source/devices/{id}/colorways.json`含稳定id、中文正式名、分区sRGB8/PBR估计、几何差异提示、精确材质映射、原始官方URL、fresh正文缓存SHA256和时间。已核对JSON引用材质名均存在当前包内GLB；官方颜色名称和八款计数直接对应正文。当前任务只交研究数据，不声称Android配色功能已实现或真机颜色已通过。
