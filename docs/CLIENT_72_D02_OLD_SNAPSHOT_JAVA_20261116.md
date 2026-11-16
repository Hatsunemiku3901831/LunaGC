# D02-OLD-SNAPSHOT：真实Java加载与恢复验证

本项继续[固定旧号资源准备](CLIENT_72_D02_OLD_SNAPSHOT_DATA_20261116.md)，只面向所选完整快照，不导入M0实例。当前仅纯资源诊断通过，完整Avatar/Inventory保存冷读仍在执行；D-02及客户端验收未通过。

## 首轮纯ResourceLoader诊断

第一次实际Java诊断直接使用只读`revision-5`（与revision-6同字节），没有对旧revision重复测试。manifest SHA-256固定为`1ada44d0ad16e69de52ea182a338c3410aa4c34e140dd78b7ec8cc0b4b7e661b`，入口核该SHA及277个输出SHA；不接受自动刷新输入。真实`ResourceLoader.loadFromResource`逐文件执行Gson与资源`onLoad`，不是反射注入GameData或手设loadedAll。能力及OpenConfig也使用现有真实路径解析入口和显式bundle路径，避免根运行缓存。

`D02OldSnapshotResourceLoadTest`实际10秒/1项通过，失败/错误/跳过0。加载结果：Avatar121、当前Depot121、Skill707、Talent762、Proud793、AvatarCurve100、AvatarPromote242、WeaponCurve2、WeaponPromote460、EquipAffix342、Relic Main28/Affix22/Level4/Set39、TPS8/Accessory12、Gadget247；五类Item文件合计9128个种类，全部选行安装成功，无折叠或拒绝。实际能力embryo121、Player能力组16、OpenConfig1964。

此轮`fullReady=false`、`playerHydrated=false`。没有读取Mongo、创建Player、调用AvatarStorage/Inventory、保存或部署；manifest本身仍ready=false。候选/临时策略没有变为目标资源恢复。原始XML与日志留`local/migration72/d02-m0-integration-20261116/old-snapshot-resource-load-first.*`及同目录归档结果。

## 下一执行边界

先以真实GameData逐个核固定保存Avatar/Item/level/phase/skill/talent/proud/affix/openConfig/主副词条/TPS与471装备引用，建立只适用于该快照Avatar/Inventory恢复的准入检查，并做缺项时零storage加载/零DB写负例。任何准入失败都在可能补默认武器或清装备归属之前停止。

全部必要引用真实可用后，才在新独立测试数据库插入固定原BSON副本并运行完整121角色/10727活动物品的真实manager、重算、保存和新Player冷读。保留12254全部Item及1527非活动原行；不删原18集合证据，不为数值通过改原HP/能量/技能等级/绑定。正式全量loadedAll、完整Manager、SDK/场景与客户端仍独立记录，纯资源通过不升级它们。
