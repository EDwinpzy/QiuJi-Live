# QiuJi-Live（中文名称：球迹直播）

> 面向 Android TV 与投影仪的足球直播客户端，内置 Python 本机后端，支持 HLS 直播播放、线路切换、播放恢复和 GitHub Release 手动分发。

**品牌：** QiuJi-Live（中文名称：球迹直播）
**工程仓库：** `QiuJi-Live`
**本地目录：** `D:\MyProjects\QiuJi-Live`
**GitHub：** [EDwinpzy/QiuJi-Live](https://github.com/EDwinpzy/QiuJi-Live)
**客户端：** Android TV / 投影仪

[简体中文](#zh-cn) | [English](#en)

| 项目状态 | 说明 |
|---|---|
| 交付客户端 | Android TV / 投影仪 |
| 播放能力 | HLS 播放、本机中继、线路切换、首帧恢复、播放健康检查 |
| 后端形态 | Python 后端随客户端 APK 内嵌运行 |
| 分发方式 | GitHub Release 发布完整 TV APK；应用内不使用云端热更新或 APK 更新 |
| 产品边界 | 不提供 Windows、iOS、独立网页或影视点播客户端 |
| 发布策略 | 仅在用户当前请求中明确授权后执行 |

---

<a id="zh-cn"></a>

## 简体中文

### 项目简介

**QiuJi-Live**（中文名称：**球迹直播**）是一个只交付 Android TV / 投影仪客户端的足球直播应用。用户可以查看足球赛程和比赛状态，选择单场比赛的直播信号源，并在播放器中观看 HLS 直播。

产品显示名保留为“球迹直播”。工程仓库、Gradle 根项目名与本地目录已统一为 **`QiuJi-Live`**；发布 APK 文件名统一为 **`QiuJi-Live-TV.apk`**。applicationId、namespace、包名及签名 keystore/alias 保持不变。

### 功能

- 查看足球赛程、比赛状态和球队信息。
- 为单场比赛选择可用直播信号源。
- 播放 HLS 直播，并支持 TS 分片经本机后端中继。
- 处理换线、重试、首帧恢复和长播放健康检查。
- TV 端支持遥控器方向键、OK 和返回键焦点导航。
- 客户端启动内嵌 Python 后端，通过本机 HTTP 服务处理赛程、队徽、直播流和分片请求。
- 正式 APK 不包含云端热更新、应用内 APK 更新、安装权限或私有 CA。
- 新版本通过 GitHub Release 手动下载并覆盖安装；赛程和直播播放不依赖项目自建服务器。

### 客户端

| 客户端 | 工程目录 | applicationId | 主要交互 |
|---|---|---|---|
| Android TV | `android/` | `com.qiubo.optimaltv.public` | 遥控焦点导航、播放器与电视大屏布局 |

### 系统架构

```text
Android TV / 投影仪
              │
              │ 本机 HTTP
              ▼
    内嵌 Python 后端
      ├─ 足球赛程与比赛数据
      ├─ 队徽与兼容静态资源
      └─ HLS / TS 分片本机中继
              │
              ▼
        上游真实直播源

GitHub Releases ── 手动发布和下载完整 TV APK（不在应用内联网更新）
```

核心约束：

- 原生客户端负责界面、遥控交互、播放器状态和本机播放链路。
- Python 后端随 APK 内嵌，不要求用户单独安装或启动服务。
- HLS 播放链路同时包含 Media3 与故障恢复策略；真实直播验收不能只验证首页或静态视频。
- TV 是唯一交付客户端；赛程、线路解析、队徽和 HLS 中继由 APK 内嵌后端完成。
- 项目自建服务器已不再参与客户端运行或更新；外部直播源、队徽 CDN 和 HLS CDN 仍由公网提供。

### 目录结构

| 路径 | 用途 |
|---|---|
| `android/` | Android TV 客户端、内嵌 Python 后端与资源 |
| `tools/backend-src/` | 内嵌 Python 后端、足球数据源、HLS 中继及契约测试 |
| `tools/qa/` | 真实直播采集、延迟对照和验收评估工具 |
| `tools/` | 后端打包、QA、资源和契约检查脚本 |
| `deploy/selfhosted/` | 已停用的历史自托管配置，仅用于旧版本追溯 |
| `apk/` | 本地构建归档目录；生成物不应替代源码验证 |
| `docs/项目文档.md` | 唯一详细项目文档 |
| `.learnings/` | 本机调试经验，不纳入 Git |
| `artifacts/` | 本机截图、日志和验收证据，不纳入 Git |

### 产品范围

- 只交付 Android TV / 投影仪客户端。
- Windows、iOS 和独立网页客户端已移除，不再提供构建入口或产品验收面。
- 仓库保留的 `www/` 仅作为内嵌后端兼容静态资源，不是第二个客户端，也不代表仍有云端服务。
- 影视、点播、IPTV、会员和后台管理产品面已移除，不应重新开放。
- 仓库整理和发布不得重新引入已下线的客户端、业务模块或旧影视运行时。

### 快速开始

环境要求：

- JDK 17 或更高版本。
- Android SDK：`compileSdk 35`、`minSdk 30`、`targetSdk 34`。
- Android TV 工程自带 Gradle Wrapper `8.14.3`，不需要单独安装 Gradle。
- Python `3.11` 或 `3.12`。
- Windows PowerShell 为本文示例环境；macOS/Linux 将 `.\gradlew.bat` 替换为 `./gradlew`，并使用对应的虚拟环境激活命令。

从仓库根目录准备 Python 工具环境：

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r tools\requirements.txt
```

修改 `tools/backend-src/` 后，重新生成 TV 内嵌后端包：

```powershell
python tools/build_backend_zip.py
```

构建 Android TV 调试包：

```powershell
Push-Location .\android
.\gradlew.bat assembleDebug
Pop-Location
```

### 构建与测试

从仓库根目录执行后端、工具和契约测试：

```powershell
python -m unittest discover -s tools/backend-src/tests -p "test_*.py"
python -m unittest discover -s tools/tests -p "test_*.py"
```

Android TV 单元测试与调试构建：

```powershell
Push-Location .\android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
Pop-Location
```

Serverless TV Release 不编译任何云端 API 或更新端点：

```powershell
Push-Location .\android
.\gradlew.bat assembleRelease
Pop-Location
```

正式包固定使用 `HOTUPDATE_ENABLED=false`、`APK_UPDATE_ENABLED=false`，不写入更新地址，
也不申请 `REQUEST_INSTALL_PACKAGES` 权限。不得把已停用的云端更新入口重新加回 TV 构建。

修改后端后，必须在构建 APK 前运行 `python tools/build_backend_zip.py`，确保 TV APK 内嵌的 `python-backend.zip` 与源码一致。

### 发布

发布、构建、提交和推送都必须由用户在当前请求中明确授权。未获得当前授权时，只做本地测试和构建检查。

获得授权后，按以下顺序串行执行：

1. 检查共享工作区和其他活动任务，确认未混入他人未提交改动。
2. 如后端源码有改动，运行 `python tools/build_backend_zip.py`，再运行后端、工具和 TV 测试。
3. 构建 `assembleRelease`，保持 `com.qiubo.optimaltv.public`、namespace、包名及既有签名 keystore/alias 不变。
4. 核对 APK 版本单调递增、内嵌后端与源码一致，并确认没有云端更新端点、安装权限和私有 CA。
5. 在投影仪覆盖安装，验证首页、遥控焦点和真实直播；记录截图、日志、版本和 SHA-256。
6. 提交并推送，创建仅包含完整 TV APK 的 GitHub Release，重新下载后核对 SHA-256。

当前发布模式不部署球迹直播服务器，不生成热更新包或 APK 分片清单。

详细发布门槛、共享工作区规则和回滚检查见 [docs/项目文档.md](docs/项目文档.md) 与根目录 [AGENTS.md](AGENTS.md)。不要自行触发生产发布或另行分配版本号。

### 真实直播验收边界

真实直播结论必须基于**当前源码重新构建并安装的客户端**和**真实上游直播源**。首页截图、单元测试、调试构建、模拟器页面交互、测试视频或旧 APK 都不能替代真实直播验收。

当前验收口径：

- 至少覆盖 3 场不同比赛，不得用同一场比赛多开代替。
- 必须使用 TV / 投影仪客户端和实际可用线路。
- 每场连续播放至少 10 分钟；若比赛结束，应切换到另一场真实比赛继续累计。
- 播放期间不得出现进程退出、FATAL/AndroidRuntime 崩溃、持续黑屏或冻结、ANR，以及用户可见的反复缓冲。
- 相对同一上游原始直播信号，客户端额外延迟不超过 5 秒，且不得随播放时间累积。
- 记录比赛 ID、线路 ID、开始/结束时间、周期截图、进程存活、`logcat -b crash`、播放健康日志、缓冲和恢复事件。

无法取得真实可播比赛、上游身份或完整日志证据时，应明确标记为“未完成”，不能把工具自测、静态页面或历史截图写成验收通过。

### 文档

- [README.md](README.md)：项目入口，包含定位、能力、客户端、架构、常用命令、发布和验收边界。
- [docs/项目文档.md](docs/项目文档.md)：唯一详细文档，包含架构、实现约束、发布顺序、运维、排障和历史验收记录。
- `.learnings/`：本机调试经验，不纳入版本控制。
- `artifacts/`：本机截图、日志和验收证据，不纳入版本控制。

不新增 `README.en.md` 或其他项目说明文档；英文内容与中文内容都维护在本文件的不同章节中。

### License

当前仓库没有 `LICENSE` 文件，因此未声明任何开源许可证，也不对复制、分发、商用或再许可作默认授权。使用前请向仓库所有者确认授权范围。

---

<a id="en"></a>

## English

### Overview

**QiuJi-Live** (Chinese name: **球迹直播**) is a football live-streaming application delivered only for Android TV and projectors. Users can browse football fixtures and match status, choose an available live source for a match, and watch HLS streams in the player. Releases are distributed manually as complete APKs through GitHub Releases.

The Chinese product display name remains “球迹直播”. The engineering repository, Gradle root project name, and local directory are now unified as **`QiuJi-Live`**; release APK filenames use **`QiuJi-Live-TV.apk`**. The applicationId, namespace, package name, and signing keystore/alias remain unchanged.

### Features

- Browse football fixtures, match status, and team information.
- Select an available live source for an individual match.
- Play HLS live streams, including TS-segment relay through the local backend.
- Handle source switching, retries, first-frame recovery, and long-playback health checks.
- Support D-pad, OK, and Back focus navigation on Android TV.
- Start an embedded Python backend that serves fixtures, team artwork, live streams, and segments over local HTTP.
- Ship release APKs without backend hot updates, in-app APK updates, install permissions, or a private CA.
- Distribute new versions as complete APK downloads from GitHub Releases; fixtures and playback do not depend on a project-operated server.

### Clients

| Client | Project directory | applicationId | Primary interaction |
|---|---|---|---|
| Android TV | `android/` | `com.qiubo.optimaltv.public` | Remote-control focus navigation and a TV-sized layout |

### Architecture

```text
Android TV / projector
              │
              │ local HTTP
              ▼
      Embedded Python backend
      ├─ football fixtures and match data
      ├─ team artwork and compatibility assets
      └─ HLS / TS segment local relay
              │
              ▼
          Real upstream live source

GitHub Releases ── manual publication/download of the complete TV APK (no in-app network update)
```

Key constraints:

- The native client owns the UI, remote interaction, player state, and local playback path.
- The Python backend is embedded in the APK; users do not need to install or start it separately.
- The HLS path includes Media3 playback and recovery policies. Home-screen checks or static test videos do not prove live-stream acceptance.
- TV is the only delivered client. Fixtures, source resolution, team artwork, and HLS relay run in the embedded backend.
- The retired project server is not part of client runtime or updates. External fixture sites, logo CDNs, and HLS CDNs remain public-network dependencies.

### Repository layout

| Path | Purpose |
|---|---|
| `android/` | Android TV client, embedded Python backend, and resources |
| `tools/backend-src/` | Embedded Python backend, football data sources, HLS relay, and contract tests |
| `tools/qa/` | Real-live capture, delay comparison, and acceptance evaluation tools |
| `tools/` | Backend packaging, QA, asset generation, and contract checks |
| `deploy/selfhosted/` | Retired historical self-hosted configuration kept for legacy traceability |
| `apk/` | Local build archive; generated files do not replace source validation |
| `docs/项目文档.md` | The single detailed project document |
| `.learnings/` | Local debugging notes, not tracked by Git |
| `artifacts/` | Local screenshots, logs, and acceptance evidence, not tracked by Git |

### Product scope

- Only the Android TV / projector client is delivered.
- The Windows, iOS, and standalone web clients have been removed and no longer have build entry points or product acceptance surfaces.
- The retained `www/` path contains compatibility assets for the embedded backend. It is not a second client and does not imply that a cloud service is still running.
- Movie-on-demand, VOD, IPTV, membership, and admin product surfaces have been removed and must not be reopened.
- Repository cleanup and releases must not reintroduce retired clients, business modules, or the old film runtime.

### Quick start

Requirements:

- JDK 17 or newer.
- Android SDK: `compileSdk 35`, `minSdk 30`, and `targetSdk 34`.
- The Android TV project includes Gradle Wrapper `8.14.3`; a separate Gradle installation is not required.
- Python `3.11` or `3.12`.
- Commands below use Windows PowerShell. On macOS/Linux, replace `.\gradlew.bat` with `./gradlew` and use the matching virtual-environment activation command.

From the repository root, prepare the Python tooling environment:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r tools\requirements.txt
```

After changing `tools/backend-src/`, rebuild the embedded backend archive for the TV client:

```powershell
python tools/build_backend_zip.py
```

Build the Android TV debug client:

```powershell
Push-Location .\android
.\gradlew.bat assembleDebug
Pop-Location
```

### Build and test

Run backend, tooling, and contract tests from the repository root:

```powershell
python -m unittest discover -s tools/backend-src/tests -p "test_*.py"
python -m unittest discover -s tools/tests -p "test_*.py"
```

Android TV unit tests and debug build:

```powershell
Push-Location .\android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
Pop-Location
```

Serverless TV releases compile no cloud API or update endpoint:

```powershell
Push-Location .\android
.\gradlew.bat assembleRelease
Pop-Location
```

Release builds use `HOTUPDATE_ENABLED=false` and `APK_UPDATE_ENABLED=false`, contain no update URL,
and do not request `REQUEST_INSTALL_PACKAGES`. Do not reintroduce the retired cloud-update path.

After backend changes, run `python tools/build_backend_zip.py` before building the APK so the embedded `python-backend.zip` matches the source.

### Release

A release build, publication, commit, or push requires explicit authorization in the user's current request. Without that authorization, limit work to local tests and build checks.

When authorized, execute the following serially:

1. Check the shared worktree and other active tasks; do not include unrelated uncommitted changes.
2. Rebuild `python-backend.zip` if backend source changed, then run backend, tooling, and TV tests.
3. Run `assembleRelease`, keeping `com.qiubo.optimaltv.public`, the namespace, package name, and the existing signing keystore/alias unchanged.
4. Verify a monotonically increasing `versionCode`, a matching embedded backend, and the absence of cloud update endpoints, install permission, and private CA.
5. Install over the previous projector build and verify the home screen, D-pad navigation, and a real live stream; archive screenshots, logs, version, and SHA-256.
6. Commit, push, and create a GitHub Release containing only the complete TV APK; download it again and verify its SHA-256.

The current release model does not deploy a QiuJi-Live server and does not build hot-update archives or APK segment manifests.

See [docs/项目文档.md](docs/项目文档.md) and [AGENTS.md](AGENTS.md) for detailed release gates, shared-worktree rules, and rollback checks. Do not start a production release or allocate a version independently.

### Real-live acceptance boundary

A real-live conclusion must use a client rebuilt from the current source and installed on the target, plus a real upstream live source. Home-screen screenshots, unit tests, debug builds, emulator page interaction, test videos, and old APKs do not replace real-live acceptance.

Current acceptance criteria:

- Cover at least three different matches; multiple instances of one match do not count.
- Use the Android TV / projector client with actual available sources.
- Observe each match continuously for at least 10 minutes. If a match ends, switch to another real match to continue accumulating evidence.
- During playback, there must be no process exit, FATAL/AndroidRuntime crash, persistent black screen or freeze, ANR, or visible repeated rebuffering.
- Relative to the same upstream live signal, additional client delay must not exceed 5 seconds and must not accumulate over time.
- Record match IDs, source IDs, start/end times, periodic screenshots, process liveness, `logcat -b crash`, playback-health logs, buffering events, and recovery events.

If real playable matches, upstream identity, or complete logs are unavailable, mark the result as **incomplete**. Do not describe tool self-tests, static pages, or historical screenshots as a passed live acceptance.

### Documentation

- [README.md](README.md): project entry point covering positioning, capabilities, clients, architecture, common commands, release, and acceptance boundaries.
- [docs/项目文档.md](docs/项目文档.md): the single detailed document for architecture, implementation constraints, release order, operations, troubleshooting, and historical acceptance records.
- `.learnings/`: local debugging notes, not tracked by Git.
- `artifacts/`: local screenshots, logs, and acceptance evidence, not tracked by Git.

Do not add `README.en.md` or another project explanation document. Both Chinese and English content are maintained in separate sections of this file.

### License

This repository has no `LICENSE` file, so no open-source license is declared and no default permission to copy, redistribute, commercialize, or sublicense is granted. Confirm the authorization scope with the repository owner before use.
