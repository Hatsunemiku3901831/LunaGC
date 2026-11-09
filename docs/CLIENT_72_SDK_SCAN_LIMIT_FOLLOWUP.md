# R2：自然 unit-scan-limit 的输入后继与有界前缀规格

本项承接[旧 event5 首诊](CLIENT_72_SDK_EARLY_CLASSIFICATION_AUDIT.md)及[独立 split 产物](CLIENT_72_SDK_EARLY_SPLIT_OBSERVER.md)，归属 `R2-CONFIGSYNC-EARLY-BRIDGE`、`R2-ROUTE-CONFIG-SELECT`。**新自然事件确认为11，不能由旧三字段夹具代表完整 LocalConfigSync 输入；完整 producer 还把 LanguageModel 对象的序列化字符串写入 language。** 固定源的正反投影已证明，这个被遗漏的输入足以让合法候选耗尽原扫描预算。自然调用是否正好在 language 内耗尽仍未观察，不能宣称已解释自然正文、长度或 NUL。

本轮沿用 Sol max 做限定离线分析，没有调用 Astra、派遣子代理或重复 GitHub 检索；主线已有 Sol max/Terra max 的分支来源检索在途。没有改旧 early/split 源、accepted epoch 的五源/55pin、Cargo、共享索引或协议。只新增本页、`tools/client72_sdk_scan_limit_followup.py`、对应测试及本卡私有目录。无 Git 写入、构建、JVM、DB、进程/端口/target/客户端/REA/安装部署操作。本卡只完成该首诊和实施规格，父项/D-02 不晋级。

## 新自然事实的来源绑定

共同根为 `local/migration72/d02-parallel-20261109/b-sdk-split/`：

| 冻结来源 | SHA-256 / 审核范围 |
| --- | --- |
| `live3.private.json` | `d2fbef9b4e9598da983ba7e5a3ad2d50d91b09531717e4ad7017ceea39638815`；live→accepted/source/adapter/artifact/fixture/manifest join |
| 同名 `.reads.private.json` | `e07e9e64d5929a4cee1fa29e063403d3c0c7aabbf616512e15b74835bd4d2b14`；同次9读/1450B，前后72B相同，event11、两个事实位1、env/等值位0 |
| `owned-main1/proof.private.json` | `6ddcbb41a82fd63174b9db70a96bb0fe83dfcb8b700e7afcdd9f9789be1a79ae`；55冻结pin、五个新源、buildInputs、两fresh各23例实际ABI |
| `main-owned-review.private.json` | `c53422f80ae8ad0f8033b2a5039fa1372e41e9788b05bd4526d2ba76cfe96499`；原15项主线owned审核 |
| 实际DLL / fixture | `946082a0c8bb29277ae29627d7791be9852b6d7f29fb120c36cb54f3e56a7f1a` / `7f07ad5e122bb994d4bc41914ff9724642048eeb5e0cf1db7d9e86349ea6c176`；与live安装身份、accepted和磁盘产物相等 |
| 实际 adapter | `bf9267fe9d305b0f0388690c30401391ee07d031e9abd4007e7465ab30a717bc`；与live sourceSHA相等 |
| `live3-restored.private.json` | `0a0ffc75eda58a001facbcc4738950c937c1de69cb8ba839064b07e272f65cdb`；只复核已冻结的客户端不存在/自有alias已移除摘要，不执行新现场检查 |

新工具先核这些固定输入，再核55pin、五源、实际产物/fixture、accepted/build及同次账本。新自然样本45次、终态11；完整284B trampoline、callback及返回+20的既有比对成立。原两fresh23例ABI仅复核历史结果，本次没有重执行Rust、原Marshal或目标机器码。

当前accepted manifest为 `3ea4ff0853353f3070b818935c20e6e56b21219f64bea983e4aec8906ed8dd65`。在本卡目录新增逐byte相同的 `accepted-cargo-manifest.private.toml`，新审计用此不可变副本绑定原buildInputs，不要求未来主线追加example后当前Cargo仍为旧值；原proof中的path/SHA不变。旧manifest、旧5报告及新split五源/55pin均未重写。

新11只证明准确caller下32768个UTF-16单元的扫描预算内没有遇NUL。本次未进入strict解码或JSON classifier，因此12/JSON形状/完整配置/原Marshal返回均不能由此推出。旧event5仍属于旧epoch，不反填成11。

## 被遗漏的 language 输入

复用完整 `LocalConfigSync` 原窗 `19806d70..19807e2e`（SHA `27138e600927d7df25c9b727cdb3650bf6228c480795b0c5b63e3f63a5e91562`）及固定metadata：

- `19807756/59/5c` 仍是ConfigModel+58的env和true→LoadLocalConfig；其+`b0` URL模型经 `140d35430`，在 `198077af` 写入outer的url。
- `198077ef` 从ConfigModel+80取language选择串，`198077f6`调用method32769 `LanguageManager.GetLanguageModel`。
- `19807800` 将返回对象交同一 `140d35430`，`1980782b`把返回string装进JSON string节点，`1980783c`写入outer的language。
- `19807933`调用JSONObject writer，`1980793c`调用StringBuilder.ToString。language是序列化对象的字符串输入；它不等于一个短语言代码。

固定类型4127 `MiHoYo.SDK.LanguageModel` 包含一个静态Instance及 **695个instance System.String字段**。它与ConfigModel+80的选择串分开。旧env/app_key/url三字段夹具没有包含这个真实输入，因此小夹具分类成功不能外推自然完整payload一定落在同样预算内。

这只确定原静态producer和字段来源。JSONObject实际枚举顺序、`140d35430`的完整字段覆盖、自然LanguageManager分支/语言/替换结果没有被本卡代为执行；后继观察也不把静态插入顺序当成自然顺序保证。

## 批准的两个完整新窗

主线封存 `b-sdk-limit11/main-windows1/manifest.private.json`，SHA `d6e228325cc80a421b3df0ed9738d3c6af9e217e9fbace1e1118191b9075f696`。工具重新绑定固定metadata中的methodDefinition和registeredAddress，核原12B `.pdata`、完整bin/bytesHex/SHA及逐指令extent。没有按请求label认定语义，未追任何直接callee。

| 实际函数 / 范围 | bytes / SHA-256 | 准确结论与未验 |
| --- | --- | --- |
| LanguageManager.GetLanguageModel / method32769 / `199d1160..199d12a6` | 326 / `6439f1c2ff6c162e40a1d3c340b9e1611ecf5f4f3c7fc862cc382cd2f8e1d573` | 调method32772 GetLanguageString，非空则经parse及method31963 ReplaceChar，另一分支调method32767 LoadLocalLanguage；写/返回共同静态+b60的LanguageModel。自然分支、具体选择及callee完整行为未知。 |
| StringBuilder.ToString / method4408 / `19b94f30..19b95072` | 322 / `c5ed31841ab88534074f12f47cdd5b22d17d6f567748a7f5af058783e69be067` | chunkLength+chunkOffset形成逻辑长度；分配请求含字符双字节长度、额外2B及+14头；`19b94fa0`写String+10，`19b94fa6`取+14目的区，逐chunk拷贝并返回新String。 |

合计648B，低于批准的2窗/4096B预算。ToString与旧Marshal `+14/u16`、metadata field1211/1212一致，支持预期的参数表示；**额外预留2B不等于已证明NUL写入**。本窗没有显式终止写，allocator的清零/终止行为、完整Marshal/GC生命周期仍未知，不继续追callee来填默认成功。本轮没有读任何自然header或length；String.get_Length也没有扩窗。

## 固定资源与 owned 正反

复用固定route报告 `bae776a1a43c678b2953e69d71b6f198f0418cb966f275b0f8a9b8200bee1471`、原pure serialized parser，核三份既有decoded artifact逐byte相同、原server_pc container/class49全文extent一致。没有读当前GAME_PATH或重新解包/执行codec，没有记录配置/decoded/key/URL原文的hash。

同一CAB中存在同名的 `zh-cn` 和 `zh-tw` TextAsset；新解析按完整container key及pathID/fileID关联，分别得到：

- `assets/plugins/mihoyosdk/resources/language/zh-cn.json`、`language/zh-tw.json`；
- `assets/plugins/mihoyosdk/resources/region/zh-cn.json`、`region/zh-tw.json`。

不能按basename把region当成LanguageModel来源。language资源只是现有包内候选；GetLanguageModel可能走缓存/更新文本，LoadLocalLanguage完整producer尚未在本卡展开，不能宣称自然已选这个TextAsset。

新工具在内存建立8个CN候选的24项对照，不保存生成的JSON、app_key、URL或它们的长度/hash：

| 同一CN源url/app_key的投影 | 原unit预算结果 |
| --- | --- |
| env/app_key/url三字段，无language | 8个候选全部可在原预算内遇到自有NUL |
| 加入695个LanguageModel字段名，全部值改为自有空串，嵌套成language:string | 8个候选全部耗尽unit预算 |
| 加入同一字段模型，值从精确language/zh-cn候选投影，未出现字段保持模型null | 8个候选全部耗尽unit预算 |

因此固定URL单项不是上述模型越界的充分解释；加入真实language结构已经足够构造合法超预算输入，无需大token、错误指针或无NUL。**这些是source-only投影与自有值实验，不是自然payload，也不是完整SDK serializer执行。** 695个字段是否全部被实际serializer输出、自然值/语言及其具体大小仍未知。

13个owned前缀正反证明：必要字段完整在language之前且language quoted value跨预算时，可以仅从已复制内容取得它们；language先于url、url中途截断、引号内伪key、重复key、错误env/shape、非法前缀或已关闭language而后续字段超限均不会得到该事实。reference不匹配保持false。有效/非法未观察后缀可有相同已复制前缀，故任何正例均明确保留完整payload/NUL/Init未知。

12项定向测试通过，包括新raw前后状态变动拒绝、source/ABI/两完整窗、同名asset隔离、24个源投影、quoted/escape边界、前缀UTF8限额和不泄漏输入。它们是Python owned模型和静态来源核验；没有新增Rust ABI或原EXE执行。

## 准确首缺与交Sol xhigh的最小规格

自然首缺已从“11还是12”变为：**本次已复制的UTF16前缀中，必要env/app_key/url是否都完整出现，且预算末端是否仍落在其后的顶层language quoted value内。** 当前账本没有这些事实。既有source投影支持该候选，但不能代答自然调用。直接提高32768、读managed length、改chars偏移或清once都不能回答这个问题。

建议新增独立prefix observer/example/verifier/adapter，冻结现有early/split源、五源/55pin、manifest/artifact/proof及旧报告。新产物复用旧core、loader、References/classify和完整ABI harness，使用独立导出名和新的accepted epoch；旧split decoder仍按原11/12语义重放。主线统一处理Cargo/构建/提交，不回填旧SHA。

唯一新路径只在原copy已经达到11时消费现有owned `Vec<u16>`，不增加target读取：

1. 将copy结果表示为“完整String”或“原unit限额处的已有units”，避免先drop再读取或clone第二份自然buffer。完整String、所有旧失败及12的行为保持。
2. 在已复制units上解析顶层对象的**完整标量key/value和真实分隔符**；按JSON引号/escape边界处理，禁止字符串搜索伪key。前缀中的重复key、非法token、嵌套的非标量outer值或未完成必要值停止。本地单位/位置只在回调内使用，不发布计数。
3. 只有env/app_key/url三个完整值已见到，再遇到真实顶层language键、opening quote，而且这个quoted value的词法状态仍跨越owned副本末端，才有新事实。不要猜JSONObject枚举顺序；不满足这个顺序就保留11。
4. 仅对已消费的必要前缀做strict UTF16→UTF8，保持65535B限额；language尾部不转换成新的大UTF8正文，不修补尾部surrogate/escape。完整所选值继续按原CN env、app_key、url:string→comboLogin reference规格比较。不得添加默认env、补JSON闭括号/NUL或把投影交给真实SDK。
5. 只有该前缀事实成立且必要字段形状/允许env成立，发布新 **event13 `managed-prefix-fields-before-language-scan-limit`**；保留72B DATA/event offset56，在现有env/两bool位输出允许env和等值结果。新decoder为13固定附加 `completePayloadValidated=false`、`payloadTerminatorObserved=false`、`originalMarshalObserved=false`、`naturalInitOrRoutePassed=false`。它不是旧event2的完整managed-input分类成功。
6. 任何前缀不完整/不合形状/未知env/预算失败都仍为11，分类位为0；不新增自然错误正文、key/URL/长度/hash/指针/实际复制次数或动态字段名。11/12及其它旧事件保持原验证约束，旧5继续只属历史epoch。

prefix解析器可复用serde解析**独立的完整标量值**及已完成的url嵌套字符串；不能为了使用整对象parser而给原前缀补`}`，也不能把prefix事实叫完整JSON有效。只把所选完整值组成自有观察投影时，应清楚命名其范围，不能当作原payload原文或writer成功证据。本页Python函数是独立oracle，不是待直接复制为Rust的完整serde替代实现。

新自有ABI夹具先保留原23case，另覆盖：合法完整输入在language内跨cap→13；same-prefix无终止/非法未观察后缀→同13但完整有效性false；language先于url/截断url/引号内伪key/重复key/非法前缀/未知env/错误嵌套shape→11；key/URL不匹配→13且相应bool false；已消费前缀UTF8超65535→11；language末尾在copy边界截断escape或surrogate时，不修补/不宣称language有效。每例继续全GPR/XMM/RFLAGS/栈/LastError、原调用一次、高64返回、源对象不变、真实owned读取轨迹、终态零追加读及drop恢复。

## 下一自然实验的scope、验收与停止

本卡不安装新observer。主线完成新独立实现、source gate、实际构建、两fresh完整owned、独立第三审核、51准确导出/72B DATA/完整284B搬移及新accepted/source/artifact join后，才准备新scope/备份恢复/新实时ready。源码/产物/输入漂移即拒绝，不复用live3的ready、PID、base或输出。

自然预算保持原90秒monitor、一次准确caller尝试；name最多13次2B、payload最多32768次2B，总同步cap32781读/65562B，另原stack return8B。外部collector仍只读code与自有DATA，最多9读/1450B/单次512B。prefix分析仅在已拥有副本内，无新RPM、无header/length读取、无参数修改或新hook点，不清once、不重arm。

新13只能验收“必要managed前缀字段及language预算位置”的限定事实，能够补自然env/reference输入；原Marshal、SDK export/writer返回、完整Init、实际route/认证仍各自未验。新11是有效负结果，表示未满足该精确前缀合同，不自动解释为某个字段或错误表示；保留后停止，不同预算重启、不扩读。12、读取失败、状态/代码/ABI不等或未知事件也按首失败停止。完成后仍由主线正常退出、恢复stock/删除仅自有alias并复核五stock/13保护及归属现场。

如果后继需要证明完整serializer覆盖或自然SDK执行，应另固定 `140d35430`、LoadLocalLanguage等准确直接callee或原native参数/返回事件的合同；本卡2窗预算已用完，不自行扩展或以默认locale/allocator成功填补它们。

```powershell
python -B -m tools.client72_sdk_scan_limit_followup --output local/migration72/d02-parallel-20261109/b-sdk-limit11/final-first.private.json
python -B -m unittest tools.test_client72_sdk_scan_limit_followup -v
```

仅离线工具/测试/文档，不需便携重启。主线维护Project/Harness、审核本地提交。首次工具在输出前因manifest地址采用十六进制字符串、实现直接用int比较而拒绝，已保留 `first-schema-failure.private.json`；修正只在窗口入口转换其表示，没有改bin、source pin、extent或比较标准。
