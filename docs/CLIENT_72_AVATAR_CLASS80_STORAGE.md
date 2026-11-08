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

## Native阶段解除与实施前细化

主线已明确解除等待：stock恢复及五输入/13保护核验通过；16B hierarchy第三同双跑、17项/10独立审核/20 pins并提交`241161b`；静态第三同5912B/8e7decdd、10项并提交`8119c06`。本native阶段另新增 `tools/continue72_avatar_class80_storage.py`及test，只在本页末尾追加，不改静态/旧hierarchy/replay源、测试或主线段落。现在执行完整原五stock gate，不能把上一静态三文件门禁冒充五文件。

新层组合冻结hierarchy连续harness，在其全部构造hook完成后的原prepare阶段安装外层code代理，保留旧hook closure及原七调用/寄存器/栈；仅准确三个存储边界转交新typed模型，其余调用继续原hook。两个6B行在prepare时以准确span加入同一已声明metadata body，保持页洞unknown；只接受原`cc35`的4B和`cc39`的2B字段读取并核固定bytes。class+80必须由原`cb93`写入；entry offset必须由原`cc3e`写入；class pointer必须等原`cbde`函数实际返回后由原`cbee`store写入，不提供任何替代返回。

新增32B层成功模型上限3，所有新分类/入口/尾/metadata byte audit/最终读出累计64读512B；与实际16B层计数一起汇总，累计成功≤7/读≤128/bytes≤1024，不重置。只有当前父层已验证的typed1234类可进入此32B模型；owner/current count2、+80=null、准确caller/width均入口核一次，后续clear使用typed范围。新success模型不包含inflation、class返回、copy、下一个storage或default。新native窗仍0起，实际新直接入口先保留失败、写理由再核，最多2个；到首缺即交准确合同。

## Native范围调整：固定12-slot vtable切片

`native/probe1.json`已经实际完成原class+80发布和两条entry（原inflation/class返回1169、1249当前泛型类，offset4/6），准确停在`14051cd3a`的未提供4B metadata read；原Uc unmapped error/source/停止点完整保留。该首缺属于同一个已审函数的固定vtable行，不是owner pointee。主线已独立从当前固定EXE/metadata重算并核12个word，证据`native/vtable-main-source-review.private.json`，明确批准本卡只补以下48B；原12B两行与此新切片分开记录。

`table=sign_extend32((header[134]+af707941) mod2^32)=4b14d1c`；`start=sign_extend32((definition1234.metadata[28]+9af313c0) mod2^32)=8489`。模板`+be`实际12，因此固定最小跨度为 `global-metadata file BODY210 + table + start*4 = 4b1d3d0`、48B，SHA `10b206224a0413e642cffd998b39c2982e4d83f09e886579a2acb345cb9937a0`，仅映到受控metadataBody+table+start*4。12项payload为3870/3872/3873/3876/10294/10293/10289/10290/0/0/10291/10292，前后10个非零word的原kind bits均60000000；两个0是固定文件实值，不能改成空cache或假定其后继语义。

只允许原`14051cd3a`的对齐4B读取、至多12次48B，邻页/邻字节或其它reader仍拒绝。总成功模型仍3/累计7、new host64/512及累计128/1024、七调用各2M不变。原slot消费会直接调用已审普通MethodInfo getter `140526fb0`；旧replay曾在这个入口对旧miss路线停止。此处只在准确vtable caller return `14051cdc7`、method index等于本次固定slot payload时，执行原getter首条机器指令而不进入旧host stop，之后仍交原hook，不增模型或替返回；准确原函数窗早已固定242B/SHA `62df1ca1bb7c777bfa056102f32302fdeb197ccec09bee3570b74348519b64e5`。首次必需runtime根/pointee未提供即停止，不为四个Object方法补返回。

## Native实际结果与冻结范围

正式`native/first.json`、`native/repeat.json`各1,371,957B，SHA `75e3bc05bb9a8044318c3fce5d30fc2d9f7a51138ede8af23a7e953e8a361283`，逐byte相同。工具29,415B/SHA `9c19d79f6606b17bfde2d00762d1e8ded232e0bdf82b4047abddb9877b1a6c65`；test14,847B/SHA `7b1eb2a9754b5ddd6ee7faef7eaf7b5956ffd4843027ee59e43d20680fb75527`，现在冻结。原五stock文件均用冻结SHA读取验证；共27个source pins。没有运行客户端/调试器/REA、构建/DB/Git或修改运行配置、资源和旧工具。主线第三尚待实际完成，本段不把双跑写成第三验收。

准确三个新成功回调只有32B dirty storage及两个原clear；原`14051cb93`已经实际发布到当前owned definition1234的+80，发布时payload为32个0，尾8B仍`9abcdef001234567`。随后原inflation在`14051cbd9`实际返回两个descriptor，原class consumer在`14051cbe3`实际返回definition1169与本次已产生的definition1249泛型interface class；它们逐64位等于前序原interface array中的两个值。原`14051cc3e`写offset4/6，原`14051cbee`写各class pointer，两个4B padding均为0。不是以静态typeRef解析替代返回，不给owner pointee或inflation返回模型。

32B层实际3个模型、22次188B host readout；本轮父16B层实际4模型、42次333B；累计7模型、64次521B，分别不超过已批准7/128/1024。父层旧首缺诊断的5次28B读由新层typed入口接管，随后又实际发生2次16B既有模型caller分类，因此父读出较旧45次345B变化；预算没有按段重置。冻结harness既有读出/模型继续按原声明范围存在，这里的累计只统计新增16B和32B层，不能称整个Python进程仅这些读/模型。七个原调用各仍2M，总上限14M；实际steps依次13/13/13/13/185/466/7828，共8,531。新native窗0。

原六前缀的name/failure/error/regs/path SHA逐项与父报告一致。前序227项captured-read地址/长度/返回bytes/error/顺序完整保留，当前共255项且每项均有原采样span来源；8个method查询/返回与canonical返回不变。新增静态两行实际读4次共12B，48B切片实际只读第一word4B；并未消费全部12项。静态输入页的任何写、邻字节、错reader/宽度、越界或超预算均停止；原cache captured input写尝试0，callback failure0，未知capture页字节0。

开发`probe1/2`及各自source/observed原件保留：32B两entry已成后，`14051cd3a`尝试读受控metadataBody+table+8489*4的4B，原错误为`UC_ERR_READ_UNMAPPED`。`probe3`第一次接48B时误把尚未由原producer填充的owned class+be=0当模板count，准确source校验在CPU前拒绝；只留下当时source，没有成功报告。正式版改用已pin的自然ordinary1234模板header+be=12，并在报告记录其capture/hash/原2B；`probe4`起才执行获批48B。没有补全邻页、整表或掩盖原失败。

本轮原第一word`60000f1e`产生index3870，准确caller return`14051cdc7`进入原普通MethodInfo getter。准确新首缺为`140526fc5`尝试读`PE+5bc8f20`的8B根；失败`Unbound runtime global cell read`，error为null。该根未提供，也没有把mapped页的0当自然缓存值。原class initialization尚未完成，未到default写回，完整Avatar/Handler/g2/D-02继续未验；`atomicSnapshot=false`、`naturalLookupObserved=false`。

两份明确指定报告各23项检查通过，包括17项独立typed存储/metadata负例和6项实际连续报告检查。覆盖全64位owner/class/template、parent publication、count/初null、0/16/24/48尺寸、clear顺序/指针/值/宽度、非零尾、原writer/实际返回、S16负offset→DWORD、两行字段宽度与邻字节、48B对齐/reader/12次预算/两个真实0、static输入写、累计预算及所有false边界。另11项独立比对通过，见`native/determinism.private.json`；实际argv/UTC/stdout/stderr/exit、source副本与test SHA均留忽略目录。

## 可继续的准确首缺与自然owner替代合同

正式`native/first.next-input.private.json`与repeat同为138,144B，SHA `6c5be2eb618e9e16e457ef741dab7c2626147d1dcca821e5e99c010f4b8d54e5`。当前冷路线的最小下一输入只有实际`B+5bc8f20/8`，B必须来自对应fresh身份；不是允许给它常量、空表或method返回。若将来另获同轮只读采样授权，条件后继是该根、`root+3870*8/8`、非null MethodInfo56B、其owner+c9的1B；单query首遍最多4读73B。四个Object index3870/3872/3873/3876合并上限13读268B，逐unique span末尾复读后最多26读536B。null/短读立即停；owner+c9 bit20若实际为1，进入未闭inflation分支前停。它只覆盖这些getter/consumer字段，不预批后继storage、writer、完整owner或vtable执行。

另列自然owner/default路线，**本卡没有新采样授权或采集**。已有8条完整56B MethodInfo的+0 owner只作为旧来源pin；旧进程已退出，未来必须在新的明确user-ready中重取canonical与10288 hit，不能沿旧PID/base/owner。原`get_Default`是`157d7fe90..157d8000f`、SHA `3fba97a567bac84fac55704f814b1145cedc8621b483232c0330df805469faa3`；本卡从当前固定EXE再次核其准确bytes并引用原cold声明，未执行这个新分支。

满足自然warm状态时，最小新增输入为以下4读256B：从10288 MethodInfo+0读取owner完整208B header；从header+68读取32B GenericClass，核definition1234/current canonical/null methodInst/owner backref；从`[owner+30]+10`读8B实际comparer class槽，当前最小合同仅接受它等于该owner；从`[owner+38]+0`读8B实际Default。owner+c8 bit0须实际为1，所有根/跨度/flags必须来自这一轮且重复一致，Default须实际非null。逐exact span复读共8读512B；不设置initialized/loadedAll或改owner。

这些输入足以让原getter直接走`fed5→ff4b→ret`，返回原Default指针；该warm路径不需要Default pointee、接口表或vtable，也不会调用cctor/lazy RGCTX/helper或写default。若任何null/flags/slot/context/回读不符，则保留该实际分支的首缺，不能跳到warm返回。此独立原消费者回放仅证明给定采样图的返回，不能晋级自然caller观察，也不替当前混合cold图宣称已初始化。

若还要确认Default对象身份，可条件新增Default对象头8B→derived class header208B→GenericClass32B，首遍再3读248B；加phaseA全部复读共14读1008B，核实际definition1239和canonical，类型不同则如实停。原Avatar/HashSet后续分派还会需要Default的实际derived class+80/count+c0及其caller实际选择的vtable pair/MethodInfo；owner header不包含这些。尚未从真实后续caller限定slot和必要pointee，所以这里不给完整Avatar预算、不请求新live或继续无限冷分配模型。

未来collector须RPM只读，记录每次地址/raw/错误/序号与begin/end UTC、集合前后身份，所有selected root/descriptor/新跨度在完整取得后精确复读。原cache collector现版已先复读再返回，不能直接追加owner读取并声称其先前复读发生在末尾；若另做明确分阶段collector，把原cache末尾复读推迟到owner输入之后，phaseA最大合计3440读59456B，带对象身份3446读59952B。重复一致只证明输入回读一致，不能排除writer/ABA或构成atomic snapshot；任何原生回放始终保护输入只读，并与自然调用/完整runtime验收分开。

主线第三复现入口是新工具`--client`取当前`tools/game_path.ps1`结果，`--output`选本卡native目录未存在的`main-third.json`；测试指定`CLIENT72_AVATAR_CLASS80_NATIVE_REPORT`为该第三报告后执行`python -m unittest tools.test_continue72_avatar_class80_storage -v`。本专题与两个新native文件交主线审核/第三/分项提交；旧静态、hierarchy、replay与raw冻结不改。

## 主线第三验收

主线已实际执行上述新进程第三及指定报告的23项测试，退出0；`native/main-third.json`为1,371,957B，SHA与正式双跑完全相同。独立核对11项通过，含27个source pins、旧六前缀、227条原采样读取及全部方法结果、原32B发布、两个完整64位class返回及offset、累计成本和准确未供应根，记录于`native/main-review.private.json`。已审核新tool/test实际差异，没有改动冻结父层或捕获指针。

本项仅关闭所述存储/原consumer范围。下一工作包改为有界只读runtime输入采集器，重新取得同轮canonical及方法，再按准确root/owner合同采集；旧ready/PID及raw不复用。完整Avatar/default/Handler、正式g2及D-02仍未验。只增加离线研究工具/测试和文档，不修改运行资源或配置，无需GS重启。
