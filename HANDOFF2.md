# 国服7.2详细接续记录

更新：2026-11-05。目标active，无暂停指令；用户要求一直推进到D-02完成并实机验收。D-02未完成，离线结果不能替代实机。当前研究提交`b89d855`，上一交接提交459eca2。

## 当前状态与已完成

A-04d5o/B-02c10完成Token必需字段key_id限定验收。原生构造151220094写request+98=4，writer为tag1184/uint32，独立wire804a04。另在Miscs.GetGlobalDispatchUrl发现并执行命名参数片段：UInt32常量4与literal61981 `&key_id=`。客户端响应私钥仅匹配4_Pub.der；Java按原生请求值选择该公钥产生响应，三个成功样本的原生4096字节会话密钥与现有Java MT64相等；故意改用2/3/5均原生拒绝，无密钥安装。

key_id名称为业务契约交叉证据推断，不是声称解密了混淆字段名。累计四个登录消息36业务字段（另有2个PacketHead字段，映射总verified字段38），8枚举102符号。tag965仍unknown，不借旧proto命名。A-04d/B-02整体仍进行中，D-02未开始实机验收。

本批3请求/6响应/2命名参数片段、192研究回归通过。两次trace和摘要逐字节一致；研究校验0，ready1（213项未满足）。Java全量集此前217失败没有消除。运行Java/主Proto/配置/补丁/资源均未改，只改研究工具、映射、文档，不需部署重启。未启动客户端、未操作数据库。

既有AGENTS.md编码规范修改和resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json继续保留未提交。patch HEAD f2874103ab70afc56260ee12d1eaca96247933b2、resources HEAD dac2a9d43332ef51879522cf39da2831b0ca0543未变；未推送。

## 工具与证据

正式入口tools/trace_client72_key_selection.py，Java对照tools/Client72KeySelectionReference.java；测试tools/test_client72_key_selection.py，validate_protocol72已接入约束，mapping研究表key_id已verified。

共享protocol/cn-7.2/key-selection-observations.json；本机local/migration72/evidence/key-selection-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json；logs/key-selection-{run,repeat,focused-tests,regression}.log。复现命令见CLIENT_72_SESSION的A-04d5o。

固定输入仍是extract_client72.HASHES三份文件。trace SHA256=8660ffaaf609c3eed42452c0b7e712154fe0cd93fd26c4589ad36ee498fff2a0；summary SHA256=c5c488772ceecf02c5a6e46845706e4379bc275ff19d55c42f4671cdddc6e92d。

研究回归选集：全部tools/test_client72*.py，加test_extract_client72、test_validate_protocol72、test_audit_resource72、test_inventory_protocol、test_review_protocol72_surface，共192项。仅运行相关研究，不用服务器健康冒充客户端通过。

## 本批边界与避坑

- 命名URL证据是两个原生片段：1531E0D76→159C1ABD0（formatter入口），1531E0D8A→159A8E9D0（Concat入口）。formatter声明参数为string/uint32/IFormatProvider，method3829；第二段格式化字符串4为显式输入。完整URL/HTTP未执行。
- literal61981的usageRow782238/cell145B42F08，所属命名方法Miscs.GetGlobalDispatchUrl/1531E0A10。不能将搜索命中或常量4单独当证据。
- 成功响应仍显式本地签名信任：私钥签名、服务器公钥及签名、根PEM输入。验证过程/结果未替换。
- 请求seed加密使用原始服务器公钥（DER SHA7427ee11...）；客户端响应解密对应4_Pub（内层DER SHA c5dae228...），两者不同。Java本项从已验证的原生client seed数值生成响应，没有假装用本地SigningKey解开原始请求密文。完整交换/实际信任补丁另验。
- Java固定0x42 padding只作离线确定性，与独立Python整数RSA/签名逐字节比较；真实私钥仅内存，公共摘要只hash。
- 不修改原Token/Response共享模型；新工具复用现有验证路径。已有旧观察表unknown标签属于当时边界，不为新语义改写历史证据。

## 已开始的下一项：Handler尾段

本机探索脚本local/migration72/analysis/probe_handler_tail.py已实际从1507BE0E0连续执行既有warmup/密码学/UID/token保存，再越过旧STOP15A4E0D9A，执行Miscs.set_robotCheckLoginInputData/1531E9EB0，停在1531D03D0（GetUniversalGameVersion的内部GetGameVersionAndChangelist入口）。

响应原始tag3/bytes/BHHECPBGDKB/object+60经setter写GLOBAL+11C18，同一个ByteString原指针、含NUL/FF的非空输入已观察相等；4096密钥仍安装。此为单次探索，没有进正式工具/重复/边界样本/语义映射。报告evidence/handler-tail-exploration.json、logs/handler-tail-exploration.log，不能称已验收。

探索复用HandlerCore72，仅在旧STOP地址绕开旧实验的停止回调，继续执行真实指令；不跳过目标调用/验证。新增允许1531E9EB0、147CF2160（ByteString等号）、1531D02B0（GetUniversalGameVersion）。当前ByteString为非池化/非引用计数夹具，GLOBAL+11C18初始空；池化/旧值释放路径未测。

命名后续：
- Handler 15A4E0DA3读取response+60→1531E9EB0；15A4E0DB2调用1531D02B0；15A4E0DB7读取response+80→15321A4D0 CalcVersionHash。
- 1531D03D0=Miscs.GetGameVersionAndChangelist，六个byref string参数gameVer/engineCL/clientCL/resourceCL/silenceCL/dataCL（method94240，param98600..98605）；实现读取本地版本配置，含StringBuilder/Regex之类对象，尚未跑体。
- 15321A4D0=Miscs.CalcVersionHash（method94441），param99075 version/string，99076 secretKey/string；不是直接名为client_version_random_key。响应+80是tag658/string/FIHDHDLJBLC，目前保持unknown。
- CalcVersionHash内format调用159AB12F0→SHA1类/UTF8→HashAlgorithm.ComputeHash159BB9650→Base64 159BC3080→GLOBAL+11B90；可优先恢复完整密码学到已知Login hash来源。不要硬替换hash计算为成功。
- Handler 15A4E0DCE之后检查GLOBAL+B6D0及分支，随后1507C0BB0/1507C0D30/1507C1080（同owner56238/HBIJFOJIFAH），这些后段未执行。

可选下一步：在明确版本来源夹具下继续GetUniversalGameVersion和CalcVersionHash；或保持未知继续封包体14A447E00/A-03闭包。不能用原始bytes送进robot setter就套旧security_cmd_buffer名称。

## 未完成与解除条件

没有全局外部硬阻塞。D-02仍需B-02全部P0语义/wire闭包、B-03生成/C-01接入、D-01真实路由/补丁加载/信任、E-01revision/场景/Lua最小资源等；新旧测试号、选人、初始化/场景握手、心跳、退出重登全部未实测。未知保持未知，继续依赖满足的开发研究。

启动前重新核对当前checkout/.env/.env.local/config.json、GAME_PATH、portable、子模块/进程/端口归属。保留mongodata/local/data与隔离库，不操作无关客户端或强杀未知进程。

## 文档与交接

README/Project/Harness/CLIENT_72_SESSION及三个账号专题已更新。覆盖前HANDOFF2已备份local/migration72/handoffs/HANDOFF2.before-key-selection-b89d855.md并验证SHA256相等；HANDOFF.md追加。目标持续active，不需另请用户恢复。
