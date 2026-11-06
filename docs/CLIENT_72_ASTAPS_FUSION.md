# AstaPS成果融合与D-02

最新用户范围：本轮先完成有独立证据支持的融合、相应验收和文档维护，然后暂停；D-02仍是后续目标，本轮不推进新实机阶段。后续缺独立证据的上游候选不能为了收尾而批准。

2026-11-06恢复，接手主仓库c9aafa6。参考仓库为MeChen618/AstaPS，固定提交`a49f51aa6f2787ffbc25cee46801b70dee860d23`，本机路径`local/migration72/references/AstaPS-7.2.155`（忽略目录）。来源/HEAD/干净状态已核对；本项目patch/resources锁定引用及既有他人差异不变。当前仍没有D-02客户端验收。

## 来源与采用规则

已读参考`docs/protocol-generation-7.2.md`、`docs/protocol-7.2-gaps.md`。上游由7.1 descriptor经kitkat 7.1和NT 7.2按名称/父字段桥接，保留服务端类型；其文档“server code uses”口径列92零号/105无counterpart。实际全量审计为2105 Proto/8066字段、967个20000+字段、567零号/12负号/13组正号重复。protobuf本身允许20000+，独立编码和读取已证明其仍可发送；跨消息共现恢复和单字段推断均需独立目标证据，不能直接批准运行。

本地固定目标以`protocol/cn-7.2/profile.json`和`tools/extract_client72.py`的三输入SHA-256为准。CmdID→类型、类型→语义、字段→wire分别对照本地原生记录；相符仅表示对照相符，冲突逐项保留。Token请求account_type/tag15、sub_channel_id/tag10使用本地证据；上游两项为20004/20005。Asta不含PlayerLoginReq Proto，其Handler忽略payload；不能据此称该请求已适配。

## 实施与验收

Project中F-01至F-05按来源、登录协议、初始化/场景、运行前置、D-02排列。先通过独立wire复现旧运行schema错误，再替换证据闭合范围；只编辑Proto源，经本项目JDK17/Gradle/protoc生成。保留AccountLoginAdmission、PlayerUidAllocator/确认保存、经济Guard、显式选人及Lua修复，不复制上游默认角色或忽略请求作为成功路径。

已核对参考仓库根LICENSE为GNU GPL v3；其build.gradle的Apache描述不能替代根许可证。本轮协议源由本地固定目标证据生成，参考源码仅作差异审计，未复制对方实现或生成Java。保留MeChen618/AstaPS、Grasscutter/LunaGC来源与署名；后续如采用源码/Proto须随同保留对应许可证。不提交客户端原始数据/凭据/日志/数据库或参考仓库本体，具体采用文件随每批记录。

## 本机前置复核

`.env`为portable、`.env.local`不存在，GAME_PATH目录及固定hash需实际复核。根配置仍用独立research库及`local/migration72/runtime-data`，HTTP TCP8088/game UDP22101，无服务监听；既有Mongo27017保留。原调试目标仍在，不重复启动、不改调试状态。机器路径/PID/原始证据存忽略目录，不复制历史PID或结论。

## 当前开放项

限定验收已完成并按用户最新指令暂停。本地提交73dd759（计划）/45e22ce（审计）/4324cd7（接入），未推送。399研究、43协议及关联、独立Mongo29套589业务/Lua全部通过；两生成检查无漂移，结构0/ready228。portable构建23秒，JAR `a8c2d89357e1c9253bbb9e982e95e075952f0b32968af3f5b0f236941a53a8f8`内嵌4324cd7；30变化class匹配编译输出、163个Guard/Player/脚本class保持。旧JAR2566f466…83fe已校验归档至忽略目录，仅供固定离线证据复现。

原source/dependency索引只变Ping方法，Proto review只变6源码/Proto、2既有摘要并新增2Asta摘要；65→67证据、1287源码/Proto保持，99候选/148矩阵/1140依赖/1254声明/4786字段、三层状态及开放边界均不变，recover两轮一致。改前14/8真实失败日志保留；首次改后测试为夹具初始化顺序失败，先初始化Grasscutter后43全过，未改生产初始化。初次Gradle未指向便携cache的下载尝试未作为结果；正式测试/构建均使用local/gradle和JDK17。

前置不足：F-02b/F-03完整协议、D-01 SDK/信任/补丁与E-01最小资源仍未齐，不能启动猜测协议。本轮没有部署/安装补丁/操作客户端；最终无游戏服务或HTTP8088/UDP22101/测试27027监听，便携status退出1为无服务器，健康/运行JAR均无对象。测试Mongo正常shutdown前仅admin/config/local；原Mongo27017/数据保留。原AGENTS/resources差异及根config/fastRequire=true均未动。实际新号显式选人、旧号登录、场景握手、心跳、退出重登均未验，D-02不晋级。

F-01限定来源审计已完成，见[协议审计](CLIENT_72_ASTAPS_AUDIT.md)和[实现/闭包差异](CLIENT_72_ASTAPS_IMPLEMENTATION.md)。10个CmdID相符；214外层字段仅130同tag同外层形状、84缺tag。LoginRsp的两个MD5字段名与本地已验语义颠倒，采用本地证据；嵌套/业务不得据相同外壳晋级。64源码、107包候选、166 Proto根及两仓1150/1157 imports闭包只是可计算的源码候选，不是实机必需序列。

F-02a限定融合TokenReq/Head/PingReq/PingRsp，45字段仅10个保留已verified业务名，其余用目标原名；本地account_type=15、sub_channel_id=10，0/20000+/旧号未进入本批输出。新生成器拒绝目标hash/原始wire/已审语义边界/包号漂移和新碰撞；旧混淆常量LCNLHAOBBFE无运行引用且占用24604，已置负并验证规范反向名称。Ping仅将共享原始值从13→6、14→1原样回显；业务名称不晋级。

F-02b（两个响应与完整LoginReq/嵌套）及F-03场景语义仍阻塞。本轮没有新增已验证场景握手schema；既有选人3消息保留，8类44个raw场景字段只保留既有研究载体，未重复生成无业务消费者的运行类。Asta缺PlayerLoginReq/EnterSceneReadyReq，忽略payload不是空schema；不采用旧号/parked/default选人/7.1 pause启发式、RSA失败切key及未知经济fallback。SDK/信任/补丁/资源和D-02仍未完成。
