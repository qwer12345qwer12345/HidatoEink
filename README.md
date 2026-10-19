# 数连 Hidato（HidatoEink）

面向 Android 墨水屏设备的 Hidato 游戏，支持 4～15 行、4～15 列的矩形棋盘。数字按 1、2、3……依次连接，下一数字必须位于水平、垂直或对角相邻的格子。

**出题原则：唯一解是硬约束，减少初始数字是优化目标。** 给定一条完整合法路径 `S`，离线优化器只研究如何用**最少的已知数字**使 `S` 成为唯一解；不优化或替换 `S` 本身。完整数学定义、证明和停止条件见 **[最少线索算法说明](docs/minimum-clues.md)**。

## 游戏功能

- 简单／中等／困难三档难度，自定义行列数；大棋盘支持横向与纵向滚动。
- 从一个已显示数字出发，依次选择相邻格子；支持改选起点、撤销、提示、重置和错误次数统计。
- 即时按唯一的内置答案判断走步，通关后展示统计；首次启动有可跳过的教程。
- Room 保存游戏进度，DataStore 保存用户设置。新游戏覆盖旧存档。

当前界面使用 Jetpack Compose 与 Material 3，已考虑点击区域与棋盘滚动；**尚未集成各厂商 E-ink 局部刷新接口或独立的纯黑白主题**，仍需在目标设备上验证。

## 出题的两种运行方式

| 用途 | 入口 | 策略 | 保证 |
| --- | --- | --- | --- |
| **游戏中实时出题** | `PuzzleGenerator` | 完整路径 → 从全部数字开始尝试隐藏 → 逐次验证唯一性；局部路径反转可提前证明某些删除会导致多解 | 仅接受已证明唯一的删除；超时会**保留**线索 |
| **离线精确优化** | `MinimumClueOptimizer` | 固定完整路径 `S` → 先播种片段反转与局部路径替换约束 → 最小击中集主问题 → 求解器调用前先做超出播种窗口的深挖局部反例搜索 → `HidatoSolver` 寻找第二答案并添加约束 | 搜索完整结束时证明 **固定 S 的最少线索**；预算不足则只返回经验证唯一的上界 |

两种方式共用 `HidatoSolver.findAlternative()` 的三个明确结果：`Found(第二条路径)`、`Unique`、`Unknown`。**`Unknown` 从不被当成唯一。**

游戏**优先读取** `app/src/main/assets/unique-puzzles.tsv` 中离线验证过的题目；对应尺寸或难度没有题目时，使用实时生成器。当前题库为 **18 道**，因此可能重复。

在线生成的线索数下限分别为：简单 `ceil(N×35%)`、中等 `ceil(N×20%)`、困难 **2**（固定首尾数字）。这些只是**停止尝试删除的下限**，不是保证达到的线索数，也不是最优性证明。在线隐藏阶段默认约 2 秒，每次唯一性验证至多约 250 毫秒；完整路径的随机生成重试不在这一严格预算内。

### 数学优化的范围

离线算法固定合法完整路径 `S`，强制保留数字 1 与终点，求使 `S` 唯一的最少线索。最小击中集主问题在合法片段反转与局部路径替换的必要约束上，结合**不相交反例打包与频率覆盖计数**两个安全下界，再由求解器排除其他合法路径。所有棋盘尺寸共用 `BitSet` 模型；大棋盘仍可能需要指数级搜索，超时绝不虚报最优。完整证明与复现方法见 [docs/minimum-clues.md](docs/minimum-clues.md)。

## 构建、测试与离线工具

需要 JDK 17、Android SDK 34，项目使用 Kotlin 1.9.24、Gradle 8.7、Jetpack Compose、Hilt 和 Room。Android `minSdk=24`、`targetSdk=34`。

```bash
# Android JVM 单元测试
./gradlew :app:testDebugUnitTest

# 构建 APK
./gradlew assembleRelease

# 重新验证内置题库的每一道题
bash tools/offline/verify-catalog.sh

# 生成新的候选题目（JDK 17、Kotlin CLI 1.9.x）
bash tools/offline/pregenerate.sh 8 8 HARD 10 10000 42

# 对现有题库第 2 个有效条目执行固定路径的精确最少线索优化
# 结果输出到 stdout，数学证明状态和性能计数输出到 stderr
bash tools/offline/minimize-catalog.sh app/src/main/assets/unique-puzzles.tsv 2 30000

# 将结果保存，再独立验证唯一解
bash tools/offline/minimize-catalog.sh app/src/main/assets/unique-puzzles.tsv 2 30000 > /tmp/optimized.tsv
bash tools/offline/verify-catalog.sh /tmp/optimized.tsv
```

这些离线脚本需要系统安装 `kotlinc`，并将其加入 `PATH`。CI 会执行 Android JVM 单测、18 道题库的唯一性验证和离线优化工具的复验；本地编译前需在 `local.properties` 中设置 Android SDK 路径。**Release APK 当前使用调试签名**，不适合直接用作正式发布包。

优化器状态如下：

- `PROVED_MINIMUM`：已证明固定 `S`、首尾数字必须给定情况下线索数最少。
- `TIME_LIMIT`／`MASTER_NODE_LIMIT`／`SOLVER_UNKNOWN`：预算不足，返回已经验证唯一的题目，但**不声称最少**。

## 技术结构

| 文件／模块 | 职责 |
| --- | --- |
| `domain/HamiltonianPaths.kt` | 生成并校验覆盖棋盘的合法完整路径 |
| `domain/HidatoSolver.kt` | 寻找第二条合法解，或完整证明唯一 |
| `domain/PuzzleGenerator.kt` | 适合手机的限时唯一解生成 |
| `domain/MinimumClueOptimizer.kt` | 固定路径的离线最少线索数学优化 |
| `data/PrebuiltPuzzleCatalog.kt` | 加载并选择已离线验证的题目 |
| `tools/offline/` | 离线生成、精确优化和题库复验 |
| `docs/minimum-clues.md` | 数学模型、正确性解释、算法边界、测试证据 |

## 已知限制与开发约定

- 当前题库规模较小；`PuzzleGenerator` 在截止或验证无法完成时会留下更多线索，**不保证局部或全局最少**。
- 进度为 Room 单槽位存档；“重置本关”只先重置内存，不会立即保存；恢复游戏时也不单独还原最后选中的格子。
- 还未对墨水屏刷新行为、较大棋盘的离线最优证明耗时和多次开局重复率建立完整的实机指标。
- **每次变更先集中完成代码、测试和文档清理，以单个原子提交推送独立分支 → Pull Request → CI 复验 → squash 合并**，不直接向 `master` 推送功能变更。
- **每个 PR 都需同步更新一份与该变更对应的技术文档**，说明目标、算法或实现理由、适用范围、测试及限制；更新当前说明时应删除过时或重复的描述。旧方案的演进记录留在 Git 历史和已合并 PR，不在 README 中无限累积。
