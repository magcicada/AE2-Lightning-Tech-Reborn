# PR #12 审查与验证

基线：Forge 1.20.1 `de873bf`；原 PR：`d22e571`；移植目标：NeoForge 1.21.1 `alpha` 的 `f5197cb`。

## 修复

- `patternsChanged()` 保留物理批次证明时，同时保留原来的活动时间索引。否则调度器失去清理入口，移除的样板会无限保留在目标的批次历史及持久化数据中。回归测试验证重建前后的证明相同，并在原始 100 tick 有效期后清理。
- 已进入整批补货模式后，少于已知整批容量的实际收货会清除整批等待和旧速率估计，重新及时探测。公平份额、供给限制及部分接收不能被当成已补满整个库存。回归测试在原 PR 上复现 20 tick 等待。
- Forge 的接口 GameTest 任务显式启用 `ae2lt_interface_input`，覆盖 PR 移植的被动输入测试。
- 基准运行器开放高基数拒绝恢复、等负载恢复、部分恢复、持续负载、60 tick 周期产出及 NORMAL 缓冲导入入口。
- 每个基准样本使用全新世界，拒绝复用目录。原运行器复用控制/负载世界，重建时旧容器的物品掉落会污染随后样本。运行中线程快照定位到大量 `ItemEntity.tick` / 实体碰撞开销；旧比较已中断并标记无效。新增运行器测试验证八个样本世界互异、交替顺序、报告收集及拒绝覆盖结果目录。

保留 PR 的溢出安全计费、被动输入能力路由、按目标缓存能量服务、不可变导出计划、严格限定的原版木桶路径、库存变更唤醒和自适应批次改进。`alpha` 保留原有 NeoForge 能力注册、组件比较、Java 21 `Math.ceilDiv` 等平台实现。

## 正确性验证

| 版本 | JUnit | 自适应压力模型 |
|---|---:|---:|
| Forge 基线 | 1,288 通过，无跳过 | 4/6 通过 |
| 原 PR | 1,312 通过 | 6/6 通过 |
| Forge 修复版 | 1,314 通过，无跳过 | 6/6 通过 |
| alpha 移植版 | 1,367 通过，4 项可选 EAEP 实际 jar 测试跳过 | 6/6 通过 |

最终完整构建两侧均通过。使用最终夹具及独立新世界复验：Forge 85/85、alpha 50/50 原生测试通过。Python 样本隔离测试与 PowerShell 基准工具回归测试通过。

两个新增调度回归测试均先在原 PR 上失败，再验证修复。基线的压力失败是调度重建恢复与机器重配置后的调用预算，不应归因于本次修复。

`alpha` 的 `AdaptiveBatchDispatchStressTest` 与 `DispatchCostBenchmarkTest` 已有 Forge 对应实现，差异仅为平台 API；没有重复引入同一测试。包含固定模型、5,000 tick 长测、公平分配、有限任务、重建恢复和机器重配置。

调度器分配量约 182.06 B/目标访问，模式哈希调用为 0；各版本一致。JUnit 中的计时受同机其他构建影响，不用于声称实际服务器提速。

## 原生稳定性记录

首次最小运行时测试为 83/85：`fastWirelessExportTypeChangesRemainResponsive` 和 `normalLocalImportBatchesWithoutBlocking` 在使用固定 40 tick 初始化窗口后遇到 `getGrid() == null`，与原 PR 披露的失败一致。

随后使用新世界、保持生产源码不变、仅添加测试启动诊断的运行通过 85/85；诊断未命中。诊断代码随后移除。该复跑不能推翻先前失败，当前没有足够证据认定底层根因。

受影响的测试随后改为在第 40–80 tick 内等待真实网格就绪，再启动原来的行为窗口；生产、消费、延迟和守恒断言不变。始终不就绪或启动后网格消失仍直接失败。此项只修复测试对初始化时序的空值假设，不宣称生产生命周期问题已经根治。

## 基准方法

正式导出比较使用 256 个目标、每目标 27 个物品 key，五轮交替 baseline/candidate；每轮各有独立空载控制和负载 JVM。预热 200 tick，采样 1,200 tick；两侧相同最小运行时、夹具、2 GiB 最大堆，计时包含 I/O 资格检查。记录提交、工作区状态与源码 SHA-256，校验物品守恒、消费吞吐和完整采样。

以下耗时均为 Forge 1.20.1 实测；alpha 的移植通过构建、通用调度压力模型及原生正确性验证，不据此推算其绝对性能。

无效轮次保存在 `build/reports/pr12-export-ab/`（见 `INVALIDATED.txt`），不计入结论。重新运行的原始结果保存在 `build/reports/pr12-fresh-export-ab/`，20 个 JVM 均成功、注册数一致、采样完整且工作量断言通过。各版本五次负载运行中位数如下：

| 指标 | 基线 | 修复版 |
|---|---:|---:|
| 平均 MSPT | 11.986931 | 10.335978 |
| 整服 P95 ms | 17.189600 | 16.552100 |
| 整服 P99 ms | 23.702400 | 29.090200 |
| 控制校正平均 MSPT | 8.713486 | 6.780077 |
| I/O 平均 ms | 6.982765 | 5.995315 |
| I/O P99 ms | 10.894500 | 10.104300 |
| GC 暂停占比 | 0.002200 | 0.001317 |
| 采样堆峰值 B | 1,298,961,392 | 1,235,421,736 |

平均 MSPT 下降 13.8%，I/O 平均下降 14.1%，I/O P99 下降 7.3%。但原门禁仍为 **FAIL / MEASURABLE_IMPROVEMENT**：I/O P99 高于运行器默认 4 ms 预算，且整服 P99 上升 22.7%，超过 5% 的相对容差。没有修改门槛，也不据此宣称全面性能无回退或高负载下稳定 20 TPS。`capacityTps` 是平均 MSPT 派生值，不是实测 TPS。

保留这些改进的依据是 I/O 自身耗时、GC、堆占用和调度压力预算的实际改善；整服尾延迟仍需跟踪。合入不代表所有性能验收通过，失败项继续保留为后续优化目标。

扩展场景为候选版单次压力运行，仅用于覆盖与容量观察，不作为正式 A/B 或统计上的无回退证明。每项使用独立世界及 JVM，预热 200 tick，采样 1,200 tick；10 项均以退出码 0 完成，夹具断言和完整采样检查通过。

| 场景 | 平均 MSPT | 整服 P99 ms |
|---|---:|---:|
| `1024x27` | 23.496 | 34.009 |
| `import-buffered-normal-1024x27` | 13.625 | 33.167 |
| `import-period60-1024x27` | 5.474 | 34.105 |
| `high-cardinality-reject` | 2.226 | 10.344 |
| `equal-load-recovery` | 2.928 | 13.849 |
| `equal-load-partial-recovery` | 2.530 | 15.077 |
| `equal-load-sustained` | 1.907 | 16.275 |
| `export-empty-1024` | 2.846 | 16.714 |
| `export-mismatch-1024` | 1.332 | 9.221 |
| `export-continuous-1024x27` | 27.833 | 41.638 |

原始记录：`build/reports/pr12-stress-sweep/manifest.json` 及同目录报告。这组 A/B 对应 `0b0c23e` 中的传输实现；当时将两个调整的原生测试夹具替换回采样版本后，完整源码 SHA-256 与 manifest 精确相同。下面的 Mixin 兼容性调整没有重跑五轮耗时比较，不能当作新的性能数据。

## Mixin 兼容性复核

- 库存通知使用 `@Inject` 的方法尾部注入，不覆盖原方法、不取消执行、不修改返回值，也不在回调中执行物品转移。
- 目标明确为实例方法 `setChanged()V`，避免与同名静态重载混淆。设置 `require = 0, expect = 0`，通知注入点缺失不作为必需注入失败；这不保证与任意字节码改写兼容。
- 子类重写而不调用父类、其他变换跳过方法尾部、异步更新库存等情况可能不产生通知。原来的时间轮轮询持续保留，FAST 空读退避上限仍为 20 tick；失去通知会影响发现速度，而不应停掉传输。
- 通知注册表仅在服务端主线程访问，目标和观察者使用弱引用。回调只为有效观察者记录待唤醒状态。
- 新增真实运行时测试，用重写 `setChanged()` 且不调用父类的木桶模拟第三方容器，验证没有通知时仍能轮询导入，并守恒全部 64 个物品。现有冷输出测试同时验证通知成功注入时仍能快速响应。

兼容性运行使用项目默认完整依赖集及独立新世界，而非前述最小运行时：Forge 构建及 86/86 原生测试、alpha 构建及 51/51 原生测试全部通过，包含无通知回退测试。这只覆盖当前依赖组合，不能代表所有整合包。

## 复现

Forge 使用 Java 17，alpha 使用 Java 21。依赖已缓存时可加 `--offline`。

```text
gradlew build adaptiveBatchStress
gradlew runInterfaceIoGameTestServer -Pae2ltInterfaceIoGameTestsOnly=true -I scripts/wireless-io-benchmark.init.gradle
python scripts/test_wireless_benchmark_runner.py
python scripts/run-wireless-export-benchmark.py --baseline <baseline-worktree> --candidate . --output <new-results-directory> --profiles export-continuous-256x27 --runs 5
```

比较旧 Forge 版本时，使用修复版运行器及其 init script；init script 也为未修改的旧版注入独立世界目录。跨 Minecraft/加载器版本的绝对耗时不能直接作为 A/B 结论。
