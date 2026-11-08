# R2-ROUTE-SIGN：combo 签名字节合同

## 有界计划（2026-11-08）

任务 ID 为 `R2-ROUTE-SIGN`。前一卡三文件、v3 报告与 raw 全部冻结。本卡只恢复固定 7.2 SDK 的 combo 签名入口 `3BE5C0 → Crypto VT +58 → 3958E0`（准确 call `3BEA13`、return `3BEA16`），核实 `app_key` producer 和当前 Java 接收合同，不再次解包、搜索 `server_pc` 或执行完整 Init 的 CRT 失败边。

现证：固定五输入身份来自恢复清单；SDK SHA-256 为 `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`。准确资源 `server_pc.json` 已在前一卡取得；原生初始化的 `app_key` writer 为 `378DDA..378E70 → config+218`，对应嵌入 ConfigModel `+130`。combo builder 向签名器传递有序字符串 map、C 字符串 key 和输出字符串。`3958E0` 设置第五参数为真，进入 `395900` 后按 map 顺序连接字段，再调用 `396020`。目前算法、分隔符、长度与输出编码仍待原始指令和独立 oracle 确认。

未知：自然 env、完整初始化参数、自然签名/请求及本服会话未采；完整 builder 是否可在有限离线依赖中闭合也未知。自然成功不能由合成签名或静态路由推出。当前 Java DTO 有 `sign` 字段，实际验证和失败语义须沿 handler、AuthenticationSystem 与 authenticator 查实。

依赖与范围：只读固定五输入、已有 REA 导出及 login/crypto/response 夹具、准确资源私有原文、当前 Java combo 认证源码快照。只新增本专题、`tools/client72_sdk_combo_signature.py`、对应测试；raw 仅在 `local/migration72/d02-resume-20261108/r2-sign/`。禁止修改前一卡、共享文档或生产代码；主线独占 Git、构建、DB、端口、客户端、调试器与部署。

预算：先复用既有反汇编与导出；若仍需 REA，最多 6 个准确 SDK 函数窗口，完成后 export/close/reset。原生实验只围绕完整 signer、准确 builder/caller 片段及必要字符串/map/crypto helper；每次最多 2,000,000 指令，最多 12 个确定性合成 case 与 2 个直接 caller/producer 实验，不扩展全 SDK。无网络、目标进程或安装。

产物和验收：以非秘密合成 payload/key 在内存执行真实原生算法，独立标准算法 oracle 对照，覆盖字段排序、字节输入、输出形态、正反边界、嵌套、非 ASCII 与 64 位值；双新进程报告一致，单元测试及独立检查通过，提供第三复验命令。报告只保存结构、长度、状态、等值 bool、代码及固定输入文件身份；不保存或 hash 签名正文、auth body、app_key 原文或 token。真实资源 app_key 仅用等值 bool 和资源来源身份。

停止条件：输入身份变化；准确函数超预算；未建模 CRT/间接边；原生与 oracle 分歧；自然输入仍缺。保留首次失败和准确解除点，不用签名成功桩补齐。若算法证据足够，交出最小 Java 必要规格供主线实施，并明确完整 builder、原生片段、模型和自然链的验收范围。

## 结果与实际范围

**完整原生 signer 已返回，并与独立 HMAC-SHA256 oracle 一致。** 12 个非秘密合成 case 覆盖 map 插入顺序、空/null key、64/65 字节 key、内嵌 NUL、`&/=/百分号` 原始字节、非 ASCII、嵌套 JSON、超过 `2^53` 的标识符、`INT64_MAX` 及 4096 字节字符串。另用手工 ipad/opad 构造核对 Python 标准库 HMAC。只在内存比较正文、key 和结果，报告不保存这些值或它们的 hash。

原生 builder 从完整入口 `3BE5C0` 执行到 **准确发送 call 前 `3BEC2C`**：实际 `3BEA13` 进入 `3958E0`，实际 `3BEA16` 返回；原生外层 JSON 序列化也已执行并与独立 UTF-8 JSON 字节比较一致。这里没有执行发送 call，也没有完整 builder 返回、队列提交或 HTTP 验收。原生数据 getter 走已缓存 device 分支；aggregate、ConfigModel、device 和 route 是明确合成输入。完整 signer 和原生 caller 前缀的范围须分开。

当前默认 Java combo 认证代码 **不读取或验证 `sign`**；签名差异不是这个源码接收条件中的拒绝项。源码快照不等于本轮运行 JAR、插件认证绑定或实际 HTTP 验收。本卡不改变已有账号认证策略，不新增验签门，不宣称自然 combo 成功；D-02 实机增量为零。

## 准确 caller、来源和 ABI

地址为固定 SDK 的 RVA，基址为 PE preferred base；不能作为当前 ASLR 地址。复用 [response 卡](CLIENT_72_SDK_RESPONSE_CONTRACT.md) 和 [loader 卡](CLIENT_72_SDK_ROUTE_LOADER.md) 已导出的 REA；没有新开 REA 会话或 EXE 整库分析。builder 的既有反编译 Evidence ID 为 `ev_54efe118b813c73c4b03ac2399d822a646d2593edb18eb2a30d8e14371c4e802`。新增报告保存准确原始指令、代码窗口 hash、literal/vtable 等值断言与原生覆盖次数；反编译只作静态来源。

| 环节 | 准确输入/输出合同 |
| --- | --- |
| component | `153820` 取无 vtable 的 aggregate `global7ACC80`；`+38` 是 RTTI 已绑定的 `MiHoYoSDKCryptoManager`，VT `6D1CB8`；`+40` 是 Config |
| app_key 来源 | 原始 TextAsset `assets/plugins/mihoyosdk/resources/config/server_pc.json`，class49、fileID0、pathID bitpattern `10246702305728925693`。本卡只复用冻结私有解码，不重新解包；当前 private JSON 与原 payload 精确 text range 做字节等值。carrier SHA 及五输入每轮重核 |
| app_key writer | 原始 `378DDA..378E70` 读取已解析配置 `app_key` 并复制至 `config+218`。ConfigModel getter `363450` 返回 `config+E8`，所以同一字段是 ConfigModel `+130`。真实资源 env0 投影单独执行 writer，756 条指令，原生 JSON 清理返回；只记录等值 bool，不记录 key 字节/长度/hash，未用真实 key 进行签名 |
| app_id | `3BE735` 经 Config VT `+D8→363450` 后读 ConfigModel `+110`（config `+1F8`）字符串 |
| channel_id | 同 getter，ConfigModel `+68`（config `+150`）字符串 |
| device | Config VT `+C0→36AA70` 返回字符串；本卡显式设置 config `+A8` 已缓存 device，真实 getter 执行并返回 |
| data | `3BE5C0` 的 RDX 是 C 字符串；`3BE8E5..3BE8F5` 按首个 NUL 取长度再复制。这里不解析/重写内层 JSON |
| 签名 call | `3BEA13: call [RAX+58]`，真实 VT slot 指向 `3958E0`；RCX=CryptoManager，RDX=caller 32 B `std::string` 输出对象，R8=`map<string,string>`，R9=app_key C 字符串指针 |
| 返回 | `3BEA16`，RAX=同一 caller 输出对象的完整 64 位指针；输出字符串为 64 B 小写十六进制。原生 builder 将其作为 `sign` 字符串加入外层 JSON |
| signer 内部 | `3958E0` 把第五参数设为 1 后调用 `395900`；该函数创建待签字符串，按 key C 字符串 strlen 构造 key 临时串，再调用 `396020`；实际 SHA-256 update/final/compress 均执行 |
| 发送边 | `3BEC2C` 预期调用 NetworkManager VT `6D1F48+10→39C310`。此时 RDX=URL C 字符串、R8=外层 JSON 字节指针、R9=长度 storage；本卡在 call 前停止，未伪造返回 |

已执行 caller 中 `376EA0` 一次、`364910/140940` 零次；它按 `comboLogin` 读取显式 map，R9 override 为空。这再次支持“缺普通 `Config/server_pc.json` 不能单独判定为自然 combo 阻塞”，但显式 map 不证明自然初始化成功或自然 env。

## 可交实现的签名规格

签名 map 的四个键固定，依 unsigned-byte 词典序为 `app_id`、`channel_id`、`data`、`device`。比较器 `136EB0` 先 `memcmp` 公共前缀，再比较长度；不是 locale、Unicode codepoint 或插入顺序排序。

待签字节为各项原始字符串字节的 `key + "=" + value`，以单字节 `&` 相连，不加结尾 `&`、BOM、换行、长度或 NUL。`=` literal 在 `6AF09C`，`&` 在 `6A8B70`。值中已有的引号、反斜杠、空格、非 ASCII、`&`、`=`、`%` 均原样进入 HMAC；没有 URL 编码或 JSON 规范化。**`data` 是 caller 已有 JSON 字符串的字节，不能先解析再重序列化，也不能把整个外层 JSON 当作待签正文。** `sign` 字段不参与签名。

算法是 HMAC-SHA256。`396020` 的 SHA-256 初值为标准八个常量；key 长于 64 B 时先 SHA-256 为 32 B，其余按 64 B 补零。原始常量 `6D9900` 是 16 B `0x36`，第二轮以 `6D9940` 的 `0x6A` 翻转至 `0x5C`。输出依 `6C11C0` 的 `0123456789abcdef` 将 32 B digest 转成 **64 B 小写 ASCII hex**；不是 base64、原始 digest 或大写 hex。

key 在本接口为 C 字符串，首 NUL 后不参与；null pointer 使用空串。signer map 的 std::string 值按 length 处理，可以含 NUL；真实 builder 的 caller `data` 则先受 C 字符串首 NUL 截断。测试刻意区分这两个边界。资源 app_key 的自然选择仍未观察，不能把 env0 投影当作自然 key。

原生 outer JSON 在本 caller 样本为紧凑 UTF-8，字段顺序为 `app_id/channel_id/data/device/sign`，全部值是 JSON 字符串；内层嵌套、非 ASCII、`INT64_MAX` 只是 `data` 字符串的一部分。签名在 outer 序列化前计算，不依赖 outer JSON 空白或字段顺序。本卡验证的是这些样本的实际字节，不扩展推断所有 JSON 数值/locale 的通用规则。

若主线以后实施独立 Java signer/validator，最小算法可用 `Mac.getInstance("HmacSHA256")` 和 UTF-8 字节、小写 hex；须保留原始四字段的字符串语义，特别是 `data` 不能经对象或 double 往返。当前 DTO 的 app/channel 是 `int`，不能用数值转换后再猜测原始有前导零的字符串。新增验签策略不属于本卡必要改动，现服务端并未以签名匹配准入。

## 当前 Java 接收合同与验收限制

`java-epoch0` 私有源码快照固定九个文件，manifest SHA-256 为 `aaf6814ef6ea3cbf26b8c89e3231687fe88966f3b3631c764a28f8b72bfe927e`。每轮验证快照 hash 并另报告当前源码是否仍等于该 epoch。实际链为 `AuthenticationHandler.sessionKeyLogin → AuthenticationSystem.fromComboTokenRequest → DefaultAuthentication.SessionKeyAuthenticator`；`Grasscutter` 默认初始化 `DefaultAuthentication`，但 setter/插件的当次运行绑定未观察。

| 项目 | 固定源码行为 |
| --- | --- |
| 路由 | `/hk4e_cn/combo/granter/login/v2/login` 注册同一 handler |
| 外层 DTO | `app_id:int/channel_id:int/data:String/device:String/sign:String`；本卡没有运行 Gson/HTTP 来代替验收类型转换 |
| handler | 外层 decode null、data null/空、内层 decode null 时直接返回。内层按 `LoginTokenData` 解析 uid/token/guest；本卡不推断这些 early return 的实际 HTTP status/body |
| 传递 | `fromComboTokenRequest` 保存 outer DTO 与 inner DTO。默认认证器的 outer `requestData` 只声明和 assert，未读取 sign、app/channel 或 device |
| 成功条件 | 按内层 uid 找到 account，且 `!passwordRequired || matchesSessionKey(inner.token)`；后者需非 null、非 blank token 与已存 sessionKey 精确相等 |
| 宽松分支 | 非 passwordRequired 账号存在时可以采纳传入 token 作为 sessionKey；这是当前已有源码行为，不是本卡新增或批准的签名策略 |
| 成功响应 | retcode 默认0、message OK，open_id=account ID，固定 combo_id，生成 combo_token；响应 data 为 JSON 字符串。具体 response 消费参见 response 卡 |
| 失败响应 | 认证条件不成立时 retcode `-201`，消息取翻译文本 |

因此，不能把“合成签名正确”当作当前 Java 会话接受，也不能把“sign 错误/缺失”单独当作这里已存在的拒绝门。实际 HTTP 及 DB/运行 JAR验收由主线在其独占范围执行；本卡没有运行 Gradle、DB、端口或服务。

## 原生失败边、停止点与下一准确输入

第一次延伸 builder 的夹具误把 HoYoNetworkEngine VT `6BF300` 放在 NetworkManager component，导致 `2EF215` 无效读取；已保留 `first-builder-failure.private.json`，该结果不用于合同。纠正为准确 NetworkManager VT `6D1F48` 后，实际发送路径进入 `39C310`，在请求附加信息链 `3AFAC0→3AFB09` 的间接调用处停下，原始轨迹和错误类别保存为 `second-builder-failure.private.json`。没有补这个间接返回或继续 CRT 初始化。最终接受样本的停止点前移到与签名出口直接相关的准确 `3BEC2C` call 前。

**下一首缺是自然 caller/config 输入和运行接收结果，不再是签名算法或普通磁盘配置文件。** 主线最小观察只需：固定 SDK/准确 caller 身份；自然 selected env/配置字段的类型状态；app_key 与准确资源行的等值 bool；四个签名输入字段存在/长度、data 字符串类型、sign 形态/长度与原生返回状态；固定 host/path 分类及本服 handler/认证结果。不要采集、保存或 hash key、token、data、请求 body 或签名正文。不需要本卡扩大 live 观察或替主线补 cleanloader。

当前缺少自然 config 和 caller 的证据，不能决定补丁要改哪一个 env/key/route；也不以原 SDK UI 正常显示、配置缺文件或静态 host 命中反推自然 combo。后续若只需服务端兼容当前请求，先核对现有 uid/token 合同与实际 source/runtime 绑定。

## 复验

测试从 `GAME_PATH` 或固定恢复清单读取当前客户端路径，工具参数应传当前 `GAME_PATH`；原始输入和资源不进入提交。以下只运行离线 Python，不启动服务或客户端。输出路径必须是新文件；已存在文件会拒绝覆盖。

```powershell
$client72Path = $env:GAME_PATH
if (-not $client72Path) {
    $client72Path = (Get-Content -Raw local/migration72/d02-resume-20261108/initial-inventory.private.json | ConvertFrom-Json).gamePath
}
python -m unittest discover -s tools -p test_client72_sdk_combo_signature.py -v
python tools/client72_sdk_combo_signature.py --client $client72Path --output local/migration72/d02-resume-20261108/r2-sign/final-first.json
python tools/client72_sdk_combo_signature.py --client $client72Path --output local/migration72/d02-resume-20261108/r2-sign/final-repeat.json
# 主线第三个新进程；报告生成后与 final-first.json 做逐字节比较。
python tools/client72_sdk_combo_signature.py --client $client72Path --output local/migration72/d02-resume-20261108/r2-sign/main-third.json
```

实际 15 项测试通过（`tests-final.log`），双新进程报告各 185,859 B、逐字节相同，SHA-256 均为 `f2f8f3ad6313451839920345bf51d6255fcf496c0d2311f7dad663971379d434`；caller 前缀 51,515 条指令、真实资源 key writer 756 条指令。独立标准库检查器另核固定五输入、PE 原始窗口、准确 call/return opcode、pad/literal、返回/边界与父工具冻结等 20 项，全部通过；它没有把报告复核冒充原生重执行。

双新进程报告和独立检查的最终身份在本卡 raw `hashes-and-determinism-final.private.json`；该记录只含代码/报告/固定输入身份，没有配置原文或签名相关字节的 hash。上一卡工具、测试和 raw 全程未修改。本卡仅离线工具/测试/文档，无部署、运行 JAR 或客户端验收增量；Git 提交由主线收口。

主线第三完整回放 `main-third.json` 退出0，与双跑逐字节同185,859B/SHA；15项原生/独立oracle测试通过，另运行标准库审计器20项全过。证明 `main-third-determinism.private.json`、`tests-main-third.log`与`main-third-independent-review.private.json`。主线复核真实signer、caller原始data字符串、outer序列化及Java源码未消费sign的边界后，接受限定签名合同；不新增强制验签策略，实际HTTP接收和自然输入继续后验。
