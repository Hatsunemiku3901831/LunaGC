# 持续推进D-02：活动检查点（2026-11-06，未暂停）

本文件按交接规范先逐字节备份旧HANDOFF2再覆盖；HANDOFF仅追加。本次是长任务中的活动检查点，不撤销用户“持续做到D-02完成并通过实机验收”的授权。没有最终交付或停止。Project管任务与验收，Harness管复现；最新状态优先于下方历史暂停文字。

## 目标、规则与现场

- 仅国服7.2；Windows portable，入口tools/local_windows.ps1及对应cmd，不用Docker/WSL/虚拟化或computer-use。用户授权脚本/API和必要的Astra xhigh、边界明确的Sol high/Terra Max子代理。
- 继续实际开发/原生研究/回归/构建，未知保持unknown，不能凭同形、编译、健康或改版本宣称D-02。只改Proto源，经Gradle/protoc生成Java。
- 不推送。独立验收及时本地commit，显式暂存自己的文件。共享单一Git索引：代理暂存/提交前先申请短窗；曾撞index.lock但未删除锁，后续串行提交。不要把代理或他人暂存混进根提交。
- 保留resources子模块既有dirty文件ScriptSceneData/flat.luas.scenes.full_globals.lua.json，未提交。AGENTS的先前外部提交f4354ef不属本工作，保留。原Mongo27017/存档/运行数据保留。
- 根.env为portable，.env.local未见；config固定SHA256为0e94caea6e33aa0d75fbd041726c188121f1eb9f0ff69787c0c5bb84d4975a45。原dirty资源SHA256 db3e6a2e87436fefb57d43e8f2b52528017d2a68d530e8cf56b85a53242e5bfe；本批构建后再次比较保持。
- 最近核对HTTP8088/UDP22101/测试Mongo27027均无监听，无服务JAR进程；原27017仍在。最新游戏目标已退出，管理员调试器/API仍在；不得沿历史PID/run。

## 固定输入与参考

GAME_PATH每次从本机配置解析，不写死历史路径。三输入SHA256：

- YuanShen.exe：8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5
- global-metadata.dat：45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a
- startup-metadata.dat：b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105

参考local/migration72/references/AstaPS-7.2.155固定a49f51aa6f2787ffbc25cee46801b70dee860d23，已核HEAD/tree/clean；来源/GPLv3说明保留。上游0/旧号/20000+、名字或同形不作为批准。当前patch3675ea789c71a6667c995f104271d1e031c34018，resources引用仍dac2a9d且dirty。未安装新DLL。

## 本批已完成与本地提交

- F-02c：75bafc7，同一Token安装key原生状态连续Login收帧；11原生/32关联，selector0、4096字节key、reader/factory/dispatch通过；6入队5拒绝。三次工具调用不是实际线程调度，完整Handler/SDK/socket未验。专题CLIENT_72_RECEIVE_KEY。
- E-01c6：b74cd16/8a91d41，ScriptLoader.eval嵌套require恢复调用者bindings并finally恢复。6例先5失败后通过，32 Lua/8真实加载回归、整场13616组双跑；fast437/481→407/449，inline0，无新错误。512组binding/426 typed模式差异、local defs、业务API、缺ID/revision仍未解决。旧JAR7ca18e10有固定审计证据；工具对旧固定JAR的守卫不应随意放宽。
- F-03d1a/b1：0cd95ef/730b90a，PlayerData root79263/BIMKHOFCKHB/7193和78350/CODAEKFNEEE，2类9raw；实际初始化用途推断、五字段nickname/prop_map/server_time毫秒/property_type/int64_value。原英文名恢复false；外5/7、子3/4及其余属性仍unknown。15原生/59关联、双跑，独立enum仅10013等级/10035气候（除100.0）。
- F-03d1c：9a9853e，三层仅PlayerData及逻辑PropValue用途别名接管，独立物理Client72PlayerPropValue避免修改其它旧消息共用PropValue。sender只昵称/当前毫秒/等级10013（outerKey=childId），未知和其它旧属性不发送，服务端属性/经济不变。11 Java先10有效失败后全过；三包实际Java→native consumer得到0/1/60及SDK边界；7生成器、47映射关联、六生成检查通过。45套778隔离Mongo Java全部过。
- 库存raw：a81f84b/eec23d2/9fd871e，15类47wire、1920扫描/32非空图/6拒绝；5map/5list、3oneof、原生copy/clear/append，双跑。未知业务未因此批准。
- F-03d2b1：fb6440d，19通知/22交付/21 Material构造+1缺资源、5拒绝，9新/59关联及双跑。六字段item_list/store_type/item_id/guid/material/count分别由清空/具名资源/真实模型/64位GUID原生字典证明。通知交付sink与每Item前缀、数量后缀、GUID索引分段；单例/分类fanout/UI未验。
- F-03d2c1：b47a490，仅独立cn72.inventorywire Proto，15类47raw。旧root两项独立断言失败保留；独立适配器36 Java（32个native对象oracle）/4生成守卫通过。3oneof、显式零/false、map覆盖/list追加/完整u64与错误已验；旧Store/Item/包号/sender/三层业务未改。
- Avatar d3a：2cfe1e4/114dfb7，根/Team/Rename3类14wire、384扫描/19图/5拒绝、8新/62关联双跑。静态65类368存储/37容器85 AOT不等于全wire。
- Avatar d3b：7470ef6，AvatarInfo外层+四叶子+已验PropValue共6类53wire；1536扫描/24图/11拒绝，9新/69关联双跑。共享reader按codec专用路由，float map独立布局，公共runtime未改。
- Avatar d3c：24caff2/e51621d，两小树6类18wire，768扫描/20图/10拒绝、8新/77关联双跑。相同类型两个字段用独立值区分，singular merge/list append原生已验。
- Avatar d3d：8a03338刚发布，16类50wire=新16352/71717两类6+复用14 Item类44（原15类47含Store根3字段）；2048扫描/44图/10拒绝、8新/85关联双跑。完整37外字段组合两份Item列表各9项，16reader实际进入。摘要新发布尚待主线review，53824仍非空范围拒绝，业务/完整Avatar不晋级。
- F-04b/c：6b72887、patch3675ea7/父b5d5881/接收2a405ba，原生验签输入合同和正式Rust供给，移除RAX=1。COPY仅R13、CHECK仅R8/root第五栈参，未知key保持；50 Rust/25 Python、6真实ilhook合成ABI、11原生密码学样本/18 key验签，双跑。固定SigningKey兼作本地root，目标私钥只在内存签名不导出。DLL dec47347b6e94e9db161705909910f302fdacc39a88c86502213e9ad2713cd4c，未安装；全DLL/SDK/legacy ccp/memguard/代理仍未验。
- F-04c2：976bd07，portable客户端入口在副作用前解析信任文件绝对路径并传给新子进程，22真实PowerShell通过。真实旧版本守卫拒绝，客户端文件hash保持。既有管理员调试器不会继承后来设置的env，不能混称供给完成。
- D-01b3a：038132e，原始客户端自然E06异常暂停处两份Global/TLS/UTF8/代码窗证据；GC TLS+19D0、CRT+1A50及页表非零。17 native Marshal转换返回与独立UTF16比对、双跑、12聚焦通过；CRT/GC仍明确模型，live Marshal未命中。专题CLIENT_72_MARSHAL_STORAGE包含三轮动态准确状态。

## 构建、测试与部署边界

当前LunaGC-7.1.0.jar仅保留历史文件名/版本，不维护7.1兼容：

- 当前SHA256 5db5d3b2db6c2ab5392b0526d9af63208a3ba6e6df507ecfe234a25a0d28fd73，内嵌b47a490，portable22秒。
- 对前个aa4b3fa5…790aa45（9a9853e）仅70个新库存raw class及3个build元信息变化；其余45948 class不变，旧发送/包号/共享Item保持。全部新/变化class与编译输出相同，InfoCommand/StartupArguments仅commit字符串变化。
- 前个9a9853e构建22秒，10变化/7新增与编译相符，其余45934和117个账号/Guard/Player/Lua/旧PropValue保持。
- 旧7ca18、aa4b3fa5等构建均在忽略builds校hash备份；不要覆盖固定旧审计输入而声称原证据无漂移。
- 最新研究600项全过（失败/错误/跳过0，约65秒）。此前独立Mongo45套778 Java全过（44秒）；本卡独立库存36 wire Java另跑通过，不相加为一次执行。完整Java历史217失败不能据选集清零。
- 最新统一review到a2f56ce为84→88摘要，旧84 hash、1268源码/Proto、151根/矩阵、1121描述符、1258类型/4810字段和开放边界不变，两次recover一致。三层28行/248字段（70 verified/9 candidate/169 unresolved），普通校验0错误，ready208条；较205增加因为把raw未知显式列出。
- d3d的avatar-items是第89个新输入，尚未review；先保留旧守卫实际拒绝，再人工核对仅新增摘要，双跑recover后接收。独立库存Proto尚无生产import，因此未擅自加入P0候选闭包。
- 完整初始化/P0、SDK/补丁实际加载、最小资源前置不齐，故所有本批JAR/DLL未部署/安装。portable status实测stopped/Healthy=false（退出1）；无服务PID、健康成功或运行JAR对象，不能冒称通过。原Mongo/数据保留，独立测试Mongo关闭前仅admin/config/local并正常退出。

## 动态卡点与避坑

1. 第一启动轮有5主模块软件观察点+3终止API，约15秒退出80000003；第二控制轮零主模块点，50秒仍运行，60秒请求pause后复核失败，最终日志80000003。不能把相关性当pause或反调试原因。
2. 第三轮在两次自然E06首次异常暂停处取得真实上下文；第二异常明确未交付并长暂停1297.985秒后，单个硬件factory观察恢复，数秒后退出80000004。PUB末异常/EXIT缺失、factory未命中。没有终止栈或独立归因，不重复同类盲load、不主动调用factory或强置结果。
3. 最新dynamic-active-session.json是debugging=false；系统无GAME_PATH目标及本轮后代，五个客户端hash保持。退出后空HW表不保证下次新target无DR残留，每次重新核归属/寄存器/代码。
4. x64dbg teb()按当前选择线程，tid()按最后debug event线程；switchthread后tid不变不能推断切换失败。固定工具源码链接见Marshal专题。
5. 只有GC+19D0的旧离线fixture会在141CAD123遇到空CRT；仅补malloc又在141CA72CF因size-query页表缺口失败。新tracked allocation/size/free是显式存储模型，不能称完整CRT/GC实现或真实heap rounding。
6. PlayerData测试首次setNickname触发数据库不是协议失败；改为专用getter夹具后保留有效11/10红测。扩大778测试时隔离cwd缺resources/src导致40个NoSuchFileException；加入只读输入junction后全过，未删旧断言。测试端口27027和运行目录隔离，不能指到27017。
7. 库存adapter首次用了旧Gson不存在的asList/isEmpty API，编译拒绝；改Iterable/size，不升级依赖。先前green-results在编译失败时其实还是旧XML，不用它声称绿；有效结果为inventory-wire-retry-results的36/0。
8. Asta audit曾将具名message map的valueType=PropValue与kind=message直接比较，真实合成红测后只修外形比较；名称差异5包括2 MD5和3用途拼写差异，不称新wire冲突。原21对照行/源码tree不变。
9. 共享摘要并行落盘曾导致统一review输入漂移拒绝，保留失败；新摘要发布前短协调窗口，不能更新hash后吞掉未知差异。Git index同样串行窗口，不删除index.lock。

## 当前并行分工与下一动作

- 根：维护Project/Harness/README/迁移、三层与受审输入；完成本活动交接备份后接收d3d并按计划开53824子项，继续生产侧满足依赖任务。PlayerData已接入，库存发送需等b2已验合同，不复制旧全部GameItem字段。
- receive_key（Astra xhigh）：F-03d2b2 Weapon/Equip。已独立确认tag1为Reliquary、tag2为Weapon；具名资源、角色装备slot6、level/exp/promote/affix链已有原生探索。正式样本/错误/双跑与10个拟批准字段仍在途，未经正式验收不进入映射/生产。
- initialization_audit（Sol high）：d3d已提交，下一53824复杂子树先交精确依赖/AOT/拆卡计划，等根新卡再实施。不要把此前65类368metadata槽直接算成全部wire；完整Avatar身份/语义仍unknown。
- trust_contract（Astra xhigh）：D-01b4，仅自建独立Windows合成目标复现Automate首次AV/C++异常交付、pause/HW/PUB/log/目标存活；不再load游戏、不改客户端/校验。先实时核空会话和DR归属，编译目标/二进制/PID/日志留local。目的先分清工具通道和游戏自身原因。
- 这些任务均活动中，无用户暂停。后续每批必要构建/条件允许部署及日志/健康/TCP/UDP/PID/JAR SHA核对，然后才进行新号显式选人、旧测试号、完整初始化/握手/心跳/退出重登D-02实机；当前一个都不能标完成。

## 复现入口

- tools/game_path.ps1解析本机目标；portable JDK17在local/runtime，Gradle cache local/gradle，Rust local/cargo及local/rustup，不下载猜测依赖。
- Project/Harness各专题有正式工具命令；协议生成检查现含generate_player_data72_sources.py及generate_inventory72_wire.py，其余五个生成入口保持。
- 本机汇总local/migration72/evidence/{player-data-*,inventory-wire-*,post-adapter-*}；原生证据按receive-key/player-contract/inventory-contract/avatar-*/marshal-storage/d01b3前缀。动态唯一当前状态先读dynamic-active-session.json，而非历史PID。
- 本次检查点旧H2备份与byte/hash证明在local/migration72/handoffs及evidence/post-adapter-handoff-backup.json；HANDOFF.md仅追加，不删历史。
