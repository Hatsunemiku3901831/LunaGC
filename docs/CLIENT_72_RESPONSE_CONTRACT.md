# 7.2登录响应最小发送合同（F-02b）

运行接入补充（4bbf9fd）：下表最小合同已用于生产builder。Token仅uid/token/sign/server_rand_key，retcode-only错误；key<=0在账号准入前拒绝，实际RSA失败不伪造签名、不切secret key。资源61只投影四字段。真实Java RSA成功及两坏参数、完整账号/UID/确认保存回归通过；LoginRsp连续收帧key selector未证，最小合同不代表SDK/完整Handler/实机登录。

本项仅为固定国服7.2三输入的离线原生wire/消费证据，不是完整Handler、SDK、网络或实机验收。不改三层mappings的状态；只有既有verified语义采用业务名，其余运行schema仍用clientField原名。AstaPS固定提交a49f51aa6f2787ffbc25cee46801b70dee860d23仅作对照，不作为本工具输入。

## 最小发送范围

| 响应 | 拟发送字段 | 独立target证据 |
| --- | --- | --- |
| GetPlayerTokenRsp成功 | uid/tag9、token/tag6；RSA额外server_rand_key/tag477、sign/tag208 | handler-core的UID/PlayerPrefs与token原指针；response-crypto独立477/208 wire→原生RSA/SHA256/4096字节session key，含非零64位与拒绝样本 |
| GetPlayerTokenRsp拒绝 | retcode/tag14 | login-semantic独立正数、-1、INT_MIN wire→实际错误日志前缀；不发未verified msg或blacktime |
| PlayerLoginRsp当前资源配置 | 原始FHCEHICKAIP/tag61，仅投影MDKG的1/3/4/5 | 本项非零、uint32边界、重复message合并→原生Handler→具名GlobalVars存储 |

Token成功不能再发送旧account_uid、account_type、platform/reg/channel/country、proficient、appid/version、IP、明文seed或security buffer等未verified响应语义。请求HLNLCJBLCMM/tag15已独立确认account_type，响应同名/tag2的业务含义仍须真实消费/来源；同名不是跨消息语义证明。account_uid不能从uid/tag9或高数字字符串请求来源推得。key_id请求tag1184已确认，不代表响应FMMLBHKFGDH/tag440已确认。response-crypto成功输入不含440或658，故最小RSA合同不发送两者。

仅uid/token的构造入口用于明确useXorEncryption=false的本地分支夹具；加密模式不能将其作为明文seed回退或客户端成功证明。真正加密成功仍需477/208有效密文/签名和既有密钥安装合同。

tag658/FIHDHDLJBLC已有独立wire→CalcVersionHash(version, secretKey)输入→ASCII/SHA1/Base64证据；实际格式为`{0}{1}mhy2020`，但旧名client_version_random_key仍未verified。空值、SHA1块边界、UTF16 fallback及坏签名保留在handler-tail；不因本次接入晋级。msg/blacktime须独立目标错误消费及wire来源后才能发送。内部封禁准入/拒绝状态和原因应保留，wire仅发送已验retcode。

## MDKG资源字段

| 子tag / raw字段 / 对象偏移 | 原生具名目的地 | 可用含义 / 当前发送 |
| --- | --- | --- |
| 1 / KDMKODCHHIP / +40 | GlobalVars.RESOURCE_VERSION / raw200192c | version；当前tag61可投影 |
| 3 / MFHJIGAMNFK / +38 | GlobalVars.RESOURCE_MD5 / raw40061f0 | md5；当前tag61可投影 |
| 4 / GFCEGMNDCKH / +30 | GlobalVars.RESOURCE_VERSION_SIZE / raw40060b0 | resource_version_size；当前tag61由旧release_total_size值投影，不能称此旧名称已验证 |
| 5 / DIDEGEOAEJN / +20 | GlobalVars.RES_VERSION_SUFFIX / raw40060f8 | version_suffix；当前tag61可投影 |
| 7 / LMMCPBMDBKP / +18 | GlobalVars.NEXT_SCRIPT_VERSION / raw4006320 | next_script_version，仅经外层1337消费证明；当前tag61不发送 |
| 2 / FBJOBGOBDIE / +44 | 控制版本不等时的资源更新分支 | relogin业务名未verified；不发送 |
| 6 / MCKEMGKBDKG / +28 | 本项无具名消费 | branch业务名未verified；不发送 |

外层61/+68与1337/+48的两个原生reader分配同MDKG类型；本项保留FHCEHICKAIP/MONEPNFKIML和MDKG原名，不自动命名ResVersionConfig/next_res_version_config。1/3/4/5是当前业务RegionInfo.getResVersionConfig()中已有值的明确投影边界，不代表这些值的资源来源或真实下载已验收。

实际检查点为Handler入口1507B7BE0→1507B8368，经过MD5/suffix和资源存储。具名字段声明、对象偏移、wire tag分别核对；只建模既有stage记录、cache reset及GraphReader明示托管运行环境。flag=false允许不同版本；flag=true夹具把版本设相等，未执行实际资源下载分支。字符串比较检查原生指针保持；数值覆盖42、UINT32_MAX、0及300；重复61/1337旧值被后续message合并覆盖；缺两配置走真实跳过分支。

LoginRsp原有本地MD5必须保持：437→DATA_MD5/client_md5，1255→SILENCE_DATA_MD5/client_silence_md5，744/1792分别silence/data suffix。Asta两MD5名字相反，不覆盖本地消费证据。

LoginRsp封帧密钥本批保持待核：既有14A446CC0收帧工具真实验证selector0→GLOBAL+19580、1→19588，但selector由夹具显式提供，factory是不透明模型，未包含20835。连续Token Handler→LoginReq封帧已证安装19580并维持selector0，未接到响应收帧/NDEN reader/Handler。本项不以一般协议习惯修改旧setUseDispatchKey(true)；解除条件为复用该已安装运行时，连续执行LoginRsp接收解帧与真实reader/dispatch，并核对其间selector来源与复写。

## 复现与解除条件

```powershell
python tools/client72_response_contract.py --client $env:GAME_PATH --output local/migration72/evidence/response-contract-trace.json --summary-output protocol/cn-7.2/response-contract-observations.json
python -m unittest tools.test_client72_response_contract tools.test_client72_response_crypto tools.test_client72_handler_tail tools.test_client72_login_semantics tools.test_client72_graph -v
```

摘要固定三输入SHA-256与工具/表来源SHA-256。原始客户端、完整trace和临时探测留忽略目录。两轮新工具报告/摘要必须逐字节相同；不能只比测试数量。现存crypto/key-selection/handler-tail为分段原生研究和声明信任输入，不是原始服务器签名或生产补丁验收。

2026-11-06实际执行：本项6个原生资源消费夹具、完整报告与摘要两轮逐字节一致；摘要SHA-256为`7d9f2e1ef5687cbd29459e14f8075d85580314b1f0a7abe01b33d77830e39e0f`。另重跑response-wire（85外层字段、256发现/11验证/10拒绝）、login-semantic（17）、response-crypto（8）、handler-tail（7），四份摘要均与已提交基线逐字节一致。上述Python聚焦回归27项全过，无失败/错误/跳过；新Java独立hex测试已编写，统一Gradle验证由集成批次执行，不能提前记为通过。对照证据在`local/migration72/analysis/f02b-response-evidence.json`，其原始输入均再次通过read_verified固定三hash。

下一项：按原始handler错误分支恢复msg/blacktime的具名消费；核查响应440、HLN/tag2、account_uid的实际来源/目的地；需资源配置额外字段时执行真实下载/版本分支并补独立wire，不从Asta或旧builder赋值猜配。完整P0、SDK/补丁、资源和D-02前置仍开放；本项不启动服务或调试目标。
