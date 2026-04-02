<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="163MusicPro" width="120" height="120">
</p>

<h1 align="center">163MusicPro</h1>

<p align="center">
  <strong>适用于小天才电话手表的网易云音乐播放器</strong>
</p>

<p align="center">
  <a href="../../releases/latest"><img src="https://img.shields.io/github/v/release/9xhk-1/163MusicPro?style=flat-square" alt="Latest Release"></a>
  <img src="https://img.shields.io/badge/platform-Android%207.0%2B-brightgreen?style=flat-square" alt="Platform">
  <img src="https://img.shields.io/badge/screen-320%C3%97360-blue?style=flat-square" alt="Screen">
  <img src="https://img.shields.io/github/license/9xhk-1/163MusicPro?style=flat-square" alt="License">
</p>

<p align="center">
  直接调用网易云音乐 API，无需第三方中间服务器。<br>
  为 320×360 手表屏幕精心适配，所有界面均支持手势操作。
</p>

---

## ✨ 功能特性

| 功能 | 说明 |
|------|------|
| 🔍 **在线搜索** | 搜索网易云音乐全曲库，支持分页加载 |
| ▶️ **音乐播放** | 上一首 / 下一首 / 暂停 / 播放，自动切歌 |
| 📝 **歌词同步** | 在线获取 LRC 歌词，逐行高亮滚动显示 |
| ❤️ **收藏管理** | 本地 / 云端收藏，数据持久化，重装自动恢复 |
| ⬇️ **离线下载** | 下载歌曲到本地，支持离线播放 |
| 🔔 **铃声设置** | 截取歌曲片段设为手表铃声 |
| 📊 **排行榜** | 浏览网易云热门榜单 |
| 📜 **播放历史** | 自动记录最近 200 首播放记录 |
| ⚡ **倍速播放** | 0.1x – 5.0x 变速，支持音调不变 / 音调随速度改变 |
| 🎲 **播放模式** | 列表循环 / 单曲循环 / 随机播放 |
| ⏱ **定时关闭** | 定时自动停止播放 |
| 🔊 **音量控制** | 自定义音量叠加层，1.5s 自动消失 |
| 🔑 **多种登录** | 扫码登录 / Cookie 登录 |
| 🛡️ **后台保活** | 前台服务 + WakeLock，锁屏和后台不被杀死 |
| 👤 **个人中心** | 查看账号信息、VIP 状态与有效期 |
| 🎵 **私人漫游** | 登录后获取个性化推荐歌曲 |

## 📦 安装

### 从 Release 安装（推荐）

1. 前往 [**Releases**](../../releases/latest) 下载最新 APK
2. 将 APK 传输到手表（通过 ADB 或文件管理）
3. 在手表上安装并打开应用

### 从源码构建

**环境要求：** JDK 8+, Android SDK 34

```bash
# 克隆仓库
git clone https://github.com/9xhk-1/163MusicPro.git
cd 163MusicPro

# 构建调试版本
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk

# 构建签名发布版本
./gradlew assembleRelease
# APK → app/build/outputs/apk/release/app-release.apk
```

> 签名发布需配置环境变量：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`

## 🚀 快速开始

1. 打开应用进入播放器主界面
2. **左滑** 查看歌词，**右滑** 关闭歌词 / 返回
3. 点击右上角 **⋯** 进入功能菜单
4. 在菜单中选择 **搜索**，输入歌曲名即可播放
5. 如需播放 VIP 歌曲，进入 **登录** 页面完成登录：
   - **扫码登录**：使用网易云音乐 App 扫描二维码
   - **Cookie 登录**：手动粘贴 Cookie

## 🏗 项目结构

```
app/src/main/java/com/qinghe/music163pro/
├── activity/          # UI 界面 (MainActivity, SearchActivity, ...)
├── api/               # 网易云 API 调用 (MusicApiHelper, NeteaseApiCrypto)
├── manager/           # 数据管理 (FavoritesManager, DownloadManager, HistoryManager)
├── model/             # 数据模型 (Song)
├── player/            # 播放器核心 (MusicPlayerManager)
├── service/           # 后台服务 (MusicPlaybackService)
└── util/              # 工具类 (MusicLog, QrCodeGenerator)
```

## ⚙️ 技术规格

| 项目 | 值 |
|------|------|
| 包名 | `com.qinghe.music163pro` |
| 最低 SDK | Android 6.0 (API 23) |
| 目标 SDK | Android 8.1 (API 27) |
| 编译 SDK | Android 14 (API 34) |
| 屏幕适配 | 320×360 (小天才手表) |
| API 加密 | WeAPI (AES-128-CBC + RSA) |
| 依赖 | `androidx.appcompat:appcompat:1.6.1` |

## 🔄 CI/CD

代码合并到 `main` 分支后，GitHub Actions 自动构建并发布签名 APK 到 Releases。

配置签名密钥：**Settings → Secrets and variables → Actions** 添加：

| Secret | 说明 |
|--------|------|
| `KEYSTORE_BASE64` | keystore 文件的 base64 编码 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |

## 📋 更新日志

### v20260402
- 移除不可用功能入口：听歌识曲、短信登录、密码登录
- 修复歌词页面切歌后歌词不刷新的问题
- 切歌时歌词页自动加载新歌词并更新歌曲名称

### v20260401
- 新增日志系统（API 调用 / 操作全记录）
- 修复 VIP 到期时间不显示
- 修复短信 / 密码登录「登录失败:null」
- 新增「识别歌曲」功能（听歌识曲 / 哼歌识曲）
- 修复右滑退出全局禁用问题

### v20260331
- 新增变速模式设置（音调不变 / 音调改变）
- 设置页面重构为平铺列表风格
- 新增开关选项页与关于页面
- 修复铃声管理名称秒数重复显示

### v2.0
- 自定义应用图标
- 新增前台服务保活机制
- 修复二维码显示不完整
- 收藏列表删除重装后自动恢复

<details>
<summary>查看完整更新历史</summary>

详见 **设置 → 关于** 页面内的版本记录。

</details>

## 🤝 贡献

欢迎提交 Issue 和 Pull Request。

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 打开 Pull Request

## 📄 License

本项目仅供学习交流使用。网易云音乐相关 API 和内容版权归网易公司所有。
