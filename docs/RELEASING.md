# APK 自动发布

工作流：`.github/workflows/android-release.yml`。只有 `push` 的 `v*` tag 进入发布，且必须严格符合 `vMAJOR.MINOR.PATCH`；不接受前导零、预发布后缀。major0..2099，minor/patch0..999，拒绝0.0.0。版本码是 `major*1000000+minor*1000+patch`，最大2099999999；同tag重跑不变。工作流通过成对 Gradle 属性 `ciVersionName` / `ciVersionCode` 注入，普通本地版本不变。

手动 `workflow_dispatch` 输入版本仅构建 Artifact，不创建或修改Release。构建产物固定为 `Q-screenshot.apk`、`SHA256SUMS.txt`。tag须指向默认分支已合并、包含当前workflow的提交，避免GitHub对另分支workflow变更的发布权限限制。

构建使用 JDK21、Ubuntu24.04预装Android SDK37.0与BuildTools36.0.0，保持项目`minorApiLevel=0`。`sdkmanager`只确保这两个包存在，不自动接受新许可证；镜像包/许可证变化时应由维护者审查后处理。

仓库 Secret `QSCREENSHOT_DEBUG_KEYSTORE_BASE64` 保存既有Debug keystore的base64。仅在临时Runner恢复到标准debug keystore位置，按原alias `androiddebugkey` 与debug密码使用；构建前后都校验SHA256证书指纹：

`693ccbb031d4296c9edcf08c1f916e201ad1fd7757b8dc62a87b0d1ca92d3ea3`

指纹是公开证书摘要，不是私钥。缺Secret或不匹配会失败。不要新建随机key替代，避免用户无法覆盖升级；勿提交keystore、base64或日志中的密钥。临时key总会清理，Artifact仅包含APK与校验文件。当前仍是Debug签名测试版。

发布时先以`--latest=false`创建，再幂等覆盖同tag两个附件；列出所有非draft/非prerelease版本，按数字语义版本比较，只有最高版本才能被标记latest。旧tag重跑只更新自己的附件。全局串行加`queue:max`避免同tag及跨tag的latest竞态，不取消运行中的任务（队列最多100）。只有发布job获得`contents:write`，构建job只读；Actions按官方tag解析为完整commit SHA固定。

官方依据（2026-10-31核验）：

- [Runner的SDK包列表](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md#android)：android-37.0、build-tools36.0.0。
- [GitHub concurrency / queue:max](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)。
- [完整SHA固定Actions](https://docs.github.com/en/actions/reference/security/secure-use#using-third-party-actions)。
- [Release create / latest=false](https://cli.github.com/manual/gh_release_create)、[幂等附件clobber](https://cli.github.com/manual/gh_release_upload)。
- [GitHub Release API权限与latest语义](https://docs.github.com/en/rest/releases/releases)。
- [Gradle9.8.0官方校验值](https://services.gradle.org/distributions/gradle-9.8.0-bin.zip.sha256)。

本地检查：`python3 -m unittest discover -s tools -p test_ci_version.py`，覆盖边界、字段溢出、数值排序、旧tag重跑与分页发布列表。真实GitHub Runner执行结果以Actions为准，本地校验不能代替CI运行。
