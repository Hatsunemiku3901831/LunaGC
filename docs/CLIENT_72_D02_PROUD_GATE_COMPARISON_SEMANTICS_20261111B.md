# E-01d1 / Proud 门槛候选的本体语义与下一原边

本项复用[944 B 单窗机械验收](CLIENT_72_D02_PROUD_GATE_COMPARISON_WINDOW_20261111B.md)，只分析封存原件。**该本体没有读出同 typed81127 的 `+10` 或作突破比较；它的 `14d4aa840` 调用链仍未知，未被排除。** 不依赖热 Equals、自然 map hit 或实际 Proud 行。

## 944 B 原值流

准确声明为 owner26342/m204056，static、唯一参数 class18163。原 `14d4aa4a0` 将该 Avatar 保存到 RSI；`4c6..4d7` 从同 Avatar `+2b8` 复制 typed32758 的16 B并调用其 getter。随后 `4e3/4f4/519` 沿原 `+212b8` map、signed-index helper 和24 B entry取 RBX。这里没有恢复 query 的声明返回类型。

RBX在 `5b5` 被写入新结果候选26348的 `+10`；该字段声明 Depot22284。其原消费是 `523/5b9` 的 `RBX+30` 数组及 `5e1` 的 `RBX+6c`。既有[字段桥梁](CLIENT_72_D02_RESOURCE_BRIDGE.md)及固定 `r3-mapping/main-third.json` 已批准二者分别为 regular skills / energySkill；本轮直接复用，不重跑 parser、组/等级用途或 Java。前两数组 DWORD由 `5da/5dd` 保存，`5ff/67b` 经原58209 getter；`+6c` 经 `5ed` getter后保存到栈。

`612/68e/700` 三条原 E8均到 `14d4aa840`：RCX=同 Avatar `+50` 的 class34054，EDX依次为上述两个 skill ID与energySkill ID，R8D=原常量1/2/3，R9=同 RSI Avatar。原 helper返回null时分别跳过对应的结果字典写入；这是 skill结果的null分支，不能称为81127的门槛跳过。

helper唯一声明为26342/m204057，参数精确为 `class34054,U32,enum71821,class18163`，返回类型未恢复。实际 Proud 类型联系存在：34054继承 `69137<63831>`，结果字典值26324的 `+18`也声明63831 Proud wrapper，`+10`则声明Skill88602。enum71821的具名成员含NormalAttack/Skill/UltimateSkill；本轮没有另恢复其数值。以上不足把任何81127元素接入该helper，故不沿此树取新窗，也不声称整链无门槛。

本体中的 `+18` 分属 map entries、数组长度及26348结果字典；没有把它们混成Depot `+18` 的81127数组。完整238条中直接从RSI取字段的指令只有 `4c6/+2b8` 和 `536/+50`。hotfix尾跳与所有callee仍保留独立未知。

## 准确 query 边的后继

复用73候选，仅对七个准确 `E8→14d6d70f0` join固定metadata/完整参数。四个声明属于MonoGachaItemSlot/Poster；两个owner21718/43499直接继承AnimatorEvent、声明参数同为class88192与Nullable<AnimatorStateInfo2743>；其余唯一声明上下文为65145/m540714。该方法五参数为class32044/29191/51565/28174/58582，没有直接Avatar18163、Depot22284或81127参数。其他六个仍unknown，不据UI/继承名排除业务。

下一只读候选已冻结为 `14a621790..14a621aab` 的795 B：原site `14a6218fa e8f1570b03` 到准确query；普通AOT caller/query槽分别 `142cc5620/142ca9ae8`，原pdata `9017620aab1a620a70510d04`。新实际slot、指令边界及返回值流待机械核验。只查该query输入来源与返回是否连到同81127元素，不追callee；cap1024 B/512条，0callee/xdata/live。私有请求SHA `6a1eb6a7e50d0ae042eb3f00c02c7caee958e4184e1cfe84d09dfaf81b2a1f74`，不是新消费者结论。

## 离线复核与范围

原944 B SHA-256 `ab0370d2a556bdde048cfda664fe41d8cc7f6d7e18cc0af889904c9b419241cb`；metadata cache `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`、完整参数报告 `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`；复用字段报告 `d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e`。五stock身份由机械原件保留，本轮没有读取当前PE。

私有 `proud-gate/semantics-audit.private.py` 两个fresh及主线第三输出均46067 B、逐byte相同，SHA-256 `5f8fdd6bd72474830f990862e704bc40f705c8f4f4b98fecd3ace0067934b0f4`。核29来源、九原件对、完整raw/asm覆盖、55锚点、11 typed字段、三条helper E8及七query声明。复现使用新basename：

```powershell
python -X utf8 -B local/migration72/d02-resume-20261111b/proud-gate/semantics-audit.private.py --output <new>.json
```

新增目标code/PE读取、CPU/native/live、JVM/数据库/客户端/部署均0。未触A第二返回或B组合loader的拒绝动作及产物。真实Proud行、实际map hit、同81127门槛、Java比较/算式、完整Avatar、resourceReady与D-02均未因此通过；共享状态与本地提交由主线处理。
