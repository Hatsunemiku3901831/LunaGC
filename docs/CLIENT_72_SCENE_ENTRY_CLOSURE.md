# D02-R4：SceneInfo 实际入场必经闭包

本轮是固定国服 7.2 文件的有界离线研究；不操作客户端、REA 活跃目标、网络、服务、数据库、构建或生产资源。原有 raw、GUID/实体关联、retain/CopyFrom/hash 合同直接复用。D-02、完整 handler 和真实入场保持未验。

## 重大问题计划

| 项目 | 约定 |
| --- | --- |
| 目标 | 从已验 `21761/type34208` 前缀继续，核 `1507E44F0` 的80154非零状态消费、manager/team/44512真实必经边与当前 Java 身份/顺序，收敛生产接入规格及首个未闭输入。 |
| 证据 | 每次核五 profile SHA；固定 scene raw/identity/deep/followup 摘要和执行源。实际 Scene byte[] 仅作为来源明确、locator待主线审的研究输入；不宣称自然 revision 已选。 |
| 未知 | 80154业务名与新旧号必要性、自然stage/能力表、组件与实体生命周期、完整handler、SceneTeam/Entity通知三层。 |
| 候选 | 80154可能为uint32→float状态同步；旧Java/Proto同形不是语义来源。World排除Beyond分支，但普通team/entity仍可能必经。 |
| 最小实验 | 重提metadata/AOT和准确原生范围；复用真实reader，独立非零/高位/重复/错误wire输入，执行新消费者至首个未采上下文；静态核当前Java生产链与存储来源。 |
| 范围 | 仅新增本专题、`tools/client72_scene_entry_closure.py`、对应测试；原始输入/反汇编/轨迹在忽略目录 `local/migration72/d02-active/scene-entry-astra/`。 |
| 失败出口 | 未知业务调用/组件/rgctx在准确模块RVA、类型与读写宽度处停止；不补空对象、零分配、旧常量或成功桩。无独立必要性证据时如实未知，不扩战斗。 |
| 验收 | 双新进程确定性、source/input/raw SHA、独立字段/原生行为断言及可执行第三命令；明确模型、partial状态、默认省略与禁止范围。父任务审核/提交，不自行Git。 |

## 本轮实际新增结论

新增三段限定证据：根 tag7 的 LoadingTask token 身份、80154 非零列表到真实 uint32→float 字典、实际 Scene 表完整 2,431 行及 Scene 3 投影。13 项新检查与48项既有 Scene 关联检查共 **61 项通过**。它们没有闭合完整网络 handler、自然能力注册、实体创建或 D-02 实机。

初始127份证据/ready224是父任务接手基线，本卡未改这些文件，也没有执行或宣称新的 ready 检查。仅三份新工具/测试/专题和忽略目录产物，无构建、部署、客户端、数据库或 Git 操作。

### 34208/tag7 是无算术转换的 scene token

从同一个真实 reader 对象出发，显式 manager 中段 `14CD685D2` 读取 root `+34`，进入 `14828FEA0 → 148290060 → 14CD5F7B0`。后者执行真正 `1578B66F0` 缺键查询，所传 uint32 到达当前 metadata literal52998/cell`145B31650`：`[Loading] LoadingTask GetLoadingTask is Null token:`。具名来源复用已审八握手摘要，并从当前文件重新提取该 literal；没有靠旧 Proto 名称或同形字段命名。

独立样本0、1、`0x80000001`、uint32 max均保持全部32位；省略与错 wire 得0，重复字段后值覆盖。手工 golden `388180808008` 对应 root tag7 的高位 token。与当前加载上下文 token 91/92失配时，真实门禁转到 `14FED6AA0`，未命中任务查询；本卡在此准确后继入口停止，不把它叫断线或全部失败效果。

这里只证明 **34208/tag7 / MCCMGLDOECH / +34 / uint32 varint → 逻辑 scene token，identity conversion**。其省略/raw0都表示逻辑0，不能套用八握手 Req/Rsp 各自 mask。Java 候选来源是当前 `Player.getEnterSceneToken()`，保持 int 的32位模式即可；不由本卡批准0 token的实际加载、重试策略或返回码。

原型从实际寄存器和字段路由核对为：`14828FEA0(level*, uint32 token, uint32 from49723_38, type34045*)`，RCX/EDX/R8D/R9；首参数及加载上下文是显式存储夹具。开始点在尚未闭合的本地实体 setter 返回之后，不能拼接为完整 manager/handler。

### 80154 的全部列表消费已执行，但非空必要性仍未知

`1507E44F0(network*, IEnumerable<80154>*)` 的 RCX 是 type56238，RDX 是真实 reader 产生的 type49723/tag3 列表。目标先由 `1517ACC90 → 1517AB3A0` 查询 **scene+230** 的实体，再检查 entity`+426`单字节。这个选择与 root tag12写入的 **scene+284** 不同，二者不能共用一个采样值。

若查到可用实体且`+426 != 0`，方法直接返回，既不清理也不插入。否则清理 network`+110`的具体 `Dictionary<uint32,float>/instance5971`，随后真正 GetEnumerator、MoveNext、get_Current逐项读取80154 `+18/u32`和`+1C/float32`，调用 `1553C8AF0/TryInsert`。EDX为key、XMM2低32位为原float bits、R9B=1为覆盖模式。整个 consumer 正常返回，不仅是选定 store 或直接调用 TryInsert。

| 新独立输入 | 实际原生结果 |
| --- | --- |
| key1/8同bucket，1.25/-2.5 | 两键两值分别保存 |
| key1/`0x80000001`同masked hash | 完整32位键保持不同 |
| 重复key7及uint32 max | key7后值覆盖，max对应最大有限float |
| 0、最小subnormal、负零 | 位型`00000000/00000001/80000000`分别保持 |
| 空列表、已有key99 | 旧字典清空，version11→12 |
| 可用实体`+426=1` | 旧key99/value0.5、version11均保持，未创建enumerator |
| 相同实体`+426=0` | 清理后保存新key9/value1.25，version13 |
| null列表 | 先清理，然后到真正null helper`1404C2BE0` |
| null字典 | 到真正null helper，尚未清理 |
| 夹具容量8、第9个元素 | 前8项已保存，止实际扩容前的`159C329A0`；不是客户端容量8上限 |

80154业务名、key与账号/保存/资源的对应、发送非空列表的必要条件仍未取得。当前 Java TeamEnterSceneInfo builder没有此列表的独立来源，不能补一条默认非零记录，也不能据空夹具成功批准生产省略。

明确模型：继承已审 reader/数组、接口表和 MethodInfo 存储；实际 `158492E10` 创建值迭代器，经托管 box模型复制48B。两处拆箱gateway为本工具生成、只把receiver调整到box+16的**显式模型**，不冒称文件中的ABI thunk。实际共享原生 `1572292B0/1572294C0/157229590`负责迭代、索引和Dispose；消费者、字典hash/碰撞/覆盖及所有float store没有成功桩。底层 Array.Clear字节操作与场景FindEntry所选实体是原有显式夹具。

### 实际 Scene 表完整解析和 Scene 3 字段

固定 locator报告 SHA `fc2c3aca3f472354f0533c9ac319387102fd6f8a1a7195b0400d06988a420fda`，实际 Scene `m_bytes` 280,285B/SHA `2964028cba6cd66b4c04e86472411d10756643e230c1b6b5c34206ab7a0e0ce0`。来源链见[locator](CLIENT_72_RESOURCE_LOCATOR.md)。本卡未更改、重新签名或重解 block；执行时输入来源固定，是否已纳入父任务审阅由父任务记录。

独立从 literal79858/cell`145B65DB0`准确RIP引用`147BB19B4/147BB1B24`找到 owner66294；whole方法`CNLIBMNOALB/147BB17E0`、具体row type70859/parser`14E3CED00`、字典`Dictionary<uint32,70859>/instance42688`及GLOBAL`+34730`。原生头计数为`((LE32 + 0x3BB46952) mod 2^32) XOR 0xC8D9FB31`；实际头`FC88258D`得到2431。没有剥头、猜JSON或从候选表造输入。

复用 Avatar 活动线 NaturalRuntime 的**冻结源码快照**，SHA `a1955fcf4b47dd70cf0dc51495b30307beb8d54a7b5b960dca97503ac1efe48f`，快照在本卡忽略目录。共享原文件仍可能迭代；工具只接受此精确快照，不能自动换成新源码求通过。row parser、循环、实际TryInsert及后续key3/4/missing-max的真实FindEntry执行原生；2,431个唯一key、游标280285等于完整输入。四字节真实头但无首行的独立负样本在游标4到`1404C2650`原生数组边界入口。

Scene新增长字符串路径需要TLS scratch。最终仅构造明确的OS TLS索引/槽和4096B预分配slab；真实`1405070C0`分配及row内联归还均执行。全表1007次请求，实际最大88B，每次used从0开始，结束used=0。未模型化该allocator、字符串变换或放宽未知调用。类型分配/数组/Dictionary容量构造、相同类型检查、UTF8→托管字符串以及memcpy/memset仍沿冻结runner的明确模型。

| type70859 字段 | Scene 3实际值 | 批准范围 |
| --- | --- | --- |
| `KDLEOKGKEAN/+7C`，encoded uint32 type58209 | 解码3，与原生字典key3相同 | 当前表行ID |
| `IELLPMKLAIO/+84`，enum80117 | 1；当前metadata符号`SCENE_WORLD=1` | 当前Scene表场景类型；**不是SceneScriptData.stageType** |
| `OGEKKNHOAPE/+18`，string | `Level_BigWorld` | 原始字段与值，不额外猜业务alias |
| `FKCMBGJCOKC/+20`，string | `Data/Environment/EnviroSystemProfile/BigWorld/ESP_Md_General` | 原始字段与值 |
| `NEALMAKPJGK/+40`，string | `BigWorld_LevelStreaming` | 原始字段与值 |

另保存Scene1/4对照及6枚举31符号。这里的encoded整数使用此前已审**配对storage permutation/seed模型**进行原生encode/decode；不是文件加密参数，也不是已采自然encoder前半表。Scene表`SCENE_WORLD=1`与已审运行类型45183的`stageType/enum66233: World=0`为不同枚举，不能把1直接写到stage。实际revision选择、SceneScriptData初始化和stage来源转换未执行，保持false。

## 生产者可接规格与顺序界限

| 消费字段 | 类型/转换与Java来源 | 本卡许可 |
| --- | --- | --- |
| 34208/tag7 | uint32 varint，逻辑值identity；`Player.getEnterSceneToken()` | 可据新证据补独立适配器/测试；不单独放行正式sender |
| 34208/tag12、18295/tag13/tag4 | 已审本地/角色实体及u64 GUID；当前活动EntityAvatar/Avatar getter | 原关联保持，先注册Avatar再处理SceneInfo；完整g2仍由Avatar线验收 |
| 18295/tag10/tag7 | 已审typed Weapon分支的u64 GUID/u32实体ID | 必须同owner且对应实际装备对象；不批0或借其它角色GUID |
| 18295/tag8/tag5 →34045 | 原有retain与武器分支CopyFrom合同 | 不混用，两份77771复制只按旧范围；其它复杂非空字段未扩大 |
| 49723/tag3 →80154 | key uint32/tag1、float32/tag2，重复key后值覆盖 | 仅raw/状态消费合同；业务来源与非空必要性unknown |
| 49723/tag1→89710→83320 | 两hash为fixed32、tag1为u32；Java现有TeamManager能力来源只是候选 | 未批准自然hash表与默认名，不把`Utils.abilityHash`或旧AbilityEmbryo直接定为新字段 |
| root/tag14 →44512、49723/tag7/tag4 | 真manager仍调用`14828FEA0/14828BA00`；后者实际静态接`15154BF20`创建/能力Clone/组件 | 没有全创建与生命周期许可，默认空对象不能代替这些输入 |

当前Java Init源码仍先发送SceneInfo，再ScenePlayerInfo/SceneTeam/SyncTeam/SyncScenePlayTeam及InitRsp；中间可能有条件发送和异常。Done先设置LOADED，再spawn/showOtherEntities/后续同步，最后DoneRsp；不能以LOADED证明成功。SceneTeam对每个活动角色调用`getWeaponNotNull()`。新号初始武器缺ItemData时，`new GameItem`更早在`data.getId()`失败，并非静默跳过，精确来源见[Java资源合同](CLIENT_72_D02_JAVA_RESOURCE_CONTRACT.md)。本工具固定8份当前Java字节SHA，仅审源码，没有运行Java。

仅准进一步独立Proto源/适配器/测试设计；正式SceneInfo sender、SceneTeam/EntityAppear三层、完整init/direct与条件通知集合、资源生成/安装与ready均不由本卡批准。不修改旧Proto或生成Java；不省略通知，不更改版本、不让服务器状态代替客户端结果。

## 精确未闭输入和下一动作

对**从21761完整入口连续执行**而言，最先仍需的自然输入是能力名称注册上下文：YuanShen模块RVA`0x100D85A0`的hash查询（调用链`1507D1620→1507E41C0→1500D85A0`），由GLOBAL根cell RVA`0x586B600`解引用后的`+C6D0`字典与`+C698`默认string。它在本卡80154消费者之前。最小输入为两个83320 fixed32 hash对应完整key/UTF16 string、表bucket/entry/count与默认string长度；writer来源是已核`AddAbilityString/1500D5480`，reader为真实ContainsKey/get_Item/FindEntry，不能用受控表当自然注册。

该前置满足后，本卡给出的下一准确观察点是`1507E44F0`输入count/每项u32+float32、scene`+230`和`+284`两个u32、查询实体`+80`指针与`+426`byte、network`+110`处理前后。自然80154非空时才继续寻找服务端保存/资源key；为空不批准删其它字段。

再后是实际stage/type45183`+70`与当前加载任务，而非Scene3枚举值；以及`14828FEA0`的token/49723能力和`14828BA00`的44512两u32/34045。创建`15154BF20`的下一资源读取`15154BF6E`依赖非文件backed静态cell`14586F4C0`（模块RVA`0x586F4C0`，uint32），再交`152E10FC0`取得模板；其自然初始化writer尚未闭，禁止填旧常量。现有实体则需精确组件context与teardown顺序，不能将“找不到旧实体”的夹具当清理成功。SceneTeam/EntityAppear依然各自缺目标三层，不由SceneInfo reader或本卡表parser代验。

这是一条按handler先后排列的输入队列，不声称只有一个全局缺口。父任务可继续独立的SDK、Avatar、资源revision与通知取证，无须再次采同一未初始化空槽。

## 失败、复现和冻结

本机原始日志保留：`enum-shared-probe`遇到AOT零地址后改为明确保留零声明；最早Dispose无unwind错误采用大注册区间，随后改为只接受已核单指令ret；`float-probe1`没有直接文件拆箱thunk而拒绝，最终显式标注managed gateway；`float-probe2`扩容先到真实GetPrime，边界修正为该首调用。`entry-probe1`遗漏literal前缀，守卫拒绝后按固定literal修正；`entry-probe2`遇到格式provider，复用旧已审格式模型，未补业务成功。Scene表前两轮分别在TLS读取和scratch归还失败，最终执行原生allocator/归还而非忽略它们。

`tests-before-freeze.log`因加profile固定hash后使用旧report出现准确source-hash拒绝（其余12项通过）；正式报告重跑后61项全部通过，不刷新旧report的hash来掩盖。

```powershell
$game = & tools/game_path.ps1
python tools/client72_scene_entry_closure.py --client $game --output local/migration72/d02-active/main/scene-entry-third.json
$env:CLIENT72_SCENE_ENTRY_REPORT='local/migration72/d02-active/main/scene-entry-third.json'
python -m unittest tools.test_client72_scene_entry_closure tools.test_client72_scene_deep_followup tools.test_client72_scene_deep tools.test_client72_scene_info_contract tools.test_client72_scene_info_raw tools.test_client72_scene_entry_identity -v
```

工具默认使用本卡已pin的`natural-runtime-snapshot.py`与locator正式路径；可用`--scene-runtime/--scene-table/--locator-report`传递同字节副本，hash不匹配即拒绝。完整报告包含本机原始表、反汇编和托管地址，只留忽略目录，不提交共享摘要。

正式新进程产物为`scene-entry-astra/final-{a,b}.json`，两份逐字节一致，均9,143,952B，SHA `917a5ad0505c6e90e57c4080f0521e26589fadd0ccfb29eb3257363b8f2a9780`，证明`scene-entry-astra/determinism.json`。工具SHA `5d255f494bf5755b204697789c3a8ef1ae4db88ea3031d772d0a7cf987f7bfb3`，测试SHA `46e77ee3b5cb765700b6c7f2ed280e1bee11c7a940d57c073264bc838015df39`。主线第三复验/Git/共享索引由父任务完成，本卡不自行提交。

主线已审真实代码、managed gateway/格式/TLS模型及原始证明，第三 `main/scene-entry-third.json`为同一9,143,952字节/SHA，与双跑逐字节一致，13新+48旧=61项通过；证明 `main/scene-and-sdk-crypto-main-proof.json`、日志`main/scene-entry-main-tests.log`。只接受已列token、受控80154消费与实际Scene表；完整handler、自然stage/注册/生命周期和SceneTeam/Entity三层保持未闭，后继独立专题继续。无生产、资源或部署变更。
