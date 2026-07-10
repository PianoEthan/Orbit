# Orbit - Optimized Reincarnation of BiliTerminal

哔哩终端的 Wear OS 重写项目。

## 简介

Orbit 是一个运行在 Wear OS 智能手表上的第三方哔哩哔哩客户端，借鉴了哔哩终端的部分 API 接口调用和 UI 设计。
使用Jetpack Compose + Wear Material 3实现，网络层重构为Retrofit+Okhttp，相比原项目更加现代。
目前已经实现大部分基本功能，如看视频、浏览弹幕、发送评论、查看动态、观看直播、离线缓存等等。
内置播放器基于ijkplayer，支持双指缩放平移手势，可开关弹幕、拖动全屏调节进度等，点击标题区域退出。
拥有一些首创的特色功能，比如推荐页左滑视频可一键净化（隐藏+报告不感兴趣+拉黑UP主），评论区长按评论可隐藏评论+拉黑发送者。
注意：项目仍在开发阶段，部分功能仍处于实验性，有Bug请到仓库 issue 反馈，有能力欢迎提交pr。

## 技术栈

- **UI**: Jetpack Compose + Wear Material 3
- **网络**: Retrofit + OkHttp
- **视频播放**: ijkplayer
- **弹幕**: DanmakuFlameMaster
- **图片加载**: Coil


## 构建

请先同步 Git 子模块，否则无法构建 DFMNext 导致构建失败！

若您无法通过 SSH 克隆，建议自行修改 .git/config 在本地修改子模块地址为 HTTPS 或镜像。

```bash
./gradlew assembleDebug
```

## 系统要求

- 最低系统：Android 6.0+ (API 23+)。不保证全部功能正常使用。
- 目标系统：Wear OS 6.0+ (API 36+)。
- 推荐系统：Wear OS 3.0+ (API 30+)。
- 最低内存(RAM)：1G
- 最低储存(ROM)：4G
- 推荐内存(RAM)：2G+
- 推荐储存：8G+

## 和其他开源项目的关系

- 本项目参考了[哔哩终端](https://github.com/huanli233/BiliClient)的API调用逻辑。
- 本项目使用了[IJKPlayer](https://github.com/bilibili/ijkplayer)作为视频播放器。
- 本项目使用了[烈焰弹幕使](https://github.com/bilibili/danmakuflamemaster)作为弹幕引擎。
- 本项目与 [BiliZepam](https://github.com/Re-BiliTerminal/BiliZepam-Compose) 没有任何关系。
- 本项目与 [Re-WearBili](https://github.com/SpaceXC/Re-WearBili) 没有任何关系。

## 许可证
- 本项目使用 [GNU GPL-3.0 License](LICENSE) 开源。
- 致某些拿开源项目肆意妄为的人：GPL 遵循的是完全的对等自由。如果你使用了社区的免费、开源成果，你就必须把自己的成果也同样免费、开源地回馈给社区。
