# F-03d3f4d2 / E-01/R3-ITEM-ENTRY：同 epoch 后继输入

本卡新增独立只读采集器和适配器，未修改冻结 v2 或其 54/2704 上限。人工 fixture 和报告位于忽略目录 `local/migration72/d02-parallel-20261109/combined-successor-inputs/`；本轮没有真实采样、native CPU、目标进程或 GS 操作。

唯一账本先采 Dictionary carrier、String 后继、List、普通类 root/条件槽和 allocator 比较单元，全部首遍完成后统一精确复读成功的 unique span。首遍最多 38 次/1632B，总计最多 76 次/3264B，单读最多 208B；错误、短读、超预算拒绝、重叠冲突和末尾变化均保留，不能补值。一致复读不等于原子快照。

Dictionary 固定验证 TryGetValue 10200、owner1222 和 String/UInt64 参数，始终核实际 RGCTX count37 及两个定义字。FindEntry 零或非零都只记原槽值和 unverified，不读取其 MI；getter 非零时沿冻结 String 后继的完整守卫采集。List 按实际 usage、class、GenericClass、单参数 inst、参数数组和 UInt32 类型采集；`+ca` 缺 bit 0x20 保留不同分支的 partial，不写 flag。普通 root 非零且 List 定义守卫成立时只读 `root+0x2738/8`；另读 `B+0x5bc96f8/8`，不跟随两值。分支分别输出 candidate/partial，整体 candidate 仅表示完整只读图，不证明 Avatar、FindEntry、自然调用、分配器、比较器执行、config、ability 或 D-02 成功。

`verify_contract` 固定新合同 SHA `93b210253b298e88d6bdd203b24a031123e40998cba1a50a3432578f2fe4d790`、父合同 SHA `917767966e0471be55e7c73b38378a25bb5aee5988518586d4664fd2047de780`、父证明及 18 个父 pin；仅对合同明确指定的历史文档使用精确副本 redirect。适配器的现场 source gate 还调用原 String `verify_sources`，并严格核对五 stock、profile/config、监视器与模块身份。主线已实际完成当前stock磁盘/source核验；新monitor、目标身份及采样门禁仍须新实时epoch，不由磁盘核验代替。

适配器固定读取本卡 `live-plan.private.json` 与 `live-ready.private.json`，要求四源 pin、76/3264/208 预算、专属 monitor 路径、确认时间和新创建身份。打开后与采集后核进程身份，末尾再核 inputs 和 alive，finally 关闭句柄；只在 backend 对应每个实际读取时记账，并逐项对照采集账本。不得把旧 epoch 指针或 root 混入本轮。ready 只能由主线新实时确认后提供；本轮未创建 ready 或执行现场采样。

人工测试：`python -m unittest tools.test_collect72_avatar_string_successors tools.test_sample_client72_avatar_string_successors -v`，16 项通过。覆盖两种派生路径、FindEntry 零/非零、独立分支、错误/短读、类型/byref/backref/MI/flag/parent、漂移/重叠/预算、旧 ready、生命周期、末尾 inputs/alive、close 和后端账本。人工最坏路径首遍恰 38/1632，总计 76/3264。两份 fresh 普通 1243 owned 输出分别为 35/1360、70/2720，逐字节相同，SHA-256 `cf9f728c21885f23b2043b78153daf2885fd387cd33566e6f214d103d71b1097`；这些均是人工内存，不能作为自然输入证据。

主线已审核实际差异、独立审阅的134项断言及16测试；第三人工报告44742B与原两份逐byte相同，SHA仍为上述`cf9f728c…1097`。审核SHA `11a747040fa41fd24d5f9b2e7a45525e1bb67ffd512ef1434f68dd8320102171`，覆盖七派生类型、FindEntry零/非零/最大64位、准确地址、统一final、生命周期与保密范围；108输入前后相同。实际当前五stock、13保护及`.env.local`缺席、18父pin和旧String源/原窗门禁通过，`main-stock-source-gate.private.json` SHA `cf3a92c70f82319d0fa44a8832b9f0f03a9223cc0555a565c2c4bbd2627a18c6`。这不是自然图或目标进程门禁验收。

下一步仅按已批准合同准备独立计划/启动/退出后核验，取得新的明确实时确认后原版90秒一次采集；不与SDK临时DLL安装并行。客户端验收、原生执行、完整构造和D-02均未验。本卡仅新增离线工具、测试和本文档，不触发GS部署；Git、Project/Harness由主线负责。
