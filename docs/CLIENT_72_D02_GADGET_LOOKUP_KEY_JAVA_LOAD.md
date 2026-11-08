# E-01/R3-GADGET-JSONNAME-LOOKUP-KEY

## 计划与独立语义验收

只推进出生Gadget50011101的配置查找key片段。当前配置键原生回放已验真实40472.field199649/+58为`Weapon_Sword_Metal`，进入具名查询；String comparer、完整配置注册/命中、配置内容和ability继续独立取证。本项不等待完整配置加载来证明已足够的字段职责，也不把字段职责验收升级为配置可用。

来源为固定CN7.2五输入与`r3-gadget-config-key/main-third.json`（389999B/SHA `10085ff137a6d918a43adaa2cf27754e4b5c638c9de12c3119705852e65e5abd`）的真实行/原生query/hash/交换/+50负对照。主线另复核既有完整world factory窗：`15154bff9`读同一+58并调用`14aa9c630`，`15154c00b`保存返回，`15154c04a`存entity+2b0。该字段由固定metadata声明为28174.field138419、type37853，配置查询value25270继承37853，typed三级cache为String/u64→17591→String/25270；据此将查找key绑定到实体配置域。后两site只静态核字节和类型，没有执行未供应的完整factory或伪造配置命中。

Java `GadgetData.jsonName`的实际职责由`EntityWeapon`交`GameData.getGadgetConfigData()`查询；`ResourceLoader`以聚合entry key加载该map。主线已读这三处源码，职责与原字段相符。本地同ID同字面值只作旁证，不是许可依据。缺String comparer/actual hit影响查询可执行性和配置加载状态，不否定已验查找key职责；此前将两层同时等待的条件在本新卡收窄。

批准范围仅`{id:50011101,jsonName:"Weapon_Sword_Metal"}`的lookup-key片段。新许可`local/migration72/d02-resume-20261109/r3-java-gadget-key/approved-lookup-key.private.json` 3838B/SHA `f5601d314c51a6417aeb899d26c80cfe2ecdf490c5ff869de2b11ab09fca7398`。旧报告`javaJsonNameAliasApproved=false`保持冻结，本卡不改旧证据。未批准完整Gadget行、type、+50别名、配置bytes/内容、ability或自然武器factory。

## 实施与验收范围

只新增独立Java合同测试与本专题；不改运行代码、资源、patch、配置、Proto、DB或已有测试。以新批准报告和原配置键报告为固定输入，用真实`ResourceLoader.loadFromResource`/Gson/onLoad加载最小片段到隔离Gadget map，读回`getJsonName()`的精确key；删除key的Java null对照不能推成客户端省略许可。不创建假ConfigEntityGadget来声称配置可用。

测试前后保存恢复Gadget map和`ResourceLoader.loadedAll`，不手设loadedAll。主线统一执行便携Gradle/JDK与审核实际测试差异/日志/XML、边界标记，然后本地提交；子代理不构建、不Git、不触客户端/DB/端口。只有上述真实Java测试通过才关闭本子项。完整实体构造/abilities、自然revision/loadAll、正向Manager恢复与D-02仍未验；仅测试/文档修改无需GS重启。

## 主线实际验收

新增`D02BirthGadgetLookupKeyResourceContractTest` SHA `278ed937ffd9f44af29c3ab3e5c76c74a4a9f32fbd69317be77bcc9e022366d4`，主线已读完整测试与来源门禁。便携JDK17/Gradle以offline/no-daemon实际编译和执行，退出0/21秒，两项0失败/错误/跳过。真实loader读回`Weapon_Sword_Metal`；删除字段只得到Java null对照。Gadget map恢复、loadedAll不变断言通过，没有创建假配置对象或手设就绪。

日志/XML/输入/主线证明保留在`local/migration72/d02-resume-20261109/r3-java-gadget-key/`。XML SHA `7bcf08bd644edcb12c44d58747012a89ecf2db876a8c929f6a516745830de988`；`main-proof.private.json` SHA `a7e4653d41e66e8f7ae355692fe6c14662a8e9ecdefd78adda34ea9b08edb581`。整理证明时曾将日志编码名称误写`utf8-sig`，读取前失败，改用`utf-8-sig`后才保存本证明；未改原测试或结果。

本子项完成只覆盖lookup-key片段，配置内容/实际hit、String原比较、完整Gadget/实体、ability、自然revision/loadAll及D-02仍未通过。现有资源中的同字面值没有被计作新增资源或本地迁移成果；无生产资源、配置或服务器运行变化，无需重启。
