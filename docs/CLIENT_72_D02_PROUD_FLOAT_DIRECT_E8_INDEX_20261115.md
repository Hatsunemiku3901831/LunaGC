# R3-PROUD-FLOAT-DIRECT-E8-INDEX：采集预算停止

状态：**阻塞**（本批 common E8 索引预算停止，不代表 Proud 全局无来源）。本批目标是固定 getter 的 raw `E8 rel32` 扫描域索引；不取 caller 正文或反汇编，stage2 未执行。Proud 字段桥、实际 row、当前采用和 resource ready 均未验，不能用本批说明客户端验收通过。

计划与固定输入清单 SHA-256 分别为 `1ef6c5bad410aca8bbfff8cb0988489111e9a0181ddf0d7bbf1d0dd430d3ac39`、`92bad0a5090c3abfb501917aa96f60946ada9375924538f579dd543e92dc17ac`；33 项来源的大小与 SHA-256 均匹配。装备专题固定报告 `50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc` 也经大小和 SHA 核对，用其 10 个准确完成窗与既有 getter 166B、critical 六窗组成排除设计。

首次 fresh 因新工具未按既定 portable 合同传 PowerShell 入口参数而停，具体输出与证据缺口见忽略目录 `local/migration72/d02-resume-20261115/proud-float-caller-index/fresh1/failure.private.json`，SHA-256 `63e469a78bb0847edece8a35658662df2906772479f8b61bf4a51f323e6d551b`。初次异常隐藏原 stderr，之后为诊断重复调用两次，原缓冲未留存；诊断重建指向遗漏已验 portable 入口调用合同，原三次 resolver 的原始缓冲未保存，重建不冒充原输出。主线只读复核未发现可据以认定内容安全审查拒绝的证据；原 A/B 内容安全拒绝及依赖继续冻结。经主线限定授权，工具仅对齐仓库既定入口参数，并为未来解析失败保存原 stdout/stderr、argv、时间、退出码与读取计数；旧失败原件未更改。

新的 fresh2 通过固定输入及当前 PE 450927000B、完整 SHA-256 `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` 校验，进入文件支持的可执行区 raw 扫描。匹配第 **4097** 个候选时按计划上限停止，停止 site 为 `0x14cc8b6f2`；本记录不认为这些候选是已证指令或 caller。该次尚未读取 pdata，也未保存 raw 前缀、完整读取账或生成候选索引、别名、完整排除及审计产物；未运行 fresh3。工具在扫描循环前按段读取，完整分段读取账仅在成功路径写入，故本次实际 exec 文件读取字节和完成区间未保存，不能据此给出覆盖或负结论。私有停止原件为 `fresh2/stopped.private.json`，SHA-256 `d0bf4738bc4b39302403b8e643edd5a2ca22dfbd45c00d70bcfdac5c369c9bca`。

首版 8 项合成/mock unittest 通过：64 位地址及正负 rel32；错误目标；E9/FF、末尾不足及末尾完整5B、伪 E8；排除区间和非执行区；pdata 无匹配、重复、重叠、独占 end、跨 end 和单匹配；方法别名保留与未知；纯产物审计缺文件和差字节失败；解析入口 argv 和失败原缓冲；解析成功路径。它们使用合成数据或 mock，不证明目标客户端的候选或指令边界。双 fresh、独立第三和正式纯产物审计均未完成。

候选上限触发后不得自动扩大预算；该 common E8 批次没有可据以选择 Proud caller 的索引。另立的 `R3-PROUD-NARROW-TYPED-CONSUMER-SOURCE` 已完成[typed 单窗来源验收](CLIENT_72_D02_PROUD_TYPED_BASE_LIST_WINDOW_20261115.md)，没有扩大 common E8 预算，也不证明 Proud 业务桥。本批没有构建、部署、目标进程操作或客户端验收；本执行者没有 Git 写入。

## R3-PROUD-E8-STOP-LEDGER：仅读取账修复

拟用于未来停止分支的留证合同，经合成测试验证保存 binary hash 与 header 读取账、声明域、每个实际读取域的请求和返回字节、已完成扫描域、当前域已读入但仅扫描至第 4097 个 site 的状态，以及前 4096 个 raw 五字节候选。第 4097 项只记录停止位置；标记索引未完成、pdata 未读取，并使用 CreateNew 防覆盖。正常未触上限时的候选结果不变。这里的候选均不证明指令边界或业务语义。

作者与独立各 10 项纯测试通过，其中新增合成数据测试核实 4097 停止、读取账和 4096 条前缀、后续域未读取、原件不可覆盖，以及未触限的输出不变。该修复未执行路径解析、PE 读取或任何真实 fresh，不能回填旧 fresh2 的实际读取量或覆盖范围；旧失败证据与原始缓冲缺失照原样保留。独立回执 `local/migration72/d02-resume-20261115/launch-timeout/e8-stop-ledger-integration1/completion.private.json` SHA-256 `acab44b42edef1ea9f6dce9b9cf0a19d29ee6aeebba67c9afb107ad76f2ec3ca`。没有真实 resolver 或 PE 测试；原 stage1 双 fresh、pdata、正式审计和独立第三仍未完成，工具与测试是未完成实际源验收的草稿，保持未提交；独立验收只覆盖合成留证合同。
