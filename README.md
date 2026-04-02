# 163MusicPro

> 为小天才电话手表打造的网易云音乐播放器，面向 320×360 DPI 手表屏幕优化，运行于 Android 7.0–8.1。

## 概览

163MusicPro 是一个面向儿童手表/手表形态设备的网易云音乐客户端。项目聚焦于“小屏、触控面积有限、系统版本较老”的使用场景，围绕在线播放、歌词、下载、收藏、铃声、排行榜和账号能力进行了适配。

当前版本：`20260402`

## 主要特性

- **手表界面适配**
  - 重点针对 320×360 屏幕设计布局与交互
  - 多数功能页面使用大按钮、平铺入口与滚动容器
- **网易云歌曲能力**
  - 搜索歌曲
  - 获取歌曲播放链接并在线播放
  - 展示歌词并随播放进度滚动高亮
  - 排行榜、私人漫游、个人中心等接口接入
- **播放器能力**
  - 播放 / 暂停 / 上一首 / 下一首
  - 列表循环、单曲循环、随机播放
  - 倍速播放
  - 音调保持 / 音调随倍速变化两种模式
  - 定时停止播放
- **本地能力**
  - 歌曲下载
  - 下载歌曲离线播放
  - 收藏与历史记录持久化
  - 截取歌曲片段设置铃声
- **账号能力**
  - 扫码登录
  - 手动 Cookie 登录
  - VIP 音乐播放支持
- **后台保活**
  - 前台服务通知
  - WakeLock 保活
  - 尽量降低手表系统回收播放进程的概率

## 为什么是这个项目

- 面向手表而不是手机：交互密度、文字尺寸、页面结构都围绕小屏设备设计
- 避免复杂依赖：保持简单的原生 Android Java 工程结构，便于继续维护
- 功能集中：从搜索、播放到下载、歌词、铃声在单个客户端内完成

## 项目结构

```text
.
├── app/
│   ├── src/main/java/com/qinghe/music163pro/
│   │   ├── activity/      # 页面与交互
│   │   ├── api/           # 网易云 API 请求与加密逻辑
│   │   ├── manager/       # 下载/收藏/历史/铃声等管理器
│   │   ├── model/         # 数据模型
│   │   ├── player/        # 播放器状态与控制
│   │   └── service/       # 前台播放服务
│   └── src/main/res/      # 布局、颜色、字符串、样式
├── gradle/                # Gradle Wrapper
└── README.md
```

## 运行环境

| 项目 | 值 |
| --- | --- |
| Application ID | `com.qinghe.music163pro` |
| 最低系统 | Android 6.0 (API 23) |
| 目标系统 | Android 8.1 (API 27) |
| 编译 SDK | Android 14 (API 34) |
| 推荐设备 | 小天才电话手表 / 320×360 屏幕设备 |
| 当前版本 | `20260402` (`versionCode 6`) |

## 快速开始

### 1. 获取代码

```bash
git clone https://github.com/9xhk-1/163MusicPro.git
cd 163MusicPro
```

### 2. 构建调试包

```bash
./gradlew assembleDebug
```

构建产物默认位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 3. 构建签名发布包

```bash
export KEYSTORE_BASE64="<base64 编码后的 keystore>"
export KEYSTORE_PASSWORD="<keystore 密码>"
export KEY_ALIAS="<别名>"
export KEY_PASSWORD="<key 密码>"

echo "$KEYSTORE_BASE64" | base64 -d > release.keystore
./gradlew assembleRelease
```

发布包默认位于：

```text
app/build/outputs/apk/release/app-release.apk
```

## 使用说明

1. 安装 APK 到手表。
2. 打开应用后，在主界面控制播放、音量、进度与更多功能。
3. 点击 **更多** 可进入搜索、下载、排行榜、历史记录、设置等页面。
4. 如需登录网易云账号，请进入 **更多 > 登录**：
   - **扫码登录**
   - **手动 Cookie 登录**
5. 如需查看版本与更新内容，请进入 **更多 > 设置 > 关于**。

## 数据与存储

- 收藏数据：`/sdcard/163Music/favorites.json`
- 历史记录：`/sdcard/163Music/history.json`
- 下载目录：`/sdcard/163Music/Download/`
- 歌词优先读取本地 `lyrics.lrc`，缺失时再请求在线歌词

## GitHub Actions / 自动发布

仓库已配置 GitHub Actions 工作流，用于构建 APK 与发布版本。

如需启用签名发布，请在仓库 Secrets 中配置：

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

## 更新日志

### 20260402

- 登录页面仅保留扫码登录与 Cookie 登录入口
- 修复歌词页面停留时切到下一首后歌词不刷新的问题
- 重写 README，补全文档结构、构建说明与使用说明

### 20260401

- 修复右滑退出被全局禁用导致的页面返回问题
- 仅在主播放页禁用系统右滑退出，其他页面恢复正常右滑
- 优化歌词/更多功能面板的右滑关闭行为

### 20260331-fix1

- 修复返回播放界面时音乐意外自动播放
- 放大关于页面文字以适配手表屏幕
- 设置页面移除登录入口，仅保留更多页面中的登录入口

### 20260331

- 新增变速模式设置
- 登录相关功能移至“更多 > 登录”
- 设置页面改为平铺列表风格
- 新增开关选项页与关于页面
- 修复铃声管理名称秒数重复显示

## 开发说明

- 项目当前为原生 Android Java 工程
- UI 修改需优先考虑手表 320×360 屏幕显示效果
- 版本号使用日期格式：`年月日(-fixN)`
- 发布新版本时，需要同步更新应用内“设置 > 关于”的更新内容

## 致谢

- 网易云音乐开放接口相关实现思路参考：
  - https://github.com/nooblong/NeteaseCloudMusicApiBackup

## 免责声明

本项目仅用于学习与交流，请在符合相关法律法规与服务条款的前提下使用。
