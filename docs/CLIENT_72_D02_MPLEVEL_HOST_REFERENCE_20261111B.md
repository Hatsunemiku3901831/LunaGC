# F-03d3s2 / MPLEVEL-ACTUAL-HOST-REFERENCE

2026-11-11：对主线已验真实 byte array 做纯 Python host 读取和完整逆向重编码。输入 payload 为 782517 字节、SHA-256 `25bf9c7e24ca9d708b39deafe361c0a339daf9cbb5aadc6b61a2324e57cd891e`；入口核对固定来源、target 第三次审计及对象中实际 LE32 count→payload→3 字节尾部关系。此结果是该文件从 offset 0 开始的 framing 和有序数据证据，不是自然 reader 的执行证明。

按已验 positive / N>0 语法读取：U8 prefix、U32 组数，随后每组 U16 key 长度、key bytes、U32 child 数及各 U16 child 长度和 bytes。文字以小端 8 字节块加固定常数，短尾按实际宽度保留；没有字符串对齐、文件补齐或 offset 搜索。解析结果按原序保存每组、每个字段的实际位置、解码 bytes、SHA 和 ASCII 视图，不把条目转为字典或合并重复项；非 ASCII 数据仅保留 bytes。若 prefix 的相应位为零或 N=0，分别在 cursor 1 或 5 停于 `unresolved-shared-empty-branch`，不构造业务空字典，也不声称该分支的原生返回行为。

实际样本 prefix raw `6a`、decoded `a0`，N=707，总 child=15835；解析结束于 782517，剩余 0。原序 key 无重复、key 和 child 字符串无空值，5 个零 child 数组的 ordinal 为 450、461、502、511、575；本样本解码 bytes 均为 ASCII。这些是样本事实，不证明原 comparer/insert 行为或共享空数组身份。完整消费是本样本的验收条件，不能据此声称 native consumer 本身有 EOF 门。

唯一包含精确 child bytes `MPLevel` 的组是 ordinal **544**，原记录 `[731264,731558)`、294 字节，SHA-256 `08d0ca8d1eb5afaf7d998dfb64a2ab3cfbd6d380f6e6c60345eaa53c76d937a1`。key 是 `Data/_BinOutput/Gadget/ConfigGadget_Scene_World`，47 字节、从 offset 731266 开始，解码 bytes SHA-256 `8ac986bbf8379bc4f8c85eb0060e3705e9e1e3293df8c404617067235d777cf2`；child count 在 offset 731313，实际值为 21，第一个 child `MPLevel` 的 bytes 从 731319 开始。21 个名称按文件原序为：

1. `MPLevel`
2. `Team`
3. `Enviro01`
4. `Enviro02`
5. `Enviro03`
6. `Grass01`
7. `Water01`
8. `BrokenIce01`
9. `Tree01`
10. `Bush01`
11. `Bush02`
12. `Bush_NoFire01`
13. `Bush_Yellow`
14. `Bush_Ashes01`
15. `CustomTile`
16. `Foxtail_Grass01`
17. `Foxtail_Grass02`
18. `Windmill`
19. `Oil`
20. `Storm_Lightning`
21. `Storm_Lightning_New`

## 验证及范围

主线已实际执行 `python tools/migration72/scene_mplevel_actual_payload_host.py --output main-third`，四份对应原件与最终 fresh3/fresh4 逐字节相同；随后纯 `--audit fresh3 main-third --output audit-third.private.json` 通过，698 B，SHA-256 `7d6fa5456742d0227d0599ec16a8f70ceb5c81dd78bb91515d9cb676cb5cfa56`。主线重跑 6 项测试通过。下列入口是复现命令，不再是待执行验收。

从实际解析所得值独立逆编码各宽度、count 和全部文字，完整 782517 字节与原 payload 逐字节相同；该检查佐证本样本范围覆盖，不代替正向解析或自然运行。事后与冻结探索 oracle 的全部 707 条组和 15835 条 child 的顺序、位置、长度、显示值及 decoded SHA 对照一致；oracle 没有作为有序结果的生成输入。

- 最终 `fresh3`、`fresh4` 的 pre、完整有序结果、唯一分组及逆编码四份产物逐字节一致。纯 `audit.private.json` 独立重算实际字段边界、长度、逐块逆式、decoded SHA/ASCII、重复和空值统计、唯一分组与全文件 inverse；它不调用解析主流程、native 或 decoder。
- `python -m unittest tools.migration72.test_scene_mplevel_actual_payload_host -v`：6 项通过，覆盖两常数的 64 位溢出及 1–7 字节尾部、精确 cursor、多个 group/child、K/L/M 为零、重复 key 原序、非 ASCII bytes、clear/N=0 准确停止、source SHA 漂移先于解析，以及固定实际样本全文件 inverse。
- `fresh1` 因成功分支的错误提示提前访问不存在的 `cursor` 触发 KeyError，失败现场保留；修正提示后 `fresh2` 成功，最终以加入独立审计后的 `fresh3`/`fresh4` 为准。主线第三入口：`python tools/migration72/scene_mplevel_actual_payload_host.py --output main-third`；随后可用 `--audit fresh3 main-third --output audit-third.private.json`。完整偏移与有序结果留在忽略目录 `local/migration72/d02-resume-20261111b/scene-mplevel-actual-host/`。

已验 caller 将 PathHash 第二 QWORD 传给 reader 工厂，并将返回 reader 交原 group consumer；传入的两个零尚未绑定为 offset/count。reader42005 的实际 backend、初始 cursor、refill 状态、原生字典返回以及 World factory/slot114 生命周期均未验。该 key 与名称不证明 World 对象或 child 配置正文。`nativeReaderExecuted=false`、`readerFactoryOffsetOrBackendProven=false`、`nativeDictionaryReturned=false`、`worldFactoryReturnProven=false`、`slot114LifecycleProven=false`、`sceneReady=false`、`d02Passed=false`；无客户端验收或部署。

冻结的[早期实例/字段计划](CLIENT_72_D02_SCENE_CONFIGMETA_SERIALIZED_20261111B.md)及[实际 payload 分析](CLIENT_72_D02_SCENE_MPLEVEL_ACTUAL_PAYLOAD_20261111B.md)保留当时停止点；其机械验收后继以本页和 Project 为准。下一项是[准确 reader 工厂及 World/Pool 依赖分界](CLIENT_72_D02_SCENE_READER_FACTORY_SUCCESSOR_20261111B.md)，Terra 正按单窗规格实现；不重复原 parser、字典或文本静态审计。
