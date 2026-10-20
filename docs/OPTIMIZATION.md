# 优化方案（2026-10-21）

本文件记录本轮针对墨水屏使用体验与热路径性能的优化：动机、做法、验证方式，
以及评估后**放弃**的项与原因。所有改动在 `optimize/eink-render-and-state`
分支，以独立小提交提交；行为变化同步更新到 README。

## 背景与痛点

项目是 Android 墨水屏设备上的 Hidato 数字连线游戏（Kotlin + Compose +
Hilt + Room）。领域层（生成、求解、最少线索优化）经过多轮打磨，本轮审查
发现的瓶颈集中在 UI 与状态层：

1. **棋盘渲染**：每个格子是一个 Material3 `OutlinedButton`。15×15 棋盘要组合
   约 225 个按钮节点，每个带独立语义节点、最小交互尺寸处理与涟漪指示；
   任意一步走棋（状态对象整体替换）引发全棋盘重组。墨水屏刷新慢，多余
   的绘制与动画只会拖慢刷新并加重残影。
2. **状态热路径分配**：`GameState.playerCells` 每次访问都 `toSet()` 新建
   集合、`placedCount` 每次访问都 O(N) 扫描；棋盘每次重绘对每个格子调用
   `isVisibleNumber`（内部再访问 `playerCells`），一次 15×15 全量重绘即
   数百次 Set 分配与 O(N²) 扫描。
3. **主题**：README 已声明的缺口——沿用了 Material You 动态取色，从壁纸
   取色在 16 级灰阶墨水屏上只能得到低区分度的脏灰；涟漪等指示动画同理
   无法呈现。

## 已实施

### 1. 单 Canvas 棋盘渲染（`perf(ui)`）

- **动机**：把组合复杂度从 O(格子数) 降到 O(1)，走步后只需重绘一个绘制层；
  消除按钮涟漪与每格语义节点；文本测量按数字缓存，避免每帧重复布局。
- **做法**：
  - 新增 `ui/game/BoardGeometry.kt`：格子下标 ↔ 像素坐标的纯 Kotlin 换算，
    点击命中 `cellAt()` 由 `BoardGeometryTest`（JVM 单测）全覆盖；
  - `ui/game/BoardCanvas.kt` 重写为一个 `Canvas` + `pointerInput`
    `detectTapGestures`：格子底/描边、数字文本（`TextMeasurer` 布局按数字
    缓存，格宽变化时随 `remember(cell)` 失效）、焦点高亮、相邻已现数字对
    连线（与旧实现一致只画格子边缘）；
  - 保留 48dp 最小格宽与大盘滚动；点击命中为整格（旧按钮有内边距缝隙）；
  - 通关后点击由 `state.completed` 门控（与旧按钮 `enabled` 等价）。
- **权衡**：以整板 `contentDescription` 替代原先每格一个语义节点。读屏
  用户失去逐格导航，换来墨水屏上大得多的重绘收益；`boardContentDescription()`
  汇总行列、已显示数字数与焦点位置。若未来有强无障碍需求，可在 Canvas 外
  叠加隐藏的语义节点。
- **验证**：`BoardGeometryTest` 4 组断言（命中映射、越界/非法格宽、5×7
  非方格全格覆盖、中心点换算）；全量 JVM 单测通过；`assembleDebug` 编译通过。

### 2. GameState 记账缓存（`perf(domain)`）

- **动机**：见痛点 2。`GameState` 不可变，缓存安全。
- **做法**（`domain/Grids.kt`）：`playerCells` 与 `placedCount` 改为构造时
  计算一次；新增 `hasHiddenNumbers` O(1) 查询，`GameScreen` 的提示按钮
  可用性判断从 O(N) 组合期扫描改为直接读缓存。
- **验证**：新增 `GameStateBookkeepingTest`：缓存与逐次扫描的朴素对照在
  走步/撤销/提示迁移下始终一致；`copy()` 后重算且不参与 data class
  `equals`；沿完整解路径走满后 `placedCount == total`、`completed`。

### 3. 灰阶墨水屏主题（`feat(ui)`）

- **动机**：见痛点 3；这是 README 长期挂着的已知缺口。
- **做法**（`ui/theme/Theme.kt`）：
  - 移除动态取色，静态高对比灰阶配色（黑/白/灰）；`surfaceContainer*`
    与 `outlineVariant` 显式置灰，避免对话框、文本框回落到默认紫色调；
    仅 `error` 保留深红（灰阶屏呈深灰仍可辨，彩屏预览语义更清楚）；
  - 深色模式为反相灰阶（部分墨水屏设备走系统级夜间反色）；
  - 在 `MaterialTheme` **内部**用无涟漪 `Indication` 覆盖
    `LocalIndication`——material3 1.2.1 的 `MaterialTheme` 自己会提供带
    涟漪的实现，放在外层无效（字节码检查确认其引用关系）。
- **验证**：编译通过；颜色角色全量显式指定，无隐式默认彩色。

## 评估后放弃 / 未做

- **走步存档去抖（减少 Room 写入）**：每步走棋都全量 upsert 存档看似浪费，
  但写入在应用级作用域后台执行、不阻塞 UI，且写入经 Mutex 全序化，是
  「新游戏覆盖旧档、进程死亡后可恢复」语义的基石；`GameViewModelTest`
  依赖该顺序语义。去抖会引入「崩溃丢最近几步」的新缺陷，收益与风险不成
  比例，放弃。
- **求解器/生成器进一步优化**：`HidatoSolver`/`MinimumClueOptimizer` 已有
  精确区间传播、不相交反例打包、频率覆盖下界等多轮优化与复现基准；在
  无法运行离线基准的前提下继续改动风险大于收益，本轮不动。
- **已现相邻数字对连线的信息泄漏**：连线对初始即相邻的连续线索（如 12、13）
  会提前揭示二者的直接相邻关系。属于玩法语义取舍而非缺陷，且简单难度下
  有提示性作用；留待玩法层面单独决策，本轮不改。
- **厂商局部刷新接口（`WindowManager` / 厂商 SDK）**：需要真机验证，无
  设备时无法负责任地实现，维持 README 中的待办状态。

## 验证汇总

- `./gradlew :app:testDebugUnitTest`：全量通过（含新增
  `GameStateBookkeepingTest`、`BoardGeometryTest`）。
- `./gradlew :app:assembleDebug`：通过。
- CI 会在 PR 上复跑 JVM 单测、18 道题库唯一性复验与离线优化工具复验
  （本轮未改动领域求解代码，题库与工具不受影响）。
