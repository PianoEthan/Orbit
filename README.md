# Orbit - Optimized Reincarnation of BiliTerminal

> [!NOTE]
> **多模块架构与多端适配**
>
> 本项目已重构为「统一底层内核 + 多端 UI」架构：
> - `:core`：跨端通用业务内核，承载账号凭据、WBI 签名、用户中心数据、动态、搜索及音视频解析等底层接口能力，通过 Kotlin Multiplatform 为 Android 与 Desktop JVM 提供业务接口和 JVM 实现；
> - `:wear`：专为 Wear OS 手表设计的客户端界面与触觉扩展，使用 Wear Material 3；
> - `:vision`：Orbit Vision 的 Compose Multiplatform 界面，供 Android 手机、平板、电视与 Desktop JVM 共用。
> - `:vision-android`：Orbit Vision 的 Android 应用入口、资源和 APK 构建。

哔哩终端的 Wear OS 重写项目。

## 简介

Orbit 是一个运行在 Wear OS 智能手表上的第三方哔哩哔哩客户端，借鉴了哔哩终端的部分 API 接口调用和 UI 设计。

使用Jetpack Compose + Wear Material 3实现，网络层重构为Retrofit+Okhttp，相比原项目更加现代。

目前已经实现大部分基本功能，如看视频、浏览弹幕、发送评论、查看动态、观看直播、离线缓存等等。

内置 Apsis Player, 支持双模播放引擎切换、双模弹幕引擎切换，支持双指缩放平移手势，可开关弹幕、拖动全屏调节进度等，点击标题区域退出。

拥有一些首创的特色功能，比如推荐页左滑视频可一键净化（隐藏+报告不感兴趣+拉黑UP主），评论区长按可隐藏评论、删除自己的评论、管理自己内容下的置顶评论或拉黑发送者，动态长按可分别置顶、取消置顶、隐藏或删除自己的动态，也可拉黑其他发布者。

注意：项目仍在开发阶段，部分功能仍处于实验性，有Bug请到仓库 issue 反馈，有能力欢迎提交pr。

## 关于 Orbit Vision

Orbit Vision 面向 Android 手机、平板、电视及桌面系统，与 Orbit（Wear OS）共用 Kotlin Multiplatform 业务库 `:core`。

界面基于 Compose Multiplatform 与 Material 3 Expressive，支持沉浸式无顶栏布局、手机底栏与大屏侧边栏（NavigationRail）响应式自适应切换，提供深色、浅色及 Android 12+ 动态壁纸配色。

目前已经支持推荐与热门流、视频详情与分P选集、UP主空间与投稿查看、关注与拉黑、关注/粉丝列表、相关推荐与系列合集、番剧/国创/电影/电视剧/纪录片/综艺全量索引、剧集多季切换与追番追剧、直播观看（推荐/关注主播列表与直播间清晰度切换）、动态图文与多图查看器、评论区主评与楼中楼子回复发表、消息中心（回复/点赞/@/系统通知）与私信收发、分类搜索（视频/直播/用户/番剧）、稍后再看与多收藏夹管理等等。

Android 播放器默认采用 ijkPlayer 进行硬件加速解码，并在高画质流仅提供 DASH 格式时智能调度 Media3 (ExoPlayer) 进行音视频自动合并，确保 1080P 高清流畅播放；内置 DFMNext 高性能 60 帧平滑弹幕引擎，支持 CC 字幕切换、视频缩放平移、洗脑循环与自动连播，各清晰度以账号实际授权为准。桌面默认使用内置 mpv。在「设置 → 播放与控制」中始终显示 VLC，检测到兼容的 LibVLC 3 时可切换。播放器自动尝试硬件解码，驱动或格式不支持时使用软件解码。

专为电视遥控器打造舒适的交互体验：项目获焦时具备富有表现力的平滑形变与高亮微动效，独立搜索页采用左侧输入与右侧热词建议的左右分栏布局，并针对 D-pad 遥控器首创免弹窗的内联九键输入浮层。

注意：Vision 模块仍在活跃演进中，已支持视频弹幕发送（颜色/模式）、专栏与图文阅读落地页（点赞/投币/收藏）、动态详情与转发、收藏夹新建/重命名/删除/批量移动；离线缓存、后台音频/PiP、互动视频、直播弹幕发送与多账号等特性正在持续对齐，欢迎体验与反馈。

## 技术栈

- **UI**: Wear 使用 Jetpack Compose 和 Wear Material 3，Vision 使用 Compose Multiplatform 和 Material 3 Expressive
- **网络**: Retrofit + OkHttp + Gson
- **视频播放**: ijkplayer (ARM64/ARM32 硬件加速) / Media3-ExoPlayer (DASH/MP4 自适应)
- **弹幕**: DFM-Next (60帧平滑渲染) / DanmakuFlameMaster
- **图片加载**: Coil
- **小米 / OPPO 手表触觉兼容**：CN Wear Overlay

## 构建

请先同步 Git 子模块，构建需要 DFMNext：

```bash
git submodule sync --recursive
git submodule update --init --recursive
```

若您无法通过 SSH 克隆，建议自行修改 .git/config 在本地修改子模块地址为 HTTPS 或镜像。

```bash
# 构建手表端安装包 (Wear OS)
./gradlew :wear:assembleLiteDebug
./gradlew :wear:assembleFullDebug

# 构建 Android Vision（手机、平板和电视）
./gradlew :vision-android:assembleDebug

# 启动桌面 Vision
./gradlew :vision:run

# 生成当前桌面系统的安装包，包含 mpv 运行库
./gradlew :vision:packageDistributionForCurrentOS -Porbit.mpvRuntimeDir=/path/to/mpv-runtime
```

Android 界面测试使用 `./gradlew :vision:connectedAndroidDeviceTest`，需要连接设备或模拟器。桌面打包需要提供与目标系统和架构匹配、含依赖的可移植 libmpv 目录。Windows 使用 DLL 运行库，macOS 使用以 `@loader_path` 等相对路径定位依赖的 dylib 运行库。Linux 可用 `python3 vision/scripts/prepare-mpv-linux.py /path/to/libmpv.so.2 /path/to/mpv-runtime` 准备目录，构建机需安装 `patchelf`。生成的安装包内置 mpv，运行端使用系统 GPU 驱动。源码开发启动可使用系统 libmpv。

## 系统要求

- 最低系统：Android 7.0+ (API 24+)。不保证全部功能正常使用。
- 目标系统：Wear OS 6.0+ (API 36+)。
- 推荐系统：Wear OS 3.0+ (API 30+)。
- 最低内存(RAM)：1G
- 最低储存(ROM)：4G
- 推荐内存(RAM)：2G+
- 推荐储存：8G+

## 和其他开源项目的关系

- 本项目参考了[哔哩终端](https://github.com/huanli233/BiliClient)的API调用逻辑。
- 本项目使用了[IJKPlayer](https://github.com/bilibili/ijkplayer)和[Media3-ExoPlayer](https://github.com/androidx/media)作为视频播放器。
- 本项目使用了[烈焰弹幕使](https://github.com/bilibili/danmakuflamemaster)和[DFM-Next](https://github.com/YNotBili/DFMNext)作为弹幕引擎。
- 本项目通过 [CN Wear Overlay](https://github.com/Star-ZER0/cnwearoverlay) 的 Gradle 插件适配小米 / OPPO 手表的表冠触觉，并修正部分小米触觉常量；未接入其 Xposed 模块。
- 本项目与 [BiliZepam](https://github.com/Re-BiliTerminal/BiliZepam-Compose) 没有任何关系。
- 本项目与 [Re-WearBili](https://github.com/SpaceXC/Re-WearBili) 没有任何关系。

## 许可证与声明
- 本项目使用 [GNU GPL-3.0 License](LICENSE) 开源。
- 详细的用户权益、隐私安全、使用免责及开源规范，请查阅 [Orbit 用户协议与声明](STATEMENT.md)。
