# F-03d3s2：World 模板与构造 consumer 的最小必要后继（2026-11-10）

本卡仅分析冻结证据，不读客户端文件或当前进程，不改变 AB stock 采集计划、source seal、旧工具、资源或生产代码。完整 Scene、resourceReady 和 D-02 保持 false。

## 中文简明计划

| 项目 | 本卡约定 |
| --- | --- |
| ID / 目标 | F-03d3s2；比较真实 World 模板 90000004 的 `Default_Item` / `MPLevel` 配置职责、post-pool `146e055f0` consumer 与有来源的创建/替换/拆除最早未闭点，选出能直接交 Java 资源/发送字段规格的最小后继。 |
| 现证 | `scene-runtime-context-third.json`、`scene-entry-third.json`、Scene normal/deep 与后续 Gadget/R3 冻结报告。Pool 11008/slot4/mode1/type46314 头和首缺 TLS index 已验，直接复用，不重开 allocator。 |
| 缺口 | `146e055f0` 对实际 type46314 的职责；`MPLevel` 注册输入/配置实际字段用途；World 44512 创建/替换/拆除及 sender 字段语义的首个未闭 consumer。未知不补值。 |
| 候选方法 | 先核来源 SHA，再只读冻结 JSON 与 metadata cache、审原 caller/callee 指令及真实字段；比较已有完整窗的收益。无需新窗则交有限规格；若必需新窗，只申请一个有原 caller/AOT 证明的真实 entry 和完整 extent 上限，交主线读取。 |
| 依赖 | 本卡独立于自然 Avatar 分配、完整 g2、新 ready 和 AB 实测；已有 String/Gadget partial 只按已验范围复用。既有 GitHub 固定 commit/中文检索结论直接复用，不网络检索或重新克隆。 |
| 可写文件 | 仅本专题，以及 `local/migration72/d02-resume-20261110/scene-required-successor/` 内新私有脚本/报告；其余文件全只读。 |
| 验收 | 每项结论有完整 source pin、准确 entry/site、原字段职责或最小缺输入；私有审计双 fresh 确定性，留主线第三命令；明确静态、模型、自然执行边界。 |
| 停止 | 规格清楚即交 Sol xhigh/Terra。没有独立可执行项则交准确有限排除和唯一下一原输入；不树状追窗、不扩通用 factory 或完整 runtime，不编造 allocator/组件成功。 |
| 恢复 | 主线核第三报告后实施明确规格；需新窗先由主线按唯一申请提取并固定 SHA。本卡不操作 Git、共享构建、Java、DB、端口、网络、GAME_PATH/PE/REA、客户端 API 或 native CPU/模拟执行。 |

## 审计结果

**本卡完成有限排除，没有解锁新的 Java 实现项。** `146e055f0` 是池对象的两阶段接口 consumer；`MPLevel/+58` 的配置 lookup-key 职责已由既有证据闭合。唯一新增的 365B row consumer 窗只继续转交原模板，仍未给出 `Default_Item/+50` 的字段职责。不能据键名批准 Java World 能力来源、sender 或省略。

### 1. post-pool consumer 的准确职责

原 `15154bfc6` 调用 Pool slot4，返回值经 `15154bfcc..15154bfd2` 原样交给 `146e055f0`。后者完整旧窗是 `146e055f0..146e0571e`，302B，SHA-256 `8bc6efd936dc23942aac7454f774ef4524f4730c5cb81fa7033eb2aace1399fd`。

此函数先按 `IAutoAllocRecycle/type11064` 查接口 offset，调用 **slot0 `AutoAllocTypeFields`，再调用 slot2 `OnPoolAllocated`**；null 走原 null helper。它不接收 Gadget row，也不直接读 `Default_Item`、`MPLevel` 或 wire 字段。因此只能批准这两个回调的顺序职责。它不是 type46314 的 `.ctor`：冻结普通方法表中该 ctor 为 `1483dc730`，本卡没有读取其代码。

type46314 声明中确有这两个同名实现，但实际接口 offset table、回调入口选择与执行没有在本卡供给；不能由声明替代自然 vtable。旧 [Scene normal](CLIENT_72_SCENE_NORMAL.md) 的 LoadingTask 受控执行也不证明 World/type46314 回调。旧静态窗只有 asm/SHA，本卡没有把它说成重新核过机器码或运行成功。

### 2. `MPLevel` 已知为配置键，注册仍缺原分组输入

真实 90000004 行仍固定为 Gadget `[1451356,1451410)`，54B/SHA-256 `3288b3a44679bcb4ff775fc52fcb5274e20818c9b58c3ce9bc5d33892198283a`。field199648/+50 是 `Default_Item`，field199649/+58 是 `MPLevel`；父 +18 的 90000004 与父 +20 的 9999 分开。

原 factory 在 `15154bfd7` 和 `15154bff9` 两次读 +58，交 `14aa97730/14aa9c630`。查询返回值在 `15154c04a` 写 entity28174.field138419/+2b0，其声明 type37853 与查询 value25270 的父类吻合。这是既有 [Gadget lookup-key 合同](CLIENT_72_D02_GADGET_LOOKUP_KEY_JAVA_LOAD.md) 的同一配置域，不能再列为待发现的 +58 职责；它没有给出 MPLevel 配置内容或命中。

注册入口 `14aa99dc0..14aa9a4e3`（SHA-256 `a86b9049a9a2b1443a23efbfc84c7d22cd20252cf0966499e4816f8e054f625d`）的参数是 type70954，其 field350252/+10 为 `Dictionary<string,string[]>/2671`。原循环区分分组 key 与子名称数组：分组 key 保存于 `[rbp-30]`，数组元素名保存在 RSI；`14aa9a14c..14aa9a15c` 把分组 key 交具名 `GetPathHash/1471361f0`，`14aa9a1b6..14aa9a1bf` 以元素名索引名称→u64 缓存。

所以当前最小缺失原输入是：**type70954 中包含 `MPLevel` 的那一条完整分组 key 和原 string[]**。键名 `MPLevel` 不能直接充当资源文件名或分组路径。继续重复注册函数静态窗不能产生这条实际数据；本卡不合成分组或配置对象。

当前 Java `EntityWorld.initAbilities()` 读取 `ConfigGlobalCombat.defaultAbilities.defaultMPLevelAbilities`，没有使用 GadgetData/jsonName 查询。只新增 `{id:90000004,jsonName:"MPLevel"}` 的加载夹具会重复已知键职责，不能闭合 World 能力来源，故本卡没有发布该 Java 子项，也不批准空能力。

### 3. 唯一新窗：原 row 继续传递，没有字段职责许可

主线按本卡冻结请求独立核了固定 PE/ordinary AOT/唯一 pdata，实际完整范围为 **`148da0a30..148da0b9d`，365B/79条**，低于请求的 480B 上限。代码 SHA-256 `1a3a905d174604d2edb9c531d86569364f232240de6a5d036369feda86d8c0ec`；主线提取报告 SHA-256 `6afa42c8a898c5d52401944af9861f33c31d28835ac93d3732cd5849af6d438d`。只新增这一个代码窗，未读 xdata、邻窗或 callee，没有 CPU/live。

原 caller 是 `15154c01b` 的五字节 `e8104a85f7`，RCX 为 Pool 后的实体，RDX 为真实模板 row。新窗普通路径先调用 `148d8e3f0(receiver)`，然后由 `148da0ab2` 从 `[rbp-30]` 恢复原 row 到 RDX，在 **`148da0ab6` 调用 `148d6ebf0(receiver, originalRow)`**。本体没有原 row 字段解引用，也没有直接实体字段 store；前后全局调用及 IFix 分支不能改称字段解析成功。

此窗排除了“148da0a30 本体已解释 +50/+58”的假设。下一准确未读原输入是 `148d6ebf0` 的 consumer 本体，或者前节所列真实注册分组数据；本卡没有继续追 callee、申请第二个代码窗或运行模拟。

### 4. 创建、已有实体和拆除分开保留

`14828ba00` 的 World 分支先从 44512 取两个 u32/+20、+24；原查询命中已有实体后，在 `14828bab6` 先调用 `148d813d0`，之后才有 `1547052a0` 的组件查询与能力处理。缺旧实体才在 `14828bbb1` 转 `15154bf20` 创建。此源流尚不能把第二 u32 定名为 Java host peer，或把组件空对象视作成功。

既有四个生命周期样本的 modelType 是 18163/51522；有旧实体时分别停于 `15184af37`、`14eaaf0e3`。它们不包括 World/type46314，不能当作本卡已完成的 World 拆除。完整注册/stage、创建返回、替换/拆除和 SceneTeam/EntityAppear 三层仍未闭。

| 候选后继 | 本卡可证结果 | 当前实施出口 |
| --- | --- | --- |
| `146e055f0` | 两阶段接口调度，无模板字段消费 | 无新增 Java 数据或 sender 许可；实际回调另验 |
| MPLevel 注册 | 分组路径 key 与子名称分离 | 缺包含 MPLevel 的原分组输入，不能按键名拼路径 |
| `148da0a30` | 唯一新窗只传递原 row | 有界排除成立；准确下一 consumer 为148d6ebf0，本卡不扩窗 |
| 旧实体/拆除 | World 已有路径和 Avatar/附属模型归属已区分 | 不能把旧 teardown 片段覆盖为 World 生命周期完成 |

## 固定来源、双 fresh 与主线第三入口

完整 20 个 source pin/长度、原字段声明与所有静态断言在本卡私有报告；包括旧 runtime 第三报告 `bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4`、旧 Scene entry `917a5ad0505c6e90e57c4080f0521e26589fadd0ccfb29eb3257363b8f2a9780`、旧生命周期 `9cf97f36e58054aa6c072266fab5c36292b47074a0ce134d7879630430f0d7e2` 和 metadata cache `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`。新窗有完整 code/asm/pdata；旧 world/注册/manager 三窗只从已保存 instruction bytes 重组核 SHA，不读取原 PE。

复用已有 Scene 候选 commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`、Gadget/R3 的 AstaPS commit `6cfecfb55f889f8e3b7156adb12809608db4f74b`、中文定向分支检索和 partial 结论；没有重新网络检索、克隆或把外部同名资源当目标配置。

本卡标准库审计双新进程各 **42,834B**，完整字节相同，SHA-256 **`9ad013c7fab8644230fd0a3dee629a2065e627f4de12d2704840c1877f40e32a`**；20 来源门禁、41 项语义/完整性断言通过。逐指令连续性检查另列，不把数量当 native 测试数。私有根为 `local/migration72/d02-resume-20261110/scene-required-successor/`，正式报告为 `fresh-{a,b}.private.json`。

```powershell
python local/migration72/d02-resume-20261110/scene-required-successor/audit_required_successor.private.py --output local/migration72/d02-resume-20261110/scene-required-successor/main-third.private.json
```

第三只读冻结来源，不依赖 GAME_PATH、PE、现场、native CPU 或 Java。原开发展示器的 checkout 层级/JSON 容器/entry 类型错误，以及首轮审计把栈 `rsp+50` 误归为 row+50 的断言失败，分别保留于 `initial-root-failure.private.txt`、`audit-development-failure.private.txt`；未改原证据或放宽 hash。

本卡仅专题和私有审计产物，无生产、Proto、资源、patch、配置变化，无 Java 测试/构建或部署；不操作 Git，交主线审核实际差异后本地提交。资源 Ready、完整 Scene、D-02 均为 false。明确无独立可执行 Java 项后交回主线，不继续占用 max 做索引或扩完整 runtime。
