# 数连 Hidato（墨水屏专属版）V1.3

专为墨水屏（E-ink）设备优化的经典 Hidato（数连）谜题 Android 应用。
从数字 1 开始，按 8 方向依次连接到最大数字，填满整个 M×N 网格即通关。

## 技术栈（全部官方方案）

| 类别 | 方案 |
| --- | --- |
| 语言 | Kotlin 1.9.24 |
| 构建 | Gradle 8.7 + Kotlin DSL（build.gradle.kts） |
| UI | Jetpack Compose（BOM 2024.06.00）+ Material 3 |
| 架构 | MVVM（ViewModel + StateFlow） |
| 异步 | Kotlin Coroutines + StateFlow |
| 依赖注入 | Hilt 2.51.1（KSP） |
| 持久化 | Room 2.6.1（游戏进度）+ DataStore Preferences（行列尺寸与难度） |
| 棋盘 | Material 3 格子按钮 + Compose Canvas 连线；大棋盘可滚动 |

## 构建命令

环境要求：JDK 17、Android SDK（compileSdk 34 / build-tools 34.0.0）、`local.properties` 指向 SDK 路径。

```bash
# 打 Release 包（在项目根目录执行，必须成功生成 app-release.apk）
./gradlew assembleRelease

# 产物位置
app/build/outputs/apk/release/app-release.apk

# 运行单元测试（关卡生成唯一性、求解器、游戏规则）
./gradlew test
```

Release 构建启用 R8 代码优化和资源裁剪，并使用调试签名，APK 可直接安装。

首次启动显示可跳过的玩法教程；完成或跳过后通过 DataStore 记住，下次启动不再显示。

## 项目结构

```
app/src/main/java/com/eink/hidato/
├── HidatoApp.kt                 # @HiltAndroidApp
├── MainActivity.kt              # @AndroidEntryPoint + Compose 入口
├── domain/                      # 纯 Kotlin 领域层（可 JVM 单测）
│   ├── Grids.kt                 # 8 方向几何工具 / Puzzle / GameState / GameEngine 规则引擎
│   ├── HamiltonianPaths.kt      # 蛇形路径 + 合法性校验
│   ├── HidatoSolver.kt          # CSP 求解器（前向检查 / MRV / 距离剪枝 / 连通性剪枝 / 双解早停）
│   └── PuzzleGenerator.kt       # 逆向生成法：蛇形+50~200 次突变 → 线索压缩 → 难度回填
├── data/
│   ├── db/                      # Room：GameSaveEntity / GameSaveDao / HidatoDatabase
│   ├── GameRepository.kt        # 单一 Room 保存入口，Mutex 按调用顺序读写
│   ├── SettingsRepository.kt    # DataStore Preferences：行列尺寸与难度
│   └── GameSaveMapper.kt        # GameState <-> Room 实体
├── di/AppModule.kt              # Hilt 模块（数据库 / 应用级协程作用域）
└── ui/
    ├── theme/Theme.kt           # Material 3 默认浅色 / 深色配色
    ├── common/EinkButton.kt     # Material 按钮（触摸区域 ≥48dp）
    ├── menu/                    # 主界面（新建游戏 + 继续游戏）
    ├── game/                    # 游戏界面（GameViewModel / GameScreen / BoardCanvas）
    └── AppNav.kt                # Navigation Compose 路由
```

## 核心算法说明

1. **完整路径**：蛇形路径保证 100% 有解且 O(M×N)；随后随机执行 50~200 次
   段反转，每次突变后校验仍为合法 Hamiltonian 路径。
2. **求解器**：CSP 框架。前向检查（放置 k 后 k+1 必须有候选）、MRV 值排序
   （候选按到下一线索的切比雪夫距离升序）、距离剪枝（预计算 nextClue，O(1) 判断）、
   连通性剪枝（每 4 步 flood fill，剩余空格必须连通）、双解早停（数到第 2 个解即停）。
3. **线索压缩**：初始全部数字可见（1 与终点强制保留），按随机顺序逐个尝试隐藏，
   隐藏后求解器验证唯一解，多解即恢复；循环至无数字可隐藏，得到极简线索集。
4. **难度回填**：简单回填 50%~70%、中等 25%~40%、困难 0%（极简线索集本身）。

## 持久化策略

- **Room**（`game_saves` 单槽位）：行列数、完整解路径、初始线索、提示已显示数字、
  玩家路径、当前数字、错误计数、提示计数、是否完成、更新时间。
- **保存时机**：连接、错误点击、撤销、提示与通关统一保存；**重置本关不保存**。
- **顺序**：不再防抖，写入在应用级作用域按调用顺序执行；读取等待前面的写入完成。
- **结算**：通关存档写入成功后才显示结算，失败时显示保存错误。
- **恢复**：新关卡落库后将 SavedStateHandle 的模式改为继续，进程重建读取原关卡；
  难度从存档恢复，损坏存档显示失败页面。存在未完成游戏时主界面“继续游戏”高亮。
- **DataStore**：记住用户上次设置的行列尺寸与难度。
