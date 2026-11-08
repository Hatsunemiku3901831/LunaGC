# R1-HIERARCHY-STORAGE：准确16B类层级存储

## 实施前计划

主线已批准只解除冻结R1-METHOD-CACHE-REPLAY的准确storage请求：`1405091d0`入口、return caller `14051f2ff`、当前snapshot `cold.registry.cell` owner、owned definition1234/depth2/class+48=null、width16。新owned16B先dirty，另8B非零尾哨兵；只提供原两次memset return `14051f30f/321`的16B清零，以及原copy return `14051f35c`的既有Object hierarchy8B复制。原`14051f333`最终槽store和`14051f361` xchg发布必须实际执行；不提供下一24B数组或MethodInfo owner pointee，不改原函数返回或default成功桩。

来源固定为replay工具SHA `1ac41682fd129a9951fa1d9560b27b44afe8780193ff609664aeadc3f0fa1945`、正式报告SHA `02f1182f1a1d7a042e08bcef400a832d5ead0b946f97527214b6de25eda38bbc`，以及原fresh method-cache capture SHA `ab042d29b67aff6735b498680e74b6152e6c72285cdeb3cc799ceef57a730998`。旧replay/source/测试/raw完全只读；本卡不重做已验9个isolated lookup。组合旧continuous harness保留实际内存/寄存器/栈，原前六case原样通过，新第七case从同一factory连续到准确新首缺。

新Overlay只截获这四个准确存储调用，记录新增模型与host readout。通过自有code-hook代理在获准边界前执行准确typed model；其它指令和回调原样交旧harness。旧函数验收还期待停在原未分配点，若仅该过时后验断言触发，就从其traceback保留实际result/state并明确标记，不修改冻结源/断言或隐藏native失败。这是组合旧harness的已知返回边界，不是改生产函数。

每次原生调用2M，重放7次原前缀调用；新增model成功调用最多4次，host readout最多64次/512B（含校验和最终读出），超过即停止。新增native窗原则0，只用已审hierarchy239B和旧连续窗口；未审直接入口/新输入/捕获span写入或tail变化均停止，不预批后继。需要新直接窗则先在本页写准确小计划再核其来源，最多2个直接窗，无成功模型扩张。

仅新增本页、`tools/probe72_avatar_hierarchy_storage.py`及对应test；私有产物在 `local/migration72/d02-resume-20261109/r1-hierarchy/`。不改旧文件、共享索引、Java/Proto/resources/patch/config，不Git提交/构建/部署/DB/客户端/调试器/REA或新采样。新自然字节需求0；完整Avatar/default/Handler/g2与D-02未验。

## 当前状态

限定storage已完成双新进程、主线第三及各17项测试，三份报告逐字节相同。原16B hierarchy内容与原指令发布均已执行，下一准确请求是旧已审`14051c9d0`中的`14051cb68`调用pool（return `14051cb6d`），count2<<4=32B，目的class+80仍null。正式报告在此新入口前停止，不提供32B存储、不预批metadata行/新writer。此函数窗在冻结连续harness已有SHA `d73f483a6f520517421e29303ed51adae405c3e29a59befe484dbc4e6b16a076`，新native窗数0。

`probe1/2`曾完成approved16B后，被旧allocator按未批准caller拒绝；初收口driver把该后继拒绝当unexpected，probe2补只读result retainer，明确这是新caller而非本次storage失败。原失败/source仍留存；只增加该准确新请求的入口前停止，未改旧allocator或失败断言。`probe3`保留初步caller分类/模型各读一次caller的版本；正式版本分类读取一次后把typed caller传入模型，不重复读取入口值，预算据实际新host读出计量。

## 冻结组合方式和模型边界

新工具复用冻结 `replay.continuous_replay`，不复制它的producer或改其source。只替换当前Python进程内code-hook包装入口，在四个准确调用点暂时绑定本卡typed模型，然后交原基础harness记instruction/path/model调用并返回；随后恢复原callback对象。其它native指令、模型及输入完全交原harness；原九个isolated lookup已有已验报告，不在本卡重做。

旧replay的后验合同仍要求停在“未提供16B”的旧位置。新存储实际通过后，这条后验断言必然过时；新driver只接受其准确两个已知literal后验错误，从该冻结函数traceback的actual result/state读取原native结果，保留 `frozenPostconditionObsolete`。其它ValueError不接受或吞掉。没有删除旧断言、改原函数返回或改old pin；原native真正的failure/error另存，不混成这条host后验。

新模型的入口校验一次：current registry owner、owned400B class、definition1234、depth2、+48=null、fresh canonical/method-null与GenericClass双向owner对应。后续两个clear和一次copy只接受该已建立typed16B范围及各自准确caller/参数，不再次猜类身份。四个新增成功调用为：

| action | 实际原caller | 原执行与新提供内容 |
| --- | --- | --- |
| allocate-dirty16 | `14051f2ff` | 新owned16B，初为cc，另8B tail；既有alloc的24B初始化及dirty/tail host写全部明列 |
| clear16 | `14051f30f` | 原第一次memset调用，0/16B，仅本owned范围 |
| clear16 | `14051f321` | 原第二次memset调用，重复保留，tail不变 |
| copy-parent8 | `14051f35c` | 仅原99-span已pin Object hierarchy8B到数组前8B；source bytes再次等于原span，后8B不改 |

原 `14051f333` 的8B store实际把当前owned1234写到最后槽；native写hook仅接受该地址/宽度/writer/value，tail或其它body写拒绝。原 `14051f361` 的xchg随后实际把数组发布到class+48；在原 `14051f365` 读取16B payload、8B尾哨兵和class+48，证明两项为 `[Object class, current owned1234]`、field指向数组、tail保持。没有用host写class+48或代替这两条原指令。

## 真实连续结果、累计预算与下一首缺

七个原调用在同一个原harness中执行，内存/寄存器/栈保持原调用方案；前六个producer/registered-intern case的name/failure/error/寄存器/path SHA与冻结replay逐项相同。第七个factory使用原fresh缓存内容查询取得canonical，原八次方法查询/方法表写回继续保留，随后本卡16B内容完成，原native继续到下一准确请求。没有新增自然字节或跨轮地址重绑定；MethodInfo+0 owner等未采pointee仍opaque，不被新模型访问。

第七调用实际 **6,749步**；各次 `emu_start` 仍硬2,000,000，未重置或拆段隐藏指令。新增模型成功调用 **4/4**；本Overlay的所有boundary caller分类、入口校验、tail检查、Object source读与publication/final读出累计 **45次/345B**，不超过64次/512B。报告逐次列地址/宽度/purpose/bytes，计量跨整个七调用运行累计，不按模型重置。冻结harness自己的原有host行为仍由原声明负责，这45/345不冒充整个Python进程的总内存读取。

无captured-input写attempt、无callback failure、无未审window、无tail错误；stop时critical depth1、shared未持有，没有执行异常展开或default写回。GenericClass显式空cache/owned类壳与旧ancestor、本fresh缓存的组合仍是受控模型，不证明同一自然运行图或完整class初始化；默认comparer/Avatar Handler/g2与D-02均false。

下一请求精确如下，**没有提供成功存储**：

| 项 | 当前实际值/来源 |
| --- | --- |
| 入口/caller return | `1405091d0` / `14051cb6d`，原call在 `14051cb68` |
| owner | 当前snapshot `cold.registry.cell`，本轮不重用历史pool地址作新身份 |
| class | 同一第二个owned400B class，definition1234；hierarchy+48已由原native发布 |
| 当前目标 | class+80仍null；原 `14051cb47` 写+ c0 count2，模板+c0也实读2 |
| width | `14051cb4e`读U16 count2，`14051cb61`左移4，`14051cb65`传32B |
| 以后若另获准 | 两个原memset return `14051cb7d/8f`，各32B；`14051cb93`原store到class+80。这些尚未执行 |

独立 `first.next-input.private.json` 为2,156B/SHA `c231c5b8131e2fa65f3bb07ef81ea537cf408458ef08e118af086956cd73826c`，与正式报告 `nextInputContract` 相同。仅解除分配请求的新自然字节需求0；这不是对后继全部输入的ready声明。原后继 `14051cc07..cc3e` 将从固定70B metadata+30、`+e42a`选6B行，表来自固定header+54和`8054c847`；行含S32 type-ref和S16 offset，原指令分别写entry+0类指针/+8 DWORD，stride16。实际选定行及其inflation/class消费者本卡未提供或执行，应在新任务先精确提取/核来源，不能先补零或新自然pointee。

## 实际测试、双跑和主线第三

两个正式新Python进程 `first.json`、`repeat.json` 各 **1,180,851B/SHA `7026c7f3b287e38c4640ed293a412442b6ce85c932f471bdbdfc2e9ce254d344`**，直接逐byte相同，exit0/stderr0；各17项独立unittest全过。源码sidecar、实际argv/exit、stdout/stderr、test source SHA及byte比较在本卡忽略目录的command/test记录和`determinism.private.json`。每个同名`.observed.private.json`保留过时后验出现时的实际continuation/storage/state，不覆盖首次失败。

正例由实际原native完成，不以测试fake替代；独立稀疏fake engine仅验证typed边界拒绝：wrong owner高64位、foreign class、definition/depth/context/nonnull/backref、错caller与0/8/24/32宽度、clear非零值/错范围/顺序、Object source/字节/宽度错误、尾哨兵变化、native尾界/错writer写、原publication未发生、model与readout累计上限。另核旧六path/寄存器、四调用/原store/xchg、下一32B未供应及false边界。16项preview通过后新增第17项next合同检查，正式两次均17项，无测试失败。

冻结工具18,285B/SHA `50522dd602d7930735c2d42982ffb06f31f601fc37378f614cc6b85919e33a4e`；测试12,929B/SHA `4ad53b919f16e53e925dd2ce4dfb114c6005924502f91ff231ca08514ee755ec`。原replay/source、capture/monitor及更早窗口/stock pins都由入口实际核对，没有借当前HEAD替代文件身份。

主线实际第三 `main-third.json` 为1,180,851B、同SHA，17项检查通过；另独立核三份bytes、20来源pin、旧六前缀、四准确模型、原store/xchg发布、尾哨兵/45次345B预算、八方法结果保持、下一32B未供应及父项false，共10项，见 `main-review.private.json`。本项限定16B验收完成。后继[准确32B class+80与固定metadata行](CLIENT_72_AVATAR_CLASS80_STORAGE.md)另卡推进；原16B工具及来源冻结不改。

第三复现须选新文件，已有产物不能覆盖：

```powershell
python tools/probe72_avatar_hierarchy_storage.py --client $env:GAME_PATH --output local/migration72/d02-resume-20261109/r1-hierarchy/main-third.json
$env:CLIENT72_AVATAR_HIERARCHY_STORAGE_REPORT = 'local/migration72/d02-resume-20261109/r1-hierarchy/main-third.json'
python -m unittest tools.test_probe72_avatar_hierarchy_storage -v
Remove-Item Env:\CLIENT72_AVATAR_HIERARCHY_STORAGE_REPORT
```

本卡只有三个新独占文件和忽略raw；未改旧replay/测试/专题、Project/Harness/HANDOFF、Java/Proto/resources/patch/config，无Git/构建/部署/DB/进程/调试器/REA或新采样。本项限定16B层级数组已完成，32B class+80及真实class/default/Avatar/g2仍待主线下一任务；不把发布一条hierarchy数组升级成完整初始化。
