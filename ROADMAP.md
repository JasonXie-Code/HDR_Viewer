# HDR Viewer — 路线图

本文档描述从 0 到可发布版本的阶段计划。顺序可按人力并行调整，但**建议先完成「验证阶段」再大面积做 UI**。

**仓库布局**：根目录仅有 `ROADMAP.md`、`PROJECT.md`；源码与 Gradle 在 **`android/`**，工具链在 **`misc/`**（见 `PROJECT.md`）。

## 产品边界（定位）

本应用按 **本地个人相册 + 户外高亮查看器** 设计：**不要求、不实现** 账号登录、云相册、跨设备同步与备份；路线图与下文的「差距」均指 **本机 MediaStore / 系统权限范围内**的体验，与 Google 账号、厂商云服务等**无关**。

---

## 阶段 0：立项与环境（第 1 周量级）

**目标**：能跑 Hello World，并有一份「**实机高亮 / 户外可读**验证清单（非必须 HDR 片源）」；**命令行构建路径**符合 `PROJECT.md` 便携约定。

- [x] 创建 Android Studio 工程（Kotlin、单 Activity / Compose）。
- [x] 定 **minSdk / targetSdk**：**minSdk 35（Android 15）**；target 与 compile 对齐 35。
- [x] **便携构建**：在 **`android/`** 下使用仓库内 Wrapper / JDK / SDK；`android/local.properties` 的 `sdk.dir` 指向 `../misc/tools/android-sdk`；新增脚本通过 `misc/tools/...` 调用，不写死盘符（见 `misc/env_portable.cmd` / `env_portable.ps1`，其中 `HDRV_ANDROID`=仓库根下的 `android`）；`settings.gradle.kts` 已优先 **Maven Central** 并含阿里云镜像，减轻 `coil` 等依赖误走 Google 源超时。
- [ ] 整理 **测试机矩阵**（品牌、Android 版本、是否官方标称 HDR 屏）。
- [ ] 在 `PROJECT.md` 开放问题中记录首批机型的验证结论（可简短列表）。

**交付物**：可安装 APK + 测试机表格草案。

---

## 阶段 1：相册主界面与系统相册级能力 — MVP（约 2–4 周）

**目标**：启动后**主界面即相册**（非独立「选择文件」工具页）；具备普通手机相册的**核心路径**，可日常替换系统相册完成「看、滑、放大、播视频、删、分享」等高频操作；全屏 **SDR 尽量亮 / nit 映射**可先占位或与系统默认并行。

- [x] **主信息架构**：**按修改日分组标题**（最新/最旧排序时）+ **自适应列数网格** + **类型筛选**（全部 / 仅照片 / 仅视频 / 仅动图）；「按相册文件夹」仍为相册下拉切换。
- [x] MediaStore 查询图片与视频（mime、分区存储、`READ_MEDIA_*`）。
- [x] **Photo Picker**：顶栏「从系统选取媒体」入口（`PickVisualMedia`），选取后刷新索引。
- [ ] **缩略图与性能**：**分页或窗口化加载**（当前仍为一次合并加载全表；大图 subsampling 见阶段 3）。
- [x] **全屏查看**：图片缩放平移；视频 **Media3 + 系统控制器**；**沉浸式**（隐藏系统栏，边缘滑动可暂时唤出）；返回手势与 `BackHandler` 仍可用。
- [x] **多选 + 删除**（确认对话框）+ **分享**（`ACTION_SEND` 多选）。
- [x] **媒体详情**：分辨率、拍摄日期、路径或 URI（脱敏展示）、文件名。
- [ ] （分阶段）**收藏/收藏夹**。
- [x] **按日期分组**（与「最新/最旧」排序联动；「名称 A-Z」为平铺无分组）。
- [x] **简单搜索**（文件名包含）。

**交付物**：外观与操作接近系统相册的 α 版；在 `PROJECT.md` 维护「与系统相册功能对齐清单」勾选状态。

**说明**：「所有系统相册功能」边界以各 Android 版本 API 为准；做不到的能力需产品说明（如某些 OEM 独占特性）。

---

## 阶段 2：视频与「户外式高亮」路径（约 2–3 周，关键路径）

**目标**：**普通（SDR）视频**全屏播放时，沿用与图片一致的 **尽量提亮**策略（窗口 `screenBrightness`、`FLAG_KEEP_SCREEN_ON`），并验证 **室内 vs 户外、自动亮度开/关** 下的主观差异；**不以 HDR 片源为交付前提**（HDR 可作为扩展样张）。

- [x] 集成 **Media3 ExoPlayer** + **PlayerView**（全屏播放；**SurfaceView** 直出仍为可选后续，利于 HDR 扩展）。
- [ ] 准备测试素材：**SDR** MP4（自创或合法版权）；可选少量 HDR 样片作对照，不写进必选验收。
- [x] **窗口亮度**：全屏播放时与图片页一致的可调 nit→`screenBrightness`；退出恢复。
- [ ] **nit 映射**：至少一款测试机记录「滑块主观 / 可选亮度计」与系数关系（见 `PROJECT.md`）。
- [ ] 文档：各机 **自动亮度**、省电模式对「阳光激发」的影响（应用层无法完全复刻处需写明）。

**交付物**：可全屏播放常见视频的 α 版 + 简短验证记录。

**风险**：OEM 户外提亮算法不开放；部分机型视频链路色彩增强选项干扰对比。

---

## 阶段 3：图片大视频内存与统一亮度（约 2–3 周）

**目标**：大图、长边预览仍流畅；**静态图保持 SDR 主路径**，与阶段 2 共用同一套「进入全屏提亮、退出恢复」策略。

- [ ] 大分辨率图：subsampling / Coil size / 分块，避免 OOM。
- [ ] 与视频 **统一亮度面板** 行为与持久化（若产品需要记住上次 nit）。
- [x] （展示）**HDR 扩展亮度通道**：全屏 **`COLOR_MODE_HDR`** + **`setDesiredHdrHeadroom(HEADROOM_CAP)`** + 静态图 **合成 Gainmap**；**GIF** 首帧占位 + 全帧解码后 **Gainmap** 循环播放（见 `GifHdrPlayback` / `GifHdrAnimatedImage`）；**视频**暂不叠 Gainmap；Ultra HDR 原图保留 `hasGainmap()`；**离线转码生成** Ultra HDR 仍为可选、非当前目标。

**说明**：相册列表 **全量加载** 仍可能在极大量媒体下占内存；分页/窗口加载属本阶段与阶段 1 未竟项。

**交付物**：大图稳定 + 与视频体验一致的亮度控制。

---

## 阶段 4：产品化、nit 面板与设置（约 2–3 周）

**目标**：可日常使用的 **相册 + 户外式高亮查看器**；**nit/亮度**对普通用户可读、可用。

- [ ] **「显示 / 户外高亮（nit）」面板**：步进、上下限、与**系统自动亮度**关系说明（文案避免承诺「超过太阳下硬件极限」）。
- [x] **预览亮度滑块（0～200%）**：**Gainmap + 设备 headroom**，不写入系统亮度（见 `PROJECT.md`）。
- [x] **预览亮度滑块跟手**：根因为 `BrightnessSheetHost` 读取 `sheetSlider` 值导致 Dialog 内容 lambda 重执行、回调实例重建、Slider 手势中断——改为传递 `MutableFloatState` 对象引用 + `remember` 稳定回调，使值读取仅在子组合 `BrightnessSheetContent` 内发生（2026-03-28 修复，见文末讨论纪要）。
- [x] **设置页**（DataStore）：默认预览亮度、饱和度增强、全屏常亮、默认排序、网格密度；关于与屏幕能力摘要；**系统亮度权限**（`canWrite` + 跳转「修改系统设置」）。
- [x] **仅全屏提亮**（相对列表）：`COLOR_MODE_HDR`、系统亮度拉满（`WRITE_SETTINGS`）仅在全屏预览生效（`ViewerBrightnessPolicyEffects`）；列表为 `COLOR_MODE_DEFAULT` + `GlobalHdrHeadroomForGallery` headroom 1.0。
- [ ] （可选）**系统亮度写入**：评估 `WRITE_SETTINGS` 与用户引导、商店政策。
- [x] **沉浸式（部分）**：全屏查看页隐藏系统栏（滑动可暂时唤出）；**方向锁定**仍为待办。
- [ ] 错误与降级：解码失败、权限拒绝；极端机型回退「0–100% 相对亮度」说明。
- [ ] 基础无障碍：TalkBack、对比度。

**外观**：**Material You 动态取色**（API 31+）已接入主题；相册网格 **圆角缩略图**、**多选触觉反馈**。

**交付物**：可给非开发者试用的 beta。

---

## 阶段 5：上架准备（约 1–2 周）

**目标**：Play 商店材料齐备、政策自查通过。

- [ ] 隐私政策（若收集任何数据；默认本地处理可简化声明）。
- [ ] 商店截图与短视频：说明文案避免夸大「超过系统阳光峰值」等效果。
- [ ] ProGuard/R8、签名、版本号策略。

**交付物**：生产签名 Release 包 + 商店列表草稿。

---

## 里程碑汇总

| 里程碑 | 核心含义 |
|--------|----------|
| M1 | **相册主界面** + 全屏查看 + 删/分享等核心路径 |
| M2 | **视频全屏** + **户外式高亮**与 nit 映射验证 |
| M3 | 大图性能 + 与视频 **统一亮度策略** |
| M4 | Beta：**高亮/nit 面板** + 相册体验打磨 |
| M5 | 上架候选 |

---

## 与常见「本机」相册的差距与演进方向（规划参考）

以下对照对象为 **系统图库 / 常见离线相册** 的主路径体验（不含账号、云、回忆合成等）。用于排期时对齐预期：**本应用长板在「全屏可读亮度 / HDR 增强策略」**；**短板多在「时间线、沉浸感、整理深度、视觉精致度」**。

### 信息架构与浏览

| 差距 | 说明 | 可纳入阶段 |
|------|------|------------|
| 时间维度弱 | 主流常有按日/月分组、快速滚动锚点；当前以**扁平网格 + 排序**为主 | 阶段 1：按日期分组（与「全部」并存） |
| 发现能力 | 主流常有类型筛选（截图/视频/动图等）、简单元数据筛选；当前**仅文件名搜索** | 阶段 1～4：分阶段加筛选维度 |
| 列表性能 | 大数据量下宜 **分页 / 窗口加载**；需避免一次载入全表 | 阶段 1、3：与 `PROJECT.md` 性能项一致 |

### 全屏查看（照片 / 视频）

| 差距 | 说明 | 可纳入阶段 |
|------|------|------------|
| 沉浸感 | 常见为全屏隐藏系统栏、下滑关闭、与返回手势一体；当前 **顶栏 + 黑底 + 亮度 FAB**，偏工具型 | 阶段 4：沉浸式、系统栏策略 |
| 视频操控 | 常见有精 scrub、倍速、（部分）PIP；当前为 **Media3 + 系统控制器** | 阶段 2～4：按需增强控件，仍以 SDR 主路径为准 |
| 过渡与动效 | 常见有列表↔预览 **共享元素** 等；当前偏少 | 阶段 4：动效与 Compose 过渡（可选） |

### 整理与系统一致性（仍属本机）

| 差距 | 说明 | 可纳入阶段 |
|------|------|------------|
| 回收站叙事 | 系统相册多强调「最近删除」；当前为 **应用内删除 + 确认**，与系统回收站 **未对齐** | 见 `PROJECT.md` 开放项；按 API 可行性分阶段 |
| 相册 CRUD | **移至相册 / 重命名** 等未实现 | 阶段 1 已列方向；依赖 MediaStore 能力与权限 |
| 收藏与标签 | **星标 / 本机收藏夹** 未实现 | 阶段 1～4：应用内元数据或 MediaStore 可写字段（需设计） |

### 外观与品牌（Material）

| 差距 | 说明 | 可纳入阶段 |
|------|------|------------|
| Material You | 常见为 **动态取色**、大圆角与 2024+ 组件密度；当前为 **固定深浅色** | 阶段 4：可选接入 Dynamic Color |
| 网格与触感 | 常见为自适应列数、圆角缩略图、选中触觉；当前 **固定 3 列**、动效与触觉偏少 | 阶段 4：网格与微交互 |

### 本地能力补充（非云）

| 差距 | 说明 | 可纳入阶段 |
|------|------|------------|
| 选图入口 | **Photo Picker** 作补充入口（非账号能力） | 阶段 1 已列；按产品优先级 |
| 桌面快捷 | 小组件 / 快捷方式打开「最近」或指定相册 | 阶段 4～5：可选 |

**说明**：上表**不**包含：登录、订阅、云端备份、跨设备同步、人脸/地点智能检索（依赖云端或账号者）。若后续仅做 **端侧** 启发式（如完全本地的简单规则），再在独立小节评估。

**近期已缓解（仍非「完全对齐」）**：**时间维度**（按日分组标题）、**Material You**（动态取色）、**网格**（自适应列数 + 圆角）、**触感**（多选切换）、**全屏沉浸**（隐藏系统栏）、**Photo Picker 入口**；**列表全量加载**与**收藏/回收站一致**等仍为差距。

---

## 依赖与决策记录

重大决策（如 minSdk、静态图技术路线）请在 `PROJECT.md` 或本文件末尾追加 **ADR 式** 简短条目：日期、选项、取舍理由。

### ADR 摘要：minSdk 提升至 35（Android 15）

- **日期**：2025-03（随仓库迭代记录）。
- **选项**：仅支持 **Android 15+**，放弃更低版本。
- **理由**：全机统一 API 能力（如 `setDesiredHdrHeadroom`、Ultra HDR 检测、`RuntimeShader` 等），减少分支与测试矩阵；与产品「户外高亮 / HDR 预览」路线一致。

---

## 2026-03-28 讨论纪要：全屏预览亮度滑块「不跟手」与相关实现

**状态：已修复（2026-03-28）** — 核心根因为 `BrightnessSheetHost` 组合作用域内直接读取 `sheetSlider` 值，导致每次滑块值变化引发 Dialog 内容 lambda 重执行、`onValueChange` 回调实例重建，Material3 `Slider` 内部拖拽手势状态被中断。修复方案见下文「根因定位与修复」。细节与性能条款仍以 `PROJECT.md` 为准。

### 现象与日志特征

- **体感**：拖动亮度面板内的 `Slider` 时，滑块**只动一小段或跳一下**便不再跟随手指。
- **Logcat（`brightness_drag`）**：单次手指滑动往往只有**一条** `slider_onValueChange`，紧接 `slider_onValueChangeFinished`；相邻两次 `slider_onValueChange` 的间隔可达数百毫秒（一次「拖动」被拆成多次离散点击）。
- **关键序列（实机日志对照）**：`vm_set_viewer`（预览亮度写入 ViewModel）之后**立即**出现 `composer ViewerBrightnessLayer`；且 `BrightnessSheetHost` 诊断里 **`sheetSlider` 与 `viewerPct` 短暂不一致**，说明预览状态与滑块本地状态更新**不同步的一拍内**仍在触发大范围重组。

### 根因（分层，曾叠加出现）

1. **布局**：`BrightnessSheetContent` 外层曾用 **`verticalScroll` 包裹整块内容（含 `Slider`）**，纵向滚动与滑块横向拖动手势**争抢**，表现为「只动一下即 `onValueChangeFinished`」。
2. **容器**：**`ModalBottomSheet`** 内部**纵向 sheet 拖动手势**与 `Slider` **争抢**；即使去掉外层 `verticalScroll`，问题仍可存在。
3. **重组（根本）**：**父组合 `ViewerBrightnessLayer` 顶层读取 `viewerBrightnessPct`**，而 Sheet 内 `LaunchedEffect` + `snapshotFlow { sheetSlider }.sample(16ms)` 会调用 **`setViewerPreviewBrightnessPct`**，使预览亮度**高频变化** → 父级**整支重组** → **`BrightnessSheetHost` 与受控 `Slider` 被连带重组**，Material3 `Slider` 的拖动手势被中断。另：**`BrightnessSheetHost` 的 debug `SideEffect` 曾读取 `vm.viewerBrightnessPct`**，Sheet 子树也会因预览刷新**额外重组**。

### 前期缓解（必要但不充分）

| 方向 | 做法 |
|------|------|
| 布局 | `Slider` 与标题行**不**放在整块底栏的 `verticalScroll` 内；**仅**展开的帮助长文使用 `verticalScroll` + `heightIn`。 |
| 容器 | 亮度面板由 **`ModalBottomSheet`** 改为 **`Dialog` + 底部 `Surface`**，再改为 **全屏 `Box` 叠加 + 底部 `Surface`**（无 sheet 纵向拖拽；区外点击 / 返回关闭；避免系统窗口 dim 遮罩）。 |
| 重组边界 | 将依赖 **`viewerBrightnessPct` 的 Pager / FAB / 调试埋点** 抽至子组合 **`ViewerBrightnessMediaSection`**，与 **`BrightnessSheetHost` 兄弟排列**；**父级 `ViewerBrightnessLayer` 不在顶层订阅预览亮度**。 |

### 根因定位与修复（2026-03-28）

**根因**：上述缓解措施消除了外部重组的波及，但 `BrightnessSheetHost` **自身**组合作用域仍在读取 `sheetSlider` 的 `Float` 值（`brightnessPct = sheetSlider`），导致：

1. 每次 `sheetSlider` 变化 → `BrightnessSheetHost` 重组 → Dialog 内容 lambda 重执行
2. `onBrightnessPctChange` 回调被重建为**新的函数实例**
3. Material3 `Slider` 内部拖拽手势检测到回调不稳定，**提前终止当前拖拽**

**修复**（`ViewerScreen.kt`）：

| 改动 | 效果 |
|------|------|
| `sheetSlider` 改为 `val sheetSliderState = remember { mutableFloatStateOf(...) }`，传递 **`MutableFloatState` 对象引用**而非 `.floatValue` | `BrightnessSheetHost` 不再读取值 → 不因滑块变化而重组 |
| `BrightnessSheetContent` 参数改为 `sliderState: MutableFloatState`，仅在**子组合**内读取 `.floatValue` | 值变化仅使 `BrightnessSheetContent` 子树重组，Dialog 层不受影响 |
| `onSliderChangeFinished` 用 `remember<() -> Unit> { ... }` 包裹 | 回调实例跨重组稳定，Slider 手势不被中断 |
| `onValueChange` 在 `BrightnessSheetContent` 内用 `remember<(Float) -> Unit>(sliderState) { ... }` 包裹 | 写入 `sliderState.floatValue` 的 lambda 跨重组稳定 |

### 同批次相关体验与工程项（非滑块主因，已在此前迭代中落地）

- **双指缩放**：全屏图 **双击** — 若当前缩放 ≤ 约 1.02 则置 **1.3×**，否则**复位 1×**（与 `onZoomedChange` / Pager 锁定协同）。
- **文案截断**：相册卡片标题 **最多 2 行** + `Ellipsis`；查看页顶栏标题 **单行** + `Ellipsis`。
- **调试**：全屏预览 debug 仍可向 `HDRV_Viewer` 输出 `gainmap_chain`、`pager_settled` 等（见 `BrightnessSessionLog.kt`）；**已移除**专用 `brightness_drag` 诊断类。

### 后续可选项（未承诺排期）

- [x] **关闭缺陷**：核心代码已修复（传递 `MutableFloatState` 对象引用 + 稳定回调）；实机可验证连续拖动时 **`Slider` 全程跟手**。
- [ ] 若产品坚持 **下滑关闭** 底栏：可评估 **升级 Material3** 使用 `sheetGesturesEnabled = false` 等 API，或单独做「关闭」按钮，避免与 `Slider` 再次冲突。
- [ ] 持续观察 **主线程掉帧**（大图 JPEG 解码、Gainmap）与滑块跟手性的关系；必要时对预览更新做节流策略（与当前 16ms sample 区分评估）。

---

*最后更新：2026-03-28 — **修复**全屏预览亮度滑块不跟手（根因：`BrightnessSheetHost` 组合作用域内读取 `sheetSlider` 值 → Dialog lambda 重执行 → 回调实例重建 → Slider 手势中断；方案：传递 `MutableFloatState` 对象引用 + `remember` 稳定回调）；同批补充双指缩放、标题省略与诊断日志说明。静态图预览技术路线仍为 **Gainmap** + **`setDesiredHdrHeadroom`**（见上文 ADR 与 `PROJECT.md`）。*
