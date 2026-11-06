# F-03d3e8：双 Object 与完整 Avatar raw 图

完成 51050 的双 Object 与全部非空组合：**复杂 53824 闭包 34 类、237 wire；Avatar 根闭包 65 类、372 wire**。分别由完整非空图实际进入全部 34/65 个消息 reader，并逐对象对照独立预期，不再以未研究空壳代替依赖。AvatarInfo 的组合实际进入 62 类，外层全部37字段；Avatar 根全部10字段，完整图结果不含 `untracedType`。

这里只将 `fullComplexRawClosure`、`fullAvatarRawClosure` 设为 true。`targetProtocolReady=false`、`runtimeStatus=not-tested`、本卡 `semanticIdentityStatus=unresolved`，未知字段仍未知；不批准业务名称、完整业务 Handler 或发送集合，不修改生产/全局映射/准入，不代表实机 D-02。原来65类368个metadata存储和复杂34类238存储是静态数量，不能当wire计数。

## 固定来源与完整闭包

两个正式全新进程均重新读取并核验 `extract_client72.HASHES` 的三份客户端输入，与[核心](CLIENT_72_AVATAR_CORE.md)相同，重新提取 metadata、全部有界usage cells、51050 reader/cctor引用、具体AOT。工具固定11份已验摘要的SHA-256，覆盖外层/core/trees/items/e1至e7；来源表保存在[新摘要](../protocol/cn-7.2/avatar-complete-raw-observations.json)的 `sourceEvidenceSHA256`。原64类精确schema与新51050合并后，类型集合恰与原静态65类闭包一致，保存全部 `closureMessages`，未重复或漏收类型。

51050 的两个Object槽共有三个wire分支，另有两个只作判别的存储槽：**12个metadata槽实际对应11wire**。512次tag1..128/wire0/1/2/5原生扫描覆盖tag100；全部新增wire都有独立非零样本或对应boxed分支样本。

| tag / wire | clientField / offset | raw |
| --- | --- | --- |
| 1 / 0 | IPECOOBLNDN_1 / +38 | boxed uint32，判别+64=1 |
| 2 / 0 | IPECOOBLNDN_2 / +38 | boxed uint32，判别+64=2；与tag1互斥 |
| 3 / 0 | HLCHKOOIDIF / +60 | uint32 |
| 4 / 0,2 | FEKDEEENNBB / +18 | uint32 list |
| 6 / 2 | KCBHOJACCKP / +30 | list→63939 |
| 7 / 2 | GACCOGGKOOC / +48 | →22404 |
| 8 / 2 | KFELKFDIMCO / +40 | →87867 |
| 9 / 2 | BEDHPILKHEH / +20 | →57431 |
| 10 / 0 | OKDKGHLCKDL / +58 | enum48370，以int32 raw读取，不批准枚举语义 |
| 11 / 2,5 | KGDFJBJMNNJ / +50 | float list |
| 100 / 2 | IDBPHIMMHJP_100 / +28 | →18849，判别+5c=100 |

上表offset为十六进制。`PNOEONGHLPL`/+5c与`BFCOKBPMILJ`/+64仅是两个判别槽，不另发wire。两个boxed-u32分支的primitive usage来源和tag100真实18849 class来源均保存于有界reader引用审计。Tag100不是假设的第三boxed值，也不把Object统一当message。

## 原生复制与多map上下文

51050 reader `0x150149410` 执行到返回；消息cctor `0x150148b30` 实际组装u32/tag4、message-list/tag6、float/tag11 codecs。新具体message-list实例25060/37610→63939由本次AOT重新解码，reader为`0x158dc41f0`。

重复tag100时，原生分配新18849，然后调用 `0x151509de0` 复制旧对象两份u32列表，再读取新payload并替换Object槽。两次重复输入实际调用该copy两次和原生range-copy `0x158db7cd0` 四次，得到 `[1,128,7]`/`[2,255,8]`；最后空tag100仍保留这两个列表。重新提取35584/11的具体Add AOT wrapper `0x158db87c0`，从固定EXE验证其JMP目标正是range-copy；不能只依据相同函数名宣称复制执行。托管数组复制底层沿用已验显式模型，不手写最终容器值。

完整Avatar根复用已验f1 `CombinedMessages` 的按实际codec分流，新增Team map与AvatarInfo两份message-map共存；原float/u32/string maps保留。原始class保持，尤其51050 class沿用34045原生codec已捕获的对象，不重新分配覆盖。保留Item copy的dictionary计数包装，另执行已有same-item-six嵌套map覆盖/list追加原生夹具，验证合并root后不会破坏该合同。

首个完整图确实暴露观察器集成缺口：signed-list入口遍历对象时遇到StringMap adapter17964，而它没有消息schema，引发KeyError。原失败 `local/migration72/logs/avatar-complete-raw-first.log` 保留。只在本工具的完整图观察器把字段归属枚举限定为实际消息owner，再调用原primitive观察器；未改已提交e4或公共runtime，未替换原生reader/Add。修复后重新全新进程执行。

## 独立正向、错误与精确范围

24组正向图包括完整51050、53824、AvatarInfo、Avatar根组合以及双Avatar非空列表。完整图沿用e1至e7所有非空依赖；Item两份列表各9个非空元素覆盖全部分支，Team/Avatar message-map、float-map、u32-map和string-map都非空。Team与Avatar maps交错使用相同key7，最终只替换Team的value，Avatar属性/map子对象保持独立，字符串map仍保留 `A→300`。

boxed tag1/tag2分别覆盖非零/u32上界、显式零，1→2→1零切换保留active零；切换+38 Object不改变tag100的+28 Object。tag100显式空为active空对象，重复复制合并如上。float-list packed/unpacked混用保留负值、负零、最大finite、最小subnormal，原始bits和实际四字节Add写点 `0x1571dc0c9`留在摘要。int64 min、2^53+1与uint64 max在深层嵌套及第二Avatar仍精确保留。错误wire/未知tag128跳过、empty packed、重复标量零与四种根empty均已验。

9个拒绝分为4个显式输入模型错误（非法tag、boxed-u32 overflow、float packed截断、u32 packed截断）与5个真实ReadMessage错误入口（tag100负长度/截断及三层不同递归限制），到入口即停，不执行完整throw。无研究范围空壳拒绝剩余不等于所有合法/非法wire行为已穷尽。

managed metadata/class/null-policy、托管分配、boxing、primitive input/codecs/delegates、UTF8字符串、固定64容量集合与底层数组复制仍为明确模型；具体reader、限额、list append、message copy/map adapter/setter实际原生执行。float/signed helper仍仅此前已验packed flag前缀，未扩大成完整反射cctor；IFix、live ABI、实际客户端收发/Handler/资源/SDK/生产发送和实机均未因此通过。

10项新增、166项关联研究回归实际全过；双正式全新进程各核三hash/11摘要hash并重新提取/扫描/非空图，完整轨迹和脱敏摘要逐字节一致。证据 `local/migration72/evidence/avatar-complete-raw-{native,native2,summary,summary2,determinism}.json`，日志 `local/migration72/logs/avatar-complete-raw-{run1,run2,tests}.log`；原失败和早期草稿保留。仅离线工具、测试、摘要和本文，不需重启或部署，Project/Harness/全局审核及提交由主线维护。

```powershell
python tools/trace_client72_avatar_complete_raw.py --client $env:GAME_PATH --output local/migration72/evidence/avatar-complete-raw-native.json --observations local/migration72/evidence/avatar-complete-raw-summary.json
python -m unittest tools.test_client72_avatar_complete_raw -v
```

下一步是主线人工审核raw闭包与独立消费语义，随后另卡Avatar适配器/最小producer及实际Java wire→目标reader回归；其余P0/SDK/资源/隔离部署/新旧号D-02实机仍由各自验收项解决。
