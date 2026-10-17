# 最少线索数：数学模型与可验证算法

## 问题不是“尽量随机删数字”

固定一条完整合法路径 `S=(s_1,...,s_N)`。用二元变量 `x_n` 表示数字 n 是否作为线索显示：

- `x_1=x_N=1`（本游戏强制保留两个端点）。
- 优化目标：`min sum(x_n)`。
- 对任何其他合法完整路径 `T != S`，定义差异集合
  `D(T)={n | T[n-1] != S[n-1]}`。
- 唯一解的必要且充分条件是：对每一条 `T != S` 都有
  `sum(x_n, n in D(T)) >= 1`。

**必要性**：如果 D(T) 中一个数字都没给出，T 满足所有已给线索，因此题目多解。

**充分性**：任何其他合法路径 T 至少与某个已给线索位置不同，因此不可能同时满足题面。

这个模型叫做 **Minimum Hitting Set**（最小击中集），也是唯一解谜题研究中“unavoidable sets / 不可避免集合”思路的核心。它不是单纯的启发式评分。

相关论文（方法借鉴，不声称 Sudoku 的数字结论适用于 Hidato）：

- McGuire, Tugemann & Civario, *There Is No 16-Clue Sudoku: Solving the Sudoku Minimum Number of Clues Problem via Hitting Set Enumeration*, Experimental Mathematics 23(2), 2014. DOI: https://doi.org/10.1080/10586458.2013.870056
- Tjusila、Besançon、Turner 与 Koch，*How many clues to give? A bilevel formulation for the minimum Sudoku clue problem*（Operations Research Letters, 2024，DOI: https://doi.org/10.1016/j.orl.2024.107105），相关实现：https://github.com/gtjusila/minimum-sudoku

## 为什么不枚举所有可能的 T？

每一种其他合法完整路径都形成一条约束。全部列举非常昂贵。

因此采用 **主问题 + 分离子问题**：

1. **主问题（HittingSetMaster）**：只考虑目前已知的差异集合，搜索基数最小的线索集合。按照 0、1、2……个可选线索依次寻找，并使用“未满足约束之间互不相交的打包数”作为下界；仅在预算内完整探索才返回确定答案。
2. **分离子问题（HidatoSolver）**：将候选线索交给求解器。
   - 返回 `Found(T)`：`T` 是违反唯一性的具体证据；加入约束 `sum_{n in D(T)} x_n >= 1`，继续优化。
   - 返回 `Unique`：当前候选唯一，且主问题已经证明没有更小的候选满足所有已知必要约束；**它就是固定 S 的全局最少线索集合**。
   - 返回 `Unknown`：不作任何唯一性推断，结束优化并保留先前经验证的题目。
3. 预先从原始路径的**合法片段反转**中构造必要约束。这些不是主观启发式；每一条反转必须形成另一条合法完整路径，故每条约束都有明确的数学证明。
4. 所有约束只保留包含关系下更强的**集合极小项**。如果 `U⊆V`，满足击中 `U` 的线索必定也击中 `V`；所以 V 可删除。

## 停止结果含义

- `PROVED_MINIMUM`：对**给定完整路径 S 和端点固定规则**严格证明达到最少线索。不是所有可能的完整路径共同的全局最少。
- `TIME_LIMIT`：达到计算时间上限，返回已经独立确认唯一的初始或改进候选；没有最优性证明。
- `MASTER_NODE_LIMIT`：主问题预算不足，同上。
- `SOLVER_UNKNOWN`：分离求解器不能完成验证，同上。

不能把 `Unknown` 当成 `Unique`；不能把“线索较少”描述成“已经证明最少”。

## 为什么它只在离线工具中使用？

精确击中集本身是 NP-hard，分离子问题也需要求解大规模路径约束。即便 8×8 也可能因不同路径而耗时悬殊。因此：

- 对 4×4～8×8（总格数 ≤64）的离线预生成谜题，可以尝试该精确优化工具。
- 15×15 使用现有**保证唯一解**的贪心生成器和离线候选筛选；不声称已经证明最少线索。
- Android UI 不运行精确优化：玩家打开关卡时仍加载预生成题库，未覆盖的尺寸依旧使用受预算约束的唯一解生成器。
- 最好保留原有线索作为**已验证的可行上界**，而不是从两个端点无依据地宣称唯一。

目前使用 64-bit Kotlin `Long` 存储最多 62 个非端点线索位。这是本版的**明确工程边界**，不是数学方法本身的限制。

## 复现实验

```bash
# 原有 JVM 单元测试（额外加入了独立的 3×3 全枚举对照）
./gradlew :app:testDebugUnitTest

# 再次逐条验证打包题库
bash tools/offline/verify-catalog.sh

# 对内置题库中“第 1 个有效条目”进行离线精确优化，最长 30 秒
bash tools/offline/minimize-catalog.sh app/src/main/assets/unique-puzzles.tsv 1 30000

# 将 stdout（TSV 行）存入文件再用 verify-catalog.sh 复验
bash tools/offline/minimize-catalog.sh app/src/main/assets/unique-puzzles.tsv 1 30000 > /tmp/minimum.tsv
bash tools/offline/verify-catalog.sh /tmp/minimum.tsv
```

`stderr` 输出 `clues=初始->最终`、`status`、`proof`、约束数、求解器调用数、主问题节点数、耗时。若 `proof=false`，也可以使用结果，因为它仍然经过唯一性验证，但**不得写成最少线索证明**。

## 测试与限制

- `MinimumClueOptimizerTest.kt` 枚举三个不同的 3×3 完整答案的全部 `2^7=128` 种内部给定线索组合，用**不共享生产求解器剪枝**的暴力路径枚举计算真实最小值；只有结果一致才能将 `PROVED_MINIMUM` 视为有效。
- 另有低主问题预算测试（不能伪造最优证明）和合法反转证据测试。
- 运行优化器和独立小棋盘模型的纯 Kotlin/JVM 实验得到：三条 3×3 示例的真实最少线索分别是 **5、3、5**，优化器均证明达到了该下界。4×4 蛇形示例得到 **9** 个线索并证明固定路径最优。
- **实际题库改进**：原 4×4 中等档题目在同一完整路径下由 **5 个线索降低到 4 个**（1、11、15、16）；离线模型返回 `PROVED_MINIMUM`，本次 PR 也更新了 TSV。独立的题库唯一性验证需由 CI 的 `verify-catalog.sh` 复核。
- 上述实验最初在**纯 Kotlin 测试替身求解器**下运行；仓库 CI 会进一步以正式 `HidatoSolver` 运行 JUnit 测试。尚未证明该技术对 8×8 或 15×15 有普遍的时间优势，更未声称已解决所有路径的全局最少问题。

**可解释性原则**：每一个剪枝条件必须有“否则存在另一条合法完整路径”或“不会排除任何最优候选”的证明。若没有可复现实测收益，就不加入更多优化模块。
