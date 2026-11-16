# D02-OLD-WEAPON-PROJECTION-JAVA：真实保存精炼出口

本项独立接入Weapon已知字段及affix组→零基level，不放行旧号完整Store/登录，不裁减Furniture/TPS或用Relic未知字段造完整快照。

## 固定合同与实现

[OLD-STORE精炼阶段](CLIENT_72_D02_OLD_STORE_SEMANTICS_20261116.md)固定13157B/SHA `50ddabda678a0b98a9523493c93a878feabdfff3551a14893bec6735dddda183`，冻结receipt SHA `40c055257d826242cace46d443749866c76188f2290da988bb2f8da4f7ee4a72`。已验target Weapon64947/tag4 map用途是EquipAffix组→组内level。当前服务器实际战斗/能力使用savedAffix*10+savedRefinement查行；全1663武器逐行报告SHA `c81c73989a941a1544a1bc4a6cf2d50f565dbdf4765bb11d0d2bcab3a5299d06`独立核对保存与选中资源。

`Client72WeaponProjection`直接取同GameItem：正savedAffix为key、getRefinement原0基值为value；level/exp/promote沿原已验字段。它不减1、不乘10当wire key、不clamp或从配置重建affixes。真正空affix列表保留空map；有组的level0仍保留entry。现有PlayerStore的Weapon分支调用这份projection，其余分支不扩。

1018项JSON省略level而typed Java/Gson为0是服务器加载事实；七条既批server-provisional效果仍保其来源分类，不冒target恢复，也不修改保存字段。root Item7/8/9/10各自的raw形状按来源表读取，不泛称全是单count；Furniture/TPS身份与Relic其它角色仍未知。

## 实际测试

- 旧号实际ResourceLoader/Player/Inventory→1663武器projection，随后正常保存与第二新Player冷读，再按原BSON逐项比对。1542个map entry、264个不同(group,level)、121空map及refinement分布0:1139/3:1/4:523全部成立；每条group/level与实际EquipAffix行及该Weapon.skillAffix匹配。原Player/Avatar/全部12254 Item比较保持，1527非活动文档不变，保存只沿原Guard/stamp。
- 独立5项手写wire验证level0/3/4、多组原key及空map区别，不使用待测builder生成golden。未知Weapon tag5/6未赋值。该组最终18秒全过。
- 库存raw36项和上述旧号生命周期1项在首次31秒批次已通过；同批5个unit因错误使用整GameItem的安全JSON解码得到null而失败。仅修夹具为真实ItemData→GameItem构造器，未加运行判空或放宽断言；失败XML/日志保留。当前通过的42项来自该37项及随后5项，不冒称一次42项成功执行。

原件同ignored集成根：`old-weapon-affix-first-results/`、`old-weapon-affix-first-failure.xml`、`old-weapon-affix-wire-final-results/`、`old-weapon-affix-results.private.json`及两轮日志。没有发送旧号Store通知，现网络准入仍在Player创建前拒绝；实际活动Store仍仅覆盖8388=6367 Material+1663 Weapon+358 Relic，2331 Furniture及8 TPS缺口不消失，350仅是已装备Relic数。正式portable构建后补，客户端六项仍0/6。
