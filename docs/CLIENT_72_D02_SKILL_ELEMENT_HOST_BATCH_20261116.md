# 旧号 Skill id/element 批量片段

`R3-SKILL-ELEMENT-SUFFIX-HOST-BATCH-20261116` 已完成限定验收：一次纯host执行输出369个真实`{id,costElemType}`片段，覆盖367个本轮准入及2个旧准入，全部对应固定121 Depot的正Skill需求。id复用[已验同row逻辑key绑定](CLIENT_72_D02_SKILL_SUFFIX_SOURCE_CONTRACT_20261116.md#logicalid-与-tablekey-的既验绑定)，没有新解raw id或执行构造/reader。

| 分类 | 实际范围 |
| --- | --- |
| 正需求 / energy | 369个不同正Skill，121个不同energy，全部energy均有原元素token |
| 正式旧准入 | 10067 None0、10068 Wind7，共2个；沿用原正式Java准入范围 |
| 本轮367 | present120、absent247；10019 Ice5复用pilot，其余119个present各读4B，共476B |
| absent语义 | 原mask bit25=0时，固定原码`ECX=0 → JE → DWORD[row+54]=ECX`明确写逻辑0/None，无元素word或旧值读取；不是JSON缺省或Depot缺引用fallback |
| 11101 | 旧mapping有片段但未进原正式行准入/JVM；本轮按已验absent分支和logicalID绑定新闭合，0额外raw语义读取 |

369行实际元素计数为None248、Fire18、Ice21、Wind20、Electric19、Water17、Rock14、Grass12。11364 Electric4仅复用[原pilot](CLIENT_72_D02_SKILL_ELEMENT_HOST_PILOT_20261116.md)控制，不计入cohort。独立byte-index、逐字节carry算术/逆序、target/Java名字和值、同row跨度及精确尾cursor均通过；没有使用候选JSON作值或oracle。

规格`debug/d02-skill-energy-search-20261116/element-batch-spec/spec.private.json`为305899B，SHA-256 `84de972aad8fc82e3008fb57dd4a3d891f505eea49a1da26ff8c27eaf25e3700`。新语义476B、0新mask语义；raw141702B一次磁盘/hash，43048B选中span与2936B已有mask hash来自同buffer，OS/loader IO未知另列。结果389661B，SHA-256 `7dcc33e85b70a208e003a54d05a7a88717743d658eb7bd742ad26b0cb4572057`；manifest SHA-256 `e33c239a305550cf6126b2cdedf0e6dc598f69a0b40e533a01a1a3d7b8f2ac5d`，合394668B低于1MiB。原件留`debug/d02-skill-energy-search-20261116/element-host-batch/`。

复现入口为[client72_skill_element_suffix_batch.py](../tools/client72_skill_element_suffix_batch.py)，命令`python -X utf8 -B tools/client72_skill_element_suffix_batch.py`；现有输出目录不可覆盖。仅离线工具/资料，无native/PE/ctor/reader/TLS/getter/JVM/DB/客户端执行，无运行变更或部署。`fragmentOnly=true`、`idRawDecoded=false`，`resourceReady/fullDeclaredSkillRow=false`；不是完整Skill或成本资源。

367个id/element片段的缺口在此限定范围关闭；下一项为真实ResourceLoader加载369 Skill片段及121 Depot三键并核正向energy同对象/element，尚未由本批执行。121行`costElemVal/specialEnergyMin/Max`、完整Player/postLoad/recalc/save及R3仍未闭，A/B/preview和准确依赖继续冻结，D-02/F-05/D02-R6仍0/6。
