# F-03d2c1：独立库存raw Java适配器

后续F-03d2c2已将本独立适配器用于库存最小发送链，详见[有限生产接入](CLIENT_72_INVENTORY_PRODUCTION.md)。以下“未接入”与旧构建数字准确描述c1验收时点；本适配器的raw字段和生成合同在c2保持不变。

本项把[完整库存raw图](CLIENT_72_INVENTORY_GRAPH.md)的15类47个字段生成到独立`cn72.inventorywire` Proto namespace。全部字段/消息保留目标raw名；没有修改旧`PlayerStoreNotify`/`Item`、GameItem、任何发送包、正式包号或三层业务状态。此适配器尚未接入运行发送链，不能当作库存初始化或D-02完成。

`tools/generate_inventory72_wire.py`每次读取并校验实际客户端三输入，再固定已验`inventory-graph-observations.json`的完整JSON内容。其canonical UTF-8 LF摘要为`b21904536dc21997f2ee9c1ecb2761e63d521f8cebb72a7320620ec7b3660fe8`；原本机CRLF文件字节摘要`ca45b0d9b038cd46fc35943924ca77ae46c8fbae82c06e131245e03f43d95b18`未改变。canonical指纹仅统一换行表示，不省略字段、样本或模型边界；任何字段、分支、packing、原生输入/结果、runtime或未知状态改变都拒绝。输出仅`src/main/proto/Client72InventoryWire.proto`，Java由Gradle/protoc生成。

15消息包括3组真实oneof、5个uint32 map、5个primitive list与根Item列表；非oneof普通标量用proto3 optional适配显式零/false合并，不声称恢复原始Proto语法。已有原生同分支clone/CopyFrom的省略保留、显式零覆盖、map替换与list追加分别由Java独立核对。原始Object判别槽不变成wire字段，未知分支不降为bytes或空壳。

实际测试先对旧生产root运行两项独立断言，均失败：旧字段集合6/9/11与原生2/8/14不同；独立非零根wire无法读出tag2的Item列表。失败保存在`inventory-wire-legacy-red-results`，不据此宣称旧sender已修好。随后改测新独立适配器，36项通过：32组已经由目标原生reader确认的完整对象图逐值比较，加根字段/独立根向量、15类47字段/三oneof/optional存在性，以及截断/负长度/错wire边界。32组包含根六非空Item、三个层次的oneof切换、同分支显式零/false、map零覆盖、packed/unpacked列表追加和完整uint64上界。原生结果是外部依据，并非自编自解唯一证明。

首次扩大测试使用了本仓库Gson没有的`JsonArray.asList`/`JsonObject.isEmpty` API，编译器拒绝；保留日志后改用现有Iterable/size API，不升级依赖或删断言。新生成守卫4项全部通过，覆盖摘要/schema/oneof/packing/金向量和未知语义漂移。所有测试输入来自固定摘要中的人工合成wire，不是原始抓包或真实账号数据。

```powershell
python tools/generate_inventory72_wire.py --client $env:GAME_PATH --check
python -m unittest tools.test_generate_inventory72_wire -v
./gradlew.bat --no-daemon --offline test --tests io.grasscutter.Protocol72InventoryWireTest -PskipHandbook=1 --console=plain
```

本机证据和失败/成功日志分别在忽略`local/migration72/evidence/inventory-wire-*`、`logs/inventory-wire-*`及对应results。该独立Proto未由任何生产Java引用，故不自动进入当前登录发送候选/import矩阵；不扩大P0必需集合。业务接管须等待F-03d2b2及独立生产子卡，限定[Material合同](CLIENT_72_INVENTORY_CONTRACT.md)的六字段不能批准全部装备/其他物品分支。

提交b47a490后实际Windows portable构建22秒成功，JAR SHA-256 `5db5d3b2db6c2ab5392b0526d9af63208a3ba6e6df507ecfe234a25a0d28fd73`，内嵌b47a490；70新增raw class与编译输出逐字节相同。除BuildConfig/InfoCommand/StartupArguments的commit元信息外，其余45948个旧class不变，旧包号/发送类/共享Item保持。旧JAR校hash归档。业务初始化/P0、SDK/补丁实际加载及资源前置未齐，未部署/未安装，无运行JAR或健康成功对象。
