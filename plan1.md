# 视频详情：元素从上到下渐显 + 异步加载解耦

已确认范围：**视频详情首屏（信息页）+ 评论页 + 相关推荐页**；加载策略：**信息到达即上屏 + 并行加载 + 取色移出主线程**。

## 目标与验收
1. 进入 `detail/{bvid}/{aid}`：封面 → 标题 → UP 主胶囊 → 元信息 → 简介 → 合集卡片 → 播放 → 三连 → 稍后再看 → 缓存，依次淡入（淡入 + 12dp 上移），整列入场 ≈ 530ms，**不改变任何布局高度**。
2. 视频信息返回后立即上屏，不再等封面下载与取色量化；消除"空内容 → 转圈 → 内容"的闪烁。
3. 滑到评论页/相关推荐页时，该页首批元素同样从上到下渐显一次；分页追加的条目不再动画。
4. 点赞/投币/收藏导致的 `videoInfo` 更新不再重新下载封面、不再重复取色；量化在后台线程执行。
5. 评论不再被相关推荐阻塞（三路数据并行）。
6. `:app:compileLiteReleaseKotlin` 与 `:app:compileFullReleaseKotlin` 通过（与 CI 一致）。
7. 设置 → 偏好设置 → **"详情页元素渐显"**（`ui_entrance_animation`，默认开）可整体关闭入场动画。

## 现状（已核对行号）
- `presentation/VideoDetailScreen.kt` 共 2068 行：
  - L880 `AnimatedContent` 的 targetState 含 `(videoInfo != null && !isColorExtracted)` → **整页被封面下载 + 取色阻塞**；且 t=0 时 `isLoading` 仍为 false，会先闪一帧空内容。
  - L222-247 `LaunchedEffect(videoInfo)` 以整个 `videoInfo` 为 key，stats 变更即重新下载封面，并在**主线程**跑 `extractSeedColorFromBitmap`（`QuantizerCelebi.quantize`，128×128）。
  - L1358-1921 `VideoInfoPage`：`TransformingLazyColumn` 内约 10 个有内容的 `item {}`，统一使用 `Modifier.adaptiveTransformedHeight(...).graphicsLayer{ applyContainerTransformation }`。
  - L1923-2028 `VideoCommentsPage`、L2030-2068 `VideoRelatedPage`：数据异步到达的独立 LazyColumn。
- `presentation/viewmodel/VideoDetailViewModel.kt` L71-115：串行链路 info → history → 上屏 → tags → related → loadReplies，评论被相关推荐阻塞。
- `presentation/ui/components/AdaptiveTransforms.kt` 提供了 `@Composable fun Modifier.xxx(...): Modifier` 的写法，新组件照此风格。
- 依赖齐备（compose BOM / wear compose / coil 2.7.0 / coroutines 1.11.0），**不新增依赖**。

## A. 新组件：`presentation/ui/components/EntranceAnimation.kt`

```kotlin
@Composable
fun Modifier.staggeredEntrance(
    index: Int,
    enabled: Boolean = true,
    durationMillis: Int = 260,
    stepMillis: Int = 45,
    maxStaggerSteps: Int = 6,
    offsetDp: Float = 12f,
): Modifier {
    val offsetPx = with(LocalDensity.current) { offsetDp.dp.toPx() }
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enabled) {
            delay((index.coerceIn(0, maxStaggerSteps) * stepMillis).toLong())
            progress.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing))
        }
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
    }
}

@Composable
fun rememberEntranceWindow(trigger: Boolean, windowMillis: Long = 650L): Boolean
```
- `graphicsLayer` 只改绘制层：不触发布局、LazyColumn 项高度不跳动；滚出屏幕后才首次组合的项拿到 `enabled=false`，直接以 1f 出现，不会"滚到哪慢半拍"。
- `enabled` 仅在首次组合生效（`remember` + `LaunchedEffect(Unit)`），之后调用方翻转布尔值不会重置已播放元素。
- `rememberEntranceWindow`：trigger 首次为 true 时返回 true，`windowMillis` 后转 false，并用 `remember` 记住"已播放"防止数据增量重播；内部只读一次 `Settings.Global.ANIMATOR_DURATION_SCALE`（try/catch），为 0 时始终返回 false（尊重系统"移除动画"）。
- 用户开关：`rememberEntranceWindow` 在系统动画缩放之外再与一次 `SharedPreferencesUtil.getBoolean("ui_entrance_animation", true)`，三处接入点同时受控（`EntranceAnimation.kt` 内以字面量 key 读取，与项目既有偏好用法一致）。开关在 `remember` 中只读一次，改完设置返回详情页即生效；不追求"详情页开着时实时生效"。

## B. 三处接入（`presentation/VideoDetailScreen.kt`）

**VideoInfoPage（L1359）**：函数体顶部 `val entranceActive = rememberEntranceWindow(trigger = videoInfo != null)`，按 LazyColumn 顺序在既有修饰符链末尾追加 `.staggeredEntrance(idx, enabled = entranceActive)`：

| idx | 行号 | 元素 |
|---|---|---|
| 0 | 1402-1421 | 封面 AsyncImage |
| 1 | 1424-1435 | 标题 Text |
| 2 | 1438-1486 | UP 主胶囊 LazyRow（staff 为空时 item 零高度，无副作用） |
| 3 | 1489-1590 | 元信息 Column |
| 4 | 1595-1660 | 简介 ClickableText |
| 5 | 1664-1708 | 合集入口 Button |
| 6 | 1711-1724 | 播放按钮 |
| 7 | 1727-1875 | 三连 ButtonGroup |
| 8 | 1879-1895 | 稍后再看 Button |
| 9 | 1900-1915 | 缓存 Button |

纯 `Spacer` item 与顶部 20dp 返回热区不参与；索引超过 6 由 `maxStaggerSteps` 截断（0/45/90/135/180/225/270/270ms）。

**VideoCommentsPage（L1923）**：`rememberEntranceWindow(trigger = replies.isNotEmpty())`；header（L1957）、发布评论按钮（L1970）、回复卡片（L2007，`enabled = entranceActive && index < 6`，idx 取 `index + 2`）。保留 `animateItem()`。

**VideoRelatedPage（L2030）**：`trigger = relatedVideos.isNotEmpty()`；header（L2046）与卡片（L2056，`index < 6`）同上。

三个页面的 `ScreenScaffold` / `rotaryScrollableBehavior` / 圆屏 `SurfaceTransformation` 一律不动。

## C. 首屏闸门 + 取色异步化（同文件）

1) L878-910 改为：
```kotlin
val showLoading = videoInfo == null && errorMessage == null
AnimatedContent(targetState = showLoading, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }) { loading ->
    if (loading) { /* 转圈 */ }
    else if (errorMessage != null && videoInfo == null) { /* 原失败重试页 */ }
    else { /* 原内容分支，保持不变 */ }
}
```
- 去掉 `!isColorExtracted` 项 → 内容不再等取色；`videoInfo == null` 即视为加载中 → 修掉首帧空内容闪烁。
- 删除随之无引用的 `isColorExtracted`（L219、L244-246）与 `val isLoading by ...collectAsState()`（L177）。

2) L222-247 取色：
- key 由 `videoInfo` 改为 `videoInfo?.cover`，并用 `extractedCoverKey` 去重 → 点赞/投币不再重复下载与量化。
- `extractSeedColorFromBitmap` + `generateWearColorSchemeFromSeed` 包进 `withContext(Dispatchers.Default)`（新增 `Dispatchers` / `withContext` 导入）；Coil `imageLoader.execute` 本身挂起。
- 成功后照旧写 `dynamicColorScheme` 与 `ActiveDynamicTheme.colorScheme`，失败回落默认配色。
- 明确接受：动态配色到达时（约 200-800ms）整体配色切换一次，落在淡入窗口内被动画部分掩盖；不额外做配色过渡。

## D. ViewModel 渐进 + 并行（`presentation/viewmodel/VideoDetailViewModel.kt` L71-115）

1. 只 `async { VideoInfoApi.getVideoInfo(bvid) }`，拿到 info 后立即修正 `bvid/aid` 并 `_videoInfo.value = info`（**先上屏**）。
2. 三路并行，各用 `viewModelScope.launch { runCatching { ... } }`（互不取消、互不阻塞）：
   - 历史进度 `PlayerApi.getHistoryProgress(aid, cids.first())` → `_videoInfo.update { (it ?: info).copy(history = history) }`（新增 `kotlinx.coroutines.flow.update` 导入）。
   - `_tags.value = VideoInfoApi.getTags(realBvid)`。
   - `_relatedVideos.value = RecommendApi.getRelated(realAid)`。
3. `loadReplies(reset = true, focusedRootId, focusedReplyId)` 在 info 就绪后立即调用（保留 `_isReplyLoading` 防重入；此时 `aid` 已修正）。
4. `_isLoading` 语义收窄为"首次信息加载中"，`finally` 里照旧置 false；失败路径 `_errorMessage` 不变，重试时清空。

## 边界与失败模式
- 取色失败 / 封面 404：`dynamicColorScheme` 保持 null，默认配色，入场动画不受影响。
- 评论或相关推荐失败：`runCatching` 吞掉、列表保持空，原有 `replyErrorMessage` 重试卡片逻辑不变。
- 信息失败：`errorMessage != null && videoInfo == null` → 原失败页；点重试清空 error → 回转圈。
- 快速进出/左右翻页：页面重组会重播一次入场（符合"进入即渐显"语义）。
- 系统动画缩放为 0：整页不做入场动画。
- 开关关闭：行为等同系统"移除动画"，三个页面都不播；再打开后重新进入详情页恢复。
- 圆屏与非圆屏：入场只作用于绘制层，与 `transformedHeight` / `SurfaceTransformation` 可叠加，两形态都需验证。
- 性能：单屏最多约 10 个 `Animatable` + 每项一个离屏层，Wear 可接受；刻意不用 `AnimatedVisibility`（会改布局高度、导致 Lazy 项跳动）。

## 验证
- 本环境无法起模拟器，用 `./gradlew.bat :app:compileLiteReleaseKotlin :app:compileFullReleaseKotlin`（与 CI 一致）；若 Gradle 无法离线运行，明确报告原因并退化为静态自查。
- 静态自查：无未使用变量/导入；`isColorExtracted` 全仓库无残留；三页面修饰符链顺序与既有 `graphicsLayer` 不冲突。
- 真机验收（用户执行）：按验收标准 1-5 观察；重点确认进入首屏不再等待取色、点赞后无第二次封面请求（网络面板/Logcat）。

## 执行顺序（AGENTS.md 要求 goal + task）
0. 写 `plan1.md`（本方案）到项目根目录。
1. 新建 `EntranceAnimation.kt`。
2. VideoInfoPage 接入（10 个元素）。
3. VideoCommentsPage / VideoRelatedPage 接入。
4. 首屏闸门重写 + 取色异步化 + 清理无用状态。
5. ViewModel 渐进并行加载。
6. 设置页"偏好设置"段新增开关 `ui_entrance_animation`（默认开），`rememberEntranceWindow` 与之做与运算。
7. 编译两变体 + 静态自查 + 提交（当前工作区干净；信息如 `feat:视频详情进入时元素渐显并异步加载`，如有本地改动并入同一次提交）。

## 显式不做
- 不改 BangumiDetailScreen / ArticleDetailScreen / DynamicDetailScreen / LiveDetailScreen（组件已可复用，后续单独接入）。
- 不改导航转场、不加骨架屏、不调视觉样式与间距、不重构 `ReplyCard`/`RecommendVideoCard`、不动评论自动加载更多逻辑。
