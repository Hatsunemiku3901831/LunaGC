# 7.2 Proud 门槛：同元素比较与突破预览列表

任务：`E-01d1/R3-PROUD-GATE-COMPARISON`，服务于 `R3-PROUD-ACTUAL-ADOPTION`。本项已找到同 Avatar、同 Depot、同 typed81127 元素的门槛比较及采用／跳过分支。其产物是**下一突破阶段的预览 group 列表**；当前实际 Proud 行的采用仍按独立来源验收。

## 固定来源与复现

复用主线已验的 owner25486 / method198316 完整单窗：2034 B、437 条指令，原 code SHA-256 `0a07ead5e86f32a54a7892ffb054d897cef610cf3a1516e44ee4f24f39ad07d6`。主线第三与 fresh1 的十件原件逐字节一致，机械工具 7 tests 已通过。窗口选择、AOT、原 E8 和 pdata 绑定见 [PROMOTE 单窗专题](CLIENT_72_D02_PROUD_GATE_COMPARISON_PROMOTE_WINDOW_20261111B.md)。

语义私有目录为 `local/migration72/d02-resume-20261111b/proud-gate/`：

| 产物 | 大小 | SHA-256 |
| --- | ---: | --- |
| `promote-preview-contract.private.json` | 16614 B | `8c55511884462ee76cdb75a22aba296d83526a50cb7eb5d7cfaacdb3771c30e2` |
| `promote-semantics-audit.private.py` | 18656 B | `cef8a41c38e18dbd30cb22b74a7fb2830b591667a3dfdac373102309e36d7519` |
| `promote-semantics1.private.json`、`promote-semantics2.private.json` | 各 47421 B | `ecdbace6ebda2667182dc5d2cdfa092759b17a71919d352d23d7a9e99732df8d` |

两次 fresh 语义输出逐字节一致；主线可用新输出名独立重算：

```powershell
python local/migration72/d02-resume-20261111b/proud-gate/promote-semantics-audit.private.py --output promote-semantics-main-third.private.json
```

审计固定合同及其 25 项来源，另外复用旧 Avatar 完整 listing、raw reader 报告、当前 Java 和旧 phase6 专题。它检查原窗连续字节／指令、精确分支、metadata 字段及参数、既有入站链和静态分支用例。EXE、两份 metadata、AccountPlatNative、Astrolabe 的版本哈希继承主线已验原窗，完整 SHA／大小留在合同与输出中。本次新增 PE 窗、callee body、live 和目标执行均为 0。

## 已验的同对象关系

| 来源或动作 | 原窗证据 | 结论 |
| --- | --- | --- |
| 实际 phase 来源 | owner 的 Avatar 字段125344 / `+230`；property 参数1002；返回 double 经 `cvttsd2si`，低32位写入字段125350 / `+25c` | 1002 的既有独立来源是 `PROP_BREAK_LEVEL`。phase 与后续 Depot 来自同 owner 的 Avatar 字段。 |
| Depot 与数组 | 同字段 Avatar 的 `+2b8` typed32758 经准确 getter，查询 GLOBAL `+212b8`；原返回行 `+18` | metadata 明确为 Depot22284.field105355 的 `81127[]`，没有把 wrapper 或普通技能数组当作它。 |
| 同元素字段 | 数组元素保留在 RSI；先读 `+14`，再读 `+10`，相等路径又读同 RSI 的 `+14` | 两字段分别为81127.field404854、404853，均 typed58209；都经已验 scalar getter。 |
| 比较与跳过 | `+14` 解码为0则先跳过；`+10` 解码值与 owner `+25c` 加1作 `CMP`；`JNE` 回循环 | 条件是 `group != 0 && threshold == phase + 1`。相等比较不依赖符号解释。 |
| 采用 | 相等路径把同元素 `+14` 解码的 U32 存入 owner `+238` 的 `List<U32>` | 原内联路径检查容量、更新 count/version，并作4字节 store；增长 helper 未读取或执行。 |
| 列表用途 | 该列表发布到 typed33600 `MoleMole.MonoAvatarStarUpDialog` 的 `+c0`；随后 Setup 接收同 Avatar、当前／下一 Promote 配置 | 确认这是下一突破阶段的预览列表。Setup 本体未扩读。 |

准确分支为 `14cbddb62 JE`（group为0）、`14cbddb7d CMP`、`14cbddb7f JNE`（门槛不等），两条跳过均回 `14cbddb20`；内联采用 store 在 `14cbddbc4`。phase 加一使用原32位运算。源数组与81127元素没有在该循环中被修改。

`15184adf0` 的既有完整源是 Avatar 定义 `+178` 的 **Promote group**，提供 Promote 配置查询的 group 参数。它不提供本比较的实际 phase；实际 phase 的来源是上述 property1002。此前把此 getter 统称实际突破值的说法已纠正。

## 分支边界

以下均为从原静态分支推导的 oracle，不是目标 CPU 或客户端观察结果；“采用／跳过”只指这份预览列表。

| 同 Avatar phase | 同元素 threshold / group | 此列表的结果 |
| ---: | --- | --- |
| 0 | 1 / 221 | 相等，采用221 |
| 1、2、6 | 1 / 221 | 不相等，跳过 |
| 3 | 4 / 222 | 相等，采用222 |
| 4、5、6 | 4 / 222 | 不相等，跳过 |
| 0 | 1 / 0 | 先因 group为0 跳过，尚未读取 threshold |
| 0 | 0 / 223 | 不相等，跳过 |

旧固定五项参考为 `(1,221),(4,222),(0,223),(0,0),(0,0)`，字段 presence 仍未知。该数组在 phase0／1／3／4／6 的静态预览分别为 `[221] / [] / [222] / [] / []`。因此旧号 phase6、threshold1 在本列表跳过，不能据此认定当前已解锁 Proud 被拒；phase0、threshold1 在本列表采用，也不能据此认定当前已解锁。

## 当前 Proud 集合的已有独立来源

旧完整 Avatar18163 listing 与固定 raw reader 可直接衔接，无需再取 UI 窗：

1. `18163.m138207` 的准确参数1是 raw class48178。其 `+68`／tag14、`+80`／tag35 均为 repeated U32，按参数2／3传入同 Avatar 的 `m138165`；原 call 在 `15184e390`。
2. `m138165` 逐输入 ID 调 `14d7ac240(U32, byref Proud63831, false)`。lookup 成功、group非零后，继续以同 Avatar 调 `151846d90` 选择当前 group 行，再走重复／替换／追加条件。
3. 两条路径分别清空并重建 Avatar `+1c8`、`+40` 的 typed34054 集合。它们与795B窗口消费的 Avatar `+50` 主动技能 Proud 集合有明确字段区别。不能简化成“所有输入 ID 无条件原样追加”。

这证明**完整 AvatarInfo 应用路径由入站 ID 列表及资源／当前 group 行选择共同驱动**。它没有证明所有通知路径，也没有在此发布 tag14／35 的 inherent／special 别名或目标7.2 Java sender wire 对应关系。lookup 与 group 行选择均复用已有源；本项未扩读 helper，也没有自然 map hit。

当前 Java `Avatar.setSkillDepotData` 及重算流程会清空 Proud 集合，按既有 `threshold <= currentPhase`、`group*100+1` 和资源存在性重建；导出时分别生成 canonical 两份列表。旧保存列表因重算而不能直接作为最终采用集合，详见 [旧号 phase6 专题](CLIENT_72_D02_OLD_PHASE6_PROUD_20261111.md)。这是当前服务端代码事实；新找到的客户端预览相等比较没有验明这两个 Java 公式。

## 验收边界与下一动作

已完成独立静态合同：同 Avatar → 当前 Depot → 同81127元素 `+10`／`+14` → property1002 phase加一 → 相等比较 → 原分支采用／跳过 → 预览列表发布。与 Equals 现场、实际 map hit、被拒的 A/B 动作及产物均无依赖。

未完成：当前实际采用的同目标 `<=phase` 比较、实际 Proud 行构造及自然 map hit、`group*100+1` 的目标客户端证明、目标 sender／通知 wire 和实机实际采用。它们不因本项静态成功而升级；父项、D02、complete-avatar 和 resource-ready 均保持未通过。

下一步复用本合同，由主线继续已有授权的精确 Proud 行／map-hit 证据。4427B交集候选目前仅有当前／下一 Promote 查询的旧局部上下文，尚无比本合同更直接的当前采用来源，本轮不请求其新窗，也不扩 UI helper 树。普通尚未读取来源不标为阻塞。
