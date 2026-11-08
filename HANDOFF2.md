# D-02 持续执行检查点（2026-11-08，第二活动检查点）

**继续执行，D-02未完成；不是暂停、离线限制或阶段后停止。** 用户恢复指令覆盖所有历史节奏限制。只在D-02全验收、用户明确暂停或全部独立工作真实外部阻塞时停止。先读AGENTS/README/Project/Harness/本页/迁移研究/部署手册；当前工作包只取Project，不把历史快照重新排队。主线独占Git、Cargo/Gradle、DB、固定端口、client/debugger和安装。

## 实际现场、保护与构建

写入前HEAD为5768668（分支7.2-dev）；patch为da19389105a16a3f59afdd25b7f76b0de610322d，resources为dac2a9d43332ef51879522cf39da2831b0ca0543。当前agents有新文件在途，先核差异归属，不一把stage。保留原resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json差异，不提交。GAME_PATH依当前配置和tools/game_path.ps1，不复制历史机器路径。

最新私有现场active-checkpoint2-inventory.private.json：无项目游戏/Java/Mongo/调试器，TCP8088/22101/27017/27027与UDP22101无监听。五stock输入在透明实验恢复后经NATURAL-CONFIG主线第三重新核匹配。保护文件实际13项（.env/config/AGENTS/resources原差异及local/data实际9文件）在protected-after-clean-live1.private.json全不变；原mongodata/local/data不清理，未启动原27017。

Java生产最后改动仍为496e96f及a9fb9df；portable build已完成，运行JAR未部署。build/artifact-proof.json：JAR SHA8f093e19b839ff1263984c596584d1ae12e6d4fd841d82562aaf30f998267291，内嵌496e96f，46306类，新增2/变化9/删除0均与编译同，46295保持。旧产物先备份。R4/完整资源/SDK实际链未齐，不把版本7.1.0改成7.2来绕guard，不启动猜测协议。

## 已完成的本轮限定工作

所有“完成”只指列明子出口，六项D-02实机验收全未完成。

- R2-LOG：a9fb9df，SessionKeyAuthenticator账号/token/dbKey片段日志最小修复，认证/会话采用/保存保持。有效sdk-red2才是四分支4/4红；此前缺throws的编译失败和旧XML不是红。LOG39绿与下列193互含，不相加。
- R2-SESSION：496e96f，独立session verify DTO/handler，一次认证、单data.token/type1、64位字符串/嵌套，passport数组不改、失败无token。真实Java HTTP→原SDK parser/formatter，final两raw同6634B/SHA7a5f3bd1…69fff6。原HTTP正文/token及其hash仅内存，helper stdout必须PIPE。账号/密码/创建/保存等9套隔离193项0fail/error/skip，Mongo27027各次正常admin shutdown。
- E-01-bridge-energy：029fea2。已有真实六表不重解包；主线第三10,370,640B/SHAd990c2c4…66307e，17检查/22pins。Avatar.id/skillDepotId、Depot.id/skills/energySkill、Skill.id/costElemType七字段准确片段，能量slot4原消费者已验；不是完整Java row/实际loader，selectedRevision仍unknown。
- F-03d3f4d2-parent-continuation：0cbf31e。fresh静态父descriptor重定位、准确vector/已知单线程锁后继，主线第三736365B/SHA2a58df6a…d32eca9b，17通过；实际首缺class1234。
- 第二轮接口前自然输入：0890588。原始90秒/44监测，24固定PE窗、实际36读2232B零读错；snapshot119f8a0b…a7384，monitor5ca5b6e5…32fb2。class1234、NativeConfig typed头和Pool slot/code/mode/type46314已得。partial仅Object371实际kind28不符旧kind18合同，原结果不改。
- F-03d3f4d2-fresh-ancestor：0676a00。Object371原表/kind28/canonical producer已解释且该支不需新371 metadata；旧sampler源ecab38c9…f9c7c冻结。主线第三1,703,202B/SHAdc738c4f…933256b同双跑、22通过。原指令发布1239/1234两个400B类壳；Nullable opaque identity仅模型，未绑定对照另保留。实际首缺15a4d6bf1读cache1169的8B；后继1249有固定接口行来源。完整类初始化/default/Handler/g2仍未完成。
- F-03d3s2-pool-prefix：7fa9b33。fresh mode1准确走1470687c0→14050a890，止14050a8ae未提供loader TLS index4B；PE TLS目录AddressOfIndex精确对应模块+5eef080。双新进程4174B/SHA3a650294…ebcc811，6通过。没有补零TLS/allocator成功桩；零模式只作独立控制。分配线程GS/TLS、分配返回/生命周期仍未闭。
- R2-KEY-native-bridge：14c0366，patch043dc1d。相同未变Rust core→host/emulator翻译→准确原SDK两个caller/PEM/RSA/Base64；主线独立release helper、第三97333B/SHA8d53ace9…439dcdf同v2双跑，14检查。18baseline/bridge、19核心调用，所有权由原析构。v1清默认stack破坏output的夹具失败保留，仅独立cleanup stack修复；不算v1通过。不代验实际hook/natural。
- R2-ROUTE-loader：305d9de。包内server_pc准确SDKDelegate.LoadFile→AssetUtil.LoadJsonFile，16env/305实际URL；url:string→map+860及app_key→ConfigModel+130原片段。主线第三471340B/SHAbae776a1…bee1471，13通过，私有decoded/config三次逐字节同，不hash明文。combo builder R9=0直接读初始化map，普通Config文件缺失/7AD200无producer不再是必要阻塞。
- R2-ROUTE-SIGN：b287819。完整原生signer=四字段原始字节排序key=value&连接后HMAC-SHA256，小写64hex，data不重序列化，sign不参与。准确builder止3BEC2C发送call前。主线第三185859B/SHAf2f8f3ad…379d434，15原生/oracle及20独立审计通过。现Java默认认证未消费sign，不新增强制验签；实际HTTP及自然输入仍未验。
- R2-NATURAL-CONFIG-contract：5768668。准确Config owner/root/env/map writer/reader，owned immutable collector；主线第三8100B/SHA396b9ceb…28b76d2同双跑，14通过。根候选5读36B，owned深读76读4644B/32nodes；自然深读始终未批准，owner长驻不保证nodes不被释放。37E430为原mutex成功持锁点，R14=url object/RBX=公开key；unlock call37E520/return37E525。app_key另是3BEA13→3958E0/R9参数事件；不能把该锁顺带当env/key保护。

## 独立透明加载：已真实完成并恢复

4572816/patch da19389实现独立47转发、无旧模块/无hook。主线编译production/owned/fixture及3mock；两个fresh owned进程每轮94baseline/188比较，713B/SHA b01c808b…099f8f同，4拒绝真实0xe0720001，stage/win32分别2/2、3/0、4/1114、7/127；14相关Python及最初observer7合计21通过。4处原始EOF空行保留以维持已审源epoch，whitespace日志留private，不伪称格式check通过。

用户新ready后真实临时安装production DLL（156672B/SHAb61fde01eefc86ce96afd135bcf171b0650a0dafe59bb0ef43cea6379ed2ab05）和准确stock别名，正常CreateProcess90秒/45样本，始终存活。没有输入凭据、调试/注入、目标函数调用或本服服务。

首observer mask0x410缺SYNCHRONIZE导致wait5；定向无RPM复现creation/image匹配但wait5，改为既有0x101010。第二原库Rust canonical长路径与Python resolve字符串不同，samefile/size/SHA均同，改为samefile。两错误均在RPM之前，原失败保留，未新启动客户端；新增9项回归全过。f919d99提交两个工具修复与真实结果。

最终同一PID/creation/image的live1-binding-paths-fixed.private.json SHA2cf58ea677d932b392d030af2183dff0d4de043da9f72bb7e7b6f5152d28a6da：真实4读781B，47目标表376B全部=stock模块base+准确RVA，SDK Crypto253B及两caller各76B完全同原码；三个模块均核Windows文件身份/SizeOfImage/磁盘SHA，终检存活。只证明loaded/绑定，不证明自然caller已运行。

用户确认SDK登录页正常并正常退出。主线OS核无目标后按backup恢复Astrolabe.dll为原stock SHA87e0a005…b442，仅删本实验新建且匹配stock的Astrolabe_orig.dll。live1-restored.private.json和console、原DLL/proxy/stock备份均保留。当前没有临时proxy安装，不要再跑旧run-clean-live1.ps1覆盖既有epoch。未来按新产物/新ready/新目录方案准备。

## 在途任务与下一动作

主线先核最新HEAD/差异/子模块/进程，再继续；当前没有外部输入问题阻止所有独立工作，不应结束。

1. /root/r2_session（已实际指定gpt-6.1-sol/xhigh）正实现R1-SAMPLE-INTERFACES：仅新sample_client72_interface_context.py/test/INTERFACE_CONTEXT_SNAPSHOT专题。严格输入为主线从冻结第三报告提取的新`r1-fresh-ancestor/main-sampling-contract.lf.private.json`，实际6465B/bac00626…，来源dc738c4f…；原独立合同实际6614B/c84ae1e0…（149个CRLF，原manifest已记录），先前交付声明误hash写前LF，gate已正确拒绝后查清，未放宽/覆盖原证据。复用原始monitor和0x101010身份框架，严格五stock；旧sampler及其源hash不改。fresh cache根8B，两定义1169/1249各槽8/头208/metadata70，a8/b0非零才同次registry两级根+descriptor16B；加Pool TLS index4B。最大14读664B/single208，不读线程/数组/旧Array/Type/371/1234。先审固定PE窗/metadata与mock测试，再主线新user-ready一次原始90秒联合采集。刚结束透明加载的ready不能复用。
2. /root/r3_mapping（gpt-6.1-sol/max）正实现R2-KEY-LOAD，主线已写CLIENT_72_SDK_KEY_LOADER.md。新sdk72_key_proxy例及owned逻辑，复用未变核心/clean47 table+asm，仅3950c0原callback，绑定实际模块与253+76+76原窗、public-only材料、进程期持有。DllMain只记录模块；首次正常转发成功后一次90秒worker只等已加载SDK，不LoadLibrary SDK。禁止旧模块/route/trust/异常handler/私钥/credentials/新ABIwrapper。先交代码+准确构建命令，主线统一Cargo，owned/产物审后才新ready真实keyhook影响实验。no_mangle可能使sdk72_key_input_inner实际导出；首次EAT核实后只能按精确冻结来源显式登记，不为隐藏符号改核心或通配放行。未启用/未部署该新产物。
3. /root/r2_route_loader（gpt-6.1-sol/max）正做E-01-bridge-equipment，新三文件client72_d02_equipment_resource/test/D02_EQUIPMENT_RESOURCE专题。复用cache六表，不再解包或UGC名称扫。现实际Avatar169/Weapon288/WeaponCurve100准确EOF；default weapon caller14E9B15A0读取Avatar行+154，5/7→11101、136→14101、level1、case6、64GUID。11101 weaponProps必须保留FIGHT_PROP_NONE的第二项，Java onLoad只滤null；curve arith支持加乘减除，不能一律乘。仅片段投影，完整Avatar/Item/Promote/curve引用/自然revision/Java加载未完成。等双跑/拒绝及交付，主线第三审核；证据足够即交准确Java实现，不能停研究层。
4. 主线继续R2实际HTTP/native body接收与窄route/信任计划，当前DefaultAuthentication combo条件为uid/token，签名不是当前默认门。生产Java未增加此项，未来需合成原生outer bytes→真实HTTP/隔离DB/SDK响应，不持久化body/token/hash。SDK边界可先隔离验证，不要求等完整场景；不得据UI/root非空判断本服成功。
5. R1接口fresh样本交Sol max继续真实cold/default/Handler，不重做已验Array/Type。R3实际revision和必要Lua/事件自然加载后才保存→新Player→Manager正向恢复，不能手设loadedAll。Scene注册/stage/MPLevel/能力/组件及最终队伍/世界/实体仍未闭。R4汇合前不宣布sender完成，必要Proto只改源经Gradle/protoc生成。

## 必须避开的旧弯路

- 不复用旧PID/绝对指针/测试数量/地址；所有live是当次monitor/creation/profile/源码与预算。
- Object371是准确kind28，不把kind18假设错误升级成客户端错误；也不普遍放行18/28。
- partial/错误/unknown保持，原字节失败不覆盖。PE已映射页内零不是runtime输入；空对象/全零默认/跳通知/删required不能求绿。
- 跨epoch静态descriptor/metadata/类壳模型不冒充同进程自然执行。Nullable opaque身份/受控空generic cache/单线程OS/storage模型与原生指令分开。
- 解包真实资源/loader定位已完成，不重新排队；普通server_pc文件缺失不是combo初始化map必要阻塞。Java不能凭近名alias/枚举None兜底；weapon第二项None值是非null枚举。
- 旧ext.dll含ccp/memguard/swap等，不能以禁用列表整装。当前真实通过的是独立零hook proxy，未来key/trust/route各自实验，不关闭签名/完整性门。
- owned ABI/native片段、编译、健康、SDK页均不能代替旧号本服登录/新号选人/世界/心跳/两类重登持久化。
- Git本地提交，不push。子模块提交与父引用分开；他人/resources原差异不stage。共享Gradle/Cargo/DB/ports/client只能主线调度，子代理不争现场。
- 独立JSON合同必须hash实际文件bytes；Windows write_text会转CRLF，不能用写前字符串的LF hash声明磁盘文件。原CRLF合同与新LF投影各保留真实身份，不能为通过gate统一忽略换行。证据sampling-contract-newline-audit.private.json。

## 交接备份

本检查点前的HANDOFF2已先备份并逐字节校验：local/migration72/d02-resume-20261108/handoffs/HANDOFF2.before-checkpoint2-20261108T015503Z.md，SHA cacfdad9aa5e8416b52afac9aeb4ab3bec21fb3148fdf5b00fe279c29f2e11fc。HANDOFF.md仅追加，原字节前缀保持。初次写入与最终源分别在active-checkpoint2-written.private.json、active-checkpoint2-final-written.private.json；以后交接继续先备份再覆盖，不把本页当停止指令。
