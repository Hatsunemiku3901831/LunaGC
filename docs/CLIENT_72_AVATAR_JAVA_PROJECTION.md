# F-03d3g2-prep Java 业务来源有限投影

本项只有测试夹具与离线检查工具，不接生产、数据库、保存或旧 `Avatar.toProto`。主线实际执行修改后的1项Java测试及两次原生回读，结果逐字节一致。只验下表getter来源与已有消费者片段；正式g2仍受f4d2完整构造和必经后缀阻塞，实机未做。

| 来源 | 独立适配器字段 | 夹具验证 |
|---|---|---|
| `Avatar.getAvatarId/getGuid` | 30509 tag6 → 48178 tag1 uint32/tag2 uint64 | 两角色 ID、超过 2^53 的不同 GUID；原生身份有限片段 |
| `Avatar.getExp/getPromoteLevel/getLevel` | 48178 tag3 map 子属性 1001/1002/4001，子 tag2 int64 | 非零和全零；key 与子 ID 一致，原生属性消费者按整数到 double 原值回读 |
| `Avatar.getEquips` 的 `GameItem.getGuid` | 48178 tag5 uint64 列表 | 同一只读物品实例 GUID，原生既有库存索引/槽交付；新增真实 Store sender 同实例夹具断言，待重测 |
| `TeamManager.getTeams/getCurrentTeamId`、`TeamInfo.getName/getAvatars` | 30509 tag5 map、tag12；26081 tag2/tag12 | Team ID、Unicode 名称与有序角色 ID 经同一角色集合查找 GUID；原生 Team 前缀和单独 current 写入片段 |

测试夹具用无参持久化构造及反射设置仅用于构造输入，所有投影读取真实 getter，并核对模型未变。三个属性即使值为零也显式设置 oneof；ID、GUID、Team ID 同样显式设置，列表空值无 wire 条目。这只是本夹具输出行为，未证明生产省略/合并策略。原生实际工作使用既有 managed 夹具、限定 reader、属性/装备及 Team 消费片段，不代表完整 handler 或实际客户端。

生命态不可由旧 `toProto` 常量取值；FightProp 省略会清旧状态，其来源未审核；当前 depot 与默认 depot 不等价。root tag3、fallback/override、复杂附属对象和完整模型构造均不投影。Store 同实例交叉验证已编写，待实际运行；没有将已有 g1 raw oracle 重计为业务来源。

主线串行运行（按本机配置提供固定客户端根目录）：

```powershell
.\gradlew.bat test --tests io.grasscutter.Protocol72AvatarProjectionTest
python tools/trace_client72_avatar_projection.py --client <GAME_PATH> --java-results build/test-results/test/TEST-io.grasscutter.Protocol72AvatarProjectionTest.xml --output local/migration72/offline-sol-terra/avatar-projection/observations.json
```

工具核对固定 profile、五个客户端文件 hash、既有证据与相关工具/Proto/测试 hash，报告自身 hash 和 JUnit 结果 hash；子进程 180 秒超时退出。不得更改旧工具或用测试通过声称生产就绪。修改后只做 Python 语法检查，尚无新的运行结果。实际失败需保留并归因，不能放宽断言或猜测 ABI。

## 主线实际验收（覆盖下方准备阶段的待测状态）

最终测试1项通过、0失败/错误/跳过；XML在`local/migration72/offline-sol-terra/avatar-projection/java-final/test-results/`。正逆序属性插入的确定性bytes相同，三个属性显式零仍保留oneof存在性；真实Store sender和Avatar装备列表读取同一GameItem的完整64位GUID。未改旧Avatar sender或共享Proto。

同一Java输出由主线在两个全新进程中分别交给既有Team UTF-8 reader及两个独立的f1/f3 runtime。身份ID/GUID、EXP/突破/等级的非零与零、装备GUID到既有库存交付边界、Unicode名称/成员顺序/current Team全部符合独立常量预期；原生属性回读为`275/3/42`及`0/0/0`，无缩放。完整trace逐字节相同，SHA-256 `0d608af88c56b20be36c73deb34fc36075811105fd095a5d0f1f64ee5c418c14`，位于同目录`native-final/{first,repeat}.json`。只证明model消费片段，不执行完整构造、装备赋值后缀、事件监听后代或正式网络发送。

首轮误用TeamRuntime属性枚举接口、第二轮误用f1 ASCII字符串夹具的失败均保留：`initial-native/native.log`及`native-final/first-ascii-fixture-failure.log`。前者按既有f1/f3独立运行上下文分离，后者对含Unicode的组合消息改用**已有**Team UTF-8 reader再接独立AvatarRuntime；未增加未知helper成功桩或新ABI模型。两次修正只解决可见的夹具组合边界，不推论完整客户端行为。

Store夹具item ID为0，而既有原生库存row的ID为10001；本卡只关联GUID，不宣称物品ID/资源或完整库存一致性。生命周期/属性计算/depot/SceneInfo等下表准备规格不因此取得生产发送许可。下方“待重测”为改动准备记录，最终实际范围以本节为准。

## 确定性与 Store 关联补充（待重测）

初版 Java 测试由主线在与网络测试同轮执行通过；该结果不覆盖本次修改。初版投影的属性插入依赖 `Map.of` 迭代，本次改用 protobuf 确定性序列化，并在同一测试中分别以正序、逆序插入相同属性，断言字节一致。真实 `PacketPlayerStoreNotify` 读取同一只读 `GameItem` 夹具，解码 Store item 的 GUID 与其 getter 及 Avatar 装备列表的值核对；未运行数据库或保存，也未宣称正式 Avatar sender 已接入。测试源 pin 从 `c44eb1ca02458f5a9f471530ce660513d2c8e290be124e5d89eb27cf296dbfed` 更新为 `f368ae7854fbb913ff48105b84e2cb4b83ff3e77d9eb39f3e487e136655e750f`，其他既有来源 pin 未变。

初版原生回放失败日志保留在 `local/migration72/offline-sol-terra/avatar-projection/initial-native/native.log`，执行到 `AvatarRuntime.prop` 时遇到未建模调用 `0x1405235f0`。静态对照发现 `TeamRuntime` 将继承的属性枚举接口首项改为 Team 的 KVP 接口；既有 f1 `verify` 单独构造 `AvatarRuntime`，f3 `run_case` 没有用 TeamRuntime 执行属性消费者。本次工具按各自既有路径为相同 Java 字节分别建立独立 runtime，未增加 ABI/成功桩；改后尚未执行，不能据此排除 wire 或属性语义问题。若仍失败，保留日志和输入，依据实际停止点界定解除条件，不推测新 helper。

## 未投影来源和解除条件

| 范围 | 已有来源及省略后果 | 解除条件 |
|---|---|---|
| f4a 生命态 tag4 | 目标有限消费证明 0/省略保留旧状态、1/2 写状态、3 归一为1；旧 `Avatar.toProto` 常量1不是实际生命态来源 | 明确当前真实服务端持久生命态与目标枚举映射、初始化和重复通知语义；不把 REVIVE 当保存状态 |
| f4b FightProp tag7 | 目标只限定验证7个枚举与 float 消费；空/省略会清旧 map。`Avatar.getFightProperties` 存在不等于全部目标ID及计算语义已匹配 | 逐项核实业务 getter、单位、计算时机与7个已审ID的关联和清理/完整性，不能以空 map 声称安全 |
| f4c depot tag11 | 目标字段驱动已验 depot 缓存查询，省略会覆盖旧值为0；`Avatar.getSkillDepotId` 是当前保存值，资源默认值是另一来源 | 核对真实当前 depot、资源加载与目标客户端缓存一致性；不猜默认或 energySkill |
| s2a SceneInfo 身份和附属对象 | 仅有限 GUID/实体身份关系，51522 附属对象名称、完整实体构造/handler 未闭；本投影不输出 SceneInfo | 由真实消费者和非空样本闭合附属对象来源、字段及构造/后缀，禁止把它命名为 Weapon 或用空壳代替 |

Store 夹具目前仅覆盖同一实例 GUID：夹具 Store item ID 为0，原生受控库存行 ID 为10001，因此没有验证物品 ID 或完整库存一致性。工具额外固定 `Avatar`、`TeamInfo`、`TeamManager`、`GameItem`、`PacketPlayerStoreNotify`、`PlayerStoreNotify.proto` 和 `Client72InventoryWire.proto` 的当前 SHA-256；这些文件未改。
