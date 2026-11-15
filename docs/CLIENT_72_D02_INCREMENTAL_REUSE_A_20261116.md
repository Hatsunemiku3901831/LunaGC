# D-02 精确字段增量复用检索：A组

任务 `D02-INCR-A-MAX-20261116` 和 `D02-INCR-A-RELIC-20261116` 分别交付。**A1：本次有界覆盖内无新的目标 MAX cap 职责桥。A2：有 6.2 已具名 JSON 的新消费者参考，但无国服 7.2 目标字段桥或独立非零 oracle；rankLevel 与 setId 两个角色均未解除互换。**两者均不代表公开来源穷尽，不改变资源 ready、完整 Player 或 D-02 实机状态。

## 固定输入与边界

按[增量计划](CLIENT_72_D02_INCREMENTAL_REUSE_PLAN_20261116.md)执行，计划 SHA-256 `50abc5bdd26c6c8c9c80ec00e60938ba0240834e02ac41e13f805dfa510cce69`。本组实际核对的只读输入：

| 输入 | SHA-256 | 本次使用 |
| --- | --- | --- |
| [原定向公开检索](CLIENT_72_D02_TARGETED_REUSE_SEARCH_20261116.md) | `534f6ef62b47a181e001093dde18eb3e0312133db4ed9fd6dc790e8056087853` | 已审树、默认 README、候选谱系和排除范围 |
| [energy 职责来源](CLIENT_72_D02_SKILL_ENERGY_BRANCH_FIELDS_20261116.md) | `b4bb22d60c5c08967f7f2a362f34fdad9c59385e76f4cead172829d0f4889073` | 88602 的 cost/START 与 24887 MAX_SPECIAL 的已验边界 |
| 既验 121 四键报告 | `9d4b69d00a438084cd9a10b6b4eacda5618eaeebdab735a3bfe38cd3c4e755cb` | 仅核 hash，cost/START 已闭，不重新产值 |
| [Relic 27 候选 typed 核查](CLIENT_72_D02_RELIC_TYPED_CANDIDATES_20261116.md) | `8fd867a21fa2668d163233e3094ef9eced228babc1741fda97d6d41d45a1a7ba` | 直接复用 27 候选/10 owner/18 声明排除 |
| Relic typed 核查原件 | `6c709c56a80d7502434cf69ca1f9a6e5332a266a54bf3abb689317dc451adc59` | 仅核 hash，不另试 74316 混淆 body |

版本基线为[固定国服输入](CLIENT_72_EXTRACTION.md#输入与安装一致性)，本轮未重新读取五 stock。原有准确安全拒绝、preview 与其依赖保持；本组不执行 native、客户端、getter、构造、上游项目、构建、JVM、数据库或服务。固定字段身份、目标版本和独立证据必须同时成立；已具名第三方 JSON、相同上游数据、数值大小、旧标签或样本中零值均不单独构成目标映射。

## A1：MAX cap

**状态：有界无新桥。**七个资源 Fork 仅重复既审的 `c021366...` 与 `9fe14a...` 提交；两个服务端仓库的未审分支 README 未表明与目标 energy schema 相关；两个单独的 GitHub 仓库/README 精确检索之一与四个公开搜索索引中的 MAX 字段词未返回候选。GitHub 全局代码接口因需要认证不可用，因此不能称全局源码零结果。

准确缺边仍是同 target `88602` 的一个字段/getter 如何到达 `24887` 的 `enum77/FIGHT_PROP_MAX_SPECIAL_ENERGY`；已验的 `enum78/START_SPECIAL_ENERGY` 是不同职责。既有候选的 `200/100`、START 的 `100/50` 或其余行 START=0 都不证明目标 cap 值、字段身份或 cap 缺席。

下一研究入口：由主线指定的 Sol max 在移除已拒动作及其产物后，只读复用计划中固定的 metadata、signatures 和已有完整消费窗，按 `88602`/`24887` 的真实类型与字段引用找**同值流到 enum77、且与 enum78 分离**的唯一可核 consumer 身份；仅有字段位式时不读取候选值选 cap。若没有这一边，就明确保留缺少具体赋值/消费边，不把普通缺输入写成安全拒绝。

## A2：Relic 新候选与准确边界

### 新的固定业务参考

[JettyCoffee/genshin-impact-extractors](https://github.com/JettyCoffee/genshin-impact-extractors) 的 README 称从 [AnimeGameData](https://github.com/Dimbreath/AnimeGameData) 提取圣遗物数据，遂先核 README，再在仓库外作不检出源码的部分克隆并只读相关文本，未运行其代码。2026-11-15 19:46:54Z `git ls-remote --heads` 仅列 `master@6d9301d269694005494efdb06c87879dc5449111`；tree 为 `40a9caebe851d0710c6225005cb6891f1d182cbd`，README blob `d909e2faa71ffe6b1d926a3b929100ac06d29192`、SHA-256 `5a5c7d621c203135a553a96205730bdd0ea416945c1b02e769a28477b7a913e1`。**该提交说明明确称基于 6.2 数据**；README 未固定输入 AnimeGameData 提交或提供国服 7.2 与本机 stock 的相同身份。

| 固定文件 | Git blob / SHA-256 | 可核实际关系 |
| --- | --- | --- |
| [reliquary/data_loader.py](https://github.com/JettyCoffee/genshin-impact-extractors/blob/6d9301d269694005494efdb06c87879dc5449111/src/extractors/reliquary/data_loader.py) | `d20bab0c3c0a3a23ebf75188c81d3d921bc69f33` / `d9a894cd893f26b5e02b3c645e17b8cf31131f041f5cf7e609c99386a766b9d6` | 从本地 `ReliquaryExcelConfigData.json` 与 `ReliquarySetExcelConfigData.json` 读取已解析 JSON，未绑定目标输入或二进制行 |
| [reliquary/info_extractor.py](https://github.com/JettyCoffee/genshin-impact-extractors/blob/6d9301d269694005494efdb06c87879dc5449111/src/extractors/reliquary/info_extractor.py) | `a20a94eb773fb1b28505ee1469a4b6018608fb3e` / `588d9192f961e2a59046f37e27c64e9521b77cc11abccb9fabc28fd81c53037b` | 将已具名 `setId` 和 `rankLevel` 分别输出为 `set_id` 与 `rank_level`；缺席默认值是该脚本策略，不是目标 presence |
| [reliquary/extractor.py](https://github.com/JettyCoffee/genshin-impact-extractors/blob/6d9301d269694005494efdb06c87879dc5449111/src/extractors/reliquary/extractor.py) | `a4de52135ab54d0873f4537d12494544b8ab572a` / `15a2b8ce8f6b3a4ea0f1e20c6fb727ecf9e117597db434a4e2ff0be5827e94ca` | `get_relics_by_set` 按输出 `set_id` 过滤，`get_relics_by_rarity` 按输出 `rank_level` 过滤 |
| [reliquary/set_extractor.py](https://github.com/JettyCoffee/genshin-impact-extractors/blob/6d9301d269694005494efdb06c87879dc5449111/src/extractors/reliquary/set_extractor.py) | `31766e7180ddbc9a9c5c25265bc1802874a5613d` / `a4e688717b1249acc9938fb5d905eb34dce14625985a1532b66434f4aaa6c465` | 套装表 `containsList` 组织部件，对部件 `rank_level` 取最大稀有度；不构成目标资源字段映射 |
| [models/field_maps.py](https://github.com/JettyCoffee/genshin-impact-extractors/blob/6d9301d269694005494efdb06c87879dc5449111/src/models/field_maps.py) | `932f676b9a3516e3fd552664f8365a76751255a7` / `6d990134dffb4793737a9fc75a2f577b6557c1e91b328d5db332bfede8babea7` | README 所说加密字段映射实际只含 Quest/Talk 等，无 Relic 混淆字段映射 |

许可需分开记：固定 [pyproject.toml](https://github.com/JettyCoffee/genshin-impact-extractors/blob/6d9301d269694005494efdb06c87879dc5449111/pyproject.toml) blob `0d56a93b59d5874bbc2c4b6f3a036b2ceba5c8c3`、SHA-256 `cd0bf6b1a47813d2d32958cf2c8851b4e76700b4852642616dc068da8ef6f95a` 声明 MIT；该 tree 无单独 LICENSE 文件，README 又写仅用于学习研究及游戏数据权利归属。许可与数据权利未进一步解决，本次不移植代码或资源。

此仓确有两条具名业务消费方式，但它们只针对独立传入的**已具名 JSON**，且与先前 AnimeGameData 候选存在上游数据谱系关系，不是同 target `46431` 或 `74316.field368811/+20` 的字段消费，不提供同源实际输入和独立非零 role oracle。它没有排除目标 rank/set 候选交换；也未在受检路径中出现 `ReliquaryLevelExcelConfig`，不能把此受限结果推广到其他源码。

主线同时转达 B 组初步发现 `tsukimoonmoon/YSRes@959db20438178a897bbc8948677a32ea5fdd2dbf` 的 3.5 参考 schema 含具名 rank/set 及条件位式；该参考由 B 组独立固定证据与写报告，A 组没有重新抓取或核验，不把它当作 7.2 目标桥。

下一研究入口：由 Sol max 只读复用既存 27 候选 typed 排除，分别将 46431 的派生/继承候选与**同类型实际资源查找**相关联，找准确字段到等级表 key 或套装表 key 的消费边；或者取得同源真实输入及独立非零 role 对照。两角色可独立验收，metadata 类型、UI 名字、数值大小或第三方 JSON 名称不能替代字段身份。

## README 与分支覆盖账

七个先前未审非默认分支的公开 remote HEAD 于 **2026-11-15 19:45:10–11Z** 用 `git ls-remote --heads` 成功枚举（Git smart HTTP，无分页；是该时点公开广告 refs，不包含未公开分支）。各 Fork 的旧默认 README 复用既存缓存，blob `bd5a62b302f19047d397c0e65f06883b86949109`、SHA-256 `ef04982fb35ca0b7e2b9c6035e9f6c8164df8de5440941278dcb8284d8a60e73`，声明 AstaPS 7.1。以下提交/树复用原报告，未重新抓同 tree/blob：

| Fork | 本轮 refs 结果 | 筛选/同源结论 |
| --- | --- | --- |
| `1F1FC`、`cjhlk`、`nyakochao`、`fengfeng65`、`Stellarysian` | `main@c0213668889ed05cbeb86660f424c3daf18bfacd`；`update-quest-excel@9fe14a67667e3bca4db026d9c98e452342e2b989` | 后者与既审 tangyanliang1 的同一个 commit/tree `3e5a05b61b2ea4139ee4989a10bb72f05cafe091`；其已缓存 README blob `ddd37d661112d617fd14a314b8ed0f792e45d393`、SHA-256 `afac0beda2c8844ad0fe4cda9804a0d21489bc68c3158d563598644c32b5f28f`，仅为资源说明；不重复源码 |
| `ANameless2`、`2406450951` | 仅 `main@c0213668889ed05cbeb86660f424c3daf18bfacd` | 无非默认分支；此 commit 与既审 develop 相同，tree `51b6e16211a5f2dc8e8520be7f50187ac212ee4d` |

七个旧默认仓库元数据称 MIT；属于相同 Asta 资源谱系，数据权利另核。两种实际 refs 输出的 SHA-256 分别为 `2e5f5d87db4a502212a7e81ca8e0cdfb2b838cd2e219339cbd54d55215cc53d6` 和 `93b5e76eab9be207753f47da9174e2acb5d946c5daa79ffb6e07c88651f71aef`。

又于 **19:48:35–36Z** 列两个服务端 remote refs，再只读已有仓外 clone 中未覆盖的固定 README；没有因分支名含版本而假设目标字段身份：

| 分支 / commit | tree | README blob / SHA-256 | 结论 |
| --- | --- | --- | --- |
| `MeChen618/AstaPS` `fix/7.1-fresh-born-intro@bbe0fe4ee3e118bf9b4c8bfc811865dc970fb192` | `64f561742d8e571e3bd0125543af82c5ac3804e3` | `39d0f41a805baf0aa265eb7445e6d168b434fdfd` / `a7773e6df92017c5df5d06d6ab8f317e4a6bc6bdfbf072179438d63517636f39` | README 声明 7.1、GPL-3.0，无 energy/Relic schema 说明；不展开源码 |
| `capybr2222/LunaGC_7.2.0` `main@d90b737f1ae82c715c4a680fdf3c62a630f37a9c` | `b1fe2122093b3a84912a27e39c23634328b8fb01` | `d4b96f11e346a0771ee834aa12185af8bb467dfd` / `fb37dafa2186d089248dc918e7b9bc01ca1f22a01a36ea8fd0c93f829fe2b9a9` | README 声明 6.6，无相关 schema；根无单独 LICENSE |
| 同仓 `6.7.0@6e96b91e4286da2e7eb2c95ed25da14267800d5e` | `c39fa6369b146c94681ab6a71d23ae52c79ecc4a` | `02b0d877c7c49008f364a0426fa248e42fd4b481` / `cdf5ae48579453d6e0c84def31af0e6e85a466829bdd1be7f53abdd33e24142f` | README 声明 6.7，无相关 schema；根无单独 LICENSE |
| 同仓 `7.0.0@8e5db4997d1967397c531b693352251babd9a0e4` | `0b6955c2007ca8f6902ebdeb201cd60ad772e57a` | `1d5a60ea53795b20d65417f7a678d216d8d4be5f` / `96f55c23a4e766cd1444fa47f073fade268711ae7a936d5030b1de6aadeec9be` | README 声明 7.0，无相关 schema；根无单独 LICENSE |
| 同仓 `7.1.0@a0b32dba9d7d07721c160ea37ff3d2d02d2854ee` | `2a7377e2fbd12dd0d00422b91e0da2be919e75cc` | `0d09b766ed1393b8260d1d349ae0a44d2b9213bf` / `2a1ed9ce3d3a67aa506219771c59e84c58864c6350c38438b18e2f8d015ef5d2` | README 声明 7.1，无相关 schema；根无单独 LICENSE |

另两个 `main@a49f51aa...`、`7.2.0@acc7a0b...` 属原报告既审提交。本次不重读其旧源码。对明确版本不适用/README 未命中的新分支，仅作上表筛选，不把未读源码称为零命中。

## 查询、失败与未覆盖

GitHub REST 仓库/README 搜索于 2026-11-15 19:45:40–41Z，best match，第 1 页、每页 100，下面两式均 HTTP 200、`total_count=0`、`incomplete_results=false`，原响应 SHA-256 均 `4af480b8ee5b87b369a76c49bd22c9a783908272ebffbe97898f8ab0f0772a5f`：

- `"specialEnergyCostMax" "schema" is:public fork:true in:name,description,readme`
- `"ReliquaryLevelExcelConfig" "binary" is:public fork:true in:name,description,readme`

**仓库描述/README 零命中不等源码零命中。**19:45:41Z GitHub `/search/code` 对 `"specialEnergyCostMax"` 返回 HTTP 401 `Requires authentication`，响应 SHA-256 `b7dbd173f33b19650f61b1c528737e2037cf768d90076fdfce5d32541765e29e`。其余同接口代码词未逐个请求；权限受限不写成 0。对入选 Jetty 固定 tree 的 `src/extractors/reliquary` 及 `src/models/field_maps.py`，以本地只读 `git grep --fixed-strings` 分别执行五词：`ReliquaryExcelConfig` 1 行、`rank_level` 5 行、`set_id` 8 行、`ReliquaryLevelExcelConfig` 0 行、`ReliquarySetExcelConfig` 1 行；数字仅为该路径命中行，不代表全仓全版本。

公开搜索索引的结果快照（不是 GitHub 代码搜索原页；排序、页码、总量和完整性不由工具公开）：19:46 左右查询 `site:github.com "specialEnergyCostMax"`、`"FIGHT_PROP_MAX_SPECIAL_ENERGY"`、`"special_energy_cost_max"`、`"max_fighting_spirit"`、`"ReliquaryExcelConfig"` 未返回结果；`site:github.com "rank_level" "Reliquary"` 返回上述 Jetty README。对应快照 SHA-256 依次为 `56960b6f4b68e73ee3d4cb5eb3ba08971be5d6c883f456d27994e4154bd476e3`、`66f6ecc272fc005c36d4f8ab23ec86fac79a0a52b09ce7d7ce9b56fb295e3faa`、`4580f6ad0f8be94ef4304342b3f25f3b749d0c0a4fb70b69e7179172c0d7b360`、`fa6c303f57627678bbeeedde7798e4a05745c54f43c47313f25ddb2f27900f8a`、`2b8157e8826c212d62d12e68d9926a20781eaaea1a7e3415ce3ee6c398910ab1`、`7697149c14f4a8cdcb3fe0347419f9cc99a3e523642a2970d789da7534383dd3`。

19:50:04–07Z 三个进一步限定索引词：`site:github.com "set_id" "Reliquary" "field"` 返回另一游戏的项目（版本/产品不适用，未取源码），`site:github.com "ReliquaryLevelExcelConfig" "7.2"` 与 `site:github.com "ReliquarySetExcelConfig" "7.2"` 未返回结果；快照 SHA-256 分别为 `1c2cee1e5a2c5df410e9ab5e102df8f053c7497c5b95a43dceed74af98eaec5b`、`fa151370727a4e41417116cd27dc2b180575dd0f4c53672845ef5a2d890f6e12`、`dacacbb044478a3705c6798898290e2b27d7c0f62592aab302f23cfd469ac167`。有限索引无结果不等外部不存在。

过程如实保留：最初并行的四个组合索引查询获得合并的无结果提示，但未留原页/准确 UTC，因此不作为正式零结果；另三词 `site:github.com "set_id" "Reliquary"`、`site:github.com "ReliquaryLevelExcelConfig"`、`site:github.com "ReliquarySetExcelConfig"` 已发起但保存时本地进程创建遇到 Windows OS error 206（命令行过长），结果未成功保存或展示，**不计入覆盖**；后续只做上面记录的不同、更限定词，没有复发原查询。Jetty README 初次保存后打印遇本机终端 GBK `UnicodeEncodeError`，后续直接复用已保存 README，改 UTF-8 输出后读取其他文件；未运行上游代码或重复下载 README。以上均非安全审查拒绝。未查其他未列公开 Fork、非 GitHub 平台或未命中 README 的源码；既有两个 Codeberg restricted 地址未重试，旧 `zornicza` 404 沿原报告，不与未搜索混同。

## 交付与证据定位

原响应、refs、固定文本和本组私有脚本保留于忽略目录 `local/migration72/d02-incremental-a-20261116/`。便于复核的清单 SHA-256：`refs-manifest.json` `863f057320696eb0826cb7941b79d3ee140565a2da3127561d7e4baa9f2c24ba`；`github-search-manifest.json` `e2f1e6e3aaf3409327f389c79fa1507b2a4014f09fd682da95c511c97380dcbe`；`server-refs-manifest.json` `246cfc5cba4572947ace2e1cc4ec78c1170d67d7f38315621a01e29ceb825239`；`server-branch-readmes-manifest.json` `43d3d4533e1322d5b292e6d8560bd9ae0c92bc11a0f92fa7d5e91bfd275f3e5a`；`jetty-manifest.json` `28f2f2fde6cb9c135e9f639a0d0b08ad7cc71f4b699549faedbe06fb968f1d95`；`jetty-files-manifest.json` `e9edd18592c9842ae1770cfe0e493ccd922b68ab40c8ef2800c94c4cfe81bc06`；`jetty-code-manifest.json` `a39ce97c9100119383eeaf227f1102730ef5716fffba744ecb3849cf5455600a`。

本组仅新增此共享研究文档；未改共享状态、运行代码、资源、Git 索引或提交，未做客户端验收，也无构建或部署。后续集成与本地 Git 提交由唯一集成者负责。两项均为有限来源研究出口，不升级父项。
