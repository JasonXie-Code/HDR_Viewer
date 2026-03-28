# HDR Viewer

本地相册体验为主的 Android 应用：浏览照片与视频；全屏预览时在系统允许范围内**尽量提高可读亮度**，并支持 **Gainmap / HDR 窗口** 等预览增强路径。详细产品定义、技术约束与路线图见 **[PROJECT.md](PROJECT.md)**、**[ROADMAP.md](ROADMAP.md)**。

## 要求

| 项目 | 说明 |
|------|------|
| 系统 | **Android 15（API 35）及以上** |
| 构建 | **JDK 17**，Android SDK（`compileSdk 35`） |
| 工程根目录 | 克隆后请在 **`android/`** 目录打开 Gradle 工程 |

## 构建

```bash
cd android
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # 当前未配置 release 签名时产出未签名 APK
```

便携工具链与 `JAVA_HOME` 注入见 **`misc/env_portable.cmd`** / **`misc/env_portable.ps1`**（`misc/tools/` 体积较大，默认不纳入 Git，需按 `PROJECT.md` 自行准备）。

## 文档

- **[docs/GITHUB_PUBLISH_GUIDE.md](docs/GITHUB_PUBLISH_GUIDE.md)**：推送仓库与在 GitHub Releases 附带 APK 的说明（中英双语）。

## 许可证

尚未在仓库内声明默认许可证；若您计划开源，请补充 `LICENSE` 并在此更新。

---

## English

**HDR Viewer** is an Android gallery-style app focused on **local photos and videos**, with **fullscreen readability and brightness** pushed as far as the platform allows, plus **Gainmap / HDR window** preview enhancements. See **[PROJECT.md](PROJECT.md)** and **[ROADMAP.md](ROADMAP.md)** for scope and technical notes.

| Requirement | Notes |
|-------------|--------|
| OS | **Android 15 (API 35)+** |
| Build | **JDK 17**, Android SDK (`compileSdk 35`) |
| Gradle root | Open the **`android/`** directory in Android Studio |

```bash
cd android
./gradlew assembleDebug
./gradlew assembleRelease   # unsigned if signing not configured
```

Portable toolchain notes: **`misc/env_portable.*`** (large `misc/tools/` is gitignored; prepare per `PROJECT.md`).

**Releases**: installable APKs are attached to **[GitHub Releases](https://github.com/JasonXie-Code/HDR_Viewer/releases)** (test builds may be **unsigned** until signing is configured).
