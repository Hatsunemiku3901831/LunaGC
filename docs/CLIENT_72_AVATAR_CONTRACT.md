# 国服 7.2 Avatar 初始化用途与最小消费合同

F-03d3f1 在既有 [外层 raw](CLIENT_72_AVATAR_OUTER.md)、[AvatarInfo raw](CLIENT_72_AVATAR_CORE.md) 与 [装备消费合同](CLIENT_72_EQUIP_CONTRACT.md) 上，闭合角色批量数据应用用途、**5 个外层字段和 3 个角色属性 ID**。`AvatarDataNotify`、`AvatarInfo` 是目标客户端具名 Avatar 资源、实际角色模型/字典、属性事件和装备槽消费者共同支持的用途别名，不是恢复到了原始英文消息名，也未采用 Asta 名字证明字段。

这里的初始化用途指通知把角色数据交给模型进行初始化或重用更新，**不限定它只用于首次登录，也不声称整个角色字典被替换清空**。完整通知后半、角色 Handler 尾部、场景对象和 UI 均未验收。`runtimeStatus=not-tested`、`targetProtocolReady=false`、`completeAvatarHandler=false`、`fullAvatarRawClosure=false`；这些字段也不构成已验完整登录最小发送集合。

## 固定输入与复现

| 相对 `GAME_PATH` 路径 | SHA-256 |
| --- | --- |
| `YuanShen.exe` | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| `YuanShen_Data/Managed/Metadata/global-metadata.dat` | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| `YuanShen_Data/Managed/Metadata/startup-metadata.dat` | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |

工具另拒绝以下已审摘要漂移，重新提取 metadata、参数签名、选定 AOT、枚举默认值及工厂 usage/generic 绑定：

| 依据摘要 | SHA-256 |
| --- | --- |
| `avatar-outer-observations.json` | `18ffa869ee1ebb7d73e028727aa1b424d0f1ce641530d73c7984696cf38093b8` |
| `avatar-core-observations.json` | `81a0411e8fe6b7cc3ca606473ae684744a8df73e97009666df1c2775e9bdb3d4` |
| `player-data-contract-observations.json` | `e239ee03cc645d61dfa87ad87db827eb268273f049c496f3730a213153c29689` |
| `equip-contract-observations.json` | `77e1df9d53d93380362f30ec0ad604b447a1123f4840963fa9f91a1da41b5011` |

```powershell
python tools/client72_avatar_contract.py --client $env:GAME_PATH `
  --output local/migration72/evidence/avatar-contract.json `
  --observations local/migration72/evidence/avatar-contract-observations.json
python -m unittest tools.test_client72_avatar_contract
```

正式摘要为 `protocol/cn-7.2/avatar-contract-observations.json`。完整指令地址计数、原生路径和原始调试输出保留在忽略目录。摘要记录每个片段的 entry、停止边界、原生入口、路径 SHA 及模型清单，并固定相关工具源文件 SHA。正式复现使用第二组输出文件名，对完整轨迹和摘要分别逐字节比较。

## 三层结论与精确接管规格

1. **CmdID→类型**：`20143 → 30509 ILEPMBEJKIA`。固定客户端 getter `152DE0CB0` 静态唯一，实际 dispatcher `1507CC240` 比较 `0x4EAF` 后调用 `1507CEE50`；后者的 metadata 唯一 typed 参数为 30509。
2. **类型→用途**：root 的角色列表逐项进入泛型工厂 `<18163>`，随后交给 `15184E100`；该模型从 Avatar 资源读取配置，保存完整 GUID、正常属性和装备列表。可稳定使用 `AvatarDataNotify` 和 `AvatarInfo` 用途别名。没有把完整 raw 子图仍未恢复的事实掩盖为完整消息身份/schema 验收。
3. **字段→wire**：只新增下表 5 个字段；原生 reader 的 tag/类型与实际业务消费者分别作为来源。

| 类型 | tag / 原字段 | wire / 别名 | 实际独立消费 |
| --- | --- | --- | --- |
| 30509 `ILEPMBEJKIA` | 6 `JGECGMPNGBD` / +0x48 | repeated message 48178，`avatar_list` | `1507CEE50` 读取实际 reader 生成的列表、逐项取得 GUID，调用泛型模型工厂和 typed `15184E100` |
| 48178 `ONGOHFDHHNI` | 1 `NLNJDCDLHHE` / +0xE0 | uint32，`avatar_id` | `15184E100 → 14B1046F0` 编码至模型 +0x250；独立 `15184ADF0 → 14B1049F0` 解码后作为 AvatarExcelConfigData 的 uint32 key |
| 48178 | 2 `FBLKPHOCBIK` / +0xD8 | uint64，`guid` | root 用它查询/建立角色模型；原生 GUID setter/getter、字典插入/查回；typed 模型保存于 +0x38，并带入角色属性事件 |
| 48178 | 5 `AODNDDBPMFB` / +0x18 | repeated uint64，`equip_guid_list`，接受 packed/unpacked | `15184E2D6 → 15184EC00` 消费该实际列表，GUID 查库存后按 Weapon class 或 Reliquary 资源槽路由 |
| 48178 | 3 `IKIFIBAEDGD` / +0x70 | map<uint32,78350>，`prop_map` | `15184E3AD → 152F7F670 → 152F7ED50` 应用正常属性，调用角色专用回调并从编码存储原生读回 |

`prop_map` 复用 [PlayerData 合同](CLIENT_72_PLAYER_DATA_CONTRACT.md) 已批准的 78350 `CODAEKFNEEE`：tag 1 `NOEEBKJAFAG` 为 uint32 `property_type`，tag 2 是 boxed int64 oneof 分支 `int64_value`。本卡不重新扩展 tag 3 float 或 tag 4 独立 int64 的业务语义。外层 map key 不是实际 setter 使用的属性 ID，输入 key 7、子 ID 4001 最终写入的属性是 4001。

只批准目标具名 enum 62096 `APKAPDELHPD` 中这三个角色属性：

| ID | 目标符号 | 本卡消费边界 |
| --- | --- | --- |
| 1001 | `PROP_EXP` | 原生正常属性转换、编码存储、角色回调的经验分支检查与回读；没有完整场景经验 UI 验收 |
| 1002 | `PROP_BREAK_LEVEL` | 相同存储链；重复赋值触发具名 `AvatarBreakLevelChanged=368` 事件边界 |
| 4001 | `PROP_LEVEL` | 相同存储链；重复赋值触发具名 `AvatarLevelChangedNoDependent=367` 事件边界 |

不批准旧 PlayerProperty 全表。既有 PlayerData 合同的 `10013 PROP_PLAYER_LEVEL`、`10035 PROP_CUR_CLIMATE_METER` 不因此成为本卡新增 Avatar 属性发送许可；FightProp 不在这张正常属性卡内。

## 身份、资源和 GUID 的实际链

root 通知入口先更新若干其他 raw 容器，再遍历 +0x48。消息列表的枚举结构由实际 reader 的 backing array/count 装配，原生 `1572292B0` 执行 MoveNext；没有由 Python 提供假列表元素。每个子对象 +0xD8 经 `1547E1DD0` 选择模型。usage 737549 / MethodSpec 21679 / cell `145990BC8` 独立解码为方法 294224、泛型参数 18163 `LOPOKBINJOP`。

泛型工厂实际执行模型查询、类型检查、缺失时分配、GUID 写入和字典插入。托管对象分配是显式模型；18163→72120→89508 的继承和使用的 virtual slots 从 metadata 重核。slot 24 `148AA58D0` 写 GUID，slot 25 `148AA58E0` 返回 GUID，重用对象执行 slot 17 `152F80500`。管理器 35156 的 `14E9B2A80 / 14E9B2C60` 使用真正的 UInt64 字典 `156F6F850 / 156F6F510`。

相同 GUID 的两条输入确实重用同一个模型，后一次 ID 和属性覆盖前一次；相同 ID、不同 GUID 则得到不同模型。`0x100000001` 与 `0x200000002` 的高位哈希碰撞仍可各自查回；`2^53+1`、`2^63`、max uint64 未经浮点转换。GUID 0 仍能到达模型初始化片段，但实际管理器插入 helper 不把零 GUID 放入索引；此边界不能解释为合法业务 GUID 的批准。

具名资源来源为 owner 65789 `MDJIHKHLPKN`，metadata 静态字段 +0x30870 是 `Dictionary<uint32,58272>`。路径 getter `14F33B360`、loader `14F33B0C0` 和 query `14F3382E0` 均属于该 owner；literal usage 782853 / index 66330 / cell `145B4B6F0` 解得 `Data/_ExcelBinOutput/AvatarExcelConfigData`。实际 getter/loader 执行至 `14D6E1570` I/O 边界，核对路径来源，没有执行文件读取。

独立角色消费者 `15184ADF0` 从模型 +0x250 原生解码 ID，再查上述字典，读取控制资源行中的编码哨兵并由 `14AEF9DB0` 解码返回。不同 ID 对应不同哨兵，未命中 ID 返回 0。资源字典 key 查找和行数据是明确夹具；未批准任何真实 Avatar 资源 ID 全表，也没有给哨兵所在资源字段 +0x178 另起业务名。

## 属性数值、覆盖与事件

模型正常属性入口首先调用 slot 14 `152F80100` 清掉既有正常属性存储，再按每个 PropValue 子 ID 应用。三个批准 ID 都是 **signed int64→double，不缩放**；不能套用 PlayerData 气候属性的 `/100`。控制样本 4001=42、1001=275、1002=3 分别原生回读 42.0、275.0、3.0。代码还覆盖负值、显式零和 int64 最小/最大值。

wire int64 `9007199254740993` 保持精确解码，但转成 double 后为 `9007199254740992.0`；这是实际原生有符号整数转浮点行为，不声称属性存储保留所有 int64 精度。GUID 的 64 位字典链与这一属性转换分开。boxed float 分支和仅含独立 tag 4 的样本，在此正常属性入口中都以 int64 0 应用；tag 2=43 与 tag 4=987 并存时应用 43。

重复外层 map key 先由真实 reader/setter整体替换子对象；不同外层 key 但相同子属性 ID 则依次写同一属性。4001 从 42 改为 99 时，实际角色回调 `1518407C0` 生成事件 367；1002 从 2 改为 3 时生成事件 368。事件 tuple 保存完整 GUID、新值、旧值；event bus 是交付边界，后续监听器未执行。新建属性的首值在该 setter 路径中传给回调的新旧值相等，因此这些首值样本不产生上述 changed 事件。

原生 `14B1046F0 / 14B1049F0` 编解码属性 key 和 avatar_id，`14A1B7EC0 / 14A1B8BF0` 编解码 double 属性值；最终用实际 `152F805B0` getter 回读。可逆字节表与 Random 初始化、pool、编码字典存储和从实际 map 节点生成的枚举接口是显式夹具，目标 setter、回调、转换、编码和读回未被替换。

## 装备来源与分段范围

本卡直接把 AvatarInfo 原生 reader 产生的 tag 5 容器交给 `15184E2D6 → 15184EC00`。列表 Count/GetItem 接口只读取该容器的真实 array/count；完整 GUID 经 `14E3DA130 → 156F6F510` 查回库存模型。两个控制库存模型沿原生 `14E43AF70 → 156F6F850` 插入，分别路由 Weapon 槽 6 与 Reliquary 资源槽 1。顺序、重复 GUID、缺失值、packed/unpacked 混合和 uint64 上界都覆盖。

这里的 Weapon/Reliquary 模型是已固定 Equip 合同所覆盖的输入模型，本卡没有再执行一遍装备构造器；原生角色槽赋值函数 `15184A5F0` 在本卡作为交付 sink，实际赋值与整数/affix 编码由独立 Equip 合同支撑。没有把 fixture 模型当成现场库存实例，也没有补造服务器 refinement 映射。

分段停止点为：root dispatcher/typed 通知完成角色列表后停于 `1507CF07A`；每个角色基础 ID/GUID 片段从 `15184E100` 到 `15184E1E4`；正常属性片段从 `15184E3AD` 到 `15184E3BF`；装备源片段从 `15184E2D6` 进入实际路由后停于尾部 `15183EAE0`。通知其他 map/list 的后半、角色其他业务字段和场景 fanout 均不拼接成“完整 Handler 返回”。

## 未知、错误、实际验收及暂停交接

root tag 3 目前只闭合 `1507CEE50` 将 +0x58 原始 uint64 写入 Player 模型 +0x128；静态检查 Player 模型字段和构造器未得到独立当前控制角色选择链，因此 **不批准 `cur_avatar_guid`**。解除条件是从该存储继续到实际当前角色选择/控制消费者，并以非零 GUID 原生执行交叉验证，不要求必须找到英文 literal。

root 除 tag 6 外全部 9 个字段保持未知；AvatarInfo 除 1/2/3/5 外全部 33 个字段保持未知，包括 tag 7 FightProp 候选和复杂列表 53824。完整 raw 子图继续属于其他卡，不能因为这 5 个字段可消费就批准其余空壳或旧完整 AvatarInfo schema。

正式正向范围为 **22 组通知、25 次角色模型应用、24 个不同分配对象、54 次正常属性应用、44 次装备槽交付**。相同 GUID 重用解释了应用数与分配数之差；空列表不分配角色。7 个拒绝中，递归上限、负 Avatar 嵌套长度和截断 Avatar 命中 3 个真实 reader 错误 helper；null typed 参数抵达原生 null-error helper 边界，未执行完整异常派发；非法 tag 和 uint64 溢出由明确输入模型拒绝；非空 53824 为明确研究范围拒绝。

红测记录为 `local/migration72/evidence/avatar-contract-red.log`。首轮非零重复属性验证暴露观察器将事件 tuple 的新旧值反标；根据原生 setter 的 XMM2 新值/XMM3 旧值和 42→99、2→3 样本修正观察器，保留首次失败日志 `local/migration72/analysis/avatar-contract-first.log`。root 列表最初缺少枚举结构、库存插入的第二个 metadata context 未设也均在本地原型中实测失败并补齐显式夹具，未改客户端或公共 runtime。

本批只新增专用工具、测试、专题与脱敏摘要，无生产代码、Proto、配置、资源或补丁修改，无客户端/服务/数据库/调试器操作，不需部署或重启。完成本卡后按用户要求暂停；下一步只作为交接建议：主线先审核三层摘要，再另卡验证生产 Avatar builder 的实际 wire；当前控制角色链、完整角色尾部与实机 D-02 仍待后续授权恢复工作。

## 正式复现记录

10 项新增 wire/证据守卫测试，以及 Avatar 外层、AvatarInfo core、PlayerData、Equip、库存、graph、AOT、签名和 literal 关联回归合计 **79 项通过**。两次正式运行都完成上述 22 组正向样本与 7 个拒绝，完整轨迹和摘要逐字节相同：

| 产物 | SHA-256 |
| --- | --- |
| 完整轨迹 `avatar-contract.json` | `2d6755a9180d121ef549069aeb3c81a74e99da323575278c7c7b7db22b2d3d91` |
| 脱敏摘要 `avatar-contract-observations.json` | `a54f62de9a803e2070c515ecc854488827ea6c62db4468c633190d6733f2b2f3` |

双跑记录为 `local/migration72/evidence/avatar-contract-determinism.json`，测试日志为 `local/migration72/evidence/avatar-contract-tests.log`。守卫会拒绝把当前角色 tag 3 或其他未知字段晋级、添加第四个属性 ID、以外层 map key 替换子属性 ID、缩短高位装备 GUID、用 fixture 替代原生 codec/查找、或把研究范围拒绝称为客户端原生错误。
