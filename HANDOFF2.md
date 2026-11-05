# 国服7.2详细接续记录

更新：2026-11-05。目标active，无暂停指令；用户要求一直推进到D-02完成并通过实机验收。D-02未完成，不能把离线工具通过替代实机结果。

## 当前状态与已完成

本批起点dfd027d，研究提交`2a71ddf`完成A-04d5n5限定验收。第二协程23192/NCIDAOJKFEI.MoveNext151226CD0从明确恢复状态2/3执行：闭包JSON→原生Base64/RSA/SHA/验签→实际30677/Region reader→配置/SDK包装/提示→第二B0 store151229560；终点151229567。三个成功样本继续复用同一seed运行时到真实Token构造/getter24604/writer tag965。

8原生样本、187研究回归通过，两次trace/摘要逐字节一致；研究校验0，ready1，当前ready报告214未满足项。两处分区入口的原始类型/字段来源关联现在各有执行证据，tag14/tag965业务名仍没有独立证据，业务命名仍35字段。A-04d5n/B-02c9、A-04d/B-02整体仍进行中。

AGENTS.md和resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json为既有修改，保留未提交；patch f2874103ab70afc56260ee12d1eaca96247933b2和resources dac2a9d43332ef51879522cf39da2831b0ca0543均未变。本批只改离线工具/测试/摘要/文档，无运行Java/主Proto/配置/补丁/资源修改，没有操作数据库或客户端，不需部署重启。后续启动仍须核对本机配置/进程/端口，使用portable。

## 新工具与证据

正式入口tools/trace_client72_dispatch_coroutine.py，测试tools/test_client72_dispatch_coroutine.py，共享protocol/cn-7.2/dispatch-coroutine-observations.json。复现命令及模型边界见CLIENT_72_SESSION末尾A-04d5n5和Harness。

本机local/migration72/evidence/dispatch-coroutine-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json；logs/dispatch-coroutine-{run,repeat,regression}.log。shared SDK/notice/seed模型本批未改，不重跑无关运行服务来冒充验证。

样本：state2/source-A、state3/空值、state3/9007199254740993；坏签名；缺content；闭包错误文本；状态码520；非法state5。前三组连续store并到Token，空值省略tag965。其余均不调用reader/SDK、不改B0、不构造Token。

## 第二协程实际边界

- state(+50) jump table15122A1F8：0→151226DEA，1→151226E77，2/3→151226D20，4→151226DCD。state5范围检查到151229E5B，保持5。恢复2/3先将状态写-1。
- 实例+18为23181/FCLFPIMPBAF闭包，+20为23177/DHABIHPMOAH接收者。闭包+18供JSON字符串，+20错误字符串、+30元数据System.Net.HttpStatusCode；输入200走成功路径，520走特定错误分支，不猜520的目标业务名称。
- post-parse1512287E8核对[rbp+98]槽持有实际parsed原指针。两个配置返回1512289DC/151228A33，分别AL=1；第二SDK路径另经153D33980转15275F080。
- 原生额外包装1471685A0、159BC3AC0、159BD48D0、14F8F0AE0、152754190、15276B740均已实际执行，未用成功stub替代。
- 坏签名到14713C680错误提示入口，标记`[RegionDispatch] DecryptRegionDispatch Decrypt VerifyHash Fail`；缺content到147161B60日志入口，标记`[RegionDispatch] DecryptRegionDispatch Decrypt inputJson Content is Null`。两者不是同一错误边界。
- 非空错误文本或状态520到151226F28，停止在重试/错误恢复之前。state0/1初始HTTP创建/等待、B0后尾段/回调均未执行。
- 沿用n4全部显式模型：本地签名信任、JSON/集合/装箱、SDK导出表/日志sink未初始化、单线程OS、presence-only singleton。不能称真实SDK初始化/网络或完整协程调度通过。

## 下一项可执行任务与剩余缺口

没有全局外部硬阻塞。保留tag965未知，不在没有新来源时反复重做已闭合的原始链。当前源码HandlerGetPlayerTokenReq实际读取account_uid/account_token/client_rand_key/key_id；前三者已核对，key_id仍是登录准入必需缺口。可优先继续A-04d/B-02该字段的独立目标证据，以及Handler后段/封包体；A-03最小P0闭包和D-01/E-01独立工作也仍可做。

请求tag1184/object+98有原生常量4，已由旧token-constructor观察表保留，但**常量4/文件名本身不能给字段命名**。已有trace_client72_response_crypto.discover_private_key在固定目标中提取客户端私钥材料，其modulus/exponent独立匹配且只匹配src/main/resources/keys/game_keys/4_Pub.der（共享response-crypto-observations已有publicKeyMatches）。可以在保持私钥仅本机的前提下补真正的密钥选择/互操作/错误key对照，再判断语义是否足够；不足继续unknown，不为推进而强行verified。

本批只读核查发现：seed加密使用的服务器公钥原生DER SHA-256为7427ee11ef690cfd43a001758eabc3604042b62f78a31cb21f4afe6d715d629e，而4_Pub.der内层PKCS1为c5dae22817432f2cd5670a1f89056460943743282e576d0e6ab01a93ef27d1c0。二者不是同一类密钥；4_Pub用于客户端响应/分区解密方向，不能说“请求seed用了4_Pub，所以4就是key_id”。实际服务端响应加密选择在HandlerGetPlayerTokenReq的Crypto.EncryptionKeys.get(keyId)，需结合真实wire和目标解密验证，而非仅名字推断。

tag965旧QueryCurrRegionHttpRsp.proto同tag名_connect_gate_ticket只是7.1参考。当前grep确认运行Handler没有消费该名字，不能以此删除字段或修改ready标准。后续若仍无独立语义来源，记录缺口并继续能解除其他登录前置的工作。

D-02全部实机内容仍未执行：新旧测试号、选人、初始化/场景握手、心跳、退出重登。B-03/C-01未接完整7.2映射；D-01实际补丁/信任/加载、E-01资源revision/场景/Lua仍未齐。

## 弯路与避坑

1. 第二协程的坏签名不是第一入口的统一日志停点。最初工具在14713C680未知调用停下，已追踪目标错误标记并分开记录；不把错误UI执行完成作为验收。
2. 保护段不能线性反汇编填充。使用实际MoveNext恢复入口，而非直接从151227092跳进解密；后者会遗漏状态和闭包检查。
3. 原SDK/提示模型没有升级为实机初始化；仍不要添加feature flag或成功返回去跳未知调用。
4. 复用seed runtime、RSA两个精确观测点、Object[]双层type-cache间接、空源默认字符串观察的注意事项见此前备份和n4专题。原Token5组此前已回归不变，本批未改这些工具。
5. 未改任何运行文件，无需用重启代替研究验收；已有数据/他人差异保留。

## 文档与交接

README/Project/Harness/CLIENT_72_SESSION及三个账号专题已同步。覆盖前旧HANDOFF2已备份local/migration72/handoffs/HANDOFF2.before-dispatch-coroutine-2a71ddf.md并核对SHA-256一致；HANDOFF.md只追加。研发未暂停，无需重新申请恢复；所有提交仅本地，未推送。
