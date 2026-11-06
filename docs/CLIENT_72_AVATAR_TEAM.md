# 7.2 Avatar Team / current-Team 消费证据

任务 `F-03d3f3 / D02-R1`。本卡提交独立字段语义证据供主线审核，不修改 mappings、旧 Proto、sender、包号或 ready。`runtimeStatus=not-tested`、`targetProtocolReady=false`；本机客户端入场、完整 Avatar handler、持久化和场景效果均未验。

## 固定来源与候选解释

每个正式进程从当前 `tools/game_path.ps1` 的结果重读三输入，使用 `extract_client72.HASHES`：

| 输入 | SHA-256 |
| --- | --- |
| YuanShen.exe | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| global-metadata.dat | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| startup-metadata.dat | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |

另固定 e8 `avatar-complete-raw-observations.json` 的 `37c90e4dea9fccc4ac20ce68616612250b5a57169e564081c33dc69a25def837`、f1 `avatar-contract-observations.json` 的 `a54f62de9a803e2070c515ecc854488827ea6c62db4468c633190d6733f2b2f3`、f2a `avatar-control-observations.json` 的 `8e2433b3d13ec17414cc26f21461f1cc62f64b16c413b327610ce0b7e7480ec0`。复用 f1 已验 command/typed reader/Avatar GUID 字典和身份片段，不重复重建底层，也不借用旧 Proto 名称。

初始问题为 root `30509` tag5/+0x28 的 map 是否表示 Team、child `26081` tag12/+0x18 的 uint64 列表是否为 Avatar GUID、tag2/+0x20 是否为队伍名称，以及 root tag12/+0x60 是否选择这个 map。仅凭 getter 或同形存储不足以证明；本卡追加具名事件、实际写入和实际消费三条来源。

## 四个字段关系

| raw 字段 | 本卡证明的关系 | 具体客户端证据 |
| --- | --- | --- |
| 30509 tag5 `CNCCIILAJNJ` | uint32 Team ID → Team 描述 | typed handler `1507cee50` 从 +0x28 逐项取 key；同一个 key 分别进入 `151a9d430` 名称写入与 `151a9ea20` 成员写入 |
| 26081 tag2 `MGIDEJKCAKD` | Team 名称 | `1507cf203` 读取 child+0x20，进入 manager39488 的 +0x78 `Dictionary<uint32,string>`；真实构造事件 363，固定 enum75464 的名称为 `TeamNameRefresh` |
| 26081 tag12 `PIDCNPCLBCE` | 有序 Team 成员 Avatar GUID 列表 | `1507cf20f` 读取 child+0x18，逐个完整 qword 交给 `151a9ea20`，同 Team 内去重写入 +0x50 `Dictionary<uint32,List<uint64>>`；`151a9ec40` 从当前 Team 列表取 GUID，调用实际 Avatar manager `14e9b2c60` / 字典查找 `156f6f510` |
| 30509 tag12 `OCIKDBJPOAJ` | current Team ID | root `1507cf61d` 读 +0x60，`1507cf621` 写 manager+0x124；`151a9bd20` 用该值选择 +0x50；同一存储的 setter `151a9e080` 只在值改变时构造 `CurrentTeamChanged=364` |

这些是待主线独立审核的关系，不宣称恢复了被混淆字段的原始英文名称。Team ID 是 map key，不是额外 message 字段。root tag3 仍沿用 f2a 的独立 Hero GUID 来源；本卡非空 fixture 让 Hero 与当前 Team 的唯一成员不同，仅说明所跑前缀未强制二者一致，不证明这种状态的游戏业务合法性。

## 执行范围与模型边界

1. 原生 dispatcher `1507cc240` → typed handler `1507cee50`，执行 Avatar 构造/索引和完整 Team map 循环，到 `1507cf4a1` **之前**停止。后面 Rename 等处理没有执行。
2. root tag12 的赋值单独执行 `1507cf606` → `1507cf627`，显式 live-in `r15=实际 reader 产出的 root`。它不是恢复完整 handler 的堆栈，不声称跨过未执行段。这个赋值片段不发 CurrentTeamChanged；该具名事件由独立 setter 测试证明。
3. Team name/get/contains、current setter/getter 运行到返回。成员消费 `151a9ec40` 对缺项实际循环跳过；遇到真实 Avatar 模型时，在 `151a9ed62` 读取 encoded model+0x2b8 **之前**停止。其后资源/派生特征含义及效果未知。

管理对象最初的 Team map 为空，current Team 明确设为输入 map 不含的另一个 key；+0x80 fallback、+0x88 override 和派生列表为空。这样 root 中成员 append 的实际尾调用走真实空 current-list 分支，未把未知的派生逻辑设成成功。root 完成后才用实际选择字段和 setter 驱动非空成员消费。

managed Dictionary 查找位置/插入、IEnumerator、对象/数组分配、Array.Copy、字符串解码和 event bus 投递为显式模型。模型消费原生 reader 产物，不填入预期最终 Team/成员结果。Team 创建、名称路由、去重、容量选择、qword 写入、当前 Team 选择、GUID 字典查询、事件值构造为实际客户端机器码。完整 managed hash-table、event subscribers、IFix 热更分支和客户端生命周期不在验收范围。

`List<uint64>` 的具体 AOT 从 owner1255 / classInstance58 重提取：`AddWithResize=157ee1fe0`。初次容量4，随后8/16/32/64由原生代码决定；实际追加位于 `151a9eb63` 或 `157ee208a`，宽度均8字节。64是本工具明确支持的 backing fixture 范围，不能解读为客户端队伍人数上限。

## 非空、错误与零值

13组独立构造 wire 覆盖两个非空 Team、跨 Team 共享成员、同 low32 不同 high32 GUID、>2^53 / bit63 / uint64最大值、uint32最大 Team ID、切换/重复切换、缺 Team、缺成员后出现有效成员、完全没有已拥有 Avatar、空 Team 与 Unicode/NUL 名称、重复 map key、packed/unpacked 混合、显式零覆盖、9和64成员扩容。

重复 map key 先由 reader 替换整个 Team message；单个 Team 内重复 GUID 再由原生 manager 去重，保留第一次出现顺序。两个 Team 可以在本片段中各自保存相同 GUID。

Team ID=0 可保存并被选中；成员 GUID=0 可进入 Team 列表并去重。Avatar 注册 `14e9b2a80` 的 `14e9b2aad/ab0` 在 GUID=0 时跳过字典登记，因此后续实际 GUID lookup 为 miss，即使 root Avatar 输入有 GUID0，也不会在本索引中解析到实例。这不是 Team reader 拒绝0，也不是 lookup 入口对0的特判。

错误覆盖负 map/name 长度、截断 map/packed、非法 tag、GUID overflow、递归限制、65成员 fixture 范围和 null typed input。报告分别标注真实 native error-helper 入口与显式输入模型/容量拒绝；没有把模型异常当成完整客户端异常执行。

## 复现、失败与剩余工作

只运行离线工具与定向 Python，无 Gradle、JAR、部署、数据库或客户端状态修改。

```powershell
$teamGamePath = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_avatar_team.py --client $teamGamePath --output local/migration72/evidence/avatar-team-native.json --observations protocol/cn-7.2/avatar-team-observations.json
python tools/client72_avatar_team.py --client $teamGamePath --output local/migration72/evidence/avatar-team-native2.json --observations local/migration72/evidence/avatar-team-summary2.json
python -m unittest tools.test_client72_avatar_team tools.test_client72_avatar_control tools.test_client72_avatar_contract tools.test_client72_avatar_complete_raw
```

实际结果：13组独立 wire、42次选择/消费组合、9拒绝；10个新增定向测试及 f1/f2a/e8 关联共35项通过。两次全新 Python 进程各自重读三 hash、重提取 metadata/AOT 后，在离线 Unicorn 中真实执行目标机器码；完整报告和脱敏摘要逐字节相同。

主线独立审核完成：复跑35项定向测试，并另用第三个全新进程执行完整原生回放；报告与下表两次结果逐字节相同。审核确认关键原生函数未落入模型、Team事件数值具名来源、GUID0注册跳过与后续查询miss的区别，以及当前Team赋值片段的显式live-in边界。四个限定消费关系可用于后续隔离producer验证；统一mapping/review与生产发送仍另审，不修改已冻结摘要来追加批准标记。原始证据为`evidence/avatar-team-main-{native,summary,review}.json`和`logs/avatar-team-main-{tests,native}.log`，均在忽略的`local/migration72/`下。

| 产物 | SHA-256 | 字节数 |
| --- | --- | --- |
| `local/migration72/evidence/avatar-team-native{,2}.json` | `e51672959d0d7e069ea48e4326dcd0a8e580526eba00e43e1b657f5dff9eceb9` | 1,563,906 |
| `protocol/cn-7.2/avatar-team-observations.json` / `local/migration72/evidence/avatar-team-summary2.json` | `3a08503225ecbb198a7cc6340b203cc6ce76dd7c14b5619da845b35e74993073` | 249,163 |

比对记录 `local/migration72/evidence/avatar-team-determinism.json`；运行日志 `logs/avatar-team-native{1,2}.log` 与 `logs/avatar-team-tests.log` 均在 `local/migration72/` 下。原始探索/失败也保留在该忽略目录：`analysis/avatar-team-native-*.txt`、`analysis/avatar-team-list-aot.json`、`logs/avatar-team-*.log`。首次指令断言把 append 寄存器抄错，`avatar-team-formal-probe1.log` 在宣告阶段拒绝；随后 GUID0 被误设成可索引成员，`avatar-team-formal-probe{2,3}.log` 与 `evidence/avatar-team-native-probe3.failure.json` 保留失败。最终原因由独立重新提取注册函数确认，没有改共享 runtime 去满足错误预期。测试命令首次拼错 e8 模块名的失败也保存在 `avatar-team-tests-probe.log`，修正后35项通过。

剩余：root tags4/9/11/13/14/15 的业务与入场必要性、Team 保存来源/后端、非空 fallback/override 来源、已有 Team 状态下再次通知的合并业务、Hero 与 Team 成员/活动实体关系、encoded Avatar+0x2b8 后续资源特征。任何 minimal producer 只能采用主线已经审核的字段；这张卡不批准空值代替未知字段、不批准 sender、完整图语义或 D02Ready。

最小 producer 仍以 f1 已审5字段为基础：root tag6 Avatar 列表、AvatarInfo tag1 ID/tag2 GUID/tag5 装备GUID/tag3正常属性；装备GUID必须与 Store 同一物品实例关联。本卡四个候选字段可在主线审核后进入隔离 producer 的 Team 一致性验证。AvatarInfo 其余33字段、完整模型初始化尾部和实际入场必需集合尚未批准；不能把 e8/g1 的65类372wire闭包或此处 Team 成功当成生产发送许可。主线 f2b 独立场景消息对 scene+0x284 的写入合同也不反向证明 root tag3 为当前控制GUID。
