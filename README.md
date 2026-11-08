# Orbit - Optimized Reincarnation of BiliTerminal

> [!NOTE]
> **多模块架构与多端适配**
>
> 本项目已重构为「统一底层内核 + 多端 UI」架构：
> - `:core`：跨端通用业务内核，承载账号凭据、WBI 签名、用户中心数据、动态、搜索及音视频解析等底层接口能力，无平台 UI 依赖；
> - `:wear`：专为 Wear OS 手表设计的客户端界面与触觉扩展，使用 Wear Material 3；
> - `:vision`：适配手机、平板与电视大屏的客户端（Orbit Vision），使用标准 Material 3 Expressive 设计，并支持遥控器 D-pad 与九键输入双模适配。

哔哩终端的 Wear OS 重写项目。

## 简介

Orbit 是一个运行在 Wear OS 智能手表上的第三方哔哩哔哩客户端，借鉴了哔哩终端的部分 API 接口调用和 UI 设计。

使用Jetpack Compose + Wear Material 3实现，网络层重构为Retrofit+Okhttp，相比原项目更加现代。

目前已经实现大部分基本功能，如看视频、浏览弹幕、发送评论、查看动态、观看直播、离线缓存等等。

内置 Apsis Player, 支持双模播放引擎切换、双模弹幕引擎切换，支持双指缩放平移手势，可开关弹幕、拖动全屏调节进度等，点击标题区域退出。

拥有一些首创的特色功能，比如推荐页左滑视频可一键净化（隐藏+报告不感兴趣+拉黑UP主），评论区长按可隐藏评论、删除自己的评论、管理自己内容下的置顶评论或拉黑发送者，动态长按可分别置顶、取消置顶、隐藏或删除自己的动态，也可拉黑其他发布者。

注意：项目仍在开发阶段，部分功能仍处于实验性，有Bug请到仓库 issue 反馈，有能力欢迎提交pr。

## 技术栈

- **UI**: Jetpack Compose + Wear Material 3
- **网络**: Retrofit + OkHttp
- **视频播放**: ijkplayer(可选)/ExoPlayer
- **弹幕**: DanmakuFlameMaster/DFM-Next
- **图片加载**: Coil
- **小米 / OPPO 手表触觉兼容**：CN Wear Overlay

## 构建

请先同步 Git 子模块，构建需要 DFMNext 和 CN Wear Overlay：

```bash
git submodule sync --recursive
git submodule update --init --recursive
```

若您无法通过 SSH 克隆，建议自行修改 .git/config 在本地修改子模块地址为 HTTPS 或镜像。

```bash
# 构建手表端安装包 (Wear OS)
./gradlew :wear:assembleLiteDebug
./gradlew :wear:assembleFullDebug

# 构建大屏与手机端安装包 (Orbit Vision)
./gradlew :vision:assembleDebug
```

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
