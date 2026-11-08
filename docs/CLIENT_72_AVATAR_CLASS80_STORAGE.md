# R1-CLASS80-STORAGE：准确32B class+80存储与固定6B行

## 实施前计划与当前限制

本卡承接R1-HIERARCHY-STORAGE的准确请求：原pool入口`1405091d0`、caller return `14051cb6d`，current snapshot registry owner，同owned definition1234/count2，class+80初null，width32。前置16B hierarchy尚未由主线第三验收；主线正在新ready的SDK临时DLL实验。**目前只允许计划和固定EXE/metadata/startup三文件的静态提取，不运行native emulator或新连续CPU，不运行五stock gate。** 不把当前Astrolabe实验身份改成stock条件，也不跳过它来宣称完整五文件通过。后续必须等主线明确通知stock恢复及16B第三通过。

固定输入合同 `r1-hierarchy/first.next-input.private.json` SHA `c231c5b8131e2fa65f3bb07ef81ea537cf408458ef08e118af086956cd73826c`；hierarchy正式报告1,180,851B/SHA `7026c7f3b287e38c4640ed293a412442b6ce85c932f471bdbdfc2e9ce254d344`。旧hierarchy/replay/source/test/raw冻结只读，不改共享索引、Git、Java/Proto/patch/resources/config，不构建/DB/客户端/调试器/REA或新采样。

## 先静态核对的准确问题

复用已审 `14051c9d0..14051e00d` 窗，SHA `d73f483a6f520517421e29303ed51adae405c3e29a59befe484dbc4e6b16a076`。原`14051cc07..cc3e`按普通definition1234固定70B metadata+30的U16、`+e42a`和序号求两个6B行，表来自固定header+54与`8054c847`。本阶段独立提取两个实际S32 typeRef/S16 offset、准确文件跨度/hash；用固定registration/startup解析typeRef，分开记录类型定义/泛型instance/VAR及原inflation仍需的输入，不把解析出的type当实际返回类或自然runtime对象。

只读三文件各用原固定SHA验证；同时核冻结源码/报告/next合同及当前PE准确函数窗。静态产物明确 `fullFiveStockGateRun=false`、`nativeCpuRun=false`、`hierarchyThirdAccepted=false`，不能作为执行ready或放宽当前部署前置。未知typeRef、负数边界、行范围或metadata不符立即拒绝，不补空行、不递归整graph。

## 获准恢复后的最小执行设计（现在不执行）

只组合冻结连续harness，保留旧六前缀及其状态，原第七次继续；不重做已验isolated cache成果。新增32B storage先dirty，另8B非零tail；仅接受上述准确class/owner/count/caller/初null。原两次memset各32B，return `14051cb7d/8f`；原`14051cb93`必须实际store发布，不用host写class+80。只提供本阶段已核两个6B行的准确12B固定metadata跨度，原`cc3e` offset store、原`cbee` class pointer store和原inflation/class consumer执行；没有返回值或owner pointee成功桩。

新增成功模型最多3次（allocation+两clear），新增host分类/校验/tail/终读累计最多64次512B；连同冻结16B阶段4调用/最多64次512B，总预算最多7个新增存储成功调用、128次1024B。两个阶段均跨七个原调用累计，不按段重置；原生每次2M。原readout/模型原件范围另列，不伪称整个Python进程总读量。新直接窗原则0，若实际到新入口，最多2个，仅准确直接边、先写理由与小计划再许可；未知读、captured输入写、tail错误、新storage/owner/caller/尺寸或预算触限立即停。

正反验收覆盖原class+80发布、两entry布局与完整64位class指针/符号offset、原六prefix、两个固定行的来源与禁止相邻字节；错caller/owner/count/class/初值、0/16/24/48尺寸、clear范围/值/顺序、wrong metadata/typeRef/offset、尾界、model/readout累计及未知inflation失败。双新进程后交主线第三。新的metadata行解析不代替原生inflation，后续storage不预批；完整Avatar/default/Handler/g2与D-02均未验。

## 文件范围

仅新增本页、`tools/probe72_avatar_class80_storage.py`及对应test，私有产物在 `local/migration72/d02-resume-20261109/r1-class80/`。目前工具仅静态阶段，不导入或执行Unicorn/原生harness；获准执行时另记录源码epoch与确切合同，不改旧pin。

## 静态阶段实际结果

主线随后实际完成16B hierarchy第三（同双跑、17检查/10独立审核）及本静态阶段第三：`static-main-third.json`为5912B、SHA `8e7decdd285f712f77631e3791a91e92e64d2801b128595023a867bdf246c99d`，同正式双跑，明确指定第三报告的10项检查通过。源码冻结时报告里的`hierarchyThirdAccepted=false`保留原样；它记录当时静态阶段，不倒写为执行已发生。stock已恢复，本卡实际32B执行另用新入口/源码epoch组合这些固定行和冻结hierarchy；本段本身仍无native CPU或class+80发布成果。

主线已通知SDK实验正常结束并恢复stock；截至本静态阶段冻结时，16B hierarchy第三仍待主线明确验收，因此仍不运行native CPU或五stock gate。此阶段仅用原固定三个SHA核EXE/global-metadata/startup，核冻结hierarchy source/report/next合同，以及已审class初始化和VAR解码函数的PE bytes/exception extent。没有读取/接受临时Astrolabe作为stock，也不把三个文件核对改写成五个stock通过。

普通1234的固定70B metadata file `a221ec`，SHA `00b9a09dbe29589ee7a40b147d587b1cdbbd1f4c1e8eee101a9d886a500837f7`。其+30原U16为7571；按原16-bit `+e42a+ordinal`，两行index445/446。table body offset为`8cfc68`，按原32-bit加法后sign-extend解出，body file bias仍`210`。

| row / file | 实际typeRef | 静态解析 | S16 offset / 写入DWORD | 6B来源SHA |
| --- | --- | --- | --- | --- |
| 445 / `8d08e6` | 444353 | kind18 / definition1169，`System.Collections.IEqualityComparer` | 4 / `00000004` | `76b9d2ddc61c974bf3a5259848f0b55a3a751c8f0b95498d8a6c5820b2c291e4` |
| 446 / `8d08ec` | 684513 | kind21 / generic405409 / instance54600 / definition1249，`System.Collections.Generic.IEqualityComparer<VAR362>` | 6 / `00000006` | `f81b083a0cca77db62e237ff4fa4b1ec5d4cc3d4d92c821662843b138ab36496` |

第二行的固定startup pair和registered one-argument instance指向typeRef448404，kind19/data362。VAR362固定14B file `4fb2580`、SHA `6d1df52b90dc9d2aa7c4298132a074cc0c20e357f93a0aa63a6596eb3b938596`。`+6`原U16是34416，不能直接称argument position；原`1404eda0d/13/27/2c/32/38/3e/40`的32-bit混合后取低16位，解得参数槽0。静态`probe1`曾把原U16误标成position，原source/报告保留；`probe2`起分别记录encodedArgumentPosition和decoded argumentPosition，原解码窗仍按冻结PE hash核，不用fixture返回值修正。

只访问三个实际descriptor（两条选中ref和VAR362），没有扫描整个type/generic graph。两个6B候选恰相邻，共12B；工具保留每行独立来源，测试覆盖行index65535→0回绕，不把所有情况当连续12B。候选runtime地址仅按已声明controlled metadata body绑定计算，不声称本fresh自然epoch地址，不向harness注入或执行inflation/class消费者。`inflationOrClassReturnProved=false`、class+80完整填行/default/Handler/g2均false。

两个新进程静态报告 `static-first.private.json`、`static-repeat.private.json` 各5,912B/SHA `8e7decdd285f712f77631e3791a91e92e64d2801b128595023a867bdf246c99d`，逐byte相同，各10项纯静态检查过：两个实际ref/offset/type、VAR原值与解码值、固定source/合同、16-bit wrap、符号S16/S32/null-ref/-2、短行/范围/count拒绝、恰12B/三个descriptor及无native/五stock/ready的false边界。标准库Python解析不运行客户端机器码，新增model调用0。

静态source SHA `be224f8408e7bba17562aead4a2511b6d4f85790014ae16c6d489f2e38e62c65`，test SHA `6f295572404b21ed76e53851b209abac1f2397b964c15591b8563244e45ad2af`；实际source副本、argv/stdout/stderr/exit和test source SHA、双跑直接比较见本卡忽略目录。未来获准native阶段须保留本静态epoch，记录新源码及正式结果，不覆盖这些原件。
