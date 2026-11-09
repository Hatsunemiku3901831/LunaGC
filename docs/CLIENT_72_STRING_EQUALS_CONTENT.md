# E-01/R3-ITEM-ENTRY：String 原字符比较路径

本卡接续[原10339入口前缀](CLIENT_72_STRING_EQUALS_SUCCESSOR.md)，完成两个正式 fresh 组：每组14个不同 owned 地址、等UTF-16长度的 case，均从本轮实际 selected10339 入口进入，经原48B可达前缀、原尾跳、EqualsHelper2908 后返回原 caller。28次实际调用全部得到预期 `AL`，没有 emulator error、未提供读取、captured 修改或额外开发调用。这里只证明这些明确提供字段的字符路径，未恢复完整 StringComparer、GetHashCode、FindEntry 或自然业务键消费。

## 固定来源与允许代码

旧两组报告保持 SHA `32fd295fc85a82165def82fb990b0eba915fce80767e6c39768ebd9e49701fcc`，主线 review 保持 SHA `56ed0eb0ea9e9fb0e865ac2fdc7f6fd929981dddca40f7471e76b682c17978d9`；只读复核，没有重跑旧调用。共同 raw 保持 SHA `1a2e7509f6149f995cc0cc5c4ddc8f0c3f8acd297afcfc27c1eb9d5caee9c63e`，批准 wrapper 保持 SHA `07494634d3d46ee26807e0f96672b3378bd50fa7451786801c988a86b0bea08e`，原共同图边界和输入权限不扩大。

主线本次取得唯一 `.pdata` 对应的完整 helper：`string-equals-helper-window1/manifest.private.json` SHA `a6b2d2073afea9835f328d45f24ba697da3f29b90a78c3225b6fd69c4049057a`，范围 `159aac320..159aac3d1`，177B，code SHA `435647e6a8b42388fe9f94a375c6822b6c2958859b0586b2a91cf851eea5b5fd`。12B pdata 为 `20c3aa19d1c3aa1994714205`，SHA `f9ef3469725d093ce01a5a5b9466c951df5498b2c2d3cfc27e1de41848724ff7`。完整连续解码实际为53条；按 manifest 实数记录，不沿用派工中的49条计数。

10339仍没有 `.pdata`，完整 extent 仍未知。执行白名单只有其实际入口可达48B/19条，以及 helper177B内的完整原指令；`159b6f330` 后续字节、`159b6f33e` 的不完整 `418d` 均拒绝。helper 的两个 null 分支在 `159aac3c6/159aac3cb` 调用未审 `1404c2be0`，以及 `159aac3d0 int3`，均在执行前拒绝。本次全部从原10339非空且等长的路径进入，没有到达这些边界，没有新取 callee。

固定 metadata 和 signatures 继续证明 String293 的长度 `+10/int32`、字符 `+14/char`，10339和2908的两个参数均为 String/typeReference33。return metadata 仍是 `not-recovered`；断言只看实际 `AL`，不把完整RAX当bool。代码按同轮 captured ASLR加载；54个完整来源 pin在两正式组前、报告定向测试和最终闭包审计均匹配，不读取当前 stock 或 GAME_PATH。

## ABI、字段和原始读取

每次入口 RCX 为同轮实际 Default object，R9为同轮10339实际伴随 MethodInfo。原前缀将原RDX/x搬到RCX、原R8/y搬到RDX，再执行原尾跳；helper入口的RCX/RDX为两个owned地址，R8仍是原y，R9仍是原伴随MI，RSP与原caller return保持，没有为helper另造MI。进入helper后，原指令自行使用r9d保存长度、r8d保存剩余长度；这些寄存器变化来自原代码。

fixture只提供4B长度、准确UTF-16内容及显式2B NUL字段，不提供对象header或String class，也没有借1243充当String class。页填充不成为合法字段，任何越界/header读取都会拒绝。全部case地址不同、长度相同；空串也使用两个不同地址。

helper的已执行非空路径读取x长度，按12个UTF-16 unit比较三组qword，剩余部分用dword读取。每条原指令、运行地址、执行前RSP、每个owned读取的地址/宽度/精确bytes及reader、tail/helper寄存器、caller8B和全部captured终读保存在报告。每组130次owned读取/752B，原输入69次审核/2704B及69次完整collector/clock重放/2704B，另有14×34次captured精确终读/18816B。

长度为奇数的尾部dword会读取显式提供的NUL。每组在 `one-equal`、`one-diff`、`embeddedNul-diff`、`13unit-equal`、`25unit-diff` 的两侧各发生一次，共10次；读点分别是原helper `159aac390/159aac392`，精确dword为 `41000000`、`42000000`、`43000000` 或 `6d000000`，报告逐条记录重叠的两个NUL字节。该结果只验证这些owned终止字段，**自然分配器是否提供相同终止存储仍未验证**。35unit case在中部qword差异处提前返回，未读其尾NUL。

## 两组实际结果

| case ID | fixture概要 / UTF-16 unit数 | AL | 每组完成原指令 |
| --- | --- | --- | --- |
| empty-distinct | 两个空串 / 0 | 1 | 28 |
| one-equal | A / A，1 | 1 | 38 |
| one-diff | A / B，1 | 0 | 33 |
| BMP-equal | 中文 / 中文，2 | 1 | 38 |
| BMP-diff | 中文 / 中问，2 | 0 | 33 |
| supplementary-equal | 🙂 / 🙂，2 | 1 | 38 |
| embeddedNul-diff | A\0B / A\0C，3 | 0 | 42 |
| 12unit-equal | abcdefghijkl，同值，12 | 1 | 43 |
| 13unit-equal | abcdefghijklm，同值，13 | 1 | 53 |
| 24unit-equal | abcdefghijklmnopqrstuvwx，同值，24 | 1 | 58 |
| 25unit-diff | 前24unit相同，末尾A/B，25 | 0 | 63 |
| 35unit-mid-diff | a×35 / a×16+b+a×18，35 | 0 | 47 |
| owned-source-key | Weapon_Sword_Metal，同值，18 | 1 | 71 |
| near-key | Weapon_Sword_Metal / Weapon_Sword_MetaL，18 | 0 | 66 |

`owned-source-key`仍是明确提供的fixture文字；不据其名称宣称自然键、实际dictionary caller或配置消费已验证。embedded NUL case继续比较后续B/C，补充字符case按两个UTF-16 unit提供，未扩展到规范化或全部Unicode语义。

独占目录 `local/migration72/d02-parallel-20261109/string-equals-content/` 的 `first.private.json` 和 `repeat.private.json` 均为608803B/SHA `215969b45954049249bd08fccca28c6ac2bfe051b7004b16b2d285b4f56ee640`，逐byte相同。每组14calls/651次观察/651条完成，14个原helper ret均回到原caller；累计28calls/1302条完成，0失败、0额外native。每连续call仍限2M，总理论上限56M，没有旧getter/allocator执行。

每组host744次/24976B，限制4096/65536/单读208B；caller14次/112B，限制16/128。累计host1488次/49952B、caller28次/224B属于两个独立预算组。argv、起止UTC、耗时、退出码、stdout/stderr、两份准确工具源副本和测试源副本全部保留，不丢弃失败或重置调用计数。

## 只读验证与交付边界

新增 [执行工具](../tools/client72_string_equals_content.py) 13605B/SHA `34d69cb182633286cee0cd8186b05913a6561942b1487714faae60b103425f8f`，以及 [定向测试](../tools/test_client72_string_equals_content.py) 11743B/SHA `f34a64130b07959300e728f4180082a16bea1e8e6a85f50d327588ad0fb6ba02`。先执行5项无CPU静态/负例预检，再执行两个正式组，随后13项只读报告/来源/guard检查通过；补强准确终止字段case和预算断言后，这13项只读检查再次通过：

```powershell
python -m unittest tools.test_client72_string_equals_content -v
```

检查覆盖精确extent与source closure、owned header/未提供尾字段/captured缺字节/字段漂移/未审callee拒绝、全部14个AL、原入口与尾ABI、逐条完整原指令、RSP恢复与caller return、逐owned字节及显式NUL来源、476次captured终读、两组预算和父项false。测试没有额外CPU调用，未重跑旧测试或旧native。

本卡只改本tool/test、本页和独占忽略证据。未操作Git、旧工具/合同/Project/Harness、当前stock、网络、REA、客户端、构建、JVM、数据库或部署；本地提交、主线审核与项目/交接更新由主线处理。仅离线工具/测试/文档，无GS重启对象。

## 主线验收与暂停边界

2026-11-10主线只读审核通过15项，证明 `string-equals-content/main-review.private.json` SHA `214c0e0b3a9b9a2b3e39c0e3c53d8a141e8ad0461119595c9881a1126de99475`。独立核对两正式组、54个来源pin、14个预期AL、逐条原opcode/ASLR、原Default/伴随MI及尾ABI、全部owned字段、10次显式NUL重叠、476次captured终读和完整预算。主线13项只读测试通过；没有再执行native调用。

研究交付时的原文快照保存在 `closeout-20261109T164643Z/before/docs/CLIENT_72_STRING_EQUALS_CONTENT.md`，SHA `212659f59bb64bc4b95b1e56ef602a1eda09e8a1a87ffd327d1d783f63a798cf`；以上私有相对目录共同位于 `local/migration72/d02-parallel-20261109/`。原报告及source closure不改写。

用户随后要求完成在途工作后暂停。本项已完成限定验收，未开启自然消费者、GetHashCode或FindEntry后继。Default创建、自然allocator终止存储、完整Comparer、自然caller/键、配置/ability、完整Avatar/g2及D-02均未晋级，原共同输入范围不扩大。后继只在用户明确恢复后重新界定输入与预算。
