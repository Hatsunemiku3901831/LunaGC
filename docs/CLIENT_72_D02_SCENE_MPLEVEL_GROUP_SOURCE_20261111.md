# F-03d3s2 / SCENE-MPLEVEL-GROUP-SOURCE（2026-11-11）

## 中文有界计划

| 项目 | 本卡合同 |
| --- | --- |
| 准确问题 | 已验注册入口 `14aa99dc0..14aa9a4e3` 的 type70954 参数由谁加载或形成？只追其 field350252/+10、`Dictionary<string,string[]>/2671` 的原分组 producer，争取给出唯一有来源的 loader/body 或准确缺输入。 |
| 已排除 | 不重做 slot114 缺8B/实际返回对象审计，不重读模板 wrapper/recycle 职责；不靠登录页触发 World，不按 `MPLevel` 或外部 `ConfigGadget_Scene_World` 名称猜文件路径，不将分组 key 与子名称混为一物。 |
| 固定输入 | Scene runtime 第三报告 SHA `bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4`；唯一允许 metadata pickle（117201003B）SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`；signature（78649549B）SHA `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`。使用前实核长度/完整 SHA，其他既存原件先 hash 后解析。 |
| 方法 | 从 type70954 声明/字段/方法、参数或返回类型精确引用与 generic 实参入手；仅核直接相关 frozen 原 caller、typed 记录和已有指令 bytes。区分声明候选、原直接调用、实际 loader 输入。固定 GitHub/clone/README 结论直接复用，不新检索。 |
| 预算 | 至多一轮全量 metadata/signature 纯数据扫描；既存 frozen 代码只按直接引用选定，不扫 PE 或任意邻窗。先做一个准确 producer 问题，不追 helper 树。若唯一入口已定，只交一个 entry/caller/typed/AOT/pdata 有界取证建议，交 root 另批提取。 |
| 写权 | 仅本页与 `local/migration72/d02-resume-20261110b/scene-mplevel-group-source/` 的中文计划、小 audit、source 清单和报告；Project/Harness/AGENTS/既存 tools 均只读，root 负责共享状态与 Git。 |
| 验收 | 每结论具固定来源、准确记录/index/site；source 级小审计两 fresh 新进程报告逐字节一致，给 root 第三命令。只关闭来源链或准确缺口，不升级配置能力、完整 Scene 或 D-02。 |
| 禁止 | 0 新 GAME_PATH/PE/code/AOT/pdata/xdata/literal 原采集；0 CPU/native/emulator/live/REA/进程/调试/补丁/网络/Git/build/DB/部署；不加载未知 pickle，不招募。 |
| 停止与恢复 | 来源不能唯一绑定时，明确已排除方法和唯一缺原输入后立即交回；安全/Daybreak 问题记录停包，不换工具/模型重试。root 审核第三后决定最小新窗或数据提取，本卡不自行执行。 |

## 结果：上游静态来源已收敛到同一实例 producer

**本卡完成来源定位子出口，唯一下一代码入口是 type70954.method587454 / `14b6accb0`。** 已存同步与另一带 callback 的 loader 都把准确的 type70954 receiver、同一输入 reader 交此实例方法，再把同对象交原注册方法。上游输入来自目标 `ConfigMetaConfig.gadgetPathDataPathHashes` 的 `PathHash.bin`，不是由 `MPLevel` 或外部同名 JSON 路径推导。

这关闭“没有合法 World 触发就没有上游研究入口”的宽缺口；没有恢复原分组内容或证明这些 loader 自然执行。method587454 的原 body 在本卡选定的两个冻结报告中均未供给，其内部对 field350252 的实际写入和格式仍未验。本卡只交这一窗建议，不自行取得原 PE、数据或第二窗。

## 固定来源与准确链

正式审计固定四份完整原件，均先核字节数和 SHA；只用禁止全局类加载的 `PureData` Unpickler 解析已指定的 metadata。前三份为计划中的固定输入；新增必要原件为：

| 来源 | 字节数 / SHA-256 | 用途及限制 |
| --- | --- | --- |
| `local/migration72/d02-active/scene-runtime-context-astra/config-owner-static.json` | 309852 / `dab9cfcc1a713475b1102fb53ea7cfdfeb7a43c83e8480a358aab286462e0e1d` | 已存31个方法的 asm、extent、recorded code SHA 和 typed refs；保留其原 provenance，不宣称本轮重新核过原机器码或 E8。 |

四原件的五文件 provenance 相符，signature 的三文件身份也与其一致；这不是重新验证当前 GAME_PATH 或当前现场。实际扫描覆盖89748个类型、445462个已解析字段、326354个带参数方法和44140个参数类型引用；返回类型、generic method substitution 和未保存调用仍不在此扫描覆盖内。

| 层次 | 准确来源和已证静态关系 |
| --- | --- |
| 类型直接引用 | type70954 只有 field350252/+10，类型为2671 `Dictionary<string,string[]>`。参数签名中唯一直接接受70954的是已验17586.method133167/register；唯一已解析持有字段是17595.field83197/+10。持有字段本身不证明它写入或生产了数据。 |
| 输入根 | 同步 loader `17586.method133164 / 14aa99a50` 在 `14aa99bb7` 取 GLOBAL+C8E0。metadata 独立绑定为23071.field108683、type77121 `MoleMole.Config.ConfigMetaConfig`。 |
| 原数组 | `14aa99bc7` 取该对象+50；metadata 为77121.field386052 `gadgetPathDataPathHashes : PathHash77122[]`。这是有目标字段及真实 load 的来源，不只依据英文名称。 |
| 精确选中半部 | 原 `14aa99be2` 指向数组+28，`14aa99bfb` 取u64，每轮 `14aa99c61` 加10。77122为值类型，boxed布局+10为origin、+18为bin；结合原数组payload+20及16B stride，选中的是**每项第二QWORD/bin**，不能改用origin或截为32位。 |
| reader 输入 | `14aa99c0d` 将同bin u64送RCX，EDX/R8D分别清0，`14aa99c15→14d6f9210`。该callee准确为60257.method500134，签名为 `(uint64,int32,int32)`；返回类型未恢复。RAX被下述准确reader参数接收，故其reader角色来自consumer typed关系，不冒称恢复了声明返回类型。 |
| 70954 producer | 同步窗 `14aa99bfe` 的type usage cell145901940独立绑定70954；原allocation call后receiver保存在RSI。`14aa99c2b RCX=RSI`、`14aa99c2e RDX=上项RAX`，`14aa99c31` 直接调用70954.method587454 `CNLIBMNOALB / 14b6accb0`。它是普通实例方法、非虚slot，显式参数只有42005 reader；不使用候选vtable作为动态证明。 |
| 同对象进入注册 | receiver先存于 `[rbp-18]`；producer返回后，`14aa99c46` 从同slot取到RCX，`14aa99c4a` 调已验register。原类初始化冷分支也回到同一site；未跨异常断言此顺序必然完成。 |

另一已存分支 `14aa99530→14aa9a530` 独立选择同数组+28/stride10，在 `14aa9a57c` 以原u64/0/0调用同reader入口；在 `14aa9a66f` 调同587454，再于 `14aa9a6df` 把同对象交register。它带调度和callback分支，本卡不解释其成功执行或线程行为；两个已存路径只是共同收敛到**同一个实例 producer**，无需申请两棵loader树。

## 单一下一原窗建议，交 root 另批

直接入场价值：此入口处在原 World `MPLevel` 配置注册的上游，能把“缺分组输入”变成具体的字段形成/reader格式问题。它比重新读register或模板wrapper更直接；尚不足以给Java能力或sender规格。

| 项目 | 唯一建议 |
| --- | --- |
| 目标 | owner70954 / method587454 `CNLIBMNOALB`，普通登记 entry `14b6accb0`，flags83、非static、slotFFFF；`this70954 + reader42005`，返回类型unknown。 |
| 原 caller | 同步 `14aa99a50..14aa99d81`，817B/188条，recorded code SHA `991c1e787c57e6268491098762c259c502ce01923796d47959bfa95ca7091569`；准确setup和callsite为c2b/c2e/c31。旧原件只有asm和记录的hash，故fresh collector必须先独立核完整caller原bytes及E8，不能将本卡计算的预期E8当旧原bytes。 |
| 提取前置 | root另批后，fresh固定五输入、准确metadata/signature、完整caller SHA、方法owner/index/实例参数、普通AOT表原cell和method587454 slot原pointer都一致，才按此entry核唯一exact/containing PE exception记录；两者必须同一begin/end。 |
| 预算 | 仅1个新语义body，完整extent≤2048B、连续反汇编≤512条；原caller核对817B、目标pdata12B及普通AOT两个QWORD16B。以上distinct证据payload上界2893B，不把全文件hash或已有PE目录索引遍历伪装成只读2893B。实际pdata/end本卡未知，禁止按下一个registeredAddress猜extent。 |
| 必须停止 | 身份漂移、没有唯一pdata、body超限或decode不完整立即留失败；0 xdata/新子callee/native/live。交付本body的原bytes/asm/SHA、typed/RIP来源与准确首个未审callee，随后返回root，不自动追下一body。 |

私有请求为 `local/migration72/d02-resume-20261110b/scene-mplevel-group-source/one-window-request.private.json`，SHA `f0cc4c1e5736069c689511c447243548ec226be0abc24a3a1f1dc56f360e3d48`。本卡没有执行该建议，也没有将普通registeredAddress作为动态命中。

## 双 fresh、第三复现和边界

仅用标准库的小审计 `audit_group_source.private.py` 为13962B，SHA `241f86f420525236fa1a58b4aa7d871c72fba92761622468f7ca9dcbff506d6d`。两个独立fresh进程各通过4来源门禁、35项source静态断言；报告各51096B，完整bytes相同，SHA **`d45198fa2cc6919ecb2c5f4f84c6264e214048d4169abf31c8e7b32ea1bbb3e2`**。`source-list.private.json` 和 `determinism.private.json` 保存范围及比对；不是native、Java或客户端测试。

root第三命令只读四冻结输入，不依赖GAME_PATH/PE/进程/现场：

```powershell
python -B local/migration72/d02-resume-20261110b/scene-mplevel-group-source/audit_group_source.private.py --output local/migration72/d02-resume-20261110b/scene-mplevel-group-source/main-third.private.json
```

开发首轮误填同步窗指令数190，实际188，静态门禁真实拒绝；错误脚本及log保留。本轮即时说明中曾把PathHash选中半部说成第一QWORD，已核原+28/stride10和字段布局，纠正为bin；初算caller长度813也已由准确end-start纠正为817。原件未改，最终断言未放宽。两个Windows `rg` glob写法错误只产生路径诊断，没有采集或修改输入。

仍缺自然 `ConfigMetaConfig` 对象与实际bin key数值、相应目标payload、含MPLevel的完整group key/string[]、587454字段形成逻辑及后续真实配置。这里列出输入缺口，不批准以null/空数组/外部同名文件替代。signature返回类型和generic substitution未恢复，不能据本次精确扫描说所有producer都已穷尽。

fixed GitHub/README/clone成果直接复用 [增量结论](CLIENT_72_D02_SCENE_GITHUB_INCREMENT_20261111.md)，没有新网络/clone或加载上游代码。`MPLevel`能力内容、actual backend/mode、GC/动态slot、组件创建替换拆除通知、完整Scene/Avatar/resourceReady/D-02均保持未验，不把未映射字段一律升P0。

本卡只新增本专题和独占private文件；0共享源码/工具/资源/Proto/配置/patch变化，0 Git/build/DB/部署/客户端操作，未遇安全或Daybreak门禁。仅离线研究无需重启；root负责第三审核、Project/Harness、必要交接及本地提交。已得到单一可执行源窗建议，max即交回，不再扩树。

## 主线验收增量

root第三进程实际通过4pin/35断言，`main-third.private.json`51096B / SHA-256 `d45198fa2cc6919ecb2c5f4f84c6264e214048d4169abf31c8e7b32ea1bbb3e2`与双fresh相同。root已采纳唯一m587454完整原窗请求，预算及停止条件保持，不批准新callee、目标执行或原bin/group数据的猜测。主线展示时曾读取错误的建议文件名，仅文件不存在，随即按本页准确JSON路径读取；未采集或修改原件。
