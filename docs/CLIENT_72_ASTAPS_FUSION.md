# AstaPS成果融合与D-02

最新用户范围：本轮先完成有独立证据支持的融合、相应验收和文档维护，然后暂停；D-02仍是后续目标，本轮不推进新实机阶段。后续缺独立证据的上游候选不能为了收尾而批准。

2026-11-06恢复，接手主仓库c9aafa6。参考仓库为MeChen618/AstaPS，固定提交`a49f51aa6f2787ffbc25cee46801b70dee860d23`，本机路径`local/migration72/references/AstaPS-7.2.155`（忽略目录）。来源/HEAD/干净状态已核对；本项目patch/resources锁定引用及既有他人差异不变。当前仍没有D-02客户端验收。

## 来源与采用规则

已读参考`docs/protocol-generation-7.2.md`、`docs/protocol-7.2-gaps.md`。上游由7.1 descriptor经kitkat 7.1和NT 7.2按名称/父字段桥接，保留服务端类型；未匹配字段停放20000+、92个包号为0、105个消息无7.2 counterpart。protobuf本身允许20000+，不能把“停放”当成不会发送的保证。跨消息共现恢复和单字段推断均需独立目标证据，不能直接批准运行。

本地固定目标以`protocol/cn-7.2/profile.json`和`tools/extract_client72.py`的三输入SHA-256为准。CmdID→类型、类型→语义、字段→wire分别对照本地原生记录；相符仅表示对照相符，冲突逐项保留。Token请求account_type/tag15、sub_channel_id/tag10使用本地证据；上游两项为20004/20005。Asta不含PlayerLoginReq Proto，其Handler忽略payload；不能据此称该请求已适配。

## 实施与验收

Project中F-01至F-05按来源、登录协议、初始化/场景、运行前置、D-02排列。先通过独立wire复现旧运行schema错误，再替换证据闭合范围；只编辑Proto源，经本项目JDK17/Gradle/protoc生成。保留AccountLoginAdmission、PlayerUidAllocator/确认保存、经济Guard、显式选人及Lua修复，不复制上游默认角色或忽略请求作为成功路径。

已核对参考仓库根LICENSE为GNU GPL v3；其build.gradle的Apache描述不能替代根许可证。本轮协议源由本地固定目标证据生成，参考源码仅作差异审计，未复制对方实现或生成Java。保留MeChen618/AstaPS、Grasscutter/LunaGC来源与署名；后续如采用源码/Proto须随同保留对应许可证。不提交客户端原始数据/凭据/日志/数据库或参考仓库本体，具体采用文件随每批记录。

## 本机前置复核

`.env`为portable、`.env.local`不存在，GAME_PATH目录及固定hash需实际复核。根配置仍用独立research库及`local/migration72/runtime-data`，HTTP TCP8088/game UDP22101，无服务监听；既有Mongo27017保留。原调试目标仍在，不重复启动、不改调试状态。机器路径/PID/原始证据存忽略目录，不复制历史PID或结论。

## 当前开放项

F-01进行中。完整P0、SDK/信任/补丁、资源及客户端流程尚未完成；ready228为接手基线。仅相符字段或编译/健康均不能令D-02通过。
