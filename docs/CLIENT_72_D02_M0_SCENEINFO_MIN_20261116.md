# D02-M0-SCENEINFO-MIN：单角色首场景最小合同

## 中文有限计划与边界

本项仅审 `21761/type34208` 的 `root14→44512`、`root15→49723` 与其能力身份子图，首发固定女主 `10000007` / depot `701` / 武器 `11101` / Scene `3`。复用已经验收的 raw、身份、生命周期、hash、场景表和当前 M0 规格，不重扫全协议、不恢复完整客户端 allocator、不展开旧号 121 角色。

1. 固定并核对 SceneInfo 相关已验摘要和已有原生源码窗；字段→wire、类型→用途、业务值来源分列。
2. 沿已有 `14828BA00`、`14828FEA0`、`1507E41C0` 与世界工厂源码，追两个 u32、能力 tuple 和 34045 的直接使用；只在具名消费者或准确 typed 关系足够时命名。
3. 对照当前 `TeamManager`、`World`、`EntityTeam`、`EntityWorld` 的真实对象和能力根，区分服务端可自定数值与必须正确的协议身份。上游同形和旧 Proto 只作候选，不补 tag 或字段职责。
4. 成熟最小合同立即交唯一集成者；缺边时给唯一 body/typed 关系的最小来源规格和解除条件。本文是唯一写入文件，不改 Git、Proto、Java、测试、构建、实例、数据库或客户端。

原 A 第二返回、preview/B 组合 loader 与准确依赖保持停止。已有成果只读复用；本项没有启动新现场或重试已拒动作。研究工具沿 `reverse-engineer-anything` 固定证据要求，本轮优先复用已采输出。

主线随后明确允许两个独立静态 source：`owner28174/m219141@148D813D0`（44512 第二 u32 的直接更新消费者）、`owner70582/m584597@151540800`（root15/tag7 的 team 工厂）。原件只写忽略的 `local/migration72/d02-m0-sceneinfo-min/`；先核固定五输入、普通 AOT、参数与唯一 pdata，再各取完整 body，不执行目标代码、不追 allocator/冻结 callee。边界不唯一、严格解码不完整、源漂移或新拒绝均保存准确停止，不裁尾、跳 data 或换工具重试。

## 有限结论与当前出口

本项完成 **三个独立完整静态 source、两条实体 ID 路径和两个模板的区分**。`root14` 的工厂使用模板 `90000004`，`root15/tag7` 的工厂使用 `90000002`；两者的实体 ID 不能共用同一个 `World` 对象，模板 ID 也不是发到 wire 的实体 ID。此前把二者都留在“完整创建未验”之下的宽缺口现缩到具体身份字段与能力角色。

**完整 root14/root15 发送合同仍未成熟。** 44512/tag1 的实体 `+42C` 状态与两个 hash 的具体业务角色未闭，34045 的初始化 bool/状态业务投影也未闭。本项没有批准以空能力、空 hash 或空对象发送，不把服务端可选全局能力策略升级为 entry-ready；完整 D-02 和客户端验收不变。首次进入世界不要求重做完整 allocator、全量 g2 或旧号 121 角色，这些也不是本项准确剩余输入的替代描述。

既验 `34208/tag12`、`tag7` 和 `18295` 的四项 Avatar/Weapon 身份字段直接复用。本项没有安全审查拒绝；最后停止是 **独立静态工具所需的唯一 pdata 边界不成立**，见下文，不能写成客户端或整个场景研究被安全拒绝。

## 固定输入与来源

本轮三个 source 和最后一个 getter 的前置均重新核对 profile 五文件。协议输入固定为：

| 文件 | SHA-256 |
| --- | --- |
| `YuanShen.exe` | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| `global-metadata.dat` | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| `startup-metadata.dat` | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |
| `AccountPlatNative.dll` | `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25` |
| `Astrolabe.dll` | `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442` |

profile SHA `a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d`。metadata/参数使用已审纯数据缓存，固定 `metadata.private.pkl` SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`、签名 JSON SHA `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`；不是本轮重新扫描所有协议。普通 AOT 指针则从当次 stock 直接读取，按 method index 核对。

只读复用并实际核 hash 的正式证据：

| 证据 | SHA-256 / 本项消费范围 |
| --- | --- |
| `scene-info-raw-observations.json` | `f52f41a817241646d63d692b120d5c134c1b30dfe8b2da5d440de5d9557f7c75`；34208/44512/49723/83320/89710 raw 字段和 wire |
| `scene-info-contract-observations.json` | `a0c88290e5d4bbd31b2ea0e83986ffc9e51235462b727429e441cba8cd48a2ad`；已验 Avatar/附属 Weapon 角色来源关联 |
| `scene-deep-observations.json` | `bed41fc74af5ef39b951c6d57834c10a56b0577da1166957d5221f19ada4acd5`；retain/release、manager 路由和能力前缀 |
| `scene-deep-followup-observations.json` | `755ff3ad04ec69684b608de36667619746ec7421b463a5728b32f292b613e974`；有限 34045 Clone 与受控注册表查询 |
| `d02-active/main/scene-entry-third.json` | `917a5ad0505c6e90e57c4080f0521e26589fadd0ccfb29eb3257363b8f2a9780`；14828BA00/14828FEA0/14828F870/15154BF20 完整旧静态源、token/80154 |
| `d02-active/main/scene-runtime-context-third.json` | `bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4`；UTF-16 base131、默认槽、23977.cctor 和世界模板来源 |

原始证据根为忽略的 `local/migration72/`；上表后两路径相对该根。场景主题来源是 [Raw](CLIENT_72_SCENE_INFO_RAW.md)、[Contract](CLIENT_72_SCENE_INFO_CONTRACT.md)、[Deep Research](CLIENT_72_SCENE_DEEP_RESEARCH.md)、[Entry Closure](CLIENT_72_SCENE_ENTRY_CLOSURE.md)、[Runtime Context](CLIENT_72_SCENE_RUNTIME_CONTEXT.md)。本项共享文档仅保存脱敏合同与摘要。

## 三个准确 source 的真实验收范围

复用 `tools/migration72/scene_mplevel_actual_xref.py` 的 `locate_pdata` / `decode` 库函数，未执行其旧任务 CLI/collect。该 helper SHA `1e462537e4fc2b37a288c0d8cc15c13e61775e00a2d891e77bc4a88d90630355`。异常目录为 RVA `1A4E6A40`、14,344,080 B，SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`。

| owner / method / 来源 | 唯一完整边界 | 实际结果 |
| --- | --- | --- |
| 28174 / m219141 `GAOHIHIJLJJ`；普通 AOT `148D813D0`；实例、1 个非 by-ref u32 参数 `HBBONDKCMJF` | `[148D813D0,148D81512)`，322 B；pdata record RVA `1A7CCCB8` | 84 条连续完整指令，重拼字节与 raw 完全一致；code SHA `87d4712213b4aad33220ba6ddd9966035add8e6d3fe9bc78b786a3d3121017a7` |
| 70582 / m584597 `DPILEFMGFLH`；普通 AOT `151540800`；static，非 by-ref u32/u32/bool | `[151540800,151540CD5)`，1,237 B；pdata record RVA `1ABE0250` | 284 条连续完整指令，重拼字节与 raw 完全一致；code SHA `df3ef4b592ed89a4aa91730a8ce9cb65405719fff2e09495b0ed0205b3f31c6a` |
| 43261 / m359954 `MoleMole.MonoEffectPluginIntensityModulator.IsAuthority`；普通 AOT `1494D52C0`；实例、零显式参数 | `[1494D52C0,1494D53FE)`，318 B；pdata record RVA `1A806108` | 71 条连续完整指令，重拼字节与 raw 完全一致；code SHA `56d9caff3fe6e864b7495cb47b0ee5529e27cd345319881121097a1c3a602fa9` |

三项共 **1,877 B / 439 条指令**，没有动态执行，`nativeCPU=0/live=0`。完整 body 中的 allocator/接口/IFix 调用只作为原始指令留存，不跟踪准确 A 返回，不按它们构造或补成功桩。三者各自只有一个普通方法声明占用该 AOT 地址；方法所属类型与参数也单独保留。

原件在 `d02-m0-sceneinfo-min/`：`219141-source.private.json` SHA `09645f694d84183206c35b4e9d0015be0bd2cf08533bc734ab0bc8b836bae1bd`、`584597-source.private.json` SHA `e2476879b75b6146f4ee93392a369cbd332278fb80598441d1087a407f47748f`、`359954-source.private.json` SHA `8c4c48037e17bc050b4e7cfa797aab65d676fdb315e3026e575e323579e3aab7`。原 raw 文件独立保留，不覆盖失败或旧证据。

## 身份参数：已证的实际流与业务来源

偏移和地址均十六进制。

### root14→44512

`14828BA00` 在 `14828BA59/5C/5F` 分别读取 `44512+20`、`+24`、`+18`。前两个 u32 的直接用途现在可分开：

| 精确 message / tag | 目标原生用途 | 本次服务器实际对象来源 / 状态 |
| --- | --- | --- |
| 44512/tag5 `LLHEBIHGDAE` / +20 / u32 | `14828BA73→1517AB3A0` 的场景实体查询 key；缺实体时 `14828BBAD→15154BF20` 第一参数。同旧源 `15154C075` 把该原值交实体初始化虚调用，`15154C157` 写 scene+280 | `player.getWorld().getEntity().getId()`，亦即 `getLevelEntityId()`，是合适的真实 ID 投影；不能把模板 90000004 或 row+20 的 9999 写入此 tag。目标 key/实体 ID 路径已证，完整创建/客户端消费未验 |
| 44512/tag1 `HKLILOKPKBJ` / +24 / u32 | 新实体时是 `15154BF20` 第二参数并写 entity+42C；已有实体时在 `14828BAB4` 交 m219141。m219141 读旧 +42C，相同直接返回；不同先调用 `148D6F5C0(entity,new,old,0)`，再在 `148D81404` 写新 +42C | `world.getHostPeerId()` 仍是业务候选。+42C 的 authority/peer 具名桥没有闭合前，不把该 getter 正式接入 sender |
| 44512/tag2 `KDKAEHEEMMJ` / +18 / →34045 | 作为收到的状态传入已有实体同步；新实体分支非 null 才调用 `147CCFBC0` Clone 并注册回调 | 实际 `World.entity.getAbilityRoots()` 是服务端状态候选；不能从旧 `AbilitySyncStateInfo` 的字段名直接迁移 |

其中 `15154BF20` 的准确模板由已审 `23977.cctor` 的 `14D2CA40D` 写 `14586F4C0=90000004`；本项只读复用，不重做 allocator 还原。

### root15→49723

`14CD68680→14828FEA0(level,rootToken,49723+38,49723+28)` 的调用已验。后者把 +38 的原 u32 交 `14828F870`；在无可用旧实体分支，`14828FA3A/3C/3E→151540800` 分别交 u32/u32/bool。

| 精确 message / tag | 目标原生用途 | 本次服务器实际对象来源 / 状态 |
| --- | --- | --- |
| 49723/tag7 `JNBCPNHJCNI` / +38 / u32 | `14828F870` 先用它查询场景实体；新工厂 m584597 的第一参数非零时不改值，送同实体初始化虚槽。当前 local=true 路径在 `151540AAF` 把该值写 scene+230，并把新实体指针写本地管理器+58 | `player.getTeamManager().getEntity().getId()` 是当前真实 team 对象的身份候选；不能改填 World.levelEntityId。目标本地实体 ID 与模板的区分已证，不能据此宣称完整 Team 协议已闭 |
| 49723/tag4 `GLHJPPFOGMB` / +28 / →34045 | 原对象送 `14828F870`；已有实体分支与 root14 一样依据 +40 状态切同步；新建分支非 null 时 Clone 并挂回调 | `TeamManager.entity.getAbilityRoots()` 及确有来源的团队状态是候选。不得把旧 builder 的 Phlogiston/MoonPhase/Hexenzirkel map 逐名移植到未知 raw 列表 |

m584597 在 `151540847` 读取 `14586F444`，而既采 `23977.cctor/14D2CA403` 写该静态槽 `90000002`。这是与 root14 的 **90000004 模板不同的原生来源**。该常量只用于客户端工厂模板查找，不是目标 wire 实体 ID，也没有因为服务端 enum 的 `TEAM/MPLEVEL` 名称而伪造 target type alias。

m584597 的第二 u32 参数同名 `HBBONDKCMJF`，在 `151540983` 写同一实体基类 `28174+42C`；非强制本地分支还与静态 `23977.OOJNMMAIJMI/14586F438` 比较，结果送虚槽155。这个比较支撑 authority/peer 候选，但静态槽及 +42C 的正式业务身份仍须准确消费者桥；不能靠相同混淆变量名定名。

## 能力子图：可以自定的业务与仍必需的身份

### 49723/tag1→89710/tag1[]→83320

raw schema 已闭：83320/tag1 `DOBGMPBDLKF` 为 u32 varint，tag2 `EMBPDEHFOJK` 和 tag3 `OPKFBNFODJM` 都是 fixed32。完整 handler 前缀先把两个 hash 分别交 `1500D85A0` 反查能力字符串，再向 network+108 的具体 `List<ValueTuple<uint32,string,string>>` 写记录。既验报告按实际存储给出的字符串顺序为 `(tag3名称指针,tag2名称指针,tag1 u32)`，不能据此直接给两个 string 加旧 name/override 标签。

hash 数值公式已经目标验证：按 UTF-16 code unit，初值0，`h=(131*h+unit) mod 2^32`；当前 `Utils.abilityHash` 可用于这个数值变换。能力名、覆盖名和条目 ID 的 **协议角色/引用** 仍不是自定 HP/攻击一类的业务数值。不能用任意不存在于客户端注册表的字符串、把两个非零 hash 改成0，或只发自增 ID 的空记录来跨过这一步。`Default` 的正确 hash 已知也不证明其已自然注册，更不证明它应属于哪一个 field。

当前 `TeamManager.getBaseAbilityControlBlock()` 从 `defaultTeamAbilityStrings()` 和 Scene3/`Level_BigWorld.teamAbilities` 取名字，按先后递增 embryoId；这提供服务端可复核的候选名称来源。它没有给目标 83320 两个 fixed32 的 name/override 角色、目标 ID 的映射职责或自然非空集合。因此不能整包搬旧 `AbilityEmbryo`。

### 两个 34045

既验 raw 为 bool/tag1/+40，五个列表/tag2..6，其中 tag2/+20 与 tag6/+18 是两份同形 `77771`，其余为 70808/51162/51050。retain/Clone 已验范围保持原专题，不把 raw 闭包或同形列表升级为 dynamic/SGV 的具体业务职责。

已有 `14828BA00` / `14828F870` 的直接条件明确：在可用旧实体分支，收到状态为 null **或** 状态 `+40=false` 时，将1传给 `147586F70`；非 null 且 `+40=true` 时传0，再进入状态同步。新建实体分支仅在非 null 状态时 Clone、配置后续回调。这既没有证明必须有某一条非空列表，也没有证明 null、false、true 任选都能完成首场景。`147586F70` 的实际职责和所选状态生产合同未闭，不能把 bool 简称为“初始化成功”然后手设 true。

用户授权可让 M0 **不追加可选全局 MP/Team/level 能力**，仍保留已审 Avatar 基础能力，并用真实 EntityWorld/EntityTeam 验身份。这是明确的 `server-provisional` 业务策略，不能反向证明 34045 可省略、49723 的能力身份列表可取消，或真实客户端不需要相应初始化。当前证据没有给出必须非空的某一列表/某一 key，故不虚构非空要求，也不批准空壳替代。

80154/tag3 的 u32→float 状态消费、重复覆盖及 scene+230 条件沿前卡复用。其业务 key、非空必要性仍未知；这与 team factory 新证 scene+230 属同一目标槽的关联是可复用进展，不是构造一条默认非零记录的理由。Scene3 的普通 World 路径也不因 Beyond 条件被排除而自动批准省略其他能力。

## IsAuthority 桥和真实停止点

第三源确实是具名 `IsAuthority`，但它本身没有读取 +42C。实际字段链按 metadata 独立绑定：

`61278.BaseMonoEffectPlugin+20 → 29296.MonoEffect`，`29296+D0 → 63617`，`63617+18 → 28174`。在普通路径有对应实体时，`1494D5351` 取实体 class+390，并在 `1494D535F` 尾跳，换算为虚槽88。owner28174 的具体声明是 **m218972 `BPHJMGBGICJ` / `148D6E520` / 零参数 / slot88**；未凭具名 wrapper 把该 getter 的内部比较补出来。

主线明确批准只补这个 getter。第四次取源在普通 AOT、metadata 和五 stock 核对后，因所扫异常目录中 **包含 `148D6E520` 的 pdata 为0条**，在 `collect_authority_getter.private.py:63` 以 `ValueError('Nonunique pdata')` 停止。工具原错误名包含“Nonunique”，准确事实为 **无 containing row**，不是多个冲突 row。getter body 读取0 B，strict decode 未开始；没有改用下一注册地址、手裁短窗、跳 data 或追 slot155。该停止只说明本工具批准的完整 body 边界来源不足，不说明源码一定不存在或该 getter 会安全拒绝。

原失败 `218972-failure.private.json` SHA `003c5c974c62912e86308de1177882f1bbee060c6e5c513676c476b900f5cec4`；同固定异常目录的无 body 诊断 `218972-pdata-stop.private.json` SHA `08bf3e34bc94131849c8b78dc833ccc355c385a4f6904b37ad2ee7b46d2e20f7`。准确停止与原错误都保留，没有将局部源码成功升级父项。

## 下一最小输入与集成边界

| 剩余问题 | 准确下一输入 / 验收与停止 |
| --- | --- |
| 44512/tag1 → entity+42C 的业务角色 | 同 stock 下 m218972/slot88 的**独立、可核完整边界来源**及完整正文，或另一条已具名且准确读同 owner28174+42C 的 typed consumer。需证明其与 `23977.OOJNMMAIJMI` 的关系和 bool 返回职责；只有正文/边界成立后才继续，不重发本次缺 pdata 的相同取源，不以 authority 名称补内部语义 |
| 49723/tag1 内两 hash / u32 的角色 | network56238+108 的具体 `List<ValueTuple<uint32,string,string>>` 到能力创建的准确 typed 消费源，独立区分两个字符串及该 u32 的引用职责，并绑定首发实际名字/资源。当前工厂两个新 source 均未消费此 tuple，不能把它们当 hash 角色证明；不要求全量客户端构造树 |
| 34045 最小真实状态 | 所选初始化条件 `+40→147586F70` 的准确职责，以及实际所选 World/Team 状态到 raw bool/必要列表的单条非空或明确可空的消费桥。需区分“服务端没有追加某项能力”和“客户端这份初始化状态已满足”；不要求恢复原版数值，遇准确冻结调用返回仍停 |
| 80154、tag9、Beyond 对象等 | 非零必要性或普通单角色场景真实调用证据到达时只前移该一项；没有来源不造业务 key，不因不懂全量后代统一挡住已有身份实现 |

可立即交唯一集成者的部分是：保持 `21761/type34208`，继续既验本地 Avatar/Weapon GUID/实体 ID 与 token；root14/tag5 和 root15/tag7 的 ID 来源分开，分别使用本次真实 World 与 Team 对象，所有通知引用同一份 ID；root14 的第二 u32、两个 34045 与 83320 能力角色仍明确未 ready。身份级 wire 回归可由集成者串行执行，但这些局部成功不能放行完整 SceneInfo sender、部署或客户端入场。

本项只新增本文和上述忽略证据；运行/Proto/生成 Java/共享台账/Git均由唯一集成者负责。没有新增 JUnit、目标动态执行、构建、便携部署或客户端验收，也没有把历史测试数算作本轮。源码窗固定输入、AOT、pdata、参数和完整重拼已做实际检查；按一次性有限任务不另建独立审核/测试。三个完整源和最后的准确停止已交主线与集成者，本文可作本地分项提交输入；本子任务没有自行提交。
