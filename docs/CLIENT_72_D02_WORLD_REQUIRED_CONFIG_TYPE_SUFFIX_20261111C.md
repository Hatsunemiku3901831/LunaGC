# D02 World 必要配置：注册类型、后缀与单块正文后继

任务 `F-03d3s2/WORLD-REQUIRED-CONFIG-TYPE-SUFFIX`，父项 D02-R1/Scene。本包只用既存原件恢复静态关系，并对**一个准确父资源**查询既存 index/目录；不恢复自然反射 cache、注册执行、reader、World 返回或 slot114。已读当前 AGENTS、Project 的 D02 状态、恢复索引、[触发条件](CLIENT_72_D02_SCENE_SLOT114_TRIGGER_CONDITIONS_20261111C.md)、[运行上下文](CLIENT_72_SCENE_RUNTIME_CONTEXT.md)、[host reference](CLIENT_72_D02_MPLEVEL_HOST_REFERENCE_20261111B.md)及[已固定复用检索](CLIENT_72_D02_SCENE_GITHUB_INCREMENT_20261111.md)。没有新网络/clone、PE/global/live/CPU、测试、运行实现或部署。

## 结论与直接后继

注册 `14aa9a14c..15c` 传入的是 valuetype11129 `MoleMole.InternalAsset` 的两个 **string 引用**：完整 `assetPathInResource` 和 `longTypeName`。第二项不是 System.Type 对象、enum、offset 或后缀本身。闭包 generic T 的准确声明为 `UnityEngine.MiHoYoBinData`/8026；接已有类型名→后缀合同，可独立定位 World 父资源。

唯一目标为：

| 项 | 准确值与来源 |
| --- | --- |
| 必要 child | `MPLevel`；已恢复 template90000004 的54B真实行，field199649/+58；factory `15154bfd7..bffd`取该字段交 `14aa97730`、`14aa9c630` |
| 完整父路径 | `Data/_BinOutput/Gadget/ConfigGadget_Scene_World`，47B；已验 group544 的原 key，包含该 child，不是按 MPLevel 拼路径 |
| 静态类型/后缀 | `UnityEngine.MiHoYoBinData` / `.MiHoYoBinData`；generic cell 与既有真实 type-map 合同相接，不试后缀求命中 |
| 单次 host hash | `336d0a54c32b516f`，完整 U64；61个UTF16 code unit低byte、补至256B，MD5 `6f512bc3540a6d3384c67c9d203ffb2e` 的首8B little-endian |
| 真实 index 命中 | 仅31049741，原记录 `[204632,204646)`，tag0/value1；record SHA `e4c645e248ea3eb5607127c71a359fdd68570f546dcceeb8037983ded9e2a6b8`；location `(24230448,1,0,0,0,7)` |
| archive 内名称 | `54c32b51`，class1210索引 ordinal58592，fileID0、完整pathID14619、fileSize3266 |
| 目录对象 | **class1208**/typeIndex2，typeHash `0451de58691ec5b6e08bba3d9b5b85fa`；`[99780384,99783656)`，3272B |

因此后继不是重新输出索引：**只需恢复同一已审物理包的 block761，便可裁出这个父资源的真实 byte[]。** 其后才核准确配置 consumer，解析所需 MPLevel 子对象/字段；3266B不是已经解出的独立 MPLevel 正文。

## 固定原件与 World 必要性

- `scene-runtime-context-third.json` 是已验3870401B原件，SHA `bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4`。其中完整注册 `[14aa99dc0,14aa9a4e3)` 为1827B，原hex连续重组与 SHA `a86b9049a9a2b1443a23efbfc84c7d22cd20252cf0966499e4816f8e054f625d`相符。
- World group 原件 `scene-mplevel-actual-host/{fresh3,main-third}/mplevel.private.json` 各8233B，SHA `e677ae76a7d23ad39dec9ac87a97c8474494ecb33dd48b6a956af5bcf1af70d9`。既有782517B payload SHA `25bf9c7e24ca9d708b39deafe361c0a339daf9cbb5aadc6b61a2324e57cd891e`；只核已知 group544 `[731264,731558)` 的294B/SHA `08d0ca8d1eb5afaf7d998dfb64a2ab3cfbd6d380f6e6c60345eaa53c76d937a1`，不重跑707组/parser审计。
- 完整 key decoded SHA `8ac986bbf8379bc4f8c85eb0060e3705e9e1e3293df8c404617067235d777cf2`；原21名称及各位置/hash完整保留在私有绑定内，原序是 `MPLevel, Team, Enviro01, Enviro02, Enviro03, Grass01, Water01, BrokenIce01, Tree01, Bush01, Bush02, Bush_NoFire01, Bush_Yellow, Bush_Ashes01, CustomTile, Foxtail_Grass01, Foxtail_Grass02, Windmill, Oil, Storm_Lightning, Storm_Lightning_New`。本包只选择第一项所需的父资源，不展开21项业务。
- template90000004行 `[1451356,1451410)`/54B，slice SHA `3288b3a44679bcb4ff775fc52fcb5274e20818c9b58c3ce9bc5d33892198283a`。其父+18是90000004，父+20是9999；+50为Default_Item，+58为MPLevel。不能把9999、Default_Item或子名称替代父路径/类型/后缀。

## 注册结构第二项的准确值流

完整 metadata117201003B/SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`、signatures78649549B/SHA `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`固定后，禁止globals解析。准确声明与原指令分列如下：

| 原点 | 观察与 typed 绑定 | 推导/未知 |
| --- | --- | --- |
| `14aa99e68/e6c` | 原dictionary枚举结果的 key 保存到 `[rbp-30]`；child array在R15，当前 child在RSI | 这里的key和child是不同值；完整World key用于同组各child的parent hash |
| `14aa99f32`，`4c8b35ff94eefa` | 精确RIP cell=`145983438`；原generic usage735837→methodSpec16871→methodDefinition80127 `CommonMiscs.GetAssetPathHash`，methodInstance58257；原argument record142444080/count1/pointer14565d808/typeIndex430698→8026 | 静态闭包T为UnityEngine.MiHoYoBinData；不把usage声明冒作自然rgctx/cache已初始化 |
| `14aa99ff7..a098` | 经该method context的原指针链及 `140517680/140538230` 两CALL形成保留在RDI的值 | 原CALL/实参明确；本包不命名其未审内部或补实际返回 |
| `a100/a125` | R14=`[RDI+18]`，非空后读RAX=`[R14+10]` | typed声明相符：RuntimeType284.field1181/+18是MonoTypeInfo335；后者field1483/+10 `full_name:string`。这是缓存布局的静态匹配，不是自然RDI/R14身份观测 |
| `a0eb` / `a12e..a13b` | 一条支路清EAX后去组装；另一条在缓存full_name为空时CALL `1404dcff0`，其RAX写回 `[R14+10]` | 第二字符串可能为null或未知helper返回；不能断言所有自然路径均已得到预期全名 |
| `a14c/a150/a154/a158` | 原 `488b55d0 / 488955d8 / 488945e0 / 488d55d8`：stack value的+0=完整key，+8=RAX，将其地址交RDX | 两个8B引用，共16B value；不是hash低/高32拆分 |
| `a15c e88fc069fc` | 原CALL `1471361f0`，准确为AssetIndex11100.m78174 `GetPathHash`，唯一显式参数的signature typeIndex27712是valuetype11129 `InternalAsset` | 53097/53098分别声明string `assetPathInResource` / string `longTypeName`。metadata boxed字段+10/+18与此原unboxed value+0/+8区分，不能把+8称Type对象 |
| `a16e..a1bf` | 保存hash，原 `147234540`检查结果可将它置0，再以当前child RSI/该U64交既有cache调用 | 静态注册关联支持“child MPLevel→其完整父资源”；原检查/字典/注册的自然成功仍未知 |

**准确停止点：**`a100`发现反射cache为空时，`14aa9a110 e8ab95a9f5 → 1405336c0` 为A已拒allocator入口；其MonoTypeInfo分配、返回及 `a118` 写回依赖立即停止，仅留原件。不追callee，不强制cache非空或走别路完成这个返回。删除该返回后，typed InternalAsset两字符串、闭包T和既有type-map仍可独立研究；上述磁盘定位不执行或模拟这个反射路径。

## 已验后缀合同复用与这次单查询

直接复用[既有资源定位合同](CLIENT_72_RESOURCE_LOCATOR.md)：真实31049740 type section含 `UnityEngine.MiHoYoBinData → .MiHoYoBinData`，存储方向 fullName→alias已验；既有native hash/MD5/packed identifier/entry-name合同也已验。本包只补注册结构+8的typed角色，不重新审type-map/nativehash或启动旧native CLI。

只用既存两份 index byte[]与location artifact，对准确父路径进行一次host数据查询；没有case、suffix或path变体，没有调用probe或目标CPU。两asset段均消费至既定边界；31049740无该key，31049741有上述唯一tag0/value1，因此不需要猜variant selector。该结果是静态所需资源的磁盘命中，**不是自然type cache、注册或active index选择的观察**。

## 可直接派工的一个正文包

独立 request：`local/migration72/d02-resume-20261111c/world-required-config-type-suffix/one-parent-block-payload-request.private.json`，**14181B / SHA `1064ea668af5daba75758e91fa5ca0e07d66994c20a62f0d949f725c73fd9a28`**。

| 项 | 固定范围 / 验收 |
| --- | --- |
| 物理源 | `${GAME_PATH}/YuanShen_Data/StreamingAssets/AssetBundles/blocks/00/24230448.blk`；139134667B，SHA `cb1a68de51f3baeba7afc771284d46410a7b6cb993aa3d085a0c8a7813e14768`，MD5 `edc854321f0e9d8c64fd6e80426fe657`；本包只复用旧身份，后继须重新完整流式核file/profile/五stock |
| header / CAB | 复用已验13709B decoded header、13737B prefix、tag3、3399块；单CAB `CAB-53126bc4d34ebc277960cf0c743cffbc`/445421180B，无尾。不能改tag或换codec |
| 唯一新增块 | index761，raw `[25741681,25777971)` **36290B**；output `[99745792,99876864)` **131072B**。旧metadata/index/target已解区间均不能提供它；定向缓存文件名查询未见761输出 |
| 原件关联 | class1210完整索引报告32075192B/SHA `1fe32b78bb317e6502325600b521c9142935ab03a02467348be067abdd2a9cbf`；选中32B索引record SHA `6e1210397b09fab2e4273d4891a560dc68115c889ca3e7d7e2aa4a883048af99`。metadata目录58670745B/SHA `cda1620fd7c1c988103d03a5b518ae0e1b94f3e1d8431fd840bf8dd582d22c26`；两者third与fresh5相同。**class1210是索引，class1208是目标对象** |
| prefix / LZ4 | 原七块工具的同一个PrefixOnly probe/源码/context/表/原29段allowlist全部固定，不修改旧工具；仅1次 `1415aa8c0..1415aa911` 81B入口，code SHA `93ee6859b3c44f6dc8a7846ed5b4552f2edc3e99d49450b73e485566931ac18f`，无model。旧block765实录28621步，后继cap100000；随后仅1块独立LZ4，精确36290→131072，不补零/截断求长度 |
| 原probe保护 | header storage13725B、table256B、archive结构与sideguards保持，原raw第128B后的tail不得改写；保存真实AL/stop/targets/edges/write/protection结果，未知入口/拒绝边即停 |
| 对象/byte[] | block内 `[34592,37864)`裁3272B。实际LE32 count必须=3266且等索引fileSize；保存后续3266B payload及最后 **2B真实tail**，tail不猜0，也不并入payload |
| 来源/内存预算 | 只核这一个物理file全长身份；为复用原绝对offset setup保留真实 `[0,25777971)` prefix，中间其他压缩bytes不转换/解码。不读其它file/body/global，不扩完整archive/native codec/reader/反射/World |
| 交付/验收 | 原raw/transformed/output/proof、parent-object/payload/tail、pre/result、双fresh；pureartifact audit独立核单块LZ4/对象/count/tail且0PE/CPU，主线第三。来源漂移、错record/pathID/class/范围、short read、未知call、超步数、保护失败或解码不完整均保存实际失败并停，不改key/块/返回 |

此包的源链、单块范围、现成prefix/LZ4机制和提取边界已齐，可由Terra机械实现后直接取得**一个真实父正文**；当前没有读取该物理块或执行prefix。后继工具只须新建独立文件，不改原707 parser、旧工具或资源。

取得父正文后，必要MPLevel字段的解析仍待准确 consumer：既有typed记录17591.field83188/+30是 `Dictionary<string,type25270>`，不能由该声明猜序列化或混淆字段别名。父payload与此准确caller上下文届时才构成下一分析输入；本包不提前解析21项、生成Java配置或声称能力就绪。

## 版本、候选对照与范围

后继先以固定profile/五stock、上述物理SHA/MD5、原manifest lineage及header/目录的实际engine记录核同源；保留静态包身份与自然运行revision采用的区别。既有Anime固定commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`、Asta `6cfecfb55f889f8e3b7156adb12809608db4f74b`及本地同名JSON只能作对照，复用原inspection SHA `330be5e22b8acf306e30b8f06b372a33bdbab96f23b16f1dfadeac09733765c1`；不新爬取/clone/pull，不以CN7.2标签或同名MPLevel声明版本一致。待目标consumer恢复所需MPLevel字段，才比较这一子对象；候选文件不能代替目标3266B byte[]。

私有交付根 `local/migration72/d02-resume-20261111c/world-required-config-type-suffix/`：

| 文件 | 字节 / SHA-256 |
| --- | --- |
| `source-binding.private.json`，21 pins、完整注册原hex/145锚点、World21/行与拒绝停止点 | 45709 / `592bcc43899979b24cba82d6534ad843cafe14b5fea604e9f13e06a32c649a8b` |
| `typed-registration-inputs.private.json` | 47170 / `e36c849abd1e56e9684f44c100dffbc8335b794cab6e3d2c945d8d52aefb886e` |
| `parent-resource-query.private.json` | 1183 / `8ac16f402b12fe111cf1246e90e31d02f5cc823e1cea53f2da66c4044294e3ba` |
| `parent-body-binding.private.json`，19固定依赖、entry/object/header/allowlist/五stock | 9477 / `6ff98fb421b6a301b21087bb1bdea29947a9452a9d42b1386a420dcb477dce23` |

本轮仅新增本专题和独占私有证据/请求；纯host查询复用既有Reader/asset_record定义，没有运行旧CLI或实例化native/runtime probe，测试0、实现0、源块/PE/global/live/CPU读取0、构建/Java/DB/端口/client/调试器/部署/Git/index操作0。共享旧docs/tools/Project/Harness/HANDOFF/config/Proto/patch/resources只读；集成/提交由主线负责。A/B拒绝及其准确依赖继续停止，不借本磁盘资源出口取得其返回；新拒绝须保留原提示并立即停相应依赖。完整Scene/World/reader/slot114与D02均不晋级。
