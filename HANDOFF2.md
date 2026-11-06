# 当前子任务验收后暂停：详细交接（2026-11-06）

**当前已按用户最新指令暂停。** 用户先前的持续D-02授权已被“完成当前子任务之后（通过验收）先暂停，然后按照要求完成交接”收束。本次完成库存F-03d2c2及已在途的限定研究、实际回归/构建/现场核对后停止；不再启动e4、Avatar生产接入或D01b5。D-02实机尚未完成，不能标通过。恢复工作需用户重新授权。

本文件覆盖前已把旧HANDOFF2逐字节备份至忽略`local/migration72/handoffs/HANDOFF2.before-player-store-62a86fb-pause.md`并核验相等；证明为`evidence/player-store-pause-handoff-backup.json`。HANDOFF.md只追加，原字节前缀保持。本文与同步文档在独立本地文档提交中保存，不自动推送。

## 当前现场与保护范围

- 仅面向国服7.2，Windows portable；入口tools/local_windows.ps1及对应cmd。不用Docker/WSL/虚拟化，不使用computer-use。先读AGENTS、README、Project、Harness、迁移研究和部署手册；Project管验收，Harness管索引/复现。
- 当前生产代码提交62a86fb；JAR内嵌同一commit。当前HEAD后续只会增加本次暂停文档，不据文档提交重新构建。所有当前在途代理已收口停止，没有后台研究exec或目标需要继续运行。
- `.env`仍LUNAGC_RUNTIME=portable，无.env.local。config SHA256 `0e94caea6e33aa0d75fbd041726c188121f1eb9f0ff69787c0c5bb84d4975a45`；.env SHA256 `76c07da2a219e33501781ee77f4cb30709db9e0311030bb1dd3b5ebf2a0f7607`，均保持。
- resources仍引用dac2a9d43332ef51879522cf39da2831b0ca0543，原dirty `ScriptSceneData/flat.luas.scenes.full_globals.lua.json`保留，SHA256 `db3e6a2e87436fefb57d43e8f2b52528017d2a68d530e8cf56b85a53242e5bfe`，未暂存或提交。AGENTS先前外部提交f4354ef保留，不属本工作。
- patch子模块引用3675ea789c71a6667c995f104271d1e031c34018，父引用已2a405ba接收；本批没有新子模块提交或安装DLL。
- 收尾status为stopped/Healthy=false（退出1）；TCP8088/测试Mongo27027、UDP22101无监听，无服务JAR进程及GAME_PATH游戏/合成目标。原Mongo27017仍监听，原mongodata/local/data保留。
- 管理员x64dbg/API仍保留，但实时无target、相关进程0、HW表空。恢复时重新核会话/归属/DR/代码，不沿旧PID。D01b5从未load、run、pause或设点，已按最新指令撤回。

## 固定输入和参考

GAME_PATH每次由tools/game_path.ps1及本机配置解析；共享文档不写机器路径。

| 相对GAME_PATH输入 | SHA-256 |
| --- | --- |
| YuanShen.exe | 8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5 |
| YuanShen_Data/Managed/Metadata/global-metadata.dat | 45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a |
| YuanShen_Data/Managed/Metadata/startup-metadata.dat | b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105 |

SDK AccountPlatNative.dll和Astrolabe.dll另与先前五文件现场证据复核相符，完整值在忽略`evidence/player-store-postbuild-runtime.json`。客户端没有修改或安装新补丁。

固定参考`local/migration72/references/AstaPS-7.2.155`仍a49f51aa6f2787ffbc25cee46801b70dee860d23，HEAD/tree/clean已核；来源/GPLv3约束保留。上游0、旧号、20000+与业务拼写只是候选，不能批准本地wire、语义或运行前置。

## 本次完成及本地提交

### F-03d2c2：库存有限生产接入，62a86fb

详见[库存生产专题](docs/CLIENT_72_INVENTORY_PRODUCTION.md)。依赖b1 Material合同fb6440d、b2 Equip合同23b50b3和c1独立raw适配器b47a490均已验收。

- 正式命令22160→20892，根改为tag2 item_list、tag14 store_type=StorePack1，tag8原名PAKLDABINMP保留且不发。根引用独立cn72.inventorywire类型，完整15类47raw/三oneof/map/list/optional存在性保持。
- 最小发送Material的itemId/guid/count；Weapon的level/exp/promoteLevel及lock；Reliquary的level/star及lock。服务端isFavourite只投影到已验is_star。其它未批准物品分支不猜造，物品仍留服务端。
- affix_map已证明是资源group→内部level key，但旧GameItem.refinement原值或减1转换未验，本项不发送affix_map。Weapon5/6、Relic其余六字段和Material2仍unknown。不能把未展示字段当存档丢失或清理理由。
- 只改根生产Proto，经Gradle/protoc生成Java；共享旧Item/Material/Equip/Weapon/Reliquary/PropValue和GameItem存储/旧toProto保持。ItemUseRecoveryTest只适配四个getter，原ID/GUID/数量断言保留。
- 旧6项有效producer红测全部失败，改后15项通过；三套57定向（15+36 raw adapter+6 capacity）全过。11份真实Java输出再经实际目标原生reader/消费：3材料数量0/17/INT_MAX，8装备锁/星组合，高位GUID、原生索引/槽6和1/等级、Weapon受控资源8.75与进度.25均符合。
- 原生消费仍沿既有声明的托管存储、受控资源及分段交付模型；不是完整Handler或实机收包。Javawire不是只自编自解。

三层只有原Store根更新和五个嵌入用途别名新增，原27行/49来源保持；本片六类30字段16 verified/14 unresolved。总表33行：Cmd/身份29V4U；schema11V18candidate4U；278字段86V9candidate183U。结构0错误，ready224项拒绝保持。用途别名不等于恢复原英文名或批准旧共享Proto。

### F-03d3：在途raw和语义研究收口

- e1 `6e0e274`：3类18wire、384扫描/22图/9拒绝、8新增93关联及双跑；string/u32具体AOT，Unicode/NUL/u32上界/重复0实跑。
- e2 `786ec86`：7类36wire、896扫描/27图/9拒绝、8新增101关联及双跑；boxed string/u32 union、float bit和同形非空列表；51050非空仍拒绝。
- e3 `c85ec3e`：11类43wire、1408扫描/33图/7拒绝、8新增109关联及双跑。float helper仅实际前缀建立packed flag，停在反射前；13条AOT、float reader/Add/4字节store已验，64元素和bit边界通过。65元素触发未建模allocator是夹具边界，不能写成客户端容量上限。摘要SHA d8369121b9cabe4603c646e2d7acab265737e54256a5d5dd7e6e42be33e8fcaf。
- e4仅只读查询19829/21103/89623入口，没有创建工具、AOT新产物、测试或原生执行；e4至e8均未开始，按用户要求暂停，不是新技术阻塞。
- f1 `36a4669`：[Avatar合同](docs/CLIENT_72_AVATAR_CONTRACT.md)只批准root30509 tag6 avatar_list，以及48178 tag1 avatar_id/tag2 guid/tag3 prop_map/tag5 equip_guid_list；子PropValue使用已验78350 tag1/2。属性只1001 EXP、1002 BREAK_LEVEL、4001 LEVEL，真实int64→double不缩放。外map key不是实际属性ID。
- f1实际22通知/25模型应用/24分配/54属性/44装备槽交付/7拒绝，10新增79关联及双跑；摘要SHA a54f62de9a803e2070c515ecc854488827ea6c62db4468c633190d6733f2b2f3。具名Avatar资源、实际泛型factory/原生UInt64字典、codec及属性事件均分别执行。原英文名恢复false，完整raw/Handler/runtime仍false或not-tested。
- root tag3只证明原uint64写入Player+0x128，没有独立当前控制链，禁止命名cur_avatar_guid；FightProp、root其余9字段/AvatarInfo其余33字段及复杂53824业务仍未知。f1没有迁生产，不是完整初始化最小集合。

### D-01b4：独立调试通道，14df545

自建MSVC/MT目标五模式双跑，10个进程的应用首次AV/C++各1次、marker3/phase40、OS退出0、PUB EXIT/STOP和log0全部闭合，8守卫通过。摘要SHA ed04d78644fb390816bd45a6b7878ecd8ef1d326a5e26ffe16c12c593b2b7b02。真实CPU DR和独立OS句柄参与证明，不是只信API返回。

确认裸con为DBG_CONTINUE，任何参数（包括con0）是NOT_HANDLED；pause使用临时软件点。load helper把“不再调试”也视作ready不可靠；每load要以新CREATE_PROCESS/SYSTEMBREAKPOINT和OS/PID/真实状态建立门。插件每次init会清日志，必须新游标从0读；旧跨load游标导致四例缺退出log的真实失败保留后修正。

本卡未重现旧游戏末PUB事件缺口，不能据合成con0负例归因旧游戏退出：当时潜在con0分支未执行。两轮游戏80000003、末硬件观察80000004仍只有日志，没有最终异常事件/终止栈；根因unknown，自然非空Marshal/SDK仍未验。D01b5原本只准备计划，用户收束后未执行。最终只读证明`evidence/debug-channel-pause-final-readonly.json`，两个active-session已更新无target/用户暂停。

## 实际测试、来源审核与构建

- 本轮完整研究选集667项全部通过，失败/错误/跳过0，约52秒；精确模块/工具源hash和日志在`evidence/player-store-research-regression-summary.json`。不要把此前600、f1的79或本轮另跑19合并成新总数。
- 全新独立Mongo27027/运行目录下47套829 Java全部通过，失败/错误/跳过0（56秒），包含账号、经济、保存、物品恢复和Lua。原始XML在`player-store-regression-java-test-results`，summary为`evidence/player-store-java-regression-summary.json`。关闭前仅admin/config/local，正常shutdown，原27017/数据保持。完整Java历史217失败未据选集清零。
- 八个生成检查实际无漂移：born、login-slice、login-contract、scene、enter-scene、player-data、inventory-wire、inventory-sources；核实际三输入，不跳hash。普通结构0，ready224拒绝。7个新增库存生成守卫纳入667。
- 人工review89→95证据、1268→1269源；只改库存sender/root并新增raw import。新六摘要为e1/e2/e3、equip-contract、debug-channel、avatar-contract；既有其它输入保持。156根/矩阵、1122描述符文件、1278类型/4867字段，两次recover一致，开放边界/closureComplete/runtime不晋级。
- f1末尾空行修正使单项工具源SHA改变，首次C2统一review后的旧守卫再次实际拒绝；重构旧摘要hash并逐内容证明只有该sourceSHA单项差异后再双recover。两轮证明分别`player-store-proto-review-check.json`、`player-store-final-avatar-source-review.json`；不是自动重签所有hash。
- 固定Asta对照原23行/参考源码/许可/编译保持，新六行使278字段中185同外形、92缺tag、1外形差异、6名称差异。store_type原生uint32与上游enum表示差异如实保留，目标0/1/2消费已有独立证据；新增is_star/_is_relic_starred是用途拼写。没有改审计器把uint32当enum以隐藏差异。
- portable实际19秒构建成功，JAR SHA256 `eee183715cf27c5d2c25da0591c890f4c144116317ba0bf0afe8bb0030aeae0e`，内嵌62a86fb。10变化/1新增class全部与编译输出逐字节相同，其余46011类保持；70库存raw类及148账号/Guard/Player/GameItem/Lua/共享旧物品Proto保护类另核相同。InfoCommand/StartupArguments仅commit字符串变化。
- 旧JAR5db5d3b2…28fd73已归档`builds/LunaGC-before-player-store-5db5d3b2.jar`并核hash；旧aa4b3fa5/7ca18及此前固定审计JAR也保留，不改旧工具输入守卫。
- 完整P0初始化、SDK/补丁真实加载和资源前置不足，未部署/未安装DLL。无运行JAR或健康成功对象，不能将构建/status替代D-02。构建/现场证明`evidence/player-store-{build-artifact,postbuild-runtime}.json`。

## 未完成与实际阻塞

1. F-03d3完整Avatar raw：53824拆卡剩e4至e8，51050双Object与tag100、signed list和多个非空组合仍未验。仅metadata槽数不能当wire。
2. Avatar三层/生产builder未接管，当前控制角色链、完整团队/改名及其余属性/通知消费未知；f1五字段不自动批准完整P0发送序列。
3. 库存refinement→目标affix key、其它Reliquary字段及其它Item业务仍unknown；最小发送不是全部背包功能。
4. D-01/D-01b3真实启动退出、live Marshal/SDK/HTTP尚未解决。Marshal17例转换和真实TLS只是限定证据，CRT/GC仍模型。
5. F-04正式保留验签DLL虽已构建/ABI和输入合同验收，仍未现场加载；legacy ccp/memguard/proxy等五处未验。不要把整DLL或SDK标通过，也不要改版本、禁校验或静态命中充实机。
6. E-01资源revision、ScriptSceneData local globals、生产Lua API/typed/缺ID边界仍在；既有fast407组449事件、inline零错误是特定固定JAR/输入范围，不是完整业务资源通过。
7. D-02仍待前置：隔离库/运行数据，新号显式选人、旧测试号、完整初始化/场景握手/心跳/退出重登均未实机验收。本次已暂停，不继续攻关这些缺口。

## 失败记录与避坑

- 当前库存根改动最初使旧ItemUseRecovery getter编译失败；此前误复制的green目录其实是旧XML，不作成功证据。有效57项为`player-store-contract-retry-results`，829为独立全新regression结果。没有删原断言。
- 旧PlayerData45套首轮40项失败来自隔离cwd缺resources/src。当前测试目录预先以junction提供resources/src/protocol只读输入，所有输出与库独立；不可用原运行data或27017。调用PS7脚本，不用PS5加ErrorActionPreference Stop把正常native stderr当异常而漏掉关闭Mongo。
- f1首轮事件观察器反标新旧值，实际setter XMM2/XMM3和42→99、2→3样本纠正；保留原失败。新建属性首值新旧相等不能误报changed事件。
- e1/e2/e3曾暴露共享codec覆盖、float codec布局误读/误写packed长度、已存在53824 class被错误codec捕获；保留local失败，采用专用上下文，不改公共runtime默认成功。float helper仅前缀，不称完整反射cctor；65扩容拒绝不称客户端上限。原代理完整避坑记录`analysis/avatar-work-pause-handoff.json`。
- 调试器load/pause返回true不能证明目标活着且暂停；不要用旧跨load日志游标。只对匹配的应用首次异常交付，显式pause/HW点使用裸con；未知停点、缺事件或二次异常先停止恢复。当前无target，不对历史PID发命令。
- Git单索引串行短窗。曾撞index.lock但未删锁；代理只提交限定文件，root不混入他人暂存。所有子任务现已停止，只有resources原dirty应留在工作区。
- 所有raw二进制、进程路径、PID、日志、数据库、依赖和原始轨迹在忽略local；共享只提交脱敏摘要。未知业务字段保持unknown，只改Proto源生成Java。

## 恢复授权后的下一动作（当前不执行）

1. 按本机配置、Git/子模块/差异归属、三输入hash及进程/端口重新盘点，不重做已验底层。保留resources原dirty与数据库。
2. 最先可执行F-03d3e4：19829/21103/89623三新类40存储下界，复用已验68918；先独立提取35584/6 int32-list的AOT/helper cctor/codec/Add/数组布局，分别验证signed/packed/unpacked/同形非零字段/零/错误/双跑，不能套u32/float。
3. 依赖满足后逐卡e5/e6/e7/e8，最后51050双Object/tag100及完整53824/Avatar组合；每项完整证据/独立样本/守卫后本地提交，再由主线人工review。
4. 完整raw及最小业务语义齐后另开独立Avatar Java适配器/生产wire卡；cur_avatar_guid必须先证明实际当前控制消费者，不从Player+0x128存储或Asta名字推断。
5. 如继续真实启动卡点，先读DEBUG_CHANNEL、DYNAMIC_DEBUG、MARSHAL_STORAGE专题，采用已验新事件门、独立OS句柄和每load新游标；另卡限定一次短观察，不主动调用factory、不装未验补丁、不盲重复load。
6. SDK/补丁/资源/P0满足后，按Project的F-04/F-05与D-02步骤实际portable部署、日志/健康/TCP/UDP/PID/运行JAR hash核对及新旧号实机闭环。用户恢复前上述步骤一律不执行。

## 复现索引

- 新生产入口：tools/generate_inventory72_sources.py、Protocol72PlayerStoreContractTest；完整raw适配器及36 oracle见CLIENT_72_INVENTORY_WIRE_ADAPTER。
- 全部本机证据前缀`local/migration72/evidence/player-store-*`、`logs/player-store-*`；actual producer→native驱动`analysis/check_player_store_java_native.py`，测试/审计/构建驱动均同前缀，原失败不删。
- Avatar e1/e2/e3与f1各专题列正式工具、双跑摘要及固定输入；D-01b4见CLIENT_72_DEBUG_CHANNEL。复现脚本只对明确目标/API会话使用，不迁移旧PID。
- 本次同步了Project、Harness、README、迁移/融合、库存专题及账号系统/数据/运维。HANDOFF简版为追加，本详细交接已按要求备份后覆盖；暂停状态以本文及Project顶部为准。
