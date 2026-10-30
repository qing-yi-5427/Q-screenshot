# 现成手机截图软件源代码研究

2026-10-30。通过公开 GitHub API 固定 commit 后读取 raw 文本；原文、tree、哈希在 `.cache/research/mockup20261030/` 与 `deliverables/xiaomi18-refresh/research/opensource-sources.json`。未下载模型、壁纸，未运行第三方代码，没有复制实现。

|项目 / 固定 commit|实际读取与结论|与 QingFrame 的关系、采用边界|
|---|---|---|
|[quentin-pla/phone-mockup-studio](https://github.com/quentin-pla/phone-mockup-studio/tree/9908f2c321787779b6e028cba1ab67cd29a6a6dd)|[index.html#L1695](https://github.com/quentin-pla/phone-mockup-studio/blob/9908f2c321787779b6e028cba1ab67cd29a6a6dd/index.html#L1695) 先按每模型轴向旋转，再 Box3 居中并按高度归一；1763/1810 屏幕材质禁 tone mapping，1973 上传图指定 sRGB。UV repeat/offset 也是每 GLB 校准，并非通用手机值。|QingFrame 已有米制模型、专属 screen mapping、unlit 屏幕与 sRGB 上传。保留真实尺寸和八角点相机 fit，不采用归一化模型或复制其 iPhone UV 参数。|
|[Girskorr/screenshot-studio](https://github.com/Girskorr/screenshot-studio/tree/74891e1e46e35c0b5d15a92dc18b5f0be23978f3)|[phone3d.ts#L149](https://github.com/Girskorr/screenshot-studio/blob/74891e1e46e35c0b5d15a92dc18b5f0be23978f3/screenshot-studio/src/render/phone3d.ts#L149) 黑底加纯 emissive 截图，clearcoat 叠玻璃；254–268 依目标像素高度算透视距离和纵向位置。export.ts 使用 PNG Blob 并释放 object URL。|支持“截图颜色与机壳灯光分离”的思路，本项目已有 unlit。其 69 行是 PerspectiveCamera，不是正交案例。其手绘 shadow blob 不采用：当前保持真实投影。其通用程序模型也不替代专属18Fold。|
|[ihou/PhoneMockup](https://github.com/ihou/PhoneMockup/tree/386ce863e29576b1585babaf9d823506b33ad758)|[Scene3D.tsx#L12](https://github.com/ihou/PhoneMockup/blob/386ce863e29576b1585babaf9d823506b33ad758/src/components/tools/iphone-mockup/components/Scene3D.tsx#L12) front 归零 Euler，左右为正负15°；770 行静止 demand、自动旋转 always。useScreenTexture.ts 明确 sRGB 与 flipY。|QingFrame 已按需提交 Choreographer 帧并在后台暂停；无需改成网页渲染架构。front 仅旋转归零，不能据此声称它用了正交。flipY 不能直接照搬：本项目 UV/readback 已有实图方向测试。纹理 hook 清理捕获 React 旧 texture 值，不照搬其生命周期写法。|

## 两个可实施收益

1. 增强已有“正面”为清晰套壳预设：正交、姿态归零、zoom=1/pan=0、反射=0，同时保持截图、画布和其余灯光；左右预设切回透视。这是从前向预设与独立屏幕着色得到的原创组合，三个仓库并未证明这个完整行为。父代理已实现并加纯函数回归，仍以本项目实际 UI 和原色样张验收。
2. 将“模型轴向→截图 UV→画布 fit”分成各自固定合同，对18Fold折叠/展开/竖向分别锁定方向与边界样张。现架构大部分已具备，采用独立 fixture 与迁移/屏幕验收补齐，不为了参考重写 renderer。避免只按整体高度 fit 在横向展开时裁切；保留当前八角点算法。

## 代码与资产许可分别核验

- Quentin 的 LICENSE 为 MIT（2026 Quentin PLA）。CREDITS.md 明确三项 iPhone GLB 是第三方 CC BY 4.0、非 MIT、非 Apple 官方资产；tree 中 Samsung GLB 没在该表列出，不能推定其许可。这些资产均未下载或采用。
- Girskorr 的 LICENSE 为 MIT（2026 Girskorr）；所读模型为程序几何，tree 未见 GLB/glTF。项目代码许可不自动证明所有第三方依赖、图像素材均可再分发；没有引入素材。
- ihou README 81–83 行宣称 MIT，但固定 tree 中缺少 LICENSE 文件，不能把这当作已核实完整授权文本。未采用其代码或资产。

研究是实际源码比较，不是身份/尺寸证据；18Fold身份仍以独立官方证据与本项目原创资源记录为准。
