# E-01/R3-GADGET-JSONNAME-LOOKUP-KEY

## 计划与独立语义验收

只推进出生Gadget50011101的配置查找key片段。当前配置键原生回放已验真实40472.field199649/+58为`Weapon_Sword_Metal`，进入具名查询；String comparer、完整配置注册/命中、配置内容和ability继续独立取证。本项不等待完整配置加载来证明已足够的字段职责，也不把字段职责验收升级为配置可用。

来源为固定CN7.2五输入与`r3-gadget-config-key/main-third.json`（389999B/SHA `10085ff137a6d918a43adaa2cf27754e4b5c638c9de12c3119705852e65e5abd`）的真实行/原生query/hash/交换/+50负对照。主线另复核既有完整world factory窗：`15154bff9`读同一+58并调用`14aa9c630`，`15154c00b`保存返回，`15154c04a`存entity+2b0。该字段由固定metadata声明为28174.field138419、type37853，配置查询value25270继承37853，typed三级cache为String/u64→17591→String/25270；据此将查找key绑定到实体配置域。后两site只静态核字节和类型，没有执行未供应的完整factory或伪造配置命中。

Java `GadgetData.jsonName`的实际职责由`EntityWeapon`交`GameData.getGadgetConfigData()`查询；`ResourceLoader`以聚合entry key加载该map。主线已读这三处源码，职责与原字段相符。本地同ID同字面值只作旁证，不是许可依据。缺String comparer/actual hit影响查询可执行性和配置加载状态，不否定已验查找key职责；此前将两层同时等待的条件在本新卡收窄。

批准范围仅`{id:50011101,jsonName:"Weapon_Sword_Metal"}`的lookup-key片段。新许可`local/migration72/d02-resume-20261109/r3-java-gadget-key/approved-lookup-key.private.json` 3838B/SHA `f5601d314c51a6417aeb899d26c80cfe2ecdf490c5ff869de2b11ab09fca7398`。旧报告`javaJsonNameAliasApproved=false`保持冻结，本卡不改旧证据。未批准完整Gadget行、type、+50别名、配置bytes/内容、ability或自然武器factory。

## 实施与验收范围

只新增独立Java合同测试与本专题；不改运行代码、资源、patch、配置、Proto、DB或已有测试。以新批准报告和原配置键报告为固定输入，用真实`ResourceLoader.loadFromResource`/Gson/onLoad加载最小片段到隔离Gadget map，读回`getJsonName()`的精确key；删除key的Java null对照不能推成客户端省略许可。不创建假ConfigEntityGadget来声称配置可用。

测试前后保存恢复Gadget map和`ResourceLoader.loadedAll`，不手设loadedAll。主线统一执行便携Gradle/JDK与审核实际测试差异/日志/XML、边界标记，然后本地提交；子代理不构建、不Git、不触客户端/DB/端口。只有上述真实Java测试通过才关闭本子项。完整实体构造/abilities、自然revision/loadAll、正向Manager恢复与D-02仍未验；仅测试/文档修改无需GS重启。
