# Skill 元素后缀三行 host 验证

`R3-SKILL-ELEMENT-SUFFIX-HOST-PILOT-20261116` 已完成限定验收：按[已验后缀来源合同](CLIENT_72_D02_SKILL_SUFFIX_SOURCE_CONTRACT_20261116.md)，一次实际纯host执行核两个既有非零控制，并从固定raw推导一个新element字段。没有执行native、PE读取、ctor、reader、TLS、getter、JVM、DB或客户端。

| tableKey | 范围 | element位置 | 实际逻辑值 | 结果 |
| --- | --- | ---: | --- | --- |
| 10068 | 旧Wind控制，`[5550,5699)` | 5690 | Wind / 7 | 与既验片段一致 |
| 11364 | 独立具名Electric控制，`[69683,69828)`；不在371盘点/cohort | 69820 | Electric / 4 | 与既验具名消费者返回一致 |
| 10019 | 当前Depot201的一个energy引用，`[1481,1594)` | 1586 | Ice / 5 | 唯一新字段；不预设候选值 |

9个来源、固定spec及raw的大小/SHA全部通过；113条尾窗、三个key/span、两个已知mask、当前Depot引用和独立控制链一致。整数mask与独立byte-index取bit一致；逐字节带carry加法与U32公式、逆序列化一致，三个尾部cursor均精确等于既验end。没有重新解raw id，key→id仍仅复用旧身份来源关系；没有读取成本/特殊能量或早期String正文。

新raw语义恰20B：11364首8B mask、三行各4B元素。固定raw 141702B仅一次磁盘读取/hash，407B选中span及16B已有mask的hash都来自同一buffer，另外记账；OS/文件系统缓冲与解释器loader IO未计量。结果9154B、manifest9543B，合计18697B低于65536B。

原件为忽略目录`debug/d02-skill-energy-search-20261116/element-host-pilot/`：`result.private.json` SHA-256 `168d9f9cefaea799455bb1c503d7c177348e04e82695ab3b48a42d304cb886f4`；`manifest.private.json` SHA-256 `1901802945c46a92089d6de6093c4251d8a09d200f954216aef135a8f0bebd92`。工具[client72_skill_element_suffix.py](../tools/client72_skill_element_suffix.py) SHA-256 `af710f45edce75fd3718f186fd10e0ae9192a38139db401685eb261ec0b8317c`，复现命令为`python -X utf8 -B tools/client72_skill_element_suffix.py`。固定输出目录已有保护，不直接覆盖重跑；新批量集合须先独立固定输入、范围和预算。

准备期只读schema检查曾假定flags行字段为`key`，触`KeyError:'key'`；未读raw或产值，随后按实际`skillID`结构实现。这是执行前包装准备错误，保留摘要于`element-tool-preparation/preparation.private.json`，摘要不是原console字节，也不记为pilot执行失败/成功。没有追加镜像测试或重跑既验suite；验收使用上述实际固定输入、双控制和独立位/算术检查。

输出始终`fragmentOnly=true`、`idRawDecoded=false`、`resourceReady=false`。pilot实际产物只覆盖10019的element单字段，原回执记录当时367个完整片段队列。随后主线已接受[logicalID=tableKey的既验绑定](CLIENT_72_D02_SKILL_SUFFIX_SOURCE_CONTRACT_20261116.md#logicalid-与-tablekey-的既验绑定)，下一批可据此合并canonical id/element，不再把raw-id复解列为前置；原回执和旧spec保留不覆盖。120正向energy绑定、121行三项成本/特殊池字段和完整Skill仍待各自验收，不生成Java空行或晋级完整恢复/R3。A/B、preview及准确依赖冻结保持；未改运行代码/资源/配置，无需构建部署或重启，D-02/F-05/D02-R6仍0/6。
