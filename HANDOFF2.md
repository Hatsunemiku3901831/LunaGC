# 国服7.2详细接续记录

更新：2026-11-05。目标active，无暂停指令；用户要求持续到D-02完成并实机验收。当前正式研究提交e1a0194，前一检查点defc23a/b89d855。D-02仍未完成。

## 本批完成与实际验证

A-04d5p正式完成Handler入口到robot输入/原生版本哈希的限定验收。工具tools/client72_version_hash.py、tools/trace_client72_handler_tail.py；摘要protocol/cn-7.2/handler-tail-observations.json；新增6测试。7原生样本、198研究回归通过，trace/摘要二次逐字节一致。研究校验0，ready1（213项未满足）。四登录消息仍36业务字段（含PacketHead总verified38），8枚举102符号；本批两响应字段不命名。

实际连续执行1507BE0E0→原有warmup/RSA/SHA/验签/seed/UID/token保存→1531E9EB0 setter→1531D02B0版本拼接→15321A4D0哈希→15A4E0DC7返回。tag3 bytes/BHHECPBGDKB/object+60写GLOBAL+11C18，保持原指针；tag658 string/FIHDHDLJBLC/object+80是CalcVersionHash的secretKey参数，业务语义仍unknown。

原生编码是420 ASCIIEncoding，**不是旧交接推断的UTF8**。SHA1CryptoServiceProvider1056→SHA1Internal1055、ASCII/default fallback、Buffer.BlockCopy、Base64都实际执行；GLOBAL+11B90已有Login client_version_hash/tag1996分段来源证明，正式p项没有连续执行Login。

版本六byref来源GetGameVersionAndChangelist/1531D03D0为明确夹具，外层拼接真实执行，只用gameVer与_R/resourceCL、_S/silenceCL、_D/dataCL；engineCL/clientCL不拼入。只为exact literal62885 `{0}{1}mhy2020`提供双字符串format库模型。两个标准问号fallback对象经真实constructor初始化后放缓存，不跑锁/缓存初建。

样本：完整版本、全空、SHA1输入55/56/64字节、含BMP/代理对/NUL、坏签名。Unicode输入实际ASCII逐UTF16单元替换为问号（代理对两个问号），NUL保留；独立hashlib/base64逐字节相等。坏签名止15A4E0645，不进尾段、不改预先非空robot/hash。robot仅非池化ByteString，引用计数/释放不在范围。

## 正式证据与复现

本机local/migration72/evidence/handler-tail-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json，logs/handler-tail-{run,repeat,focused-tests,regression}.log。复现见docs/CLIENT_72_SESSION的A-04d5p。

trace SHA256=526715c656d9dc5622a947782fec0ac541940d5cb70faaf4cb8856b475862acd；summary SHA256=967b7b9524afa3e87e7e6316248cd6a9938a5d80c6c0e68ec2b9344146c3b0bf。

全研究回归为tools/test_client72*.py加test_extract_client72、test_validate_protocol72、test_audit_resource72、test_inventory_protocol、test_review_protocol72_surface，共198。Java全量集既有217失败未消除。本批不改运行Java/主Proto/配置/补丁/资源，未启动服务/客户端/数据库，不需部署重启。

## 下一项A-04d5q：已成功的连接探索，尚未正式验收

local/migration72/analysis/probe_handler_login.py已在同次原生执行中从1507BE0E0连续到**14A447E00封包入口**。过程中真实Handler→版本哈希→WorldType.Login/PackageType.Full分支→原生Action调用→1507C0BB0→1507C21B0→151213730完整Login构造→151214D20发送队列/transport/getter23172。没有重置CPU或把token/hash回填请求；请求+D8保留response+68的token原指针，请求+60保留GLOBAL+11B90的hash原指针。输出wire tag1=t、tag9=CNRELWin7.2.0_R123_S456_D789、tag1996=R1+LkJ1jGeV6p3CW0BVqprFAT4M=；阶段[9,6]，会话密钥4096字节。

单次探索报告evidence/handler-login-exploration.json、日志logs/handler-login-exploration.log。仍只有一组成功；没有正式工具/样本覆盖/重复/测试/提交，不称通过实机或完整Handler返回（在嵌套发送函数里停止）。下一步迁入正式工具，核查全部初始化来源、补缺GameManager/时钟条件/坏签名等边界并重复验证。继续保留明确SDK/旧Shell/版本/传输夹具。

### 连接实验使用的独立声明与原生路径

- GameManager.Instance为50103静态raw0x400B6D0；实例+20字段FAKHICOBBBO类型59570/LLNNNEEBKEP，该对象+18是WorldType11326。默认表独立恢复：Login=0、Home=1、Level=2，Invalid=-1。**不能用Level=2或null状态绕过普通登录分支**。探索明确WorldType.Login=0。
- GlobalVars.packageType raw0x2001904、地址14586CF14，类型11334/PackageType；None0/Less1/Medium2/PlayGo3/FastStart4/Full5。**3不是平台Windows编号**；探索明确Full5，实际安装运行值尚未测量。
- 两份探索枚举证据worldtype-exploration.json、package-type-exploration.json来自固定目标默认表，脚本probe_worldtype.py/probe_package_type.py临时选择已有enum恢复器；正式化应独立核验并记录，不依赖忽略目录结果。
- Login分支15A4E16C9构造Action，最终14BB76740在Full输入下原生调用该Action；不是模型直接调用回调。
- MethodInfo cell145A00A40独立usageRow749199，source0x6007226B（kind3，methodDefinition467563），slot76104，method cache注册+10=14596C000。目标方法owner56238/HBIJFOJIFAH.EAIJKILEHFE/1507C0BB0。探索只按此独立映射提供methodInfo+8指针，不猜回调地址。
- 类型usage：698995/cell145887448→142 System.Action；702787/cell14589A988→82313 PNLAGOKKFEN；708398/cell1458BA860→63102 OBLOCFOLEPD。脚本probe_handler_gate_types.py、probe_handler_delegate.py可查。
- 已有GLOBAL+A070辅助对象的+30放明确缓存配置（无需临时假造配置parser成功），14A9CD540原生返回缓存；+76默认false。GLOBAL+111B0放原始presence对象，+3B=false走原生立即分支，具体来源/字段业务名仍未知，正式化需声明此夹具或继续追来源。
- Engine clock：141447780→141705750→1419D9F00真实执行，selector7读1457A98B0+7*8的初始化指针，其+a0 double=100，经cvtpd2ps比较manager+208默认0。不要猜该clock的业务名或单位；完整初始化/真实时钟未测。
- SendFlow72在151214D20首次进入时安装（不重置CPU），依旧明确已连接AKFCDHJBCHD传输选择/对象池/sequenceFFFFFFFF和sent_ms200000044输入；真实getter23172，停止封包体之前。

### 组合模型的两个已修正问题

1. 必须先安装Constructor72环境再安装HandlerTail72，避免后装Constructor覆盖刚算出的GLOBAL+11B90。Ctor创建的原始account+50来源需保留给Handler新会话；遗漏会在151213836走真实null错误边界151214817/int3。没有修改目标指令绕过，已补回原始夹具值，不给该字段猜业务名。
2. 两模型共享同一个NumberFormatInfo：将ctor.provider与rt.provider统一。否则Login TryParseUInt32模型因provider对象不一致停止；这不是游戏协议错误，已修正模型组合。
3. Constructor的text读取实际托管UTF16内存，以消费Handler/原生Concat产生的字符串。提前关闭Ctor literal hook，保留Handler唯一hook；不要再重复加载两套literal。实际文件中已移除Ctor对1531D02B0和159A8D050的模型，让GetUniversalGameVersion及双/三字符串Concat都原生执行。
4. flags列表同时提供5714（既有old warmup）与3628（既有old Shell），各分支由真实Contains选择；不能只保留一个导致跑到尚未验证的新Shell路径。
5. 探索新增允许1507C0BB0、1507C21B0、14BB76740、14A9CD540。其余仍执行已声明函数或停止未知调用；不要插成功stub。当前未继续封包体或Handler回栈后的SDK/UI动作。

## 整体未完成与工作区

D-02仍需A-03/P0映射闭包、B-03生成/C-01接入、D-01实机补丁/信任/路由、E-01资源revision/场景/Lua等。新旧测试号、选人、初始化/场景握手、心跳、退出重登全部未实测。没有全局外部硬阻塞，继续q连接正式化与封包体等依赖满足工作。

本批正式新增仍是离线研究，无部署要求；下一次启动必须重查GAME_PATH/portable/.env/.env.local/config.json、进程和端口归属，保留旧数据库与local/data。既有AGENTS.md修改及resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json保留未提交。patch f2874103ab70afc56260ee12d1eaca96247933b2、resources dac2a9d43332ef51879522cf39da2831b0ca0543未动。全部提交仅本地。

## 交接文档

README/Project/Harness/CLIENT_72_SESSION及账号三文档已同步p正式范围；q探索入口同步Project/Harness。覆盖前原HANDOFF2已备份local/migration72/handoffs/HANDOFF2.before-handler-tail-e1a0194.md并核对SHA256一致；HANDOFF.md只追加。目标继续active，无暂停，不需再询问用户是否继续。
