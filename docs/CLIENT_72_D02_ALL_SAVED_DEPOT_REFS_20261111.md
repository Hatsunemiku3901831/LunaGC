# E-01 / R3-ALL-SAVED-DEPOT-REFS：121 个实际保存 Depot 的原字段

本卡从固定旧号投影的 121 条实际保存 Avatar 行，按各自保存的当前 Depot 查询六个原 typed 字段。全体角色在 Java postLoad 中重算状态，因此四活动样本不足以选择完整旧号所需资源；本卡补充数据样本，不证明字段业务语义、资源闭包或旧号可恢复。逐角色及字段明细、原查询/getter trace 和正数清单仅保留私有目录。

## 来源与限制

驱动从固定投影重新核账号归属，和既有旧 Avatar coverage 报告逐项独立比较，拒绝缺行、重复、来源或保存内容漂移；不输出账号 UID 或原 BSON。121 个 Avatar 各对应一个不同的实际保存当前 Depot，不从候选表补行。所选 snapshot 的 dependencies SHA-256 `3ef64138cd0525442df1625e823bb41a66089c3ea114deb99c7da24f201ec0b7`，manifest `9fffdc25ddb24376552e3163db9ed6de441548d843fbda82649996fb59d4910b`，实际 Avatar BSON `273fa466d17e504d2eef2abb00b175afe3b4aa50f339d622b4614e828c3afc5a`；manifest 内全部导出文件均重核。旧 coverage 报告 SHA `ca9b24ed6e47cca54bc069a1c4f3b8cf19b5900117860efe755ade49293246ea`，四活动原字段报告 SHA `8c06c130d7f2efa6a31a5848eda471810834e019c1a1153158576d2c15f3dfd5`。

每次完整重核来源审计的 20 项继承 pin 与 9 项直接 pin、coverage 的固定输入、mapping 的22项 sourcePins、payload/locator/codec 和五个客户端文件；准确 pin、原文件字节数与结果在私有报告。原 Depot payload 的121个所选行各自重算跨度 SHA；34 个已批准函数区间均核字节和边界。原模型、codec 和预算不扩大，未增加 PE 窗口、模型或成功 stub。

原 whole parser 对 26024B payload 运行一次，得到171行、cursor 26024、顺序/key/source 与固定 mapping 完全一致；helper 既有五次 selected 查询单列。随后同 runtime 对121个实际保存 key 各运行原 query：RAX 均精确等于本次 whole 创建并插入的 typed 22284 指针。实际登记11个原函数。2924次 getter 全部调用原 `14aef9db0` 并到达原 RETURN；每次 query 和 getter 的完整 trace 保留在私有报告。另按四活动旧报告逐字段比较实际 typed 值，一致。

## 输出合同和汇总

仅提取原 fieldIndex 105354、105355、105357、105358、105363、105369，按 metadata 验证原 offset 和嵌套类型；嵌套只保留原 typed field 索引，不增加业务 alias。数组保持顺序、重复、零和 null；每个外层和嵌套字段的输入 presence 均为未知：对象状态不能区分输入缺失和 parser 默认值。私有报告按 fieldIndex 汇总正 typed 数值及出现次数，仅供选择后继资源研究，不代表每个正数已证明是资源引用。

| 原 fieldIndex | 不同正数 | 正数出现次数 |
| --- | ---: | ---: |
| 105357 | 726 | 726 |
| 105358 | 248 | 248 |
| 105363 | 121 | 121 |
| 105369 | 121 | 121 |
| 198735 | 20 | 20 |
| 198736 | 1 | 20 |
| 198737 | 20 | 20 |
| 404853 | 2 | 242 |
| 404854 | 390 | 390 |

105363 是本次 query 的 id 校验；198736 是保留原值的 enum，不能一概当引用。其余数字也不得凭外观命名 proudGroup、promote 阈值或认作已批准语义。81127两叶身份、条件与原 Proud 行关系尚未证实；完整 Talent/Proud 语义及121角色各自实际 AvatarPromote/Curve 的 phase6 消费仍未完成。

## 复现、测试和交付

先写有界计划并冻结合同，最终计划私有 SHA `73e078304f32efc32941d974e2b08cd44b31103d65e7196f9e53b91d4ae9b366`。离线 driver SHA `faa91135c7c5cba3b09ed48a44866671289d434f296830b063232e2d332164cc`，测试 SHA `2adfdcfa772b14c71264ebb16fc53dd83783c2089a8cc630084f78a6b0e3a31b`。

```powershell
python -m unittest tools.test_client72_d02_all_saved_depot_refs tools.test_client72_d02_old_avatar_coverage tools.test_client72_d02_old_depot_fields -v
python tools/client72_d02_all_saved_depot_refs.py --output <新文件名>.json
```

仅允许输出独立私有目录 `local/migration72/d02-resume-20261110b/c-all-depot-refs/`，拒绝覆盖或越界；发生 pin、span、类型、query、getter、预算或 unresolved 不符即停并留私有 failure。每次调用沿用40,000,000指令/60秒、数组4096、深度5；未提高上限。14项测试通过，覆盖保存选择漂移/重复/缺来源、嵌套正数/零/null/顺序及64位、whole键与跨度漂移、pin和输出边界、UID隔离。独立审计逐项核121个原 payload span、同运行时 query 指针、726个外层字段和嵌套 presence、2924个 getter RETURN。

最终两次独立 fresh 结果 `final1.private.json`、`final2.private.json` 均 9,014,403B，SHA-256 `b6c806dfd5634f20501a9e57a70d88bf4d41b63b47740cf68dff817b7d79527a`，逐字节相同。先前两次探索版也一致；最终版增加原 source-audit 20+9 pin 全核及 getter RETURN 门禁，未扩大执行边界。主线负责第三独立验收、任务索引和提交。未连接数据库、JVM、服务、客户端、DLL、调试器或网络，也未构建部署。所有投影批准、资源就绪、完整 Avatar 和 D-02 完成标记保持 false；下一步只能依据按字段分组的原数值和直接消费证据选择必要资源族，分别验证实际 phase6、Talent/Proud 语义与完整恢复，不能将本卡当作客户端验收。

主线14项定向测试及第三独立CLI通过；`main-third.private.json` 与最终两fresh逐byte相同，仍9014403B/SHA `b6c806dfd5634f20501a9e57a70d88bf4d41b63b47740cf68dff817b7d79527a`。另独立核121唯一当前Depot/同runtime queryRAX、2924 getter原RETURN、726外层字段、34批准/11实际区间及所有false语义标记；见私有main-review。只关闭全保存Depot原字段样本子出口。
