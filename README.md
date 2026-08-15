# 极简直播（EasyLive）

面向**适老化**的安卓直播源播放器。系统轻量、兼容低版本安卓（最小 Android 5.0），进入即播、单手大按钮、防误触。

## 核心特性

- **进入即播**：自动续播上次频道，没有记录就播第一个
- **扁平频道列表**：M3U 与简易列表统一成「一个列表」，不套分类（适老化）
- **分区手势**：
  - 屏幕**左 1/3 上下滑** → 调亮度
  - **中 + 右 2/3 上下滑** → 换台（上滑下一台 / 下滑上一台）
  - **单击屏幕** → 显示/收起控制层（4 秒无操作自动隐藏）
- **控制层**：设置、锁定（防误触）、频道列表
- **锁定防误触**：锁定后触摸手势失效（防手机误触），遥控/键盘仍可用，长按任意键解锁
- **设置页**：开机启动（开机自启）、直播源管理（网址拉取 / 本机文件导入 / 清空）
- **大字号、大触控区、高对比**，竖横屏自适应（跟随手机旋转）
- **遥控 / 键盘支持**：不上架商店、无需 Leanback；方向键换台、左右调亮度、数字键选台、长按任意键解锁（见下节）

## 遥控 / 键盘操作

支持红外 / 蓝牙遥控与键盘。触摸手势与按键两套交互并列存在：

| 按键 | 功能 |
| --- | --- |
| 上 / 下 | 上一台 / 下一台 |
| 左 / 右 | 调暗 / 调亮 |
| OK / 确认（Enter） | 显示 / 收起控制层 |
| 菜单键 | 打开频道列表 |
| 数字 0-9 | 选台：输入「列表序号」（如按 5 = 第 5 个频道）；连续输入多位，1.5 秒无输入自动确认，按 OK 立即确认 |
| 长按任意键 | 锁定时解锁 |

锁定策略：锁定只封**触摸手势**（防手机误触），遥控 / 键盘在锁定时仍可换台、调亮度、数字选台；长按任意遥控键即解锁。控制层与频道列表在锁定时不打开（防误改设置）。

## 技术栈

- Kotlin + ViewBinding（比 Compose 轻，老机不丢帧）
- 播放内核：AndroidX Media3 ExoPlayer（原生支持 HLS/m3u8）
- 本地存储：Room（频道）+ DataStore（设置 / 上次频道）
- 最小 SDK 21，目标 34

## 服务器直播源格式（简易列表）

在服务器放一个 **UTF-8 纯文本**文件（如 `channels.txt`），一行一个频道：

```
频道名称,播放地址
频道名称,播放地址,台标图片地址
```

规则：
- 空行、以 `#` 开头的行 = 注释，忽略
- 没有逗号 → 整行当播放地址，频道名自动取地址
- 第三字段（台标）可选，没有就显示文字占位
- **播放地址里不要出现英文逗号**

示例：

```
# 我的电视直播源
CCTV-1,http://your-server.com/live/cctv1.m3u8
CCTV-5,http://your-server.com/live/cctv5.m3u8,http://your-server.com/logo/cctv5.png
湖南卫视,http://your-server.com/live/hunan.m3u8
```

## M3U / M3U8 格式

标准 M3U 直接导入即可，`#EXTINF` 取名称与 `tvg-logo`，下一行取地址。
**分类（group-title）会被忽略**，所有频道进同一个平列表。示例见 `channels.m3u`。

## 使用流程

1. 用 Android Studio 打开本工程根目录（`live-player/`）
2. 连接安卓设备 / 模拟器，运行 `app`
3. 首次进入因无频道会提示「去添加直播源」→ 设置页填写你的 `channels.txt` 网址 → 「更新直播源」
4. 之后进入 app 即自动播放

## 构建 APK（打包）

### 方式一：GitHub Actions 自动打包（推荐，本机零环境）
1. 把本仓库推到 GitHub
2. 在仓库 **Actions** 页，工作流 `Build Debug APK` 会在 push 到 `main`/`master` 或手动 `workflow_dispatch` 时自动运行
3. 运行完成后，到 Actions 页面的 **Artifacts** 下载 `app-debug.apk`
4. 手机允许「未知来源」后安装，开屏即播（已内置默认直播源）

配置见 `.github/workflows/build.yml`（GitHub 运行器网络开放，可正常下载 Android SDK 与依赖）。

### 方式二：Android Studio 本地构建
1. 打开 `live-player/` 目录
2. 等待 Gradle Sync 完成（需联网下载 SDK 与依赖）
3. 菜单 **Build → Build Bundle(s)/APK(s) → Build APK(s)**
4. 产物：`app/build/outputs/apk/debug/app-debug.apk`

### 方式三：命令行
```bash
# 需先装好 Android SDK 并配置 ANDROID_HOME，且网络可达 dl.google.com
gradle assembleDebug      # 本机已装 Gradle 8.5 时
```

### 已内置默认直播源
`app/src/main/res/values/strings.xml` 的 `default_source_url` 已设为调试用源，首次启动无频道时自动拉取，开屏即播。正式发布前改成你自己的 `channels.txt` 地址，或留空让用户在设置里添加。

### 环境要求
- JDK 17（AGP 8.2 要求，勿用 JDK 21 跑 AGP 8.2）
- Android SDK Platform 34 + Build-Tools 34.0.0
- Gradle 8.5

## 开机自启说明

开启「开机启动」后，设备开机且用户已在系统「自启动管理」授权时，会自动拉起并续播。
部分国产 ROM（小米 / 华为 / OPPO / VIVO 等）默认拦截自启，需在系统设置里手动允许本应用自启动。

## 目录结构

```
app/src/main
├── AndroidManifest.xml
├── java/com/easylive/player
│   ├── PlayerActivity.kt          # 主控：播放/手势/控制层/抽屉/续播
│   ├── player
│   │   ├── GestureOverlay.kt       # 屏幕三等分手势分区
│   │   ├── BrightnessHelper.kt     # 亮度控制
│   │   └── LogoLoader.kt           # 台标加载（无第三方库）
│   ├── data
│   │   ├── Channel.kt / ChannelDao.kt / AppDatabase.kt
│   │   ├── Preferences.kt          # DataStore：上次频道/开机/源URL/亮度
│   │   └── SourceRepository.kt     # M3U + 简易列表解析与入库
│   ├── ui
│   │   ├── SettingsActivity.kt     # 开机启动 + 直播源管理
│   │   └── ChannelListAdapter.kt   # 大字号频道列表
│   └── receiver/BootReceiver.kt    # 开机广播
└── res/...                         # 布局/字符串/主题
```
