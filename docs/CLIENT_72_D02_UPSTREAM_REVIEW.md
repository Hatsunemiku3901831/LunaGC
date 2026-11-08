# E-01 / R3-UPSTREAM-D02 与 R2-PATCH-DELTA：定向外部审计

2026-11-09；仅静态比较和离线文件断言，未执行上游脚本、安装 hook、运行客户端或服务。结论：所审输入尚无可直接合入、足以解除 D-02 首缺的增量；已有同一行或只改表示不计新增。上游资源是候选证据，不是国服 7.2 原生 revision 或运行时验收。

## 固定来源与许可

| 来源 | 实测远端默认分支 HEAD / 固定 commit | 根 tree | 许可及使用边界 |
| --- | --- | --- | --- |
| [AstaPS-Resource](https://github.com/MeChen618/AstaPS-Resource/tree/6cfecfb55f889f8e3b7156adb12809608db4f74b) | `6cfecfb55f889f8e3b7156adb12809608db4f74b` | `3cf83488a94b47a018934e5f91c1bf7e93357aae` | 根 LICENSE 为 MIT；复制或实质部分分发应保留版权及许可文本。许可证声明不单独证明游戏原始数据的完整权利链或目标版本身份。 |
| [hk4e-patch-universal](https://github.com/KitsukiDev/hk4e-patch-universal/tree/2b589125874b0b96f8694bfad31e34811eba8573) | `2b589125874b0b96f8694bfad31e34811eba8573` | `822e0b73e04a6ec63461e0d7ce87c923f7e5e964` | 根 LICENSE 为 GPL-3.0；本次未复制实现，若将来引入代码须先按适用条件处理许可。 |

本次分别对以上明确 URL 执行 `git ls-remote --symref <url> HEAD`，二者均为 main，HEAD 等于指定起点，无新 commit 需增量下载。缓存是 API 快照，不是独立 Git 仓库：不能在其子目录直接 `git remote`，否则 Git 会向上找到本项目仓库。commit 元数据的 tree 与未截断 tree 一致；缓存全部 29 个文件及新定向取得 6 个文件的实际 Git blob 均与 tree 相符，同时固定 SHA-256。新取文件仅 LICENSE、303 BIN、scene3 point、scene3 主 Lua 和两出生组 Lua。逐文件、大小、blob、SHA、13 个本地输入及18个 Java/patch 源文件 SHA 见忽略目录 `local/migration72/d02-resume-20261109/upstream/manifest-final.private.json`（SHA-256 `aaafca02e68704e278aa51ccface7a6cc7f0e9cf907403987385275658e6d9e7`）；完整原始内容留忽略目录。tree 列表由固定提交 API 快照提供，本次未从全部子 tree 原始对象重算根 tree；该链校验所取文件与列表内部一致，并非签名认证。

## 必要性、重复、表示和未知

| 范围及直接消费者 | 上游与现有本地数据 | 结论和仍需证据 |
| --- | --- | --- |
| 出生 Avatar 10000005/10000007：`HandlerSetPlayerBornDataReq` 校验，`AvatarStorage.addAvatar` 建默认装备，`Avatar` 消费属性 | 两端均有两行；整行不相等。本地另有字段；14 个共享路径 hash 的 JSON 类型/值表示不同，不能当作新角色或断言整行语义等价。 | 出生角色必要；上游未消除完整 Avatar/Item 目标字段、自然加载和 selected revision 缺口。[目标字段合同](CLIENT_72_D02_JAVA_RESOURCE_CONTRACT.md)及已验能量、装备片段继续为准。 |
| depot 501/701 与 504/704：出生 handler 检查；`AvatarSkillDepotData.onLoad` 查技能和建立 proud 索引 | 四行都存在；501/701 上游省略 `energySkill`，本地显式 0；四行 `inherentProudSkillOpens` 上游空对象与本地具名零字段有表示差异。504/704 的具名 special 行两边相同，本地另含混淆键。 | Java int 缺省可得 0 是本地 Java 模型，不是原生解析或 alias 验收。Java 有多个 `@SerializedName` 备选且本地可能同一对象含多个名称，需按实际输入顺序与真实 Gson 加载验证，不能凭相似构造别名；目标 energySkill / 混淆字段和实际消费仍需独立原生证据。 |
| 武器 11101：`ItemData.onLoad` → `Avatar` 曲线和属性 | 两边均有；上游第二个 `weaponProp` 保留曲线类型但省略 `propType`/`initValue`；本地分别显式 `FIGHT_PROP_NONE`/0。第一属性数值经 float32 相符。 | 本地 Java `WeaponProperty.propType` 缺省 null，`ItemData.onLoad` 会过滤该项；显式 NONE 不被该 null 过滤，两者数组形状不同。已有原生派生合同保留 NONE/0，不能靠看似零值断言等价或替换。真实上游行在 Java/原生端的行为未新运行。 |
| WeaponPromote 11101 五阶：`WeaponPromoteData.onLoad`、`Avatar`；AvatarPromote 12 七阶：`AvatarPromoteData.onLoad`、`Avatar` | 按 phase 对齐，武器五行经 float32 规整相等，phase 0 原 JSON 对象也相等；角色七行除本地独有空 `promoteAudio` 外经 float32 规整相等。列表原始项和顺序保留。 | 无新增出生用途突破行；零阶原材料槽及属性项不可伪造空列表。[已验用途](CLIENT_72_D02_PROMOTE_RESOURCE.md)不覆盖所有字段职责、自然加载或 revision。 |
| AvatarCurve / WeaponCurve：100 个 level，`AvatarCurveData` / `WeaponCurveData`，`Avatar` 查 level、类型 | 两边各100个唯一 level，逐 level 全行经 float32 归一均相等；原 JSON 行顺序不同，原数值十进制呈现也不同，直接对象相等仅各1行。 | 这是已存在数值的表示差异，不新增曲线；必须按 level 键对齐，不能按数组位置对齐。float32 比较只证本次行，不证源文件或全部 Java/原生消费合同等同。 |
| 303：`PlayerProgressManager.addStatueQuestsOnLogin` 条件查询/尝试 `addQuest(30302)`；`ResourceLoader.loadQuests` → `MainQuestData.onLoad` → `QuestBinFallback` | 上游 Excel 30302 的 `acceptCond` 是 STATE_EQUAL `[0,3]`，本地同项为无类型占位；上游 303 BIN 有61个子项，本地16个；上游 30302 BIN 无 acceptCond，具名 finish 是 COMPLETE_TALK，finishExec 解锁 scene3 point/area 3。另一固定目标候选 BIN 是63项，见[303合同](CLIENT_72_D02_QUEST_303_START.md)。 | 不把任一来源的字段、数量或开始条件当作目标同源事实；Excel 和 BIN 消费时序可改变自动接受及索引。上游不能解决目标 `acceptCondComb` object 被 Java LogicType 拒绝、未明字段映射或正向 manager 恢复。需固定目标独立原生字段消费者和样本后再设计转换；禁止 alias、强置 loadedAll 或以 BIN 代替目标 Excel。 |
| scene3 point 3：30302 完成后的候选解锁引用，`ResourceLoader.loadScenePoints` 建 map，`RespawnPositionHelper` 消费类型 | 本地 point3 `$type=TransPointNormal`，上游为 `SceneTransPoint`；所见坐标相同，另有 `damageRatio` / `type` 及其他字段表示差异；两端整表 points 数为1707/1819。 | 类型字面量与字段差异不是已证 alias；当前 respawn helper 同时识别两名称仅说明此消费者源码，不证明所有消费者、Lua或目标客户端等价。point3 属完成任务后的条件引用，不把其称作出生时必已解锁。 |
| scene3 主 Lua / group 133003505、133003601：`SceneScriptManager` / `SceneGroup` 及既有 GROUP_LOAD、GADGET_CREATE 受控实验 | 两个 group 文件 SHA 不同但 CRLF→LF 后字节相同；主 scene Lua 内容不同，出生坐标相同，但边界、die_y、block 集合有变化。 | 两个组不是新功能；换行规整不证明本地部署同源。主场景不能因出生坐标相同覆盖：实际格网、关联 block/group、事件、保存状态和目标 revision 要以受控输入和真实调用证明。旧 25 组、505/601 既有实验只覆盖其限定夹具。 |

上游 README 自述以 7.1 基线追加 7.2 内容，声称服务端加载验证；这不提供本机目标客户端自然解析、完整入场或跨资源集一致性的证明。上表“本地”指本次读取的 resources 工作树，非目标资源真值；现有受保护的 dirty 文件未改动。

## Patch 实际交集与阻塞

固定上游 `winhttp.rs` 将四个域的精确主域及点分隔子域转到 loopback，仅对成功返回的连接句柄记录、OpenRequest 时去掉 secure 标志，CloseHandle 时移除；未匹配域保持原连接参数。本地旧 `patch/src/modules/winhttp.rs` 则无条件替换 host/port 并对所有请求清 secure，且没有对应句柄清理。这是有意义的源码差异，但不是当前 7.2 自然账号路径的覆盖证明。

[SDK transport](CLIENT_72_SDK_TRANSPORT.md) 的固定账号 DLL 导入为 Winsock 及 WinHTTP 代理相关项，没有静态导入 Connect/OpenRequest/SendRequest；Astrolabe 存在请求链导入。条件性的账号提交链指向 curl 兼容入口，函数名是行为推断，动态解析、fallback 及自然调用仍未知。故不能用上游 WinHTTP hook 解决本机自然 route/key 或声称覆盖 curl/Winsock。另上游 `security.rs` 的 `after_key_sign_check` 写 `rax=1`，不得随之启用或视为信任验证。当前窄 key 入口与原生校验另遵循已有合同。

可执行的后继规格：先固定当前五输入和真实模块身份，以已批准、能保持原生校验的只读方法确定自然请求实际 transport、目标 host/path 分类及连接生命周期；若证明该请求经过这三个 WinHTTP 调用，再单独实现和测试域名精确匹配/点分隔子域、大小写、非目标及相似后缀拒绝、连接失败不入集合、成功关闭及句柄复用、不同请求的 secure 标志、原参数/返回值及错误状态。缺该覆盖证据时停止，不安装 hook、不扩大到所有 URL、不绕签名校验。自然请求、线程、TLS/证书和运行 route 仍未验。

## 已执行核对和后继

主线已用独立脚本重新读取最终 manifest、35份上游原文件、两个 commit/tree 快照、13个本地资源和18份源码，逐 blob/SHA 核实通过；证明为 `local/migration72/d02-resume-20261109/upstream/main-review.private.json`。初稿引用过旧 manifest hash，已更正为上文实际最终原件；没有修改最终原件或扩大其来源认证。审计结论只关闭本次定向审计范围，未解除完整资源或自然路由父项。

`python local/migration72/d02-resume-20261109/upstream/verify.private.py` 实际通过：35 文件 blob、两个 tree、两个 100 行按 level 的 float32 比较、12 个突破 phase 的 float32 比较、目标行存在、Weapon 缺省差异、两 Lua 换行、30302 条件差异；并验证改动 blob、重复 ID、改变 float32 的负例被拒绝。该脚本是本次私有核对，不是生产工具或 Java/客户端测试。早期一次按数组位置对比曲线产生错位输出，已保留私有脚本供追溯；最终只用唯一 level 键对齐结果。没有新增正式测试、构建、部署或客户端验收。

E-01 下一步是沿固定目标资源及原生消费者补全 Avatar/Item 的字段桥梁和实际加载，再以真实 loadAll、必要日志/引用与独立数据库验证保存→新 Player→manager 正向重绑；303 的真实目标条件需独立原生证据。R2 下一步是确定自然 transport 和 route，未达到 WinHTTP 后继实施条件。D-02 和上述缺口继续保持未完成。
