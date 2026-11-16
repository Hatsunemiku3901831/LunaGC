# D02-M0-SCENE-RAW-JAVA

本项把来源人已完成并固定的SceneAvatar/SceneTeam raw合同转成独立Proto，未编写完整sender、升级Scene握手或运行客户端。三层来源及语义剩余按[SceneAvatar](CLIENT_72_D02_M0_SCENEAVATAR_WIRE_20261116.md)和[SceneTeam](CLIENT_72_D02_M0_SCENETEAM_MIN_WIRE_20261116.md)读取，不能用本项Java编译/测试代替目标实机或fresh原生对象构造。

## 固定来源与schema范围

- SceneAvatar专题SHA `aefcfa3e4e769b5ccdcc163647f62444a08b19977914b280ed8e63ad80eac196`：41868的13个无变换scalar、7个u32列表、2个u32/u32 map及6个已有批准内容的引用槽，共28字段进入`Client72M0SceneAvatarRaw.proto`。26952、17469、27216三个未知内层不声明、不造opaque空对象。scalar名字不自动批准AvatarID/depot/GUID业务值。
- SceneTeam专题SHA `01cca3ca3dc17c0774e38da334810131f8434bcf941a842602025752c03f40e4`：76429全部17个外字段、83228四字段进入`Client72M0SceneTeamRaw.proto`；86588仅声明19个真实owned样本已证的bool/tag13。其list/tag5保持.cctor-only来源等级，没有因本项Java适配提升为reader闭合。
- 73976五字段、83320三个字段及89710列表从既验`scene-info-raw-observations.json`复用，文件SHA `f52f41a817241646d63d692b120d5c134c1b30dfe8b2da5d440de5d9557f7c75`，进入`Client72M0SharedSceneRaw.proto`。83320的tag2/3保持fixed32，不赋予name/override角色。34045/27196/27246/48178/53824直接引用已批准65型`Client72AvatarWire.proto`，不复制旧语义schema。
- 16796仍只引用此前已证scalar子集，不新增未闭的root list或完整Avatar oneof适配。83228外层完备不代表内层实体、业务flag/能力状态或完整场景完备。

optional标量只用于Java显式零/false合并存在性；packed输出是同已批准packed/unpacked primitive输入合同的适配，不声称恢复原Proto语法。所有Java由Gradle/protoc生成，未手改生成物。

## 实际回归

`M0SceneRawGraphWireTest`新增10项，首次执行即通过；连同Entity raw12项及Scene身份2项共24项，25秒，失败/错误/跳过0。独立手写wire按primitive key/varint/length规则构造，不用待测Java builder产生预期值：

- 13个scalar的非零/u32 max/u64 max，保留2^53以上GUID；不经double。
- u32 packed与unpacked同字段追加、两独立map、高位u64叶子及既验复杂子图非空嵌套。
- 两个Team列表元素、不同Avatar/Weapon GUID及实体ID、Scene ID、能力retain/copy两份独立状态、两个fixed32原字节、高tag549/645。
- 86588的0/1/2/80000000/FFFFFFFF五个既有native样本及Java bool归一化；不存在/显式false分开。
- 截断、错误长度及未知内层未声明的schema边界。错误断言只针对Java解析器，不冒称本轮新执行了目标native错误路径。

命令：

```powershell
.\gradlew.bat test --tests emu.grasscutter.server.packet.send.M0SceneRawGraphWireTest --tests emu.grasscutter.server.packet.send.M0EntityRawWireTest --tests emu.grasscutter.server.packet.send.M0SceneEntryWireTest -PskipHandbook --console=plain
```

原XML和汇总归档于`local/migration72/d02-m0-integration-20261116/scene-raw-java-results/`；首轮日志为同根`m0-scene-raw-graph-wire-tests.log`。本项没有新native CPU、helper/model、A返回、客户端、DB或补丁操作。正式构建随后按实际产物登记；完整SceneTeam/Entity/SceneInfo和自然认证仍有准确未闭边，不发送完整场景包。

## 旧号快照出口短核

有限旧号mode已保留全部存档，但完整网络入口仍拒绝。代码及[库存生产合同](CLIENT_72_INVENTORY_PRODUCTION.md)、[Equip合同](CLIENT_72_EQUIP_CONTRACT.md)当前只批准Material与Weapon/Relic有限字段；raw15型47字段不是所有分支业务合同。Item7/8/9/10不能按形状命名Furniture/TPS。现生产Store会略2331 Furniture与8 TPS；Weapon affix_map已证“EquipAffix组→组内level key”，保存refinement的映射尚未有目标桥，不能默认减1或把Java组合lookup当客户端身份；Relic2/3/4/5/7/8仍未闭业务角色。

下一输入分别是准确Item分支typed消费者、Relic字段实际使用或保存精炼与组内key的桥；可用已有raw继续单小合同，不需要全Manager或全97包。未找到可直接照搬的完整Furniture/TPS/Relic生产实现；这个有界检查不是宣称所有外部来源穷尽，也不阻已有M0女主/武器身份子集。
