# F-03d3s2a：SceneInfo 身份关联有限消费合同

2026-11-07后继见[Scene深入研究](CLIENT_72_SCENE_DEEP_RESEARCH.md)：retain/release、两份77771深复制、hash表真实查找、World/Beyond条件和替换时序已限定验收。与Avatar精确typed工厂交叉后，仅51522的Weapon来源分支获weapon-role关联，不把全部51522定名。80154非零必要性/自然资源/实体及完整handler仍待输入，旧摘要保持。

2026-11-07，D02-R1，有限身份关联合同已通过主线独立审核。固定国服 7.2 三输入；只新增离线工具、测试与脱敏摘要，不接入生产。

## 目标与证据缺口

本卡沿 type34208 的 typed handler 与管理器，检查 18295/tag10、tag13 两个 uint64，以及 tag4、tag7 两个 uint32 的实际来源和去向，并核对多角色列表与根 tag12 的本地实体关联。现有 [raw 图](CLIENT_72_SCENE_INFO_RAW.md) 证明字段到 wire；[场景身份](CLIENT_72_SCENE_ENTRY_IDENTITY.md) 证明根 tag12 经实际 store 到本地角色 getter，但尚未证明四个子字段的业务身份或完整实体构建。

候选解释是角色与装备各自的 GUID/实体 ID 配对；在具名消费者、实际调用目标或类型来源交叉闭合之前，四字段保留 raw 名。根 tag7、三子图的发送/等待/省略必要性仍未知，不根据旧 Proto 名或同形对象命名。

## 最小实验与出口

1. 每个全新进程重新解析 `GAME_PATH`、校验 EXE/global/startup 三 hash，提取 metadata、方法参数、usage 与具体 AOT。
2. 从真实 typed handler `1507D1620` → 列表处理 `1507E4900`、管理器 `14CD68530` 起，追四字段到独立消费者；关键 reader、关联 store/getter 必须执行原生指令，未闭合业务入口直接停止。
3. 用独立非零、两同形对象互换、多列表、64 位高位、零/省略和错误 wire 比较真实读取及关联行为。托管 collection、allocation 等模型单独标明；不以成功 stub 代替业务。
4. 双新进程报告一致、聚焦守卫通过后冻结有限合同。拿不到独立语义或必要性保留未知；不扩 72829/54267 的完整战斗图。

影响限定于后续生产者设计的证据输入。`fullNetworkHandler`、`fullSceneEntityEffects`、`targetProtocolReady` 和客户端实机均不晋级。本卡不构建、不部署、不操作客户端/数据库。REA MCP 当前没有暴露可调用入口；既有冷分析超时不重复，使用固定输入 Capstone/Unicorn 证据并标明来源。

## 三层来源与已执行关联

Cmd21761→type34208 的 getter、注册、typed handler/manager 参数、四个独立 literal 每次由 f2b 声明核查重新提取。字段→wire 复用已审 s1 摘要固定 SHA `f52f41a8…f7c75`，本卡原生 reader 再读独立 literal、交换及错误样本。类型→用途使用本轮新提取的参数、class usage、具体 factory usage、继承/虚槽和独立具名消费者，不依据旧 Proto 名。

真实 `1507D17D4 → 1507E4900` 消费 `List<18295>`。托管 enumerator 入口只从真实 reader 生成的 backing array/count 装配结构，实际 `1572292B0` MoveNext、数组边界与逐项索引执行；没有在 Python 依次送入猜测的子对象。U64 字典 FindEntry/TryInsert 以具体共享 AOT `1222/instance59<uint64,Object>` 对应 `156F6F510/156F6F850` 原生执行。`set_Item` 的真实 context usage 分别为 764896/spec104891/class19807 与 764891/spec104886/class5845，并非把共享 AOT 当作具体消息类型。

| type18295 字段 | 已证明用途及实际调用链 | 未证明部分 |
| --- | --- | --- |
| tag13 `OKMEHFHNFCG` / +48 / uint64 | `1507E49E1 → 14E9B2C60` 查角色模型；实际精确 class 检查 type18163。独立 `MoleMole.MonoTeamBtn.GetAvatarEntityId/14E7712F0` 使用同一完整 GUID 字典取同一模型 | 不等同当前控制 GUID；root AvatarData/tag3 未晋级 |
| tag4 `FDDDFIAINCE` / +40 / uint32 | `15184AEB0` 在 `15184B069` 写角色模型+218；具名 `GetAvatarEntityId` 实际读回+218；原生 slot18 getter 同义 | 未构建实际 Avatar entity |
| tag10 `NPKKCHABEJH` / +50 / uint64 | factory `1547E1DD0` 的实际 cell `145994388` 独立重提为 usage738059/spec23017/method294224/参数51522；原生查询/类型检查/注册，再在 `1507E4ACF` 写该模型+38；`1507E4AC4` 同时把角色 GUID 写其+88 | **Weapon 名称仍 unknown**。只批准“type51522附属模型 GUID 与 owner Avatar GUID 关联” |
| tag7 `IEDJNHILJBF` / +44 / uint32 | `14EAAF060` 在 `14EAAF086` 写附属模型+28；继承链51522→25683→72120，实际 slot18 getter `14EAAEF80` 读回；已有非零旧 ID 经真正 `1517AB3A0` 场景实体查询 | Weapon 名称、旧实体实际拆除及世界效果未验 |

偏移均十六进制。四个原生 store 独立记录 source、宽度、值、对象偏移与 receiver GUID。附属模型新分配是显式托管 fixture；原生工厂的 GUID 查询、类型检查、GUID getter/setter、注册和关联指令实际执行，不能据此声称构造器/live ABI 已验。

## 多项、零值、省略和必要性

25个成功/明确停止夹具包含：非零独立 literal、两个同形18295对象及列表逆序、两GUID或实体字段交换、相同低32位但不同高32位、`2^53+1`、bit63、uint64 max、uint32高位/max、重复角色、已存在附属模型、零/省略和真实非空能力停止边界。

- 缺失或错误 class 的首个 Avatar 模型使真实列表方法直接返回，后续合法项未处理；不是跳过当前项继续。它说明此消费链要求先注册可查的 Avatar 模型，不能代替全网络消息排序验收。
- 同一 Avatar GUID 重复出现会把实体ID更新为后值；两个附属模型各自保存该 owner GUID。U64字典实际区分相同低32位的键，没有浮点或32位截断。
- 显式零与省略实体字段都会在所选旧值夹具中清为0。旧 ID 91/92 确实传入原生场景查询，查询不到实体是受控前提，未替代旧实体拆除成功。
- 附属 GUID 为0仍得到一个受控分配对象并写入实体ID/owner，但真正注册方法跳过0，后续真实 GUID 查询找不到它。这不是生产允许0的证据。
- handler分段 `1507D1735` 对空列表走真实日志入口，非空列表进入真实列表消费者；`1507D17D9` 对根tag12为0/省略进入真实零ID分支，非零才到manager调用。两段有明确 live-in，不是完整handler返回，也不证明三nested可省略。
- type34045非空角色能力输入在真实 retain虚调用点 `151846096` 停止，未安装成功 stub；该样本只完成此前角色实体store，未继续附属模型。72829/54267图没有扩展。

根tag12与列表身份在同一原生解码对象上再次验证：真实 `14CD68530 → 1517B18F0` 的 `1517B1904` 四字节store写scene+284，再运行具名 `GetLocalAvatarConfigId`。场景中的实体/配置ID映射是根据已观察模型实体ID建立的**明确fixture**，并非客户端已生成世界；缺失Avatar的样本即使root ID非零，也查不到本地配置。零例单独调用manager绕过前述handler零ID门禁，不能批准生产零值。

本卡没有得到根tag7的独立业务名称，也未批准任何发送/等待/省略的完整必要集合。能力、Team及实体通知的最终必经顺序，实际网络完整handler、世界构建与客户端入场均保持未知。

## 模型、失败和复现

托管class/vtable/分配、枚举结构、固定容量数组、一个明确key的HashSet membership、U32注册字典交付捕获与场景字典索引为模型。关键消息reader、foreach、U64哈希/比较/插入、Avatar/附属实体store、具名getter与根本地store都不是成功模型。没有网络、数据库或客户端操作。

最早 probe1 在注册所需 context 的未映射读取失败；probe2补异常归属后明确定位 `14E9B2AD3` 读取空context，随后据真实源码布局补已使用的 `145A8AC10`。probe4 的新声明守卫误把 context 的方法认作 TryInsert，重新独立解码得到真实 `set_Item/10183`，保留失败并修正声明，不修改目标指令。早期 disasm 探针误按注册方法跨度读取泛型函数，主动停止自有离线脚本后改用目标PE unwind extent；没有启动或结束游戏进程。这些失败及中间成功报告都留在 `local/migration72/{analysis,logs,evidence}`。

最终错误样本分开标记 primitive 输入模型与实际 native error entry；包括非法tag、截断子消息、u64 overflow，不混称客户端所有错误均实际throw。

```powershell
$game = & tools/game_path.ps1
python tools/client72_scene_info_contract.py --client $game --output local/migration72/evidence/scene-contract-native-replay.json --observations local/migration72/evidence/scene-contract-summary-replay.json
python -m unittest tools.test_client72_scene_info_contract tools.test_client72_scene_info_raw tools.test_client72_scene_entry_identity -v
```

本卡交付四文件：[工具](../tools/client72_scene_info_contract.py)、[守卫](../tools/test_client72_scene_info_contract.py)、[脱敏摘要](../protocol/cn-7.2/scene-info-contract-observations.json)及本说明。两个全新进程均重新核三输入、metadata/usage/AOT，25个消费夹具和3个错误样本完成，完整与摘要逐字节一致：

- 完整 SHA-256 `c9fd50bafb45c7b60e0b3cbfc14665ff2cbb259f5e9e606e49790b29e37121db`。
- 摘要 SHA-256 `a0c88290e5d4bbd31b2ea0e83986ffc9e51235462b727429e441cba8cd48a2ad`，已发布新摘要，旧输入未改。
- 原始/摘要 `local/migration72/evidence/scene-contract-{native,summary}-final-{a,b}.json`；日志 `logs/scene-contract-final-{a,b}.log`。
- 新增10项 + s1的12项 + f2b的6项，实际 **28项通过，失败/错误/跳过0**；日志 `logs/scene-contract-final-tests.log`。这是本卡聚焦范围，不冒称全部研究回归。

主线已逐claim审核四文件及全部明确模型/停止边界，独立28项和第三新进程原生回放均通过，完整/摘要与上述双跑逐字节相同，六source pin保持。证明 `local/migration72/evidence/scene-contract-main-review.json`，主线输出 `evidence/scene-contract-main-{native,summary}.json`，日志 `logs/scene-contract-main-{tests,native}.log`。没有运行文件变更，故无需构建、重启或部署；没有新增客户端验收。按用户最新要求收口后暂停，Weapon独立身份/能力必要性及生产者使用边界留恢复后，不扩Object或接生产。
