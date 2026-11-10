# E-01d1 / R3-OLD-SAVE-DEPENDENCIES：旧档依赖离线提取

## 目标、已有和缺口

只读识别选定旧测试号的持久字段与额外资源引用，不能从出生装备局部验收推导任意旧档完整恢复。既有两性出生、11101、零阶及装备局部 Mongo 已验范围见[装备报告](CLIENT_72_D02_EQUIPPED_LOCAL_RESTORE_20261110.md)；本批没有重跑这些验收。**接手时没有明确选定旧测试号，也没有被确认为该账号、同一一致时间点的不可变快照**；之后用户已选定本轮旧号（身份留私有材料），当前等待主线取得固定快照，不是“该机器不存在任何旧数据”。

在 `local/` 下以集合名、snapshot/dump/backup/manifest 及 BSON/归档扩展名做有界文件名盘点，共381项：266项可读 JSON、108项可按当前 decoder 读取 BSON、7项归档未解包。可读 BSON 包括大量 Mongo `storage.bson` 存储元数据，不能作为用户存档；其他已知装备/任务测试证据同样不代表用户旧号。清单的相对路径、来源分类、修改时间、大小、完整 SHA-256 和语法可读性留在忽略目录 `local/migration72/d02-resume-20261110b/c-old-save/candidate-inventory.private.json`，SHA `98e72023b2939a041ac74d1cbf544cd67ce13d6441d4c61237f33438e49b3500`。文件可解析不证明账号、备份一致性或用途；本次没有枚举或输出账号身份、凭据和原始文档。盘点受文件名、格式及目录范围限制，不宣称全盘穷尽。

## 源码合同

`DatabaseHelper.java:393,421` 查询 Avatar `ownerId == Player.getUid()`；Item 还需 `deleted != true` 和 `count > 0`。`Player.java:81` 的持久 `_id` 是 UID；`Avatar.java:48-58,81` 与 `GameItem.java:38-41,98-101,174` 分别定义 `_id`、`ownerId`、角色/物品 ID、等级、突破、当前 depot 和装备所属角色。`AvatarStorage.java:197-256` 按角色及当前 depot 查资源，缺资源跳过；Inventory 在 `Inventory.java:825-880` 按 `equipCharacter` 尝试绑定，然后 postLoad 无武器时才尝试初始武器并重算。仅凭保存的 item ID 和 equipCharacter 不能判断装备类型、槽位或 postLoad 是否成功。

`Player.java:153,246,430,1774-1814,1838-1872` 保存位置、场景和 TeamManager，加载后还可能修复队伍和副本返回位置，之后创建 World；`TeamManager.java:48-50,241-267` 的单人常规队伍按 `teams[currentTeamIndex]` 取 `TeamInfo.avatars`，`TeamInfo.java:13-14,33-35` 是角色 ID 列表。实际 fixture BSON 显示 map key 为字符串；临时、试用、多人队伍是不同运行时分支，持久常规队伍不等于最终场景队伍。位置字段为 Morphia 的 `x/y/z`；不使用 Gson 别名猜测保存字段。World `getSceneById/tryAddPlayer` 在 `World.java:162-175,214-260` 有实际资源和位置消费。技能等级、已解锁 talent 和 proud 保存值只作为条件引用候选，不能全部提升为入场 P0；proud 还会由当前 depot 和升级状态重算。

## 输入、方法与产物

[离线 driver](../tools/client72_d02_old_save_dependencies.py) 接受私有 manifest，必须对 manifest 与每个 BSON / JSON / Extended JSON / JSONL 文件核 SHA-256，选中 player 输入必须恰一文档；根据玩家 `_id` 隔离 Avatar 和 Item，记录缺字段、装备与当前常规队伍中找不到所属角色的引用、正资源 ID、保存的条件引用及明确的未知。`birth-id-only` 只表示 ID 属于已验出生范围，不证明当前等级/突破、资源行、完整语义或运行通过；Scene 独立审查。输入和输出包含私密数据，必须留在忽略目录。decoder 仅用于本次支持的 BSON 类型，遇未支持类型停止，不是通用数据库恢复工具。

```powershell
$manifest = '<忽略目录中的私有 manifest.json>'
$manifestSha = (Get-FileHash -LiteralPath $manifest -Algorithm SHA256).Hash.ToLowerInvariant()
python tools/client72_d02_old_save_dependencies.py --manifest $manifest --manifest-sha256 $manifestSha --output '<忽略目录中的新输出.json>'
python -m unittest tools.test_client72_d02_old_save_dependencies -v
```

manifest 对象含 `player`、`avatars`、`items` 三项，每项为 `{"path":"...","format":"bson|json|jsonl","sha256":"完整哈希"}`，格式值三选一；可用 BSON 连接文档或 JSON 数组，禁止输出到共享目录。输出使用新文件，避免覆盖。需要确保输入是同一一致时间点、已选定玩家而非多个快照的混合；driver 不证明这一点。

输入固定：装备绿轮九组共27份 BSON 及其27份 Extended JSON 对照的逐文件来源、时间、大小和完整 SHA 在忽略目录 `fixture-audit.private.json`（SHA `4b3fee9628906d099e8765eb8e6ed59a6f1fe0c2d12d8d7d5b3604bb65940610`）；最终审核 `final-audit.private.json` SHA `9293fcb0dd2666771c60b0f6e8a9b74ff3eea62acd8900b3f12e97bf838f8e12`。六份既有资源原件已核原字节且可解析，完整 SHA 分别为 `ae86a458b6a9af5e139e624ede46f9867eba2e31d1aef9d27fb7a4749ad78f9f`、`d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e`、`546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1`、`50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc`、`9007d08f91848c9aa2c63d8690b3f5f9c6e8cc7165a3fa07a0dc78b8e55a53c2`、`51c63295b7756b6083157ed14d5e39134fe95ba29d78ea097046965239134880`；对应路径及用途见装备报告“六个固定输入”。这些原件用于确定已有合同边界，本 driver 不自动 join 它们。

## 实际验收、停止与恢复

Terra原四项 Python 测试通过；主线审核后新增非活动Item不产生装备依赖的回归，总五项通过：64位 BSON、嵌套与哈希拒绝，Extended JSON 与缺字段，UID 隔离、正引用及队伍/装备缺引用，损坏 BSON 拒绝。最终代码实际读取九组27 BSON，对照27 JSONL 的选定字段投影逐组相同；九组均验证不同 owner 不混入、移除角色后装备缺引用被标记，另三个独立 CLI 进程与对应直接投影一致。fixture 的常规队伍为空，不能证明非空真实旧号或 World；测试只是提取能力，不是新的 Java 保存、资源语义或客户端验收。

恢复须由主线明确选定旧测试号，并取得来源和一致时间点可追溯的不可变玩家、角色、物品集合快照与完整 SHA，私下核 Player `_id` 和对应 `ownerId`。若只有数据库存储目录，主线按本轮已有授权准备来源一致的独立副本及只读导出，先核原库离线/锁和归属；不启动原 dbpath。本卡不启动数据库。先按私有 manifest 运行并核缺字段/UID/条件引用，再按实际额外 ID 分配资源族和来源语义研究；缺资源、未知字段或疑似混合快照停止，不造默认装备/空 ability、猜映射或声称就绪。建议 Project 保留本子项“离线 driver 及夹具通过；真实旧号待选定和固定快照”，R3/D-02 均未完成。未修改运行代码、资源、配置或原始数据；未部署、启动 JVM/Mongo/服务/客户端或做本次 Git 提交。

主线独立验收：54份固定输入完整SHA保持，九组BSON/Extended JSON投影一致且正引用由独立读取逐项核对；单独CLI通过、错误manifest SHA在输出前拒绝。主审`main-review-v2/main-review.private.json` SHA `930f66249bd807afc5126835a54fcdd10857c85524b5a397de33bcfb3cd693bd`。最初主审harness误把集合传成单文档，AttributeError原件保留；只修harness形状。按真实DatabaseHelper查询排除deleted/count<=0条目的运行依赖，但仍保留其保存字段供审计。已选旧号的实际快照另验；本次没有JVM/DB/客户端运行。
