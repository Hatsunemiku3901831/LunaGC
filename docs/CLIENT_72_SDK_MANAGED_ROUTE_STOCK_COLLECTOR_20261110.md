# AB-STOCK-CURRENT-INPUTS：managed route 当前输入 collector

本批新增 B 的纯 Python 有界 reader/adapter，以及独立 AB 私有 coordinator 与门禁草稿。**只有人工callback、源文件hash和PowerShell静态解析；未执行真实backend/API、实际CheckOnly、GAME_PATH/进程端口查询、客户端、PE执行/提取、native CPU、网络、构建或部署，也没有创建或借用ready。** 当前输出只表示非原子输入候选，不证明SetEnv/GetURL、40108/PostRequest或认证采用，不授予GC pin、replay/pop或任何写域。

## 固定来源与新增文件

唯一合同为 [managed stock输入规格](CLIENT_72_SDK_MANAGED_ROUTE_STOCK_SPEC_20261110.md)，20348B，SHA `606814b70431e5ed208a4f5c71ca87760b5e6bf2c513012b3f525fbe9354ae94`。机器规格SHA `3807982afbdab44f79b24b9a206e8bdbf216c7f8856192adeede7196b1b3b382`、21pin清单SHA `cb5e66330892e6a081f94545df82ed5697f2966c06d69bb7a0bf4d9f83808b5c`、双13884B静态报告SHA `5cc9a55d521174813f21b9c8e21655d9b36e36ee3a0c917f323af4bf47f7c532` 均在新reader中完整固定。

- [B reader](../tools/client72_sdk_managed_route_inputs.py) 实现固定读图、共同final、pending/seal与纯数据重验。
- [B adapter](../tools/observe_client72_sdk_managed_route_inputs.py) 只在本次原hProcess上调用前后identity和source门禁；不新开句柄、查线程或cleanup。
- [reader人工测试](../tools/test_client72_sdk_managed_route_inputs.py) 与 [adapter人工测试](../tools/test_observe_client72_sdk_managed_route_inputs.py) 不构造真实backend。
- 新私有 `ab-stock-implementation/coordinator.private.py` 通过冻结A `run_flow` 的原sample参数明确组合A→B，没有close override或旧monitor改动。

source gate核新五pin和清单21项完整bytes/SHA；只读JSON或流式hash既有metadata/code原件，不执行PE、不unpickle、不导入旧heap。清单中的contextAtRead（包括Project/Harness）不冒充当前gate。B当前两tool实际SHA由独立B_epoch绑定，并在B前后重新核；A五tool映射完全不增加B字段。外层plan绑定两个闭包并集；最终seal/source pins/实际盘点与新ready归root。

## 固定13项与独立final

令B为本次实际EXE base，依次读：`B+586b600/8→S`、`B+5887680/8→KC`、`S+c18/8→C`、`C/8==KC`、`C+58/4`原int32 env、`B+5893d50/8→KU`、`S+b80/8→U`、`U/8==KU`、`U+e0/8→T`、`B+5886040/8→KT`、`T/8==KT`、`T+10/4`原int32 L、条件 `T+14/2L`。

参照/对象0、错类、负/零/不在64..69的L、short/error或uint64地址越界停止扩图。原64位class精确相等，不去tag，不读class描述/父链、不补类；S不按object解释。字符仅对八个已审完整CN base的UTF16LE byte table比较，不解自然UTF16、不规范化、不剥或加`?`、不读NUL或页填充。env只投影0/1/4/6/8/9/22/19，未知int32仍保留非敏感原结构；任一未知使B partial并脱敏。两个已知类别可以真实不一致，仍保留候选与agreement=false，不伪称采用。

成功首读span（包括真实0/错类/未知长度）按原ordinal、原地址、原width复读一次；失败首读不进final。final首错/变化即停，不跟新pointer。首遍≤13/226B、总≤26/452B、单≤138B。失败请求计请求宽度；unknown只导出实际读数与非敏感结构请求bytes，实际敏感bytes总量不导出。

## 敏感输入的统一seal

ordinal9/11/12/13及其地址/ref/长度/width/bytesRead/bytes均只进入不可JSON序列化的 `PendingCapture`；repr固定。read异常不传播内容repr，输出固定错误分类及合法Win32码，不保存自然内容hash。B adapter返回 `PendingObservation`，同样不能直接序列化。

只有精确known、B全first/final不变、B前后identity/source通过、外层最后process alive/stock/source通过及owned cleanup成功，coordinator才调用seal允许实际敏感raw。raw来自真实读回调完整bytes，不从白名单reference补证；summary仍为固定类别。已知raw可通过 `replay(report)` 独立按原序列重新计算图/分类/预算。这里是纯数据重验，没有目标CPU或native replay权限。

任一unknown/absent/empty/错类/短错/漂移/身份或source末核失败，所有敏感项仅导出ordinal/name/phase/UTC、固定结果/错误及redacted=true；无地址/ref/len/width/bytesRead/content/hash/敏感bytes总量。first known而final改未知或失败也走此分支。暂存原件从未写groups JSONL，失败后不用“先写再删”。unknown的 `replay` 仅核原非敏感结构、请求顺序、final原地址/宽度与固定负终态，明确不能重建未知字符或声称完整内容raw。

## AB顺序、生命周期与账

新coordinator复用正常CreateProcessW flags0、45次/90秒module-only metadata、原hThread/hProcess、A第四profile及其final/cleanup；A完整子报告原样保留。只有 `A.endIdentityVerified && A.primaryCloseVerified` 才在原hProcess仍打开时进入B前identity→一次B及其final→B后identity/source。A图partial而身份/cleanup有效时可独立B，overall继续partial；A身份/cleanup失败或B前门禁失败时B为0 RPM。

原A flow随后执行最终process alive与finally；primary使用缓存，不重复native Close，原process独立Close一次。返回后做外层固定stock/source末核，最后才seal B。process close失败也脱敏B；异常不被finally return吞掉。两图各自final，无跨ledger去重或联合atomic声明。

| 独立RPM账 | 首遍上界 | 含各自final | 单读 |
| --- | --- | --- | --- |
| 原A第四profile | 36/926B | 72/1852B | 208B |
| B固定图 | 13/226B | 26/452B | 138B |
| 同正常创建合计 | 49/1152B | 98/2304B | 208B |

A的2TBI/96B、两次thread alive、线程ID/PID/times各1、primary Close1不变。B仅新增两个现有process identity callback；每个继承process Wait/GetExitCode/image/times各1与module Create/First/Close各1、实际M次Next，success按 `14+M_before+M_after` 派生。coordinator从两个完成snapshot的实际items数记录M；这是冻结源码派生计数，不是逐native原件，也没有固定M硬界。

完整人工路径的group数为self_probe1、launch1、image6、creation_time6、status51、sample50、process Close1；原A-only分别为image4/creation4/status49/sample48。metadata/primary/磁盘核验各自另账，不用RPM cap冒称全部API硬上限。

## 新门禁草稿与未验项

独立目录含新run/launch/check-normal-exit/stock-gates脚本和未封plan-template；沿冻结A门禁样式，仅在新目录复用并适配AB profile和ROOT四级深度。正常90秒主流直接调用旧A flow，未复制一套A collector/primary逻辑。CheckOnly在唯一backend构造前返回；人工入口测试mock source gate，不能冒充实际CheckOnly。

未来仍须root核五stock/carrier/13保护/currentAGENTS、portable/version14/.env.local缺席、stock Astrolabe/alias缺席、动态GAME_PATH、进程和固定TCP/UDP，B临时DLL互斥、全source closure、独立第三及完整新问题/离线前置，再封全新plan并请求该轮300秒实时ready。launcher/run/Python重核同runId/planSHA，CreateNew receipts，隐藏UAC，无DLL/文件安装。采完由用户正常退出后核stock/保护/端口；不自动kill/clientexit。旧SDK/B/A ready均不得借用。

本批没有真实托管对象/GC/native证据。当前state不能证明SetEnv/GetURL曾返回、40108/33107采用、可信本服route或认证；A完整20site/12roots、第二allocation/数组身份/GC ownership仍未闭。任何CPU、replay/pop、H+10/this+70或六写域均保持false。

## 实际人工验证与交付

先封17份旧A/helper/B observer源快照，另补同字节旧plan-template供fixture定位；共18份before SHA保持不变。四A profile在完整/首错/final错共12场景完整JSON逐byte及请求序列相同。原A68测试与冻结helper15测试原字节/断言不动。最初111项运行直接加载旧helper test，其人工临时目录位于旧helper下并已自动清理；发现后改为执行新目录before原字节test/helper/template，让其HERE及临时输出全部位于本批目录。先前输出保留，最终两fresh采用隔离后的runner，不称旧目录从未发生临时fixture写。

完整命令为私有 `test-coordinator.private.py`：原83项、B18项、coordinator10项，共**111项**；两fresh人工进程均须exit0，实际argv/UTC/stdout/stderr和SHA保存在新目录。新增覆盖八类/64..69边界、63/70/零/高64位、所有13项first/final短错与漂移、禁止规范化、逐输出递归无secret、known实际raw纯重验与unknown结构重验、B前后source/身份、两种A partial、A失败B0读、最终alive/stock/source/cleanup失败、准确预算和一次cleanup。最大人工图达到98读/2304B/single208；它是自造稀疏bytes，不是target对象或自然采用。

四PS仅Parser.ParseFile，语法通过不代表UAC/CIM/现场门禁通过。实际diff、新source SHA、冻结input pin、两fresh原始输出、source gate、before/after摘要由 `ab-stock-implementation/` 交root审核；本批不执行实际CheckOnly、不创建ready、不自行采集，也不本地Git提交。

## 主线独立离线验收

主线检查实际交付差异、18份旧源码/模板原字节及当前来源闭包，实际独立运行111项全部通过；另建高64位String图，逐项核原请求/宽度/真实whitelist raw、已知env与route不一致、末核失败统一脱敏及全新unknown内容不落盘。独立第三AB使用L=87的原始完整head图，实际98读2050B/最大208B，A五tool与B两tool epoch分离、各自final和一次cleanup成立；B内容从真实人工回调重验，未知只验结构。

审核原件 `local/migration72/d02-resume-20261110/ab-stock-implementation/main-review/`，证明SHA `7ec3c1ed345a6d19bb8432b1c830a6c3aa160f72bdbda50af2224115845f44d3`。第一次审核脚本错误假定旧A source返回有passed键，KeyError前未构造native backend；原脚本保留，改按旧既有metadataCacheHashedOnly字段审核后通过，没有改A实现或来源门禁。

此处关闭离线实现子项。实际CheckOnly、当轮完整source seal、stock/配置/进程端口盘点、用户新ready和自然采集仍需主线按后继门禁执行。HashSet旧卡265B计数纠正为真pdata201B，不改变现有A4或AB读图；第二分配块/Default只作后继提案，本轮上限仍98/2304B。
