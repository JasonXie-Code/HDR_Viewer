# HDR Viewer — 项目说明

## 仓库目录约定

**根目录**放置 **`README.md`**（仓库对外简介，配图见 **`docs/images/`** 与 **`docs/images/README.md`**）、**`ROADMAP.md`**、**`PROJECT.md`**（立项与路线图）。可移植工具链在 **`misc/`**，Android 应用与 Gradle 工程在 **`android/`**（含 `app` 模块、`gradlew*`、`settings.gradle.kts` 等）。**Gradle 构建输出摘录**等临时文本请放在 **`misc/build-logs/`**，勿放在仓库根目录。

## 目标

在 Android 上做一个**打开后主界面即「手机相册」体验**的应用：浏览与整理**普通照片、视频（多数为 SDR）**。核心差异化是：在全屏查看时，在系统允许范围内**尽可能抬高屏幕亮度与可读性**，效果上参考**户外太阳下**手机自动激发的「高亮/易读」体验，而不是以播放 HDR 电影片源为主。

在此基础上，提供**独立于系统亮度条侧栏**的**亮度精细调节区**，界面仍可用 **nit（cd/m²）作为刻度/目标用语**（便于和规格、主观描述对齐），但需理解其为**估算与意图**，不是对整机背光的绝对控制。

> 说明：应用无法突破硬件、热设计、省电策略与系统对「阳光激发亮度」的闭门算法；目标是 **在 app 能力内用满**（窗口亮度、常亮、将来可选的系统亮度写入等），而不是「超过系统在太阳下的最大值」。

## 产品体验：相册主界面与功能范围

进入应用后，**第一屏即相册**（时间线 / 相册集合 / 全部照片等常见结构可选），交互与信息架构应对齐用户对系统相册的预期，**至少**覆盖下列能力（随版本迭代，优先级可由 ROADMAP 调整）：

- **浏览**：照片/视频统一网格或按相册分页；缩略图流畅滚动；大图/全屏查看；双指缩放与拖拽（图片）；视频播放、暂停、进度拖动、音量与系统旋转策略。
- **来源与权限**：与系统媒体库一致的分区存储与权限模型；拒绝权限时的引导。
- **基础整理**：多选；删除（含确认与回收站/系统行为说明）；移至相册（若系统 API 支持）；重命名/详情信息（尽可能与 MediaStore 一致）。
- **分享与系统衔接**：分享到其他应用；「在相册中打开」类系统默认行为以外的入口可后置。
- **搜索与视图（可分阶段）**：按日期分组、地点/标签若可得则后续接；搜索可先简单文件名筛选再接媒体索引。
- **回收站 / 最近删除**：与 Android 版本能力对齐（无系统 API 则明确仅用应用内删除确认，不伪造系统回收站）。

**在相册体验之上**，单独提供 **「显示 / 亮度」或「户外高亮」** 入口（可从全屏查看页滑出面板或设置子页）：在查看 **普通内容** 时，把**本窗口**亮度倾向拉满，并辅以文案说明与系统自动亮度的关系。

## 问题定义

- **图片 / 视频**：以 **SDR 为主**（相册常见 JPEG/MP4），无需强制 HDR 元数据即可使用；重点是**全屏阅读亮度**。
- **亮度策略**：全屏沉浸 + 保持唤醒；查看时尽量提高 **窗口亮度系数**（`screenBrightness`）至 1；退出恢复。**阳光下的额外增益**很大程度依赖 **自动亮度 + 环境光**，本应用可在后续迭代中探索 `WRITE_SETTINGS` 写入系统亮度、或引导用户手动拉高系统条（需在隐私与商店政策下谨慎设计）。
- **与 HDR 内容的关系**：**非项目主路径**。若个别机型在 HDR 片源上更易观感到峰值，可作为次要研究，但不改变「普通内容尽量亮」的主目标。

### 以 nit 为单位的亮度参数（产品定义与工程现实）

- **产品侧**：仍以 **nit** 展示与输入，作为「多亮」的统一话术；可与用户心理白盒（「大概 800 nit 档」）对齐。
- **系统侧**：Android **没有**稳定、跨机型 API 直接设 **绝对 nit** 或完整复刻「阳光模式」。应用侧主要是 **`screenBrightness` 0–1**、可选将来对 **系统亮度** 的写入、以及提示用户 **打开自动亮度**。
- **策略**：nit 滑块 **映射到窗口系数**；是否再叠代「系统级拉满」单独开关与权限说明。

开放问题：是否提供「仅全屏媒体页提亮 / 列表页不提亮」以降低耗电与烧屏风险。

### 两段式亮度滑块（产品设想，待实现）

把全屏预览里的「亮度」做成**连续、但语义两段**的单一滑块（或带明显分界的轨道），便于用户理解：**先榨系统能给的，再在内容侧加码**。

| 区段 | 含义（对用户） | 实现方向（工程） | 注意 |
|------|----------------|------------------|------|
| **第一段** | 尽量接近 **调系统亮度条** 的效果：整体变亮、颜色相对稳定 | 优先 **`WRITE_SETTINGS`** 写 `SCREEN_BRIGHTNESS`（常配合手动模式）+ 可选保留 **`screenBrightness` 窗口系数**；需 **`Settings.canWrite` 授权**与**退出/离开时恢复**原亮度与 `SCREEN_BRIGHTNESS_MODE` | 敏感权限与 Play 说明；与「自动亮度」同时开启时行为需实测 |
| **第二段** | **HDR 观感增强**：在系统已较亮的基础上，再拉高 **高光/动态**（不是无限拧背光） | **SDR→Ultra HDR（gain map）** 或受控 **HDR 窗口 + 扩亮**，仅影响当前预览内容；与第一段**叠加**使用 | **非**整图均匀「再亮一档」；色准、耗电、**OEM 解码路径**差异大；第二段可做成「不支持则灰显」 |

**交互建议**：轨道中间断开/变色，副标题写明「系统亮度区 | HDR 增强区」；第二段启用前可要求第一段已到顶或显式提示「HDR 会改变高光表现」。

**工程注意（HDR 扩展亮度通道）**：**仅全屏预览**时由 **`ViewerBrightnessPolicyEffects`** 将窗口 **`COLOR_MODE_HDR`**；列表/设置等界面为 **`COLOR_MODE_DEFAULT`**。相册列表仍由 **`GlobalHdrHeadroomForGallery`** 在非全屏时固定 **`setDesiredHdrHeadroom(1.0)`**，避免发灰。全屏预览内 **`setDesiredHdrHeadroom(HdrGainmapHelper.HEADROOM_CAP)`**（当前 **6**，与 Gainmap API 上限一致；**不**以 `Display.getHdrCapabilities()` 的低估峰值限制局部高亮）；**不再**写 `screenBrightness`。滑块 **0～200%** 通过 **`HdrGainmapHelper.applyGainmapInPlace`** 调节 **`ratioMax`**（解码后 **单次** 拷贝为 mutable ARGB，亮度变化仅原地改 Gainmap，避免 Coil 按档位整图重载闪烁）；**视频**本阶段不叠 Gainmap；**GIF** 全屏与相册网格一致，**`BitmapFactoryDecoder` 仅首帧**，再走同一 Gainmap 路径（**不播放动画**，以换取与静态图一致的 HDR 提亮）。

**设备与滑块**：

1. **`DisplayCapabilityInfo`**：`desiredMaxLuminance` 为面板峰值 nit；**假定 SDR 全屏白** `DEFAULT_SDR_WHITE_NITS`（默认 800，产品可调）；**`maxHeadroomRatio = peak / 假定SDR白`**，裁剪至 [1, 6]；`peakToAvgRatioRaw` 仅用于诊断（API 常报 peak≈avg）。
2. **滑块映射**：`ratioMax = lerp(0.05, HEADROOM_CAP, brightnessPct/200)`，`displayRatioForFullHdr = HEADROOM_CAP`（见 `HdrGainmapHelper.ratioMaxFromBrightnessPct`）。
3. **Ultra HDR 原图**：预览滑块仍写入统一合成 Gainmap（0～200% 可调）；与「保留文件内元数据导出」可后续区分。
4. **性能**：亮度键（整型 0～200）驱动 `remember` 内原地 `setGainmap`，**不**经 Coil `Transformation` 整图缓存；**会话持久**为 `GalleryViewModel.sessionBrightnessPct`，**画面预览**为 `viewerBrightnessPct`；**亮度面板**为独立 `BrightnessSheetHost` 子树，**滑块状态**与 `snapshotFlow`+`sample(16ms)` 均在子树内，**目标**是**避免**拖动时牵动含 `HorizontalPager` 的全屏预览层每帧重组。**布局**：**勿**将 `Slider` 放在包裹整块底栏的 `verticalScroll` 内，否则与横向拖动手势冲突。**亮度面板**用 **全屏 `Box` 叠加层 + 底部 `Surface`**（与预览同窗口，**无**系统窗口对背后内容的深色遮罩）；**勿**用 `ModalBottomSheet`（纵向 sheet 手势与 `Slider` 争抢）。**重组**：**勿**在 `BrightnessSheetHost` 组合作用域内读取滑块 `Float` 值；应传递 **`MutableFloatState` 对象引用**给子组合 `BrightnessSheetContent` 并在子树内读取，且 **`onValueChange` / `onValueChangeFinished` 回调**用 `remember` 保持稳定，避免 `Slider` 手势中断（见 `ROADMAP.md` 2026-03-28 纪要）。预览读亮度放在独立子组合 `ViewerBrightnessMediaSection` 内，与 Sheet 兄弟排列。
5. **Pager** 与 **调试直方图**：仍用 **`settledPage`**、**PixelCopy** debounce（落定页、亮度稳定 1s）；debug tag **`gainmap_chain`**（原 `tone_chain`）。

**旧版 AGSL + `RenderEffect` 已移除**：`graphicsLayer` 离屏易把 >1.0 截断在 SDR；现以 **Gainmap + 设备 headroom** 为唯一静态图 HDR 路径。

## 非目标（初期）

- 专业级校色与完整色彩管理工作流（可列为后期）。
- iOS 或其他平台。
- 云端相册同步（除非后续另行立项）。
- **不以**「必须播放 HDR10/Dolby 片源」作为产品交付前提。

## 技术约束与事实

1. **阳光高亮**常由 **ALS + 自动亮度 + OEM 电源策略** 共同决定；应用层多数只能 **辅助**，不能直接访问「阳光 nit 曲线」。
2. **`screenBrightness = 1f`** 是应用能直接控制的 **SDR 窗口亮度** 上限之一，与「系统亮度条拉满 + 户外」的体感可能仍有差距。
3. **长时间最高亮度**增加耗电与 OLED 风险，需超时恢复、设置说明。
4. **HDR / Ultra HDR 官方 API**（如 `COLOR_MODE_HDR`、SurfaceView 直通）对 **SDR 相册主场景**不是必需；此前若启用 HDR 窗口模式，对纯 SDR 图还可能产生不必要 tone 行为，故**当前以 SDR 提亮路径为主**。
5. 可选后续：**Media3 + SurfaceView** 播放视频并叠加上述亮度策略，仍以 SDR 内容为主。

### 亮度「极限」分层（调研摘要：应用能 push 到哪里）

屏幕上 **硬件瞬时能达到的最高 nit**（含阳光激发、HDR 峰值等）由 **面板 + 驱动 + 电源与热管理** 决定，**没有**对普通应用的「一键绝对极限」开放 API。下面是**常见可编程手段**与天花板关系（与公开文档、AOSP 行为及社区实践一致）：

| 手段 | 作用范围 | 权限 | 与「太阳下特别亮」的关系 |
|------|----------|------|--------------------------|
| **`WindowManager.LayoutParams.screenBrightness`（0～1）** | 通常仅 **当前 Activity 窗口**；`-1`（`BRIGHTNESS_OVERRIDE_NONE`）表示不覆盖 | 不需要 | 不等价于系统「激发亮度」。室内且用户把系统条调得很低时，**即使 1f 也可能仍偏暗**。 |
| **`Settings.System.SCREEN_BRIGHTNESS`（常写 0～255）** | **整机手动亮度条档位**；常配合 `SCREEN_BRIGHTNESS_MODE_MANUAL` | 需 **`WRITE_SETTINGS`**；Android 6+ 还要 **`Settings.canWrite` + 引导用户到「允许修改系统设置」** | 一般可达到 **手动模式下的满条**，但仍 **不一定** 等于 **强光下自动亮度算法** 给出的临时高亮（HBM/激发策略多在 `AutomaticBrightnessController` 等系统服务内，**无稳定公开 API**）。 |
| **自动亮度 + 高环境光（户外）** | 系统根据 ALS 调参 | 应用顶多 **引导用户开自动亮度**，不能替系统读 lux 调曲线 | 最接近用户感知的 **「太阳模式」**，但依赖用户设置与环境。 |

**若目标是在应用内把「非激发路径」尽量用满**：可 **叠加**「窗口 `screenBrightness=1f`」+「可选请求 `WRITE_SETTINGS` 后写 `255` + 手动模式」，并 **文案提示**打开自动亮度、户外对比；**退出预览或退出应用时恢复**用户原亮度/模式，减少差评与 policy 风险。

**Google Play / 体验**：`WRITE_SETTINGS` 属高敏感能力，须在应用内明确说明用途与可逆操作；过度提亮注意发热与 OLED 烧屏说明。

官方参考：[Settings.System](https://developer.android.com/reference/android/provider/Settings.System)、[LayoutParams.screenBrightness](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#screenBrightness)。

## 建议技术栈（可在 ROADMAP 中随验证调整）


| 领域      | 建议                                                  |
| ------- | --------------------------------------------------- |
| 语言 / UI | Kotlin + Jetpack Compose；主题在 **API 31+** 使用 **Material You 动态取色**，否则固定深浅色 |
| 最低版本    | **API 35（Android 15）及以上**；旧系统不再作为支持目标 |
| 媒体索引    | MediaStore、Photo Picker（Android 13+）                |
| 视频播放    | Media3 ExoPlayer；全屏亮度与 `FLAG_KEEP_SCREEN_ON` 与图片路径一致 |
| 图片      | Coil 等；以 SDR 解码为主                              |
| 权限      | `READ_MEDIA_`*（分区存储时代）；若做系统亮度写入再评估 `WRITE_SETTINGS`            |


## 合规与体验

- 遵守 **Google Play 政策**与分区存储要求；权限最小化。
- **亮度与闪烁**：快速从暗到极亮可能引起不适，可提供温和动画与设置项。
- **烧屏 / OLED**：长时间最高亮度固定 UI 有风险，全屏媒体场景相对可接受，但仍建议超时降亮度或提示。

## 便携性与自包含约定

本仓库按 **便携项目** 维护：复制整份目录到新电脑后，**构建、脚本、命令行工具**应尽量只依赖 `misc/` 内自带内容，**不假设**系统已安装全局 JDK、Android SDK、Python 等。

- **脚本与自动化**：调用解释器与工具时使用**仓库内路径**（或先定位仓库根再拼接），例如 `%HDRV_ROOT%\misc\tools\python\python.exe`、PowerShell 中 `Join-Path $RepoRoot 'misc\tools\python\python.exe'`。避免文档或脚本写死 `C:\`、`P:\` 等他机盘符。
- **Gradle / Android**：命令行构建在 **`android/`** 目录执行 `gradlew*`（Wrapper 与该模块同源）；`misc/gradlew*` 仅作**备份/初始拷贝**，可与 `android/gradle` 同步。仓库内 `android/gradle.properties` **不提交**本机私有机盘符下的 `org.gradle.java.home`；本地构建请设置 **`JAVA_HOME`**（指向 JDK 17）或先执行 `misc\env_portable.cmd` / `misc\env_portable.ps1` 注入环境变量；若仅在单机上固定 JDK 路径，可在本地取消注释并填写 `org.gradle.java.home`，**勿将含 `P:\` 等路径提交至公共远程**。
- **Android Studio**：IDE 仍可能使用自带 JBR；**命令线**与 **CI** 以 `misc` 工具链为准，避免「本机能编、别人不能编」。
- **可选**：将 `GRADLE_USER_HOME` 指到仓库内目录（见 `misc/env_portable.cmd` 内注释）可进一步固化缓存位置，代价是仓库体积变大。

**一键注入环境变量**：`call misc\env_portable.cmd`（cmd）或 **点源** `misc/env_portable.ps1`（PowerShell 用 `. \misc\env_portable.ps1`）。

## 本地构建工具链（misc）

自 `G:\PonyChat` 拷贝至本仓库 `misc/`，用于离线或便携开发：以 **Android 构建**为主，并附带 **嵌入式 Python**（构建脚本、工具链辅助、与 PonyChat 一致的可移植环境）。

| 路径 | 说明 |
|------|------|
| `misc/tools/python` | 嵌入式 Python（解释器与 `Lib`、`Scripts` 等；主程序为 Kotlin/Android 时仍可用于脚本与自动化） |
| `misc/tools/jdk-17` | JDK 17 |
| `misc/tools/android-sdk` | Android SDK（已验证含 `platform-tools/adb`） |
| `misc/local-repo` | 本地 Maven 仓库镜像（体积小；由 `android/settings.gradle.kts` 以 `../misc/local-repo` 引用） |
| `misc/tools/dependencies` | 预置 AAR（可按需 `flatDir` 或手工参考） |
| `misc/gradle/wrapper`、`misc/gradlew`、`misc/gradlew.bat` | Gradle Wrapper **备份**（与 `android/` 内 Wrapper 保持一致时可从此复制） |
| `misc/env_portable.cmd`、`misc/env_portable.ps1` | 设置 `JAVA_HOME`、`ANDROID_*`、`PATH`；并输出 **`HDRV_ANDROID`**（即 `%HDRV_ROOT%\android`） |

`android/local.properties` 中 **`sdk.dir`** 指向 `../misc/tools/android-sdk`（相对 `android/` 目录）。优先通过 `misc/env_portable.*` 设置 `JAVA_HOME`、`ANDROID_SDK_ROOT` / `ANDROID_HOME`；勿手写其他电脑盘符。若必须在 `android/gradle.properties` 中写 `org.gradle.java.home`，须为**当前机器**绝对路径。

## Android 应用工程（当前实现）

- **Gradle 工程根**：**`android/`**（`settings.gradle.kts`、`app/`、`gradle/`、`gradlew*`）。使用 Android Studio 时请 **Open** 该 **`android`** 目录，而非仓库根（根目录无工程文件）。
- **包名**：`com.hdrviewer.app`；**minSdk 35（Android 15）** / **targetSdk 35**（与 `compileSdk` 对齐，见 `android/app/build.gradle.kts`）。**Launcher 图标**：源稿 **`misc/logo_concepts/logo/new_logo_concept_sun_frame.png`**，自适应图标前景为各密度 **`res/drawable-*/ic_launcher_foreground.png`**，背景色 **`ic_launcher_background`** `#1D2534`（与图稿深蓝对齐）。
- **命令行构建**：`call misc\env_portable.cmd`，然后 `cd /d %HDRV_ANDROID%`（或未设置变量时 `cd /d <仓库根>\android`），执行 `gradlew assembleDebug`；Debug APK 为 **`android/app/build/outputs/apk/debug/app-debug.apk`**。`settings.gradle.kts` 依赖解析优先 **Maven Central** 并含 **阿里云** 公共镜像，减轻仅发布在 Central 的库误走 `dl.google.com` 导致超时。
- **图形菜单（推荐调试）**：**`AAA一键绿色部署.bat`** → **`misc/green_deploy.py`**（便携环境）→ **`misc/AAA安装调试App.py`**（adb、`android/local.properties` 与 `gradle.properties`、编译安装、日志等）；已对齐 **`android/`** 与 **`com.hdrviewer.app`**。
- **ADB 亮度 / 环境光监控**：**`misc/brightness_monitor.py`** 循环采集 `settings` 系统亮度、`dumpsys display`（含 HBM 等）、**`dumpsys sensorservice`** 中**全部**环境光类传感器（**`env_als_json`** / CSV 列 **`env_als_summary`**；**`env_lux`** 为**融合主路**，排除 Raw/Strm 误选），并以 **`env_lux_display`** 对照 `mAmbientLux`；以及 **`adb logcat`** 中 tag **`HDRV_Brightness`** 的 **`session_pct`**，及 tag **`HDRV_Histogram`** 的 **`histogram_json`**（全屏预览区 **`PixelCopy`** 合成后 luma 直方图，**debug APK**；**`v`: 2**、**`note`**: `display_window_pixelcopy`；可选 **`brightness_pct`** 与滑块有效值对齐；控制台 JSON 中解析为 **`preview_histogram`**）。控制台默认**缩进换行 JSON**（含 **`als_sensors`** 数组），**`--json-compact`** 为单行 JSON Lines；**`--log`** 仍为 CSV。每次运行默认在 **`misc/logs/`** 新建 **`brightness_monitor_<时间戳>_<pid>_<内容名>.log`** 并**实时追加**（**`--no-session-log`** 关闭；**`--session-name`** 改内容名）。可用 **`--no-logcat`** 关闭 logcat。
- **已实现（相册 + 高亮预览）**：
  - **主导航**：底部 **照片 / 相册 / 设置**（`Navigation Compose`）；**三 Tab 之间切换无页面过渡动画**（`MainNavHost` 对 `photos`/`albums`/`settings` 互跳使用 `EnterTransition.None` 等），进入全屏预览、单相册内页等仍为横向滑动 + 淡入淡出。**照片** 为时间线网格（全部媒体）；**相册** 为封面 + 数量卡片网格，点入 **单相册** 全屏路由（无底部栏）；**设置** 为独立页（DataStore 持久化：默认预览亮度、饱和度增强、常亮、默认排序、网格列宽；**「全屏预览亮度」**说明含 Gainmap 文案与屏幕能力摘要；关于含版本号）。
  - 主界面 **照片与视频网格**（`MediaStore` 合并查询，排序：最新 / 最旧 / 名称）；**搜索**（文件名包含）；**类型筛选** 为顶栏下 **FilterChip** 横排（全部 / 仅照片 / 仅视频 / 仅动图）；**按修改日分组标题**（最新/最旧排序时；名称排序为平铺）；**自适应列数**网格（列宽下限可配置）+ **圆角缩略图**；**多选**带轻触觉反馈（长按进入或点「多选」）、**分享**（单选/多选）、**删除**（确认后走 **`MediaStore.createDeleteRequest`** 系统界面，因分区存储下直删常失败）；顶栏 **从系统选取媒体**（`PickVisualMedia`，选取后刷新列表）；多选仅一项时可看 **详情**；顶栏 **排序** 为独立下拉（不再与相册列表混在溢出菜单）。
  - 全屏 **横向滑动**切换媒体；**沉浸式**（隐藏系统栏，边缘滑动可暂时唤出）；**系统返回键 / 预测性返回**在预览页由 `BackHandler` 关闭预览回到相册（而非直接退出应用）；图片 **Coil + 双指捏合缩放**：自定义 Transform 检测**默认不 consume**，仅在 **已放大 / 多指 / 捏合缩放** 时手动 consume，以便 **1× 单指横滑**交给分页；**单指双击**复位 100% 缩放与位移；放大时暂时关闭横向滑动以便拖拽（与 settled 页联动）；**视频** **Media3 ExoPlayer + PlayerView 系统控制器**（仅**当前停留页**播放，离开页/进后台暂停）；**GIF**（`image/gif`）相册网格缩略图为 **首帧**。全屏预览：**先** `BitmapFactoryDecoder` 首帧 + **CircularProgressIndicator** 遮罩，**后台** `decodeGifFramesForHdr`（`AnimatedImageDrawable` 反射逐帧，失败则 `Movie` 时间片采样）；每帧 **Gainmap** 后内存列表 **`GifHdrAnimatedImage`** 循环播放；**滑块** 变更时对 **全部帧** 原地更新 Gainmap。
  - **亮度**：全屏 **右下角 FAB** 打开 **底部面板**（**全屏叠加层，无 dim 遮罩**）；面板仅 **大号百分比** 与 **0～200% 滑块**；详细说明与设备能力在 **设置 → 全屏预览亮度**。进入全屏时**默认预览亮度**以 **DataStore** 中设置为准（`sessionBrightnessPct` 由 `onViewerEnter()` 同步）；**会话内**跨图片保持；**高亮段饱和度增强** 在 **设置页** 开关。顶栏保留 **分享 / 详情 / 删除**。
  - **预览亮度（0～200%，Gainmap）**：**仅全屏预览**时 **`COLOR_MODE_HDR`**（见 **`ViewerBrightnessPolicyEffects`**）；全屏 **`setDesiredHdrHeadroom(HEADROOM_CAP)`** + 静态图 / GIF 首帧 **合成 Gainmap**；**不**写 `screenBrightness`；离开预览 **恢复 headroom**（`HdrBoostEffect` dispose）；**`FLAG_KEEP_SCREEN_ON`** 可由设置页关闭（`DualSegmentBrightnessEffects`）。
  - **运行时权限**：`READ_MEDIA_IMAGES` + **`READ_MEDIA_VIDEO`**（minSdk 35，不再声明分区存储时代旧权限）。**`WRITE_SETTINGS`**：仅在 **全屏查看媒体** 且已授权时，通过 **`ViewerBrightnessPolicyEffects`** 将 **系统亮度条** 临时拉至 **255**；恢复快照由 **`SystemBrightnessSession`**（内部 **`SystemBrightnessSnapshot`**）在 **Compose 生命周期**（`ON_PAUSE` / 离开全屏 `DisposableEffect`）与 **`MainActivity.onPause`** 中 **`restoreIfBoosted`** 兜底，避免部分机型退出应用时漏恢复；**不在** `MainActivity` 前台全局拉满。设置页展示 **`Settings.System.canWrite`** 状态与跳转 **`Settings.ACTION_MANAGE_WRITE_SETTINGS`**。**首次进入**（媒体读权限已授予后）若尚未 `canWrite`，`HdrViewerApp` 仍可弹出一次性说明；已授权或用户点「稍后」等后写入 `SharedPreferences` 标记，不再重复弹窗。
  - **顶栏**：照片网格 **搜索、排序、从系统选取、⋮ 更多**（更多内仅 **多选**）；类型筛选已移至 **FilterChip** 行。
  - **安全区**：`enableEdgeToEdge` + **`MainActivity`** 全局 **`WindowInsetsController` 隐藏系统状态栏与底部系统导航/手势条**（边缘滑动可短暂唤出）；**应用内** 仍保留 **Material 底栏**（照片/相册/设置）与各页 **TopAppBar**。**权限页** / **`GalleryScreen`** 等使用 **`WindowInsets.safeDrawing`**（`Scaffold` 的 `contentWindowInsets` 或 **`windowInsetsPadding`**）；**全屏预览** 媒体层 **全屏铺满**，**顶栏为叠加层**，各页 **TopAppBar** 统一 **`appTopBarWindowInsets()`**（`safeDrawing` 仅顶边与左右，避免沉浸式下仅 `statusBars` 为 0 闯入刘海，并与预览顶栏对齐），亮度 FAB **`navigationBars`** 内边距。**当前预览** `storeKey` 由 **`rememberSaveable`** 保存，**横竖屏切换** 后仍停留在同一媒体。
  - **全屏预览**：**单击** 图片/视频区域可 **隐藏或显示** 顶栏与亮度 FAB；**缩放、平移、换页** 不会自动恢复；**再次单击** 才恢复。设置中开启 **高亮段饱和度增强**（约 **100%→1.0、200%→ColorMatrix 1.14** 线性），仅 **静态图 / 单帧 GIF 预览**；**视频** 与 **多帧动图** 不叠（动图仍仅 Gainmap）。
- **尚未实现**：移至相册/重命名、系统回收站一致；**仅列表页 vs 全屏** 提亮策略与功耗说明可继续细化；Ultra HDR **展示分流**已做（gain map 检测 + 窗口色域），**离线转码 / 生成** gain map 仍非目标（见 ROADMAP）。

### 业界公开路径与本品取舍（检索摘要）

- **带 gain map 的 JPEG（Ultra HDR）**：Google 文档要求展示时在适当时机 **`setColorMode(COLOR_MODE_HDR)`**，并依赖系统对 gain map 的合成；**Glow HDR** 等商用工具侧重 **离线转成 Ultra HDR 文件**，与本应用「本地预览提亮」相邻但不等同。
- **纯 SDR 静态图**：窗口 **启动即 `COLOR_MODE_HDR`**；全屏静态图 **合成 Gainmap** + **`setDesiredHdrHeadroom`（设备上限）**，由系统 Canvas 合成扩展亮度；**非** AGSL `RenderEffect` 路径。
- **系统能力**：Android **`android.graphics.Gainmap`** 与 **`setDesiredHdrHeadroom`** 为相册实时预览主路径；Media3 视频后续可接 **VideoEffect** 等（见 ROADMAP）。

## 文档与仓库约定

- **ROADMAP.md**：阶段目标、可交付物、风险与开放问题。
- 架构、显示链路或亮度策略有重大变更时，同步更新本文件与 ROADMAP。

## 开放问题（需实机验证）

- 各机型在 **仅窗口亮度 1f** 与 **自动亮度开/关** 下的主观对比（室内/户外）。
- 是否、`WRITE_SETTINGS` 写入系统亮度的收益与政策风险。
- **nit 映射**：窗口系数与用户主观「阳光可读」的标定。
- **相册功能边界**：与系统「照片」应用逐条对比的验收清单（分享、垃圾桶、相册 CRUD 等到哪一版对齐）。
