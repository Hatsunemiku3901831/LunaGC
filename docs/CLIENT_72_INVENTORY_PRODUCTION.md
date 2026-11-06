# F-03d2c2：库存有限生产接入

本项只接入已经由目标原生代码独立证明的[Material 六字段](CLIENT_72_INVENTORY_CONTRACT.md)与[Equip 十字段](CLIENT_72_EQUIP_CONTRACT.md)。`PlayerStoreNotify`使用命令20892，根tag2为Item列表、tag14为store_type；tag8保留原名`PAKLDABINMP`且不发送。完整初始化、所有物品类型及D-02实机仍未验收。

## 协议与业务范围

根Proto引用[独立库存适配器](CLIENT_72_INVENTORY_WIRE_ADAPTER.md)的`cn72.inventorywire.NDGHFPMJFIM`。该适配器15类47个raw字段、三个oneof、map/list及显式零/false存在性保持；本项不修改共享旧Item/Material/Equip/Weapon/Reliquary Proto或GameItem存储。Java只由Gradle/protoc生成。

| 分支 | 实际发送与服务端来源 | 保留的未知范围 |
| --- | --- | --- |
| 根 | item_list、StorePack=1 | tag8不解释为weight_limit |
| Item/Material | GameItem.itemId/guid/count；原生Material分支tag5 | Material tag2及其它Item分支保持raw |
| Item/Weapon | Item tag6→Equip tag2；level/exp/promoteLevel及isLocked | affix_map业务含义已验，但服务端refinement到目标level key转换未验，本项不发送；Weapon tag5/6未知 |
| Item/Reliquary | Item tag6→Equip tag1；level、isFavourite→is_star及isLocked | 其余六字段未知，不发送旧经验/主副词条等猜测值 |

目标锁/星的wire是bool，进入客户端模型后才分别编码为UnLock/Lock、UnStar/Star的1/2。保留这个边界，不把模型枚举直接发到bool字段。其它尚未批准的物品种类不编造wire分支，仍保留在服务端库存；本项不是完整背包功能验收。

三层仅修改原PlayerStoreNotify并增加Item/Material/Equip/Weapon/Reliquary五个用途别名，其余27行逐项保持；新增15份分层来源引用，旧49份来源内容不变。本片六类30字段中16个业务字段verified、14个unresolved。总表33行：Cmd/身份29 verified、4 unresolved；schema11 verified、18 candidate、4 unresolved；278字段86 verified、9 candidate、183 unresolved。原英文名恢复不作批准，raw物理类型与逻辑用途别名分别记录。

`tools/generate_inventory72_sources.py`核实际三输入哈希、完整原生raw/Material/Equip合同与三层边界，只生成根Proto及其唯一正式命令；未知命名、oneof错绑、锁枚举误用、affix值类型、来源漂移和命令冲突均拒绝。`tools/validate_protocol72.py`接入相同库存合同检查。八个现有/新增生成入口均实跑无漂移，结构0错误；ready仍拒绝224项，不晋级P0闭包。

## 实际开发和验证

旧发送端的有效红测6项全部失败，分别暴露旧命令22160、旧根6/9/11及Item结构。红测保留在忽略`player-store-contract-red-results`。改后15项producer/独立wire测试通过；包含空库存独立字节`7001`、材料数量0/17/INT_MAX、GUID超过2^53、八种装备锁/收藏组合、明确oneof/字段集合及服务端refinement/affixes和物品对象保持。

从真实Java测试输出提取11份producer字节，再交给固定目标的实际原生reader及既有消费工具：3份Material分别通过清空/列表交付边界、原生数量/移除选择和UInt64 GUID字典；8份Equip通过原生factory、GUID索引和角色装备槽路由/赋值。GUID `0x8000000000000001`完整保留，锁/星1/2正确，Weapon level3/exp250/promote2在受控具名资源下得到属性8.75、进度0.25，角色槽分别6/1、等级3。采用原专题明确的资源/托管存储及分段交付模型，不称完整Handler或实机消费。

定向三套57项全部通过（本卡15、独立raw适配器36、容量6）。首次改根后，旧ItemUseRecoveryTest的四个getter引用不能编译；只适配返回类型的getter，原物品ID/GUID/数量断言保持。初次误复制的旧XML不作成功证据，正式定向结果为`player-store-contract-retry-results`。随后全新独立Mongo/运行目录实际47套829项Java通过，失败/错误/跳过均0，包含账号、经济、保存、物品恢复及Lua回归。关闭前独立库仅admin/config/local并正常shutdown；原27017及运行数据保留。

本机原始证据为`local/migration72/evidence/player-store-*`、同前缀logs/results与`analysis/check_player_store_java_native.py`；固定输入和显式模型沿用两个原生合同，不把自编自解作为唯一证明。原始客户端、数据库、日志和机器路径留在忽略目录。

## 来源复核与剩余范围

固定AstaPS参考仍为a49f51aa6f2787ffbc25cee46801b70dee860d23，原23条对照结果和参考源码/编译/许可保持。新六条使对照达到278字段：185同tag同外形、92缺tag、1外形差异、6已验名称差异。唯一新增外形差异是本地原生raw读取的uint32 store_type与上游StoreType enum表示；目标具名枚举0/1/2已由独立消费验证，数值wire按本地证据保持，不放宽审计器把差异隐藏。新增名称差异is_star/上游`_is_relic_starred`属于用途拼写，不是wire冲突。其余五处名称差异见PlayerData专题。

统一review将生产root/发送类及新增raw import纳入来源与protoc闭包；逻辑嵌入别名的进入只表示已验字段依赖，不宣称每份旧同名Proto或全部候选发送必需。旧守卫先拒绝，人工核对后双跑recover，开放边界、runtimeStatus和closureComplete保持未验。最终回归、构建产物及暂停交接见Project、Harness和HANDOFF2。

运行接入已本地提交62a86fb，667项研究全过（失败/错误/跳过0，约52秒）。统一review最终95证据/1269源码、156根/矩阵、1122描述符文件/1278类型/4867字段，两次recover一致；Avatar合同工具末尾空行改动使单项源hash变化，旧守卫实际再次拒绝，严格核对只有该来源hash变化后再双recover，其余所有内容保持。完整P0/开放边界未晋级。

提交后Windows portable实际构建19秒成功，JAR SHA-256 `eee183715cf27c5d2c25da0591c890f4c144116317ba0bf0afe8bb0030aeae0e`，内嵌62a86fb。10个变化class及1个新增sender switch辅助class与编译输出逐字节相符；其余46011类不变，70个已有库存raw类及148个账号/Guard/Player/GameItem/Lua/共享旧物品Proto保护类另核保持。InfoCommand/StartupArguments仅commit字符串变化。旧JAR5db5d3b2…28fd73已校hash归档。

本卡完成对应构建及产物核对，现按用户最新要求暂停。完整P0初始化、SDK/补丁真实加载与资源前置仍不齐，未部署/未安装DLL；portable status实际stopped/Healthy=false（退出1），HTTP8088/UDP22101/测试Mongo27027无监听，无服务或客户端目标，原Mongo27017保持。配置/.env/原dirty资源及五个客户端输入hash复核保持；无运行JAR或健康通过对象。原始构建/现场证明见`evidence/player-store-{build-artifact,postbuild-runtime}.json`。恢复授权前不启动Avatar生产接入或新的实机观察。
