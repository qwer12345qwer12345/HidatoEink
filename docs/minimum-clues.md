# 固定路径 S 的最少线索算法与验证记录

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

1. **主问题（HittingSetMaster）**：对已知差异集合按线索数从少到多枚举。用不相交反例打包与频率覆盖计数两个可证下界的最大值跳过不可能的较小基数；递归时仍以不相交约束剪枝。只有完整搜索才返回确定答案。
2. **分离子问题（HidatoSolver）**：将候选线索交给求解器。
   - 返回 `Found(T)`：`T` 是违反唯一性的具体证据；加入约束 `sum_{n in D(T)} x_n >= 1`，继续优化。
   - 返回 `Unique`：当前候选唯一，且主问题已经证明没有更小的候选满足所有已知必要约束；**它就是固定 S 的全局最少线索集合**。
   - 返回 `Unknown`：不作任何唯一性推断，结束优化并保留先前经验证的题目。
3. 预先从原始路径的**合法片段反转**中构造必要约束。这些不是主观启发式；每一条反转必须形成另一条合法完整路径，故每条约束都有明确的数学证明。
4. 所有约束只保留包含关系下更强的**集合极小项**。如果 `U⊆V`，满足击中 `U` 的线索必定也击中 `V`；所以 V 可删除。

### 无须枚举即可证明的下界

设已知差异集合中找到 `k` 个**两两不相交**的集合 `D₁,…,Dₖ`。要排除这 `k` 条真实替代路径，至少需要从每个集合保留一个**不同的**线索，因此：

`线索数 ≥ k + 2`（其中 2 是强制首尾线索）。

寻找这 `k` 个集合使用确定性的**短集合优先贪心打包**。这个策略未必能找出最大的 `k`，但无论找到多少，结果始终是安全的数学下界，而非猜测。如果它恰好等于一个已经通过完整唯一性验证的题目的线索数，则可以立即报告 `PROVED_MINIMUM`，无需再运行击中集主问题。若不相等，就用它跳过所有必然不可能达到的较小线索数，并继续正常求解。

实现位于 `MinimumClueOptimizer.disjointWitnessLowerBound()`，只处理数字集合，不因 8×8/15×15 切换策略，也不引入额外求解框架。参考 [McGuire 等人的最少线索研究：相互不交的 unavoidable sets 下界](https://www.math.ie/checkerold.html)。

## 停止结果含义

- `PROVED_MINIMUM`：对**给定完整路径 S 和端点固定规则**严格证明达到最少线索。不是所有可能的完整路径共同的全局最少。
- `TIME_LIMIT`：达到计算时间上限，返回已经独立确认唯一的初始或改进候选；没有最优性证明。
- `MASTER_NODE_LIMIT`：主问题预算不足，同上。
- `SOLVER_UNKNOWN`：分离求解器不能完成验证，同上。

不能把 `Unknown` 当成 `Unique`；不能把“线索较少”描述成“已经证明最少”。

## 为什么它只在离线工具中使用？

精确击中集本身是 NP-hard，分离子问题也需要求解大规模路径约束。即便 8×8 也可能因不同路径而耗时悬殊。因此：

- 无论 4×4、8×8 还是 15×15，离线精确优化器都使用**同一套算法与数据结构**。固定端点与谜题尺寸只通过 `Puzzle` 输入，而不是特殊分支。
- 使用 JDK 标准的 `java.util.BitSet` 记录线索差异集合；其大小随棋盘自动增长，**不再存在 64 格上限**，无需自定义多个 `Long` 的掩码类。
- 大棋盘证明最优仍可能需要指数级时间。到达主问题节点上限、求解器预算或时间上限时，返回**已经验证唯一的上界**，并明确标注“未证明最优”；不会因为超时损害唯一性。
- Android UI 不运行精确优化：玩家打开关卡时仍加载预生成题库，未覆盖的尺寸依旧使用受预算约束的唯一解生成器。

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

`stderr` 输出 `clues=初始->最终`、`proved-lower-bound=已证明的线索数下界`、`status`、`proof`、约束数、求解器调用数、主问题节点数、耗时。若 `proof=false`，也可以使用结果，因为它仍然经过唯一性验证，但**不得写成最少线索证明**。

## 验证与复现边界

频率覆盖下界：设有 m 条非空反例约束，任一内部数字最多出现于 F 条约束。选择 k 个内部线索至多覆盖 kF 条，因此必须 k≥ceil(m/F)。将此下界与不相交证据打包下界取最大值仍然安全；**下界本身不能证明唯一性**。实现位于 `coverageWitnessLowerBound()` 与 `witnessLowerBound()`。

可重复对照：三条约束 `{2,3}`、`{3,4}`、`{2,4}` 的不相交打包下界为 1，频率下界为 2；100 组确定性随机小型集合族使用独立穷举计算最小击中集，测试组合下界不超过真值。题库 18 条路径的**初始合法反转约束**中，新下界超过旧打包下界的数量为 **0/18**；不据此宣称真实题库的运行时间加速，新增反例后的效果也尚未量化。

既有测试还覆盖 3×3 全部 128 种内部线索组合的独立穷举、4×4 最少线索实例、8×8/9×8/15×15 同一模型、第 64 位以上的 `BitSet`、预算耗尽不得虚报最优。CI 运行 JVM 单测、18 道内置题库唯一性验证，以及离线优化输出的再次复验。大棋盘可进入算法不等于其最优性已证明。

维护规则：每轮只合入一项有正确性依据或可重复收益的算法优化；集中完成代码、测试、本文和过时文档清理，以**单个原子提交**形成 PR，CI 全部通过后才 squash 合并。旧版本演进留在 Git/PR 历史，不重复累积在 README 或独立短篇文档中。
