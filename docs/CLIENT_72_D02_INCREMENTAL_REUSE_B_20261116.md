# D-02 Material / Weapon 入口增量公开检索

本报告对应两个独立出口：`D02-INCR-B-MATERIAL-20261116`、`D02-INCR-B-WEAPON-20261116`。执行[已验计划](CLIENT_72_D02_INCREMENTAL_REUSE_PLAN_20261116.md)，计划 SHA-256 `50abc5bdd26c6c8c9c80ec00e60938ba0240834e02ac41e13f805dfa510cce69`。先按 Fork 的描述和 README、再按实际分支和固定 tree 筛选；对命中版本相关 schema 的分支才读取相关文本源码，没有运行上游代码。

**两个出口均为“有界检索无目标桥；有旧版参考”。** 新发现 YSRes 3.5 文件解析链和 Weapon 版本差异，但没有可核验的本机国服 7.2 revision 连接；不能据此生成目标行、walker 或 cursor。没有证明公开来源穷尽，A/B、preview 与准确依赖继续冻结。

## 固定输入及各自结论

- **Material**：复用真实 `m_bytes` 1,560,105 B，SHA-256 `088f2b43c860c2892c1f6e9a0349547dcd94b021e5d56983349f2cfd81e7ee89`。既验 569 B 原窗在有效条件下从同 reader 的当前 cursor 读取 LE32 并推进四字节，其独立标量形成倒计数；239 B 路径 loader 已证同对象 `+1c` 槽清零。两者不提供有效 backend `+18` 基址、首次 refill 实际文件位置、header 值、count 或首行。新增 3.5 schema 不能填这些边；不读取条件 `P=0` 的四字节。[旧证据](CLIENT_72_D02_MATERIAL_HEADER_SOURCE_20261116.md) SHA-256 `e604a18e26158ddbdc8da73a12af1d064160c36299491467e51031be42a54140`。
- **Weapon**：复用 128,936 B raw，SHA-256 `cca19efbe032e5416d78c28fcb69c2ea2d9707187197aa4ed90d869c3a1ff918`；whole 报告 SHA-256 `1e9817cbea6da6db5ee564d5c3f3a64436fa3319e6d9b8ccf5aef0014cf85a86`、11101 六键、239 种必要 cohort 和其限定验收。`[4,400)` 是 87097 wrapper 的入口/返回跨度，不是已证的 20600 或 88968 父行 F 入口；11201 不在必要 cohort。新旧 schema 均没有提供固定目标同 reader、同 row 的 wrapper→raw 父入口 cursor 等式和完整物理行边界。六键职责、presence、转换及数组单位不能靠旧版补齐。[旧证据](CLIENT_72_D02_SHARED_STRING_CURSOR_SOURCE_20261116.md) SHA-256 `bd9e1d4e36749b9a61fadbb8287c54f3ca68489fc15cb315e73a10b82acd779c`；[旧号范围](CLIENT_72_D02_OLD_WEAPON239_SOURCE_BLOCKER_20261116.md) SHA-256 `fdd2fce8563f1e1cc411f340ebe25d71e67f96493a3c0513656f6c961a8da172`。

旧[Material 检索报告](CLIENT_72_D02_MATERIAL_ROW_REUSE_SEARCH_20261116.md) SHA-256 `78af0a1bb44f8151dba00f76e52331009b08e9c5b757440d8a04c3a2a18a35ee`，旧[定向检索报告](CLIENT_72_D02_TARGETED_REUSE_SEARCH_20261116.md) `534f6ef62b47a181e001093dde18eb3e0312133db4ed9fd6dc790e8056087853` 均已核；既审 parent、Hiro420/ztsvlr YSRes 默认分支及其旧 Material/aux blob不重复计为新增。shared String 仍只给正常 tail 的 `P_after=U32(P_at_tail+N)`，不推出入口净推进。

## 新发现：3.5 表解析链及版本差异

[tsukimoonmoon/YSRes main](https://github.com/tsukimoonmoon/YSRes/tree/959db20438178a897bbc8948677a32ea5fdd2dbf) commit `959db20438178a897bbc8948677a32ea5fdd2dbf`、tree `082db7a221255433243383f62ec58021cf36124e`。README blob `9f663593cc9f66aa38d09c92f86d7c3c17deb089`，SHA-256 `5dc9ebbabd65ba2ad2075603e1153e633f89ea14faf71f44502783b5490a9c79`，声明目标 **3.5**。同一内容也出现在下表其他 Fork；这些不是独立目标 oracle。

1. [main.py](https://github.com/tsukimoonmoon/YSRes/blob/959db20438178a897bbc8948677a32ea5fdd2dbf/main.py) blob `27cfad03f309de9f074b84dd10b19f11d5ddedf9`、SHA-256 `1615788ce00e537690d0b4b55bb0fadca076c5bf4c223ef676ffdbf950c02f05`，将两个表名分别映射到 `MaterialExcelConfig` 和 `WeaponExcelConfig` 并交给同一 `UniversalParse`。
2. [parse.py](https://github.com/tsukimoonmoon/YSRes/blob/959db20438178a897bbc8948677a32ea5fdd2dbf/parse.py) blob `11bc3cf7c8d7b57c46c892ef3db3a1a5e406a9a3`、SHA-256 `f63af703662530265be50cce55b7a29655ac5f1545c5f3c2df655a5e6fbe0c4c`，在文件读入的同一个流上先用 `VlqBase128LeS` 得到计数，再循环调用对应行类。这是该旧工具的文件语法，不说明目标文件头的位置、转换或 reader 构造。
3. [py/output.ksy](https://github.com/tsukimoonmoon/YSRes/blob/959db20438178a897bbc8948677a32ea5fdd2dbf/py/output.ksy) blob `361bc157a96c0fcc79ada471b181e4814d115db2`、SHA-256 `6e9eb7e967362d6e488165fa853ae72366d582197cdff40dfae518a266dcd3b7`。两种行从 length-prefixed bitfield 开始；两个 `*_excel_config_loader` 定义均为空。Weapon 行平铺 weapon 和 item 字段，未定义本目标的 wrapper→20600→88968 关系或 cursor。实际被导入的生成类 [py/output.py](https://github.com/tsukimoonmoon/YSRes/blob/959db20438178a897bbc8948677a32ea5fdd2dbf/py/output.py) blob `3347ed5326b1f6e9b57e28f60baa981a0a2456e3`、SHA-256 `3c61c48a17cae72ebbe2d549d37e0b1338f311277c42bc05846c6d9fd6bdf34c`；只读对应两个类的 `_read`，没有执行。它确认旧实现的同流 bitfield 和字段次序，不证明与目标相同。
4. [3.4 schema](https://github.com/GreenXeMotion/YSRes/blob/98468f62882a57992b5b371f47d65101c54d419c/py/output.ksy) blob `16a53829e781e6f824f715123c01837b818d1aaa`、SHA-256 `8f8b5074d724347a8cc6696f303ab425d310e26a93892fb737fb7a543356d9a8`；另一 3.4 blob `e8f2499e2f2b51dac8c275884504392feb51a71b`、SHA-256 `49a1a38343518d73b8125107a4b1a3b7115c6969caaa8bac960546357b24a97d`。三份 schema 的 Material 段内容相同；3.5 的 Weapon 段较两份 3.4 删除 `gacha_card_name_hash_pre: s1`。字段段比较先将换行规范化为 LF；Material 段 SHA-256 `3a8874bb1db5b27e8727a4d4f79f5aac7f373984ffe56b89144d9be462d1dd5f`，Weapon 3.4 / 3.5 段分别 `5b5aa02c369056c5b00560cb5e2a4c2f8d537682f7ebd620b28813e94aeb169d` / `d7f8b9a038207fd367fe407fc7762853aa953d1f85456eb9ca4e47e30a60cf81`。这说明至少存在待核的版本差异，不证明任何目标字段位、长度、presence 或值。

该 YSRes 根 tree 未见独立 LICENSE；`output.ksy` 文件头声明 `AGPL-3.0-or-later`，不能据此推定仓库其他文件许可。当前只记录来源与语法，没有融合源码。旧 5.2 的 count/flag 和 2.2 schema 仍各依原报告只作参考，不因 3.5 与其局部相似而视为独立证据。

对 A 组的辅助线索：上述三份 schema 的 `reliquary_excel_config` 段相同、具名 `rank_level` / `set_id`，`avatar_skill_excel_config` 段相同且无 special energy 字面名；其段并未与目标 46431/74316/88602 字段或实际查表 consumer 绑定，不能排除标签互换或证明目标字段缺席。已及时交主线，不扩大 A 组任务范围。

## Fork / 分支实际覆盖

2026-11-15T19:45Z GitHub Fork API 均 HTTP 200、每页100、第一页无 `next`：YSRes 13 个、DataParser 6 个。YSRes 的 Hiro420 和 ztsvlr 已由旧报告审过，本轮其余 11 个的默认 README、公开分支均取得；DataParser 6 个全部取得。17 个默认 README 均以分支头 commit 重新固定且与默认读取相同；`test` 分支 README 另以该分支 commit 固定。分支响应均 HTTP 200，第一页无 `next`。表中相同 commit/tree/blob 只计一个源码家族，不计独立 schema。

| 实际 Fork / branch | commit、tree 和读取结果 |
| --- | --- |
| `GreenXeMotion/YSRes`、`nonobject14514/YSRes`、`66hh/YSRes` / `main` | `98468f62882a57992b5b371f47d65101c54d419c`，tree `c8186002831a60579170d563298391973fa00b6c`；README blob `0df30d0760fe92b8a17e1b6004d8a58c4673a536`、SHA-256 `4ba3308c5046ccdcc468584f83a0894eb1e76345c291ceed6f404c531c0500f4`，声明 3.4；schema 为上文 3.4 blob `16a538…`。 |
| `Xiaobin0860/YSRes` / `main` | `def0988bad4ad5b93a3f57cefeb9e326c6fcad61`，tree `0128f98f932e4dbea1e26106cdc528e313f44d04`；README blob `eaf9ea8ee855e2e23809af4fcf2a3442c518fe1e`、SHA-256 `e28f75d8ca6d8fe97ef42c770e91c9bab9542e85ca54e23b2cef094f21b6b457`，声明 3.4；schema blob `e8f249…`。 |
| `nonobject14514/YSRes` / `test` | 唯一本轮新发现的非默认分支，`8d54d788eef1eea15070592c050bf77e21e48c00`，tree `60e8a38a0822fa220d1ff8c78cc2c6d8f864bf8a`；README blob `2c1de80c0b4ac20fe846cd75a93eef6b37e8a9ba`、SHA-256 `12ddb26ecfc2643822a0108bb18b9eeda0f1158b0f376fc26a759a7dc7d0f5ae`，声明 3.4；`py` tree `abeb78c0b362f57830b73a0176442efdec07f5a4` 与 Xiaobin 相同。 |
| `tsukimoonmoon/YSRes`、`Hengle/YSRes` / `main` | 同为上文 `959db204…`、tree `082db7a2…`、README 3.5。 |
| `hengtek/YSRes`、`Areha11Fz/YSRes` / `main` | `baef915080a5fefd0d27bea6421af482d575bc02`，tree `df0a51aaaffe61ebac79c34e88cdf34a8fd6fded`；README 与上文 3.5 相同，`py` tree `380d45a60fc1b2ca684e0e8b5d548c80397be8d2` 和目标 `output.ksy` / `output.py` 均与 `959db204…` 相同。 |
| `ERthereal/YSRes`、`NctimeAza/AnimeGame-res`、`keaideye/YSRes` / `main` | 都指向旧 parent `726dfe5f6af3700abb9623183be0c7e03b586f00`、tree `f5e9015823740c997bd82f036b80bf7398dd066b`；README 3.4、gitlink `1045cf6f0d5d77f914d5441c318724bcb67a19be` 均沿旧报告，不重审旧内容。 |
| `Hiro420/DataParser`、`ztsvlr/DataParser-paimooon`、`nonobject14514/DataParser-paimooon`、`The-P-Group/DataParser-paimooon`、`vermillion-excl/DataParser-paimooon`、`Hengle/DataParser-paimooon` / `main` | 六者均为 `3bffcd26c35c410aac216979b41c3fed60934e78`，tree `35116aa520ff15dac150d0e08b684422975c77c2`；README blob `4a471fe8f1afabfde73f16870dc2c601b8b98aa9`、SHA-256 `2352303bead9ff370a714f25a7058d1ba05ebdd4a0bf25f0c039800332cef9cd`，声明定义基于 **2.2**；许可 GPL-2.0。Material blob `97e8cb4975c0cbbab26cef664edd80aebe12f3f6` 和 aux blob `ae660848f0199ef80ee97d283788e1fdbfde0d64` 均为旧报告已审；新读 [Weapon KSY](https://github.com/Hiro420/DataParser/blob/3bffcd26c35c410aac216979b41c3fed60934e78/Excel/weapon_excel_config.ksy) blob `63388e471bc1035f0714b6112969994fab9779d3`、SHA-256 `d038bf339b6ce252a9c4d98b2cfca3898bd99f7f67518d744cd5480676038efb`，同样是旧版平铺行 schema，不给目标 wrapper 父入口。此 Weapon blob 在旧审两个 gitlink 亦相同，不当三份独立来源。 |

本批没有发现指向目标版本的新增 gitlink；3.4/3.5 新分支使用内置 `py/output.ksy`，不把内置文件误记成 DataParser 新 gitlink。目标源码只对入选树读取，未把其他源码视为已搜索。

## 查询账、未覆盖和证据保存

原始 API 响应、README、目标文本及脚本保存在忽略目录 `local/migration72/d02-incremental-b-20261116/`。响应、时间、请求 URL、状态、页次、SHA-256 等固定在：`api-initial-manifest.json` SHA-256 `727ae125d063490cb2ae3ee7744e93eecd49d428e3e3d44db7c08100f47b6ec4`、`fork-inventory.json` `3772879d593eaecebf7192ffb4b53f48ae98e8e100a7eab5fb5853972b8de6da`、`tree-inventory.json` `9a1a161cf029a781be0648e02ca344be292f598a25e8b63b7597d892fb8e3bbc`、`target-sources-manifest.json` `dcc064cdf32a5c46ee32f413c8a83d2f0205ba46ddd5b0b03bb1e6d7f2739e84`、`schema-section-fingerprints.json` `efb716433dec17ad78c4c495fd58ef253a8493846853b78ec50c8bfccd37584e`、`platform-queries-manifest.json` `653448c12a91bb3de43267613edffad5bb3400f1e4a720a7b87f648338c94a4a`、`generated-source-manifest.json` `8727b9760e345fd26620c02b78e1b18ba80a44513886ae3e45114f0722b2fcfc`。

| 平台 / 精确范围 | 实际状态与解释 |
| --- | --- |
| GitHub 仓库搜索 `"WeaponExcelConfigData" "binary"`、`"material_excel_config" "table"`，各附 `is:public fork:true in:name,description,readme`，relevance 与 updated/desc，均 page 1 / per_page 100 | 19:45:20–21 UTC 四次 HTTP 200、`total_count=0`、`incomplete_results=false`、无 next；四份空响应 SHA-256 都是 `4af480b8ee5b87b369a76c49bd22c9a783908272ebffbe97898f8ab0f0772a5f`。仅说明仓库名称、描述和 README 搜索该组合为零，不等于源码零命中。 |
| GitHub 代码搜索 `repo:tsukimoonmoon/YSRes "material_excel_config"` | HTTP 401 `Requires authentication`，响应 SHA-256 `b7dbd173f33b19650f61b1c528737e2037cf768d90076fdfce5d32541765e29e`；未把未授权视为代码零结果。按计划改为固定 tree 和必要文本源码的本地只读检索，不声称其他仓库源码已搜索。 |
| GitLab 项目搜索 `WeaponExcelConfigData` / `material_excel_config` | HTTP 200，各 0 项，第一页；仅项目搜索，响应 SHA-256 均 `4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945`。GitLab `scope=blobs` 查询 Weapon 返回 HTTP 401，代码范围未覆盖。 |
| Gitee 仓库搜索上述两个单词 | HTTP 200，各 0 项，第一页；响应同 `4f53cda…`。返回链接显示默认 `fork=false`，故未覆盖其 Fork，也不是 README/代码零结果。 |
| `git.xeondev.com` Forgejo 仓库搜索上述两个单词 | HTTP 200，`ok=true`、各 0 项，第一页；响应 SHA-256 `7c60055808f1242bb4ee29c5aac08d78235f51f17b2c4bd692ffc798399dddd9`。仅该实例的仓库搜索，不是全部 Forgejo 或源码搜索。旧 mavuika 5.2 已审，不重查。 |
| 搜索工具对 GitLab/Gitee 的四个上述双关键词组合 | 可见摘要无结果，工具未提供排序、分页、总数及原网页字节；未保存可复核原页 hash，不据此推断平台零结果。另两次搜索引擎发现旧 3.4 / 5.2 二手摘要，响应原页同样未固定，未作为事实来源或新增源码覆盖。 |

其中平台 API 查询于 2026-11-15T19:49:16–19Z 发出；Gitee 及其 Fork、其他公开托管平台的 README/代码、没有 README 命中证据的长尾、新 7.2 schema 和受限 Codeberg 地址均未覆盖或按原限制不重试。不存在因 HTTP 401 推断零结果的情况。

## 给 Sol max 的独立下一步及操作边界

Material：以[表头/loader 已验源](CLIENT_72_D02_MATERIAL_HEADER_SOURCE_20261116.md)和既存完整 factory/refill 原件为固定输入，只核**同一 reader 的 backend/+18 到达定义与首次 refill 传参**是否有独立于 A 返回/构造的新写入边；没有该边或独立目标文件语法时停止，不读取条件 `P=0` 的 header，不预设首 flag。

Weapon：以[shared String / Weapon 范围判断](CLIENT_72_D02_SHARED_STRING_CURSOR_SOURCE_20261116.md)、whole 与已存 observer、完整父源为固定输入，只核同 reader、同 row 及其游标关系；wrapper 跨度、旧版平铺 schema 和 11101 值本身不建立 F cursor 等式。若缺可核完整来源或真实 raw 入口样本，继续保持缺边；不取已冻结构造路径、不按值扫描、不扩 11201。

一次操作偏差：初查仓外既有 YSRes partial clone 是否含若干 commit 时，首次 `git cat-file -e` 未设置 `GIT_NO_LAZY_FETCH=1`，Git 在 19:46:43–44 UTC 自动向该仓外 clone 的对象库补取部分 pack。没有修改其工作树或执行上游代码；后续 Git 对象查询设置了 `GIT_NO_LAZY_FETCH=1`，新读文本在上述忽略目录独立固定。未清理补取对象；已告知主线。

本报告是来源调查而非客户端验收。没有改运行代码、Proto、配置、补丁或资源，没有构建、部署、JVM、数据库、服务、目标 native/parser/getter/ctor、客户端、Git 索引或提交。主线负责状态文档和集成；D-02 / F-05 / D02-R6 仍为 0/6。
