# D02-M0-SCENEINFO-MIN-STATES：首场景null与空状态最小合同

## 中文有限计划

主线先批准只读既采fullsources；发现C3原件只有hash/call索引、缺少正文后，进一步只批准重取 `m467533@1507E41C0` 的560B完整body，要求三输入、普通AOT、typed参数、唯一pdata和旧codeSHA全部一致。本项不执行CPU、不追allocator/未知VM、不取新helper/global source。SceneInfo/Authority/Peer/SceneTeam既有阶段专题保持稳定，唯一tracked产物为本文。

真实M0服务器不追加可选全局MP/Team/level默认能力是明确的业务策略，不能先充当network可空证明。Avatar五基础Config保留；正常 `applyAbilities` 还追加场景局部 `Level_BigWorld` 的 `GrapplingHookSkill_Ability`，集成侧实际场景Avatar因此为六项。下述Team空集合结论不外推为Avatar空能力。

1. 对44512/tag2→34045，分别核新实体与已存实体路径的null/nonnull条件、Clone/同步与后续依赖；不将“仅nonnull才Clone”扩大成全生命周期通过。
2. 对49723/tag4→34045，复用14828FEA0/14828F870的实际参数与null/空状态分支，明确bool+40与列表空/对象缺省的区别。
3. 对49723/tag1→89710→83320，仅核count0是否跳过hash反查/注册循环、哪些pointer在count判断前已被解引用；不造默认hash或空record。
4. 对49723/tag3→80154，仅核空列表原生控制流、跳过与后继行为及首M0是否有独立必要性。49723/tag12→72829、tag9 float及未知业务key保留未知；34208/tag12仍是前阶段已证本地Avatar实体ID，不与此混淆。
5. 每项列准确source/hash/site/何时跳过/何时必需，成熟最小合同立即交集成者。未调用到的嵌套不造默认对象；准确A/B/preview或既有受限后继保持原停止，不用全能力图阻挡可独立的已证字段。本文只报告静态分支与已有验收范围，不放行Ready/Init/Done或entry-ready。

## 实际结论与可编码范围

**四项均形成了首M0普通分支的有限合同。** 新建World/Team实体路径允许34045状态缺省；真正的非null、零元素control列表会清旧tuple缓存并跳过所有hash处理；80154正常空列表会清旧字典并返回。null列表与正常空列表不同，不能互换。

| 项 | 目标wire/对象 | 首M0可编码状态 | 准确边界 |
| --- | --- | --- | --- |
| C1 | 34208/tag14→44512/tag2→34045 | 保留44512真实身份carrier，省略tag2状态 | 新实体分支非null才Clone；旧实体 `+426!=0` 的同步callee未闭，不能推广成所有重入允许省略 |
| C2 | 34208/tag15→49723/tag4→34045 | 保留49723真实Team身份carrier，省略tag4状态 | 新实体分支同样跳Clone；其共同场景后继仍存在，不是整个创建/handler已验 |
| C3 | 49723/tag1→89710/tag1[]→83320 | **显式存在89710，列表含零个83320** | 清network+108后正常空枚举返回；列表pointer为null则进入错误入口；不放一条零值83320充数 |
| C4 | 49723/tag3[]→80154 | 正常typed列表含零个80154，wire中没有tag3元素 | 普通可更新实体分支清network+110后返回；null列表进入错误入口；受 `+426` 保护的实体保持旧状态 |

本合同只接当前真实 `EntityWorld/EntityTeam.getInstancedAbilities().isEmpty()` 的M0策略。若真实World或Team非空，不能把它删成空快照，应拒绝使用该有限投影并恢复实际能力数据来源。`TeamManager.getAbilityControlBlock()` 的legacy默认名列表不作为本轮来源：当前有限资源下，`filterKnownNames` 会把空hash表视为unknown并保留全部legacy默认名，不能由此生成未知hash。集成侧可直接从这份已明确的M0策略及真实空Team实例产生零元素control快照。

root14仍应带真实World level实体ID及与Peer通知一致的authority key；root15仍应带真实且不同的Team实体ID。前阶段模板 `90000004/90000002` 不可代替实例ID。省略两份34045不等于发送空44512/49723，亦不等于将Avatar/Weapon自己的状态删除。本项不放行Ready/Init/Done、完整SceneInfo sender或entry-ready。

## 固定输入与原件

三输入复核来自当前 `GAME_PATH`，不记录本机客户端绝对路径：

| 输入 | 字节数 | SHA-256 |
| --- | ---: | --- |
| EXE | 450927000 | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| global-metadata | 83644188 | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| startup-metadata | 4116148 | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |

metadata索引 `local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl` 固定117201003B / `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`；signatures固定78649549B / `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`。方法owner、参数与字段generic argument均由这两份既验输入核查，不按旧Proto或近名挑选。

| 证据 | 完整SHA-256与作用 |
| --- | --- |
| `local/migration72/d02-active/main/scene-entry-third.json`，9143952B | `917a5ad0505c6e90e57c4080f0521e26589fadd0ccfb29eb3257363b8f2a9780`；C1/C2/handler/C4/MoveNext完整静态源及既验80154样本 |
| `protocol/cn-7.2/scene-info-raw-observations.json`，589469B | `f52f41a817241646d63d692b120d5c134c1b30dfe8b2da5d440de5d9557f7c75`；各层tag/wire、具体列表参数及正常空89710/49723 reader样本 |
| `protocol/cn-7.2/scene-deep-observations.json`，285458B | `bed41fc74af5ef39b951c6d57834c10a56b0577da1166957d5221f19ada4acd5`；C3旧范围/codeSHA/call索引；本来不含完整指令正文 |
| `local/migration72/d02-m0-sceneinfo-min-states/control-run1/result.private.json`，31523B | `5d654d2b8207e804e2b9135f7677a93bbe3f45befead8ab9c19bd926b1ab54c7`；本轮唯一新增560B完整source与147条严格解码 |
| `local/migration72/d02-m0-sceneinfo-min-states/state-evidence-v2.private.json`，24140B | `fc22a583956ae4e0629b10f5f6c0d2a0907eb7a40955abe6c1b7e987dc84777f`；六份旧完整source重新核连续指令、原字节回拼和hash，精确site摘录及三个旧float样本；不重新执行旧native样本 |

字段→wire沿[既验raw专题](CLIENT_72_SCENE_INFO_RAW.md)，身份沿[SceneInfo最小身份](CLIENT_72_D02_M0_SCENEINFO_MIN_20261116.md)、[authority桥](CLIENT_72_D02_M0_AUTHORITY_CFG_20261116.md)与[Peer wire](CLIENT_72_D02_M0_PEER_WIRE_20261116.md)分项复用。本项补的是null/count0消费条件，不改旧专题当时的验收范围。

本轮逐份重拼核对的完整body：

| source范围 | 字节/指令 | codeSHA-256 |
| --- | ---: | --- |
| `[14828BA00,14828BF30)` | 1328 / 303 | `22b198c565dc5693a128ef837ccc19643a7845122c103ca64150244880ff8d0f` |
| `[14828F870,14828FE97)` | 1575 / 360 | `30fb7f22cecbb378269bf170ac601f0de65e80862500ac259d02e1da06d94375` |
| `[14828FEA0,148290056)` | 438 / 112 | `c6362f35fe6a777a8f0eed5c7c371d105e7a05adebef4ad1443459e24b066ff5` |
| `[1507D1620,1507D1A6A)` | 1098 / 238 | `de04cf4743c5e030fa71d693c7bc25eb813020104cd31652a5e612c4ac5388f5` |
| `[1507E44F0,1507E48C0)` | 976 / 240 | `444d45dc3ae61ca03864564be12175c0f3e17cd7e610e86870414445440486b6` |
| `[1572292B0,15722937A)` | 202 / 64 | `2ac71498f846c38563b80eaa6400f8faf6ee0bcab0b1aafe50fe4abe680cbcce` |

上述source的callback、工厂返回、patch guard及具名callee只按已有正文记直接边，不在本项补实现、执行或追冻结后继。所有静态分支结论均限定对应普通原生路径，不声称已证明当前进程的guard值或替代分派行为。

## C1：44512的34045可缺省条件

`14828BA59/5C/5F` 依次取44512 `+20` 实体ID、`+24` authority key、`+18` 34045状态；后者保存在RSI。

新实体分支 `14828BBB1` 调用既采工厂 `15154BF20`，`BBB6` 保存返回指针。紧接的 `BBB9 TEST RSI` / `BBBC JE14828BEBC` 在状态null时直接到epilogue，`BEC7 RET`。因此状态null不会进入 `BBC9` 的closure分配、`BBE1→147CCFBC0` Clone及其后callback构造。此处不解释工厂受限分配返回，也不以跳Clone证明工厂/场景初始化全部成功。

旧实体另分两类：

- `entity+426==0` 到 `14828BDC0 TEST RSI`；null在 `BDC3→BEBC` 返回，nonnull才到 `BDE8` Clone。
- `entity+426!=0`：`BAF5/BAF8` 对null状态选择EDX=1；nonnull的 `state+40==0` 同样选择1，`+40!=0` 选择0，再在 `BB13` 调用 `147586F70`。随后 `BB71/BB77` 把原RSI作为R8交 `14773FBC0`，可为null。本项没有该同步callee的消费闭包，不能将null通过上层分支扩大成所有旧实体同步已验。

空34045是nonnull，会进入Clone路径；它与缺省状态不同。当前首M0新实体无需为绕过未知bool或列表而造一份空34045。

## C2：49723的34045可缺省条件

既验manager将49723 `+28`（tag4）原对象交 `14828FEA0` 的R9，`FEA8` 保存在RDI；`FEFB` 原样传R9至 `FEFE→14828F870`。该方法将状态保存在R14。

新实体分支 `14828FA41→151540800`，`FA46` 保存返回对象，`FA49 TEST R14` / `FA4C JE14828FBE6` 在状态null时跳过 `FA59` closure分配和 `FA71→147CCFBC0` Clone。`FBE6` 保留新实体为RSI，之后按共同Player/场景条件走 `FB87..FBD6` 等后继；这不是以null让整个handler提前成功返回，也没有绕过工厂返回依赖。

旧实体 `+426==0`：`14828FCDB TEST R14` / `FCDE JE14828FBD8`，null返回，nonnull才 `FD03` Clone。旧实体 `+426!=0`：`F94C/F94F/F953/F95A` 按null或 `+40` 切bool，`F96B→147586F70`，其后 `F9CF→14773FBC0` 继续接收原状态指针。此同步callee仍未闭，与C1分别保留，不能因服务端Team为空就宣称所有重入状态合法。

这里讨论的是49723/tag4的34045；49723/tag1的89710是另一份collection快照，不能一起省略。

## C3：真实零元素control与hash循环

上层 `1507D1620` 在 `1647` 取34208 `+20` 的49723，再于 `1654` 取49723 `+20` 的89710。`165B` 在control为null时转 `18B8` 日志分支，`1901→147161B60` 后 `190D→1676` 继续。这条路**不调用control消费者，也不清network+108**，所以null不是“清为空”的替代。

既有deep索引只存 `1507E41C0` 的hash/call，不存完整正文；既知旧目录精确检索也没有该完整body。本轮按主线授权补此唯一source：

- metadata：owner56238，m467533 `GOPAHNBPKIE`，普通非虚方法，唯一参数为非by-ref的89710。
- ordinary AOT：cell `142C36738`，原值 `c0417e5001000000`，精确指向 `1507E41C0`。
- 唯一pdata：`[1507E41C0,1507E43F0)` / 560B，recordRVA `1AB7998C`，原记录 `c0417e10f0437e1070e77304`。异常目录hash仍为 `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`。
- 全560B保存，147条strict指令无gap/无skipdata/完整EOF回拼；codeSHA `d2a678c25bc5b1e859c67d60ee2d2d5d0fc44ea16c6141d9151d46f546f19877`，与旧索引完全相同。无CPU执行、新callee或global内容读取。

普通路径（`1507E41E9/41F0` 未取patch替代分派）的实际顺序：

1. `41F6/41F9` 检查control，手工直接调用时null可返回；上层handler已区分它。`41FF/4207/420A` 要求network `+108` 缓存对象非null，否则到 `43BC` 错误入口。
2. `4210` 对缓存version加1，`4213` 读旧count，`4217` 写count=0；旧count>0才经 `4229→159C68C70` 清backing。清缓存发生在读取消息列表之前。
3. `422E` 取89710 `+18`，`4232/4235` 检查列表；null到 `43C1→1404C2BE0` 错误入口。metadata证明89710唯一实例字段就是该 `RepeatedField<83320>`（field445354，instance12390），没有另一份本项遗漏的必需scalar。
4. `4251` 从正常typed列表取枚举结构，`42A5→1572292B0` MoveNext。正常零元素枚举返回false后，`42AC→43D4`，`43E7 RET`；不会读取Current/83320。
5. 非空分支才到 `42DD` 取record，`42E3` 拒绝null record，`42F0/42FB→1500D85A0` 分别反查两个hash，然后 `4280..4296` 写tuple或 `4352` 走扩容追加。两个hash的角色、扩容helper和patch后继均未因此升级。

count0不是从方法名推测：复用旧完整 `1572292B0` 正文，array-mode在 `92BE..92C9` 将初始index -1加到0，比较 `0<count0` 得false；List-mode在version匹配前提下于 `92F6..92FF` 比较index0与count0，走 `9321..932E` 清Current并返回0。没有新增iterator模型、默认返回或native实验。既验 `empty-89710` reader样本输入为空、结果为空集合；其托管分配/列表初始化仍属于原报告的显式离线环境，不冒自然客户端ABI。

因此可编码的是**有presence的空集合**：type49723内部字段fragment `0a00` 表示tag1存在、89710 payload为空；89710内部没有任何tag1元素。不要在89710 payload里写 `0a00`——那会构造一条空83320。真实Team为0时，此快照完整表达已知collection状态并使普通consumer清缓存，不需要给两个未知hash强行填值。

## C4：80154空列表、null和保护分支

上层 `1507D16D0/16D7` 把49723 `+18` 的正常 `RepeatedField<80154>` 交 `1507E44F0`。完整source与既验样本给出：

- `1507E452F→1517ACC90` 查询当前scene+230对应实体；普通 `4546/454D` 在实体 `+426!=0` 时到 `4827` 返回，旧字典保留。此位不与authority对象bool `+460` 混同。
- 可更新路径 `4553` 取network+110字典，要求其非null。`4563/4568` 看旧count；非空才清backing，`45A5` version加1。随后 `45A8/45AB` 检查输入列表，**null到4872错误**，其前清旧状态已经发生。
- 正常空枚举 `46C8` MoveNext、`46CA/46CC` false→`4788` cleanup/dispose→`4827` epilogue→`4833 RET`。非空时才执行 `4758` Current、`4773` 取80154 `+18` u32、`4776` 取 `+1C` float及 `477E→1553C8AF0` TryInsert。

旧原件中本轮只读复用三个已执行样本，没有重新跑CPU：

| 样本 | 实际结果 | 范围 |
| --- | --- | --- |
| `empty-clears-old-state` | 零次insert；旧字典清空；version11→12；boxed枚举1 | wire `60db8080800812096881808080808080107a00`，SHA `7077669b9b677443ec72ff83ed8992b97e8a1c0056d450b22f4c496cc8fa19a7`；49723的tag3零次出现，正常reader产物为空列表 |
| `protected-entity-keeps-old-state` | entityFlag=1；不清旧字典、不insert；version仍11 | 实际保护分支；不证明所有场景都具有此flag |
| `null-list-clears-before-error` | clear发生、零次insert，之后到原生错误入口 | 在正常reader结果之后由旧夹具显式改为null；不是自然空wire会产null |

三个样本均保留原报告模型范围：托管interface表、box/枚举存储及Array.Clear是显式fixture；MoveNext/Current、consumer和字典原生操作不是成功stub，`criticalModeledAddresses=[]`。原native成功不升级完整handler/自然场景构建。

故M0输出零个80154即可；wire里没有tag3元素，正常Protobuf列表对象仍非null。不能写一条空80154来代替空列表，否则普通非保护分支会将其当实际record送TryInsert。非零业务key/float仍无必要来源，本项不虚构它们。

## 保留未知与后续执行边界

49723/tag9 `+3C` float确在 `1507D1676` 读取，并在实体缺失或普通 `+426==0` 条件下由 `16BB` 写network+200。现证据没有给它业务名或当前服务器非零来源；parser省略时的自然零位模式不等于已决定一个生产默认值。本项不添加猜测float。

49723/tag12 `+30→72829` 只在上层 `1700→1482879C0` 返回true且相关管理器存在时，由 `172C/1730→1496A7750` 继续消费。非空Object后代、该条件的自然场景取值及完整consumer保持旧专题范围；不填一个空72829去绕过它。34208/tag12本地Avatar实体ID是另一条已验身份边。

当前四项最小合同不再需要某个未获输入的hash值或完整allocator树。下一可独立执行项由集成者完成：补raw Proto、对真实Team空实例投影有presence的零元素control和零元素80154、断言两个34045未声明，并拒绝真实Team非空时使用此投影。测试需区分89710缺省与存在、零元素与一条零值元素，且继续交叉真实World/Team/Avatar/Weapon身份。此处只有研究文档与忽略证据，无运行/Proto/测试修改，无需本项构建部署；Git、生成和测试由唯一集成者串行执行。

若后续验收实际选择旧实体 `+426!=0` 的重入同步，最小未闭边是两处实际调用的 `14773FBC0` 对可null状态的消费，而非重取所有34045后代。若Team实际非空，再只恢复83320两个hash/ID到真实能力来源的必要关系；当前空集合不批准那些角色，也不提供假值。准确A返回、preview/B及原0-pdata失败继续按各自稳定专题保留。本项无新的安全审查拒绝；一次只读终端JSON打印遇Windows GBK字符编码错误已留于忽略证据，未涉及目标执行或取源门限。
