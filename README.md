<!-- HDR Viewer — 中英双语说明；目录锚点依赖 GitHub 标题 slug，若跳转失效请使用浏览器「在页面中查找」。 -->

<div align="center">

<img src="docs/images/logo_concept_sun_frame.png" alt="HDR Viewer 概念 Logo" width="120"/>

# HDR Viewer

**本地相册体验 · 全屏可读亮度 · Gainmap / HDR 预览增强**

[中文文档](#中文文档) · [English](#english) · [PROJECT.md](PROJECT.md) · [ROADMAP.md](ROADMAP.md) · [Releases](https://github.com/JasonXie-Code/HDR_Viewer/releases)

</div>

---

## 目录（跳转链接）

| 语言 | 快速跳转 |
|------|----------|
| **中文** | [项目定位](#项目定位) · [为何限定 Android 15](#为何限定-android-15) · [仓库结构](#仓库结构) · [构建步骤](#构建步骤) · [亮度策略（示意）](#亮度与预览策略示意) · [配图说明](#配图说明) · [应用界面截图](#应用界面截图) · [延伸阅读](#延伸阅读) |
| **English** | [What this app does](#what-this-app-does) · [Why Android 15](#why-android-15) · [Repository layout](#repository-layout) · [Build](#build) · [Brightness pipeline](#brightness--preview-pipeline) · [Figures](#figures) · [UI screenshots](#ui-screenshots) · [Further reading](#further-reading) |

---

## 中文文档

### 项目定位

**HDR Viewer** 是一款面向 **Android** 的相册类应用：启动后 **主界面即相册**（时间线 / 相册集合等），可浏览与整理 **本机照片与视频**（多数为 **SDR** 日常素材）。与「必须播放 HDR 电影片源」类产品不同，本项目的核心差异化在于：**在全屏查看时，在系统允许范围内尽可能抬高可读亮度与观感**，并可在预览链路中启用 **Gainmap、HDR 窗口 headroom** 等能力，使 **普通内容** 在户外或高光环境下更易阅读。

> **教育性说明（边界）**：手机在「阳光激发」下的极限亮度由 **面板、热设计、ALS、自动亮度策略** 等共同决定，**应用无法保证超过系统闭门算法**。本应用的目标是：在 **应用权限与 API 可达范围内用满**（例如窗口亮度、常亮、可选的系统亮度写入等），并在产品文案中诚实说明这一边界。详见 [PROJECT.md — 问题定义](PROJECT.md#问题定义)。

### 为何限定 Android 15

| 原因 | 说明 |
|------|------|
| **API 对齐** | 工程将 **minSdk / targetSdk / compileSdk** 设为 **35**，以统一使用 **`setDesiredHdrHeadroom`**、Ultra HDR / Gainmap 等与预览相关的现代 API，减少机型分支。 |
| **维护成本** | 缩小测试矩阵，使「户外高亮 / HDR 预览」路线可持续迭代；**minSdk 35** 的取舍见 [ROADMAP.md — 依赖与决策记录](ROADMAP.md#依赖与决策记录)。 |

因此：**低于 Android 15 的设备无法安装**；若需在旧系统运行，需单独评估降级与兼容性，不在当前主线路线图内。

### 仓库结构

```text
HDR_Viewer/
├── README.md                 ← 本文件（对外简介）
├── PROJECT.md                ← 产品与技术详细说明
├── ROADMAP.md                ← 阶段计划与里程碑
├── android/                  ← Gradle 工程根目录（请用 Android Studio 打开此目录）
├── misc/                     ← 便携脚本、环境注入、工具说明（misc/tools 体积大，默认不提交）
├── docs/                     ← 补充文档与配图（如 docs/images/）
└── Photos/                   ← 本地示例素材目录（默认不纳入 Git；需要时复制到 docs/images/ 再引用）
```

**为何强调打开 `android/` 而不是仓库根目录？** 因为 Gradle Wrapper、`settings.gradle.kts` 与模块均在 **`android/`** 下；在根目录打开会导致 IDE 无法识别工程。详见 [PROJECT.md — Android 应用工程](PROJECT.md#android-应用工程当前实现)。

### 构建步骤

1. **准备环境**：安装 **JDK 17** 与 **Android SDK**（含 API 35 平台）。若使用仓库推荐的便携布局，可在仓库根目录执行 **`misc\env_portable.cmd`**（cmd）或 **`. .\misc\env_portable.ps1`**（PowerShell）注入 `JAVA_HOME`、`ANDROID_*` 等（见 [PROJECT.md — 便携性与自包含约定](PROJECT.md#便携性与自包含约定)）。
2. **进入模块目录**并执行 Gradle：

```bash
cd android
./gradlew assembleDebug       # Windows: gradlew.bat assembleDebug
./gradlew assembleRelease     # 未配置 release 签名时，产出未签名 APK（用于测试）
```

3. **说明**：**`assembleRelease`** 在未配置签名时生成 **`app-release-unsigned.apk`**，适合自测与 [GitHub Releases](https://github.com/JasonXie-Code/HDR_Viewer/releases) 分发前的验证；**上架应用商店**需配置 **release 签名** 并遵守各平台政策。

### 亮度与预览策略（示意）

下图帮助理解「系统能力」与「应用侧预览增强」的分工（简化示意，非完整类图）：

```mermaid
flowchart TB
  subgraph System["系统层（示例）"]
    ALS[环境光 / 自动亮度]
    HBM[激发亮度策略]
  end
  subgraph App["HDR Viewer（应用可达范围）"]
    Win[窗口亮度 / 常亮等]
    HDR[COLOR_MODE_HDR / Headroom]
    GM[Gainmap 合成预览]
  end
  User((用户)) --> App
  ALS --> HBM
  Win --> User
  HDR --> User
  GM --> User
```

更完整的工程说明见 [PROJECT.md — 两段式亮度滑块](PROJECT.md#两段式亮度滑块产品设想待实现)、[PROJECT.md — Android 工程现状](PROJECT.md#android-应用工程当前实现)。

### 配图说明

以下图片来自 **`docs/images/`**。根目录 **`Photos/`** 中的原始截图已**逐一复制**为下表中的 `screen_*.jpg` 文件名并纳入文档，便于在 GitHub 上展示（**`Photos/`** 本身仍可能被 `.gitignore` 忽略）。

| 预览 | 说明 |
|:----:|------|
| <img src="docs/images/logo_concept_sun_frame.png" alt="Logo" width="96"/> | **概念 Logo**：与 `misc/logo_concepts/` 设计一致，用于品牌识别。 |

### 应用界面截图

以下五张图对应 **`Photos/` 目录内全部素材**（文件名见 [`docs/images/README.md`](docs/images/README.md)），按**主界面 → 相册 → 设置 → 全屏预览**的阅读顺序排列，便于对照 [PROJECT.md — Android 应用工程](PROJECT.md#android-应用工程当前实现)。

#### 1）照片 · 时间线与筛选（`screen_photos_timeline.jpg`）

| 预览 | 说明 |
|:----:|------|
| <img src="docs/images/screen_photos_timeline.jpg" alt="照片时间线" width="280"/> | **对应页面**：底部导航选中 **「照片」**。可见 **顶栏**（搜索、排序、从系统选取媒体、更多）、其下 **类型筛选**（**全部 / 仅照片 / 仅视频 / 仅动图** 等 FilterChip），以及 **按修改日分组标题**（如「2026年3月28日」）与 **圆角缩略图网格**。这与工程中「主界面即相册、时间线 + 筛选」的产品定位一致。若您手中的截图为**纵向长图**，下半截可能来自系统或其他应用拼接，**以本仓库当前 `GalleryScreen` 实现为准**。 |

#### 2）相册 · 文件夹网格（`screen_albums_tab.jpg`）

| 预览 | 说明 |
|:----:|------|
| <img src="docs/images/screen_albums_tab.jpg" alt="相册 Tab" width="280"/> | **对应页面**：底部导航选中 **「相册」**。**深色主题**下以 **双列网格** 展示相册卡片：每卡含 **封面缩略图**、标题与 **「N 项」** 数量。部分标题显示为 **`0`** 或数值型 ID，多见于 **MediaStore 未提供友好相册名** 时的回退展示，属常见边界情况。 |

#### 3）设置 · 显示与亮度 / Gainmap 说明（`screen_settings_tab.jpg`）

| 预览 | 说明 |
|:----:|------|
| <img src="docs/images/screen_settings_tab.jpg" alt="设置" width="280"/> | **对应页面**：底部导航选中 **「设置」**。可见 **「显示与亮度」** 分组：文案说明全屏预览与列表页的显示策略；**默认预览亮度** 滑块（如 **200%**）、**高亮段饱和度增强** 与 **预览时保持屏幕常亮** 开关；下方 **「预览说明」** 解释 **0～200% 经 Gainmap 调节、不直接等同拖动系统亮度条** 等产品逻辑，与 `PROJECT.md` 中 Gainmap 段落一致。 |

#### 4）全屏预览 · 单图与亮度入口（`screen_single_image_viewer.jpg`）

| 预览 | 说明 |
|:----:|------|
| <img src="docs/images/screen_single_image_viewer.jpg" alt="单图预览" width="280"/> | **对应页面**：**单媒体全屏查看**。顶栏为 **返回、文件名、分享、信息、删除**；内容为一张**插画类图片**（画面右下角可见 **「豆包AI生成」** 水印，属**被浏览的媒体本身**，非应用内置水印）。右下浮动按钮为 **亮度 / 高亮** 相关入口，对应全屏 **可读亮度** 能力。 |

#### 5）全屏预览 · 缩放与内容（`screen_preview_zoom.jpg`）

| 预览 | 说明 |
|:----:|------|
| <img src="docs/images/screen_preview_zoom.jpg" alt="预览缩放" width="280"/> | **对应页面**：全屏查看时 **缩放** 状态（画面底部可见 **200%** 等缩放指示）。内容为网络梗图（**My Little Pony** 角色梗），用于演示 **双指缩放 / 高倍查看** 下的阅读体验；**全屏预览亮度与 Gainmap 策略** 仍仅作用于当前媒体区域，与 `ViewerScreen` 行为一致。 |

### 延伸阅读

- **[PROJECT.md](PROJECT.md)**：产品边界、nit 话术、合规与开放问题。  
- **[ROADMAP.md](ROADMAP.md)**：里程碑与阶段任务。  
---

## English

### What this app does

**HDR Viewer** is an **Android** gallery-style app: the **first screen is your library** (timeline / albums). It focuses on **local photos and videos** (mostly **SDR**). The differentiator is **fullscreen readability**: push brightness and rendering as far as **the platform allows**, and use paths such as **Gainmap** and **HDR window headroom** so everyday content stays readable in bright environments.

> **Important boundary**: peak “sunlight” brightness depends on **hardware, thermals, ALS, and OEM auto-brightness policies**. This app **cannot promise** to exceed the system’s closed-loop behavior; it aims to **fully use** what apps are allowed (window brightness, keep screen on, optional system brightness writes, etc.). Details: [PROJECT.md — 问题定义](PROJECT.md#问题定义).

### Why Android 15

The project sets **minSdk / targetSdk / compileSdk to 35** so preview-related APIs (**`setDesiredHdrHeadroom`**, Ultra HDR / Gainmap, etc.) stay consistent and the test matrix stays smaller. See [ROADMAP.md — Dependencies & decisions](ROADMAP.md#依赖与决策记录) (ADR: minSdk 35).

Devices **below Android 15 are not supported** by the current roadmap.

### Repository layout

```text
HDR_Viewer/
├── README.md
├── PROJECT.md
├── ROADMAP.md
├── android/          ← Open this folder in Android Studio (Gradle root)
├── misc/             ← Scripts & portable env (misc/tools is large; not committed by default)
├── docs/
└── Photos/           ← Local samples (often gitignored); copy into docs/images/ for README assets
```

### Build

1. Install **JDK 17** and **Android SDK** (API 35). Optional: run **`misc/env_portable.cmd`** or **`misc/env_portable.ps1`** to inject `JAVA_HOME` / `ANDROID_*` as described in [PROJECT.md](PROJECT.md#便携性与自包含约定).
2. Run:

```bash
cd android
./gradlew assembleDebug
./gradlew assembleRelease   # unsigned if signing is not configured
```

3. **Signing**: Unsigned release APKs are fine for **internal testing** and [GitHub Releases](https://github.com/JasonXie-Code/HDR_Viewer/releases); use a **release keystore** for store distribution.

### Brightness & preview pipeline

```mermaid
flowchart TB
  subgraph System["System"]
    ALS[ALS / auto-brightness]
    HBM[Boost policies]
  end
  subgraph App["HDR Viewer"]
    Win[Window brightness / keep-on]
    HDR[COLOR_MODE_HDR / headroom]
    GM[Gainmap preview]
  end
  User((User)) --> App
  ALS --> HBM
  Win --> User
  HDR --> User
  GM --> User
```

More detail: [PROJECT.md](PROJECT.md) (brightness / Gainmap sections).

### Figures

| Preview | Description |
|:-------:|-------------|
| <img src="docs/images/logo_concept_sun_frame.png" alt="Logo" width="96"/> | Concept logo (see `misc/logo_concepts/`). |

### UI screenshots

All five **`screen_*.jpg`** files in **`docs/images/`** are copies of every image under repo-root **`Photos/`** (see [`docs/images/README.md`](docs/images/README.md)). Order: **timeline → albums → settings → fullscreen**.

#### 1) Photos · timeline & filters (`screen_photos_timeline.jpg`)

| Preview | Description |
|:-------:|-------------|
| <img src="docs/images/screen_photos_timeline.jpg" alt="Photos timeline" width="280"/> | **Screen**: **Photos** tab selected. **Top bar** (search, sort, pick from system, overflow), **type chips** (**All / Photos only / Videos only / GIFs only**), **date section headers**, and a **rounded thumbnail grid**—matching the “home is the gallery” design. If your file is an **extra-tall composite**, treat only the **HDR Viewer** portion as authoritative; see `GalleryScreen` in source. |

#### 2) Albums · folder grid (`screen_albums_tab.jpg`)

| Preview | Description |
|:-------:|-------------|
| <img src="docs/images/screen_albums_tab.jpg" alt="Albums tab" width="280"/> | **Screen**: **Albums** tab, **dark theme**, **two-column** cards with **cover**, **title**, and **item count** (`N 项`). Titles like **`0`** or numeric IDs often appear when **MediaStore** has no friendly album name—normal edge case. |

#### 3) Settings · display & Gainmap (`screen_settings_tab.jpg`)

| Preview | Description |
|:-------:|-------------|
| <img src="docs/images/screen_settings_tab.jpg" alt="Settings" width="280"/> | **Screen**: **Settings** tab. **Display & brightness** copy; **default preview brightness** slider (e.g. **200%**); **highlight saturation boost** and **keep screen on** toggles; **Preview explanation** text describing **0–200% via Gainmap** (aligned with `PROJECT.md`). |

#### 4) Viewer · single image + brightness FAB (`screen_single_image_viewer.jpg`)

| Preview | Description |
|:-------:|-------------|
| <img src="docs/images/screen_single_image_viewer.jpg" alt="Single image viewer" width="280"/> | **Screen**: **Fullscreen** single asset. **Top bar**: back, filename, share, info, delete. Image content includes a **Doubao AI** watermark on the **asset itself**, not added by the app. **Floating brightness** control bottom-right maps to fullscreen readability features. |

#### 5) Viewer · zoom (`screen_preview_zoom.jpg`)

| Preview | Description |
|:-------:|-------------|
| <img src="docs/images/screen_preview_zoom.jpg" alt="Zoomed preview" width="280"/> | **Screen**: Zoomed preview (**~200%** indicator). Sample content is a **meme** image (illustrative only). Demonstrates **pinch-zoom** reading; brightness/Gainmap behavior follows `ViewerScreen` / project docs. |

### Further reading

- **[PROJECT.md](PROJECT.md)** — product definition and technical constraints.  
- **[ROADMAP.md](ROADMAP.md)** — roadmap and milestones.  
---

<div align="center">

<sub>仓库：<a href="https://github.com/JasonXie-Code/HDR_Viewer">github.com/JasonXie-Code/HDR_Viewer</a></sub>

</div>
