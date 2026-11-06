# D-02持续开发活动检查点（2026-11-06）

**开发持续进行，没有暂停。** 最新用户指令明确恢复并覆盖此前“当前子任务验收后暂停”限制，持续到新旧号、选人、初始化、实际入场、连续心跳和退出重登全部实机通过。本文件是活动检查点，不代表交付完成。D-02尚未通过实机，不能用离线合同、构建或健康替代。

覆盖前已将原HANDOFF2逐字节备份到忽略 `local/migration72/handoffs/HANDOFF2.before-d02-active-4f0e5c6.md`，SHA-256 `e1907a1bc4f45a11835295103cbf560e70554dd4cf6d99d0da1aa11ef1d39c6f`，证明 `evidence/d02-active-handoff-backup.json`。HANDOFF.md只追加。

## 现场、固定输入与保护范围

- 仓库接手HEAD `6f5c99e72fdcccfe5e8d80d6fbd1cbcbd612f505`，7.2-dev；本检查点前最新本地提交 `4f0e5c6`。只有国服7.2、Windows portable，入口 tools/local_windows.ps1；不使用Docker/WSL/虚拟化或computer-use。后续按实时Git和配置重核，不沿旧PID。
- 接手只存在resources既有差异：子模块HEAD `dac2a9d43332ef51879522cf39da2831b0ca0543`，`ScriptSceneData/flat.luas.scenes.full_globals.lua.json` SHA `db3e6a2e87436fefb57d43e8f2b52528017d2a68d530e8cf56b85a53242e5bfe`，未动/未提交。patch HEAD `3675ea789c71a6667c995f104271d1e031c34018`，未安装新DLL。
- `.env` SHA `76c07da2a219e33501781ee77f4cb30709db9e0311030bb1dd3b5ebf2a0f7607`、config SHA `0e94caea6e33aa0d75fbd041726c188121f1eb9f0ff69787c0c5bb84d4975a45`保持，无.env.local；portable/HTTP8088/UDP22101，原Mongo27017保留。源码portable Mongo仍固定27017、补丁HTTP固定8088，隔离部署尚待R4，不仅改环境变量冒称隔离。
- 当前根JAR保持 `eee183715cf27c5d2c25da0591c890f4c144116317ba0bf0afe8bb0030aeae0e`，内嵌62a86fb；本轮目前只离线工具/文档和在途独立测试，无运行代码/Proto/配置/资源变化，不重建/部署猜测服务。已有mongodata和local/data保持。R3仅被授权独占Gradle定向测试，不jar/服务/原数据。
- GAME_PATH每次用tools/game_path.ps1解析。固定EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`；global metadata `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`；startup metadata `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`。SDK/Astrolabe也重核匹配profile。具体路径/进程/配置投影留 `evidence/d02-resume-inventory.json`。
- Asta参考仍固定 `a49f51aa6f2787ffbc25cee46801b70dee860d23`且clean，不将参考名称、0/旧号或相似结构批准为本地wire/语义。

## 已完成及本地提交

1. `dde3b6b`：恢复状态写入README/Project/Harness/迁移顶部，覆盖历史暂停；未重计旧667研究/829 Java。
2. `13c3f85` / D02-R0：入场源码台账 `docs/CLIENT_72_D02_ENTRY_DEPENDENCIES.md`。98候选/132源码位置无缺项，13/49源hash无漂移；实际recover源码81方法/98边/61直接构造类型，依赖294方法/411边/2854未关联。列新旧号、四握手、队伍/实体、间接管理器、HTTP/Union、事件替包/异步/Lua及阻塞后继。只完成台账，不称完整P0/runtime。结构0、ready224保持。
3. `bd2d9f8` / E-01d1a、R3子合同A：两角色10000005/10000007→默认武器11101和四depot501/504/701/704的skills/subSkills/energySkill/attackModeSkill正引用存在；固定四表Git tree/blob。3新+7原共10测试实际通过，主线独立报告与代理双跑相同 SHA `a5974ef3…f5698f`。标准模块import红测已修并复验，完整资源/revision/加载未知。
4. `3be6e19` / F-03d3e4：三类40wire/384扫描/19图/10拒绝，独立signed具体AOT/reader/Add/4字节store/helper前缀，同形子图及完整uint64。8新增/122关联；主线另16定向和第三新原生进程，完整/摘要与双跑同 `c1c6cd41…b45d27c` / `6ec19e0b…b056ee0`。
5. `de1c499` / F-03d3e5：五类24wire/640扫描/21图/8拒绝，非空多list、同形子图和signed int64 min/max。8新增/130关联，主线8定向和第三原生进程相同；完整/摘要 `5a7d86a7…1cb5e36` / `8481fb5e…893e193`。
6. `ba9daf5` / F-03d3f2a：`docs/CLIENT_72_AVATAR_CONTROL.md`、client72_avatar_control工具。root tag3经Player+128/原生GUID索引进入具名SetHeroMoveRatio的Hero实体源；GetLocalAvatarConfigId另读场景管理器+284的entity ID。7原生图/5新增+f1原10共15测试、双新进程相同，完整 `cff07f69…ece1604` / 摘要 `8e2433b3…7480ec0`。明确的不同实体存储夹具不证明真实切人/入场，不批准cur_avatar_guid，不改mappings/ready。继续找+284场景消息写入。
7. `4f0e5c6` / F-03d3e6：两类36wire/256扫描/13图/9拒绝，三同形48088独立值和合并、signed列表、三uint64高位换位。8新增/138关联，主线8定向及第三原生执行相同，完整/摘要 `6c503f33…a33e02c` / `be201804…21a9bf5`。

各原生卡独立限定完成，不把重叠回归相加为一次总数；未知业务、完整Avatar/复杂图、Handler/runtime和D-02不晋级。所有原始轨迹/失败/二进制在忽略local，协议只改Proto源、生成Java，现阶段未改生产Proto。

## 正在进行的代理与文件归属

- `/root/d02_r1_avatar_raw`，Astra xhigh，独占新增Avatar raw工具/测试/摘要/专题。e7两类29wire/256扫描/14图/9拒绝，8新/146关联及双新进程已报告，**主线尚待第三原生重跑/审核/提交**。e8已研究51050双Object（tag1/2 boxed-u32、tag100→18849）及原生u32 range-copy，继续完整53824/Avatar组合。不得改全局review/mappings/Project/Harness、既有工具或生产/资源。没有Git暂存提交权限。
- `/root/d02_r2_startup`，Astra xhigh，独占调试器/游戏/自有fixture。新增observe_client72_startup.py、test_client72_startup_observer.py尚未主线审核提交；先整理两轮真实及合成对照，继续按证据调整。主线和其他代理不操作同target。
- `/root/d02_r0_ledger`，Terra high，R0已完成后复用为R3资源线。拥有audit_d02_resources72.py及测试扩展、Client72D02ResourceAudit.java、最小资源专题和新D02Group505LoadTest。当前**独占Gradle定向test窗口**，不构建jar、不启网络或数据库；完成归还后主线审核。不得动原resources/config/data或Git索引。
- 主线独占Git暂存/本地提交、全局来源审核、三层状态、集成、部署和最终验收。目前新增研究还未加入原95输入的login-proto统一review；合适的阶段汇合要逐差异人工审核后双recover，不能自动签所有hash。raw子卡不直接晋级正式映射。

## R2真实启动新证据与失败

1. 第一次新load的CREATE_PROCESS/SYSTEMBREAKPOINT与独立OS句柄均匹配，但x64dbg数据库恢复旧 `cn72_d01b3_factory_hw`，slot0/RVA52FBF0及CPU DR匹配旧研究。未run前守卫拒绝。仅该已证旧点被删除，恢复同target；排查导致系统点暂停552.266秒，不是干净短暂停对照。
2. 该目标实际run约13秒后EXIT=80000004；37匹配首次AV各con1，系统/EP裸con。OS/PUB EXIT+STOP/每load新游标日志闭合，但无五入口或三终止API命中、无最后异常/终止栈，原因unknown。逐快照后来发现旧DR在同TID的EP就再现，之后一直保留；不能声称全target干净，也不能优先归因其他线程。
3. 新合成对照用已验channel_fixture：现设现删可退出0；进一步精确复现“数据库恢复旧点→系统点删除且DR0→EP同TID恢复旧DR、表仍空”。在EP清这个已证marker残留后，后续停点DR0，phase40/marker3、OS/PUB/log退出0。dump/eval两API同源且实测一致，不是只get_regs缓存。EFLAGS reserved bit1在自有目标也可变化，不当游戏业务flags问题。
4. 第二次原始游戏依据新对照执行：load到首run0.203秒，所有39停点两API一致、六DR全0，无旧HW恢复或清理；系统/EP裸con，37首次AV各con1。仍约14秒后EXIT80000003，OS/PUB/log三方闭合，无入口/终止API命中或最后异常，五客户端文件hash不变。当前游戏/fixture无目标；API弱is_running=true不能代表活着。
5. 不能宣称裸con/DR修复已恢复客户端。下一先比较两轮DLL/线程/最后现场及旧D01b3控制记录：旧零主模块点/仅三终止API曾到两CPP异常并运行60秒，本轮均有五主模块软件点。可规划最小单变量零主模块观察/系统终止来源对照，先报实验再执行；不盲重复load、不关闭校验、不主动调用factory，不将调试工具差异归因为客户端问题。

原始 `evidence/d02-r2-*`、logs同前缀；实时状态必须由R2代理重新核会话/OS/进程。工具仍在变更，不能从本文件对历史PID发run。

## R3资源后继、实际回归与缺口

- 六张成长/突破/天赋表已从固定候选commit取得，逐blob/大小验证；扩展工具验证level1角色/武器曲线、突破级0、从depot/skill推导天赋和proud组、武器gadget，共21项存在性，双跑 SHA `70ddb0ca…02dde6e`。当前测试总4（原3+新增1）+原资源7=11通过，不称4新增。原A合同无extended参数仍字节/hash不变。**主线待审提交**。
- 旧审计2566f466 JAR、真实serializer及候选Gadget构造inline源码等价网格模型，固定出生点得到25去重候选，六层hash同既有审计；生产R-tree查询与矩形结果相同。两报告JSON相同、原始集合排序不同，未称字节一致。不是当前生产tick或安全落点证明。
- require-bindings归档7ca18e10 JAR按既有hash门禁，25组fast/inline各双独立JVM真实ScriptLoader/SceneGroup加载，均0错误/缺脚本/trigger/业务哨，两模式逐组bindings/typed hash相同，301gadget/62monster/33region/31suite/53trigger。只验证该集合/顺序，不替代全场fast407/449或完整资源。
- 初始suite绑定的133003505 GROUP_LOAD已从“首个业务哨”推进到新增JUnit实际事件调度→Lua→ScriptLib→实体状态/删除，第三轮2测试通过。首轮缺ConfigGlobalCombat和重复ScriptLoader.init、第二轮容器dropSystem=null，均为隔离夹具缺项，失败留存；补真实固定combat/drop资源与DropSystem后通过。最终补组destroyed标记和重复事件后2项全过，无database/HTTP/dispatch初始化。**主线审核要求还在进行**：加跨group同configId隔离断言，精确恢复test所改全局GameData/config/runMode，避免确定性污染同JVM后继。仅新test修改，不改生产。
- 候选504/704的LHFOOPMJHBN由实际ResourceLoader/JsonUtils/onLoad加载后special=null；隔离重命名对照得到751。两新JVM相同，但只有旧资源结构/值相似线索，没有独立目标语义，不可盲加别名。
- 133003504的LockMonsterHp/UnlockMonsterHp在suite4战斗/死亡动作；133003785区域在suite2；实际任务/缓存/套件状态未核，不能自动后置。revision候选与客户端运行热更新关系未知，真实group激活/持久存档/落地仍未验。R3/resourceReady不晋级。

## 可直接执行的下一步

1. 延续现有三个代理，不重新建冲突任务。主线先审e7并定向/第三原生重跑后提交，继续审e8完整闭包；R3完成新JUnit自身隔离修正并归还Gradle窗口后再审/运行真实触发测试、六表与25组证据，逐项提交。
2. 主线F-03d3f2b离线继续场景控制：`local/migration72/analysis/d02-r1f2-field284.json`登记49228/56238的+284候选，唯一直接setter候选 `1517B18F0`，下一查原生body及callrefs，沿实际场景消息到本地角色消费者。原始index/flow/callrefs仅候选，不按同偏移或线性传播批准。
3. R2先收口已完成通道复现与两游戏失败的脱敏摘要，明确仍unknown；依据新最小对照规划继续，任何额外观察都实时核归属和源hash。原始二进制/日志只local。
4. raw/语义新摘要稳定后人工复核全局login-proto输入清单，保持旧95源和三层/开放边界，双recover及结构/ready记录。原ready224尚未被新证据升级，不修改规则、required或用unknown当可省略。
5. 完成独立Avatar raw适配器/生产sender及必需队伍/世界/实体、双HTTP/Union合同和最小资源后，才R4汇合→R5隔离便携部署→R6旧号/新号显式选人、完整握手、角色实际落地、多次心跳、退出重登保存。原账号/经济/保存回归仍保留；缺必要合同继续补，不启动猜测协议。

活动检查点之后按最新Project/Harness和Git继续；尚无D-02完成或暂停授权，不在单子项完成后停工，不自动推送。
