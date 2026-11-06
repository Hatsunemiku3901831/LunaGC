# F-03d3g1：独立 Avatar raw Java 适配器

主线后续构建于2026-11-07完成：portable入口30秒，JAR SHA-256 `dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3`、内嵌c667344。逐class比较282新增均为本独立Avatar容器，3个build元信息变化；它们均与实际编译输出逐字节相同，其余46019旧class保持，无删除。证据`evidence/avatar-wire-build-artifact.json`。status实际退出1、stopped/Healthy=false，8088TCP/22101UDP/27027无监听，原Mongo27017和R2已核暂停目标保留，配置及原资源dirty hash保持，见`avatar-wire-postbuild-runtime.json`。完整P0/SDK/资源前置未齐，未部署或安装补丁，没有运行服务JAR/PID或客户端成功对象。后文“未打包”为代理初交付时的历史范围。

将[e8完整raw图](CLIENT_72_AVATAR_COMPLETE_RAW.md)的65类372个字段生成到独立 `cn72.avatarwire` Proto package，Java容器为 `Client72AvatarWireOuterClass`。所有目标消息/字段保留原raw名，包含7组真实oneof、13个map、42个primitive packed list；14个Item子类在本独立包内完整复现，不改既有库存适配器或共享Item。enum只保留已验int32 raw，不生成猜测的业务枚举。

没有修改 `AvatarDataNotify`/`AvatarInfo` 旧共享Proto、Avatar.toProto、PacketAvatarDataNotify、PacketOpcodes、业务映射或ready准入。该独立适配器没有生产sender引用，`targetProtocolReady=false`、runtime/实机未验，不代表最小初始化发送集已经成立。

## 固定输入和确定性生成

[generate_avatar72_wire.py](../tools/generate_avatar72_wire.py)每次重新核验实际客户端三输入 `extract_client72.HASHES`，值与[核心](CLIENT_72_AVATAR_CORE.md)相同。然后固定e8完整证据与其11份来源摘要的全部JSON，包括原生wire/预期对象、scope和错误边界，不只固定schema片段。e8原文件字节SHA为 `37c90e4dea9fccc4ac20ce68616612250b5a57169e564081c33dc69a25def837`，canonical UTF-8/LF JSON SHA为 `82a53a6a79082f0888d475c91842f0be9612c6ca4ea1690e9eb0308f6601a62c`；后者避免同一JSON仅因Windows换行表示不同而误拒绝。11个来源的canonical指纹在生成器中逐一固定，缺失/内容漂移拒绝。

输出仅 [Client72AvatarWire.proto](../src/main/proto/Client72AvatarWire.proto)。map的message value使用实际nestedType，保留string-key/u32、u32/float和u32/message等原始类型；Object分支按判别槽分组，不把两个discriminator存储发成wire。78350与85344的摘要虽然没有显式oneof名字，仍依据已验discriminatorOffset建立各自独立oneof；51050保留两组，tag100为18849 message。

普通singular标量采用proto3 optional，显式零/false/空string在合并时不会被当成省略；这是本适配器保留原生行为的选择，不声称恢复了原始Proto语法。oneof内标量依靠分支存在性保留active零。42个已验primitive list明确输出 `packed=true`，解析同时接受原生已验的packed/unpacked。Java只通过Gradle/protoc生成，没有手改generated Java。

## 实际 Java 与原生交叉验证

Windows portable/JDK17、离线Gradle实际执行protoc、compileJava、compileTestJava和定向测试，36秒完成：新 `Protocol72AvatarWireTest` **275项**、原 `Protocol72InventoryWireTest` **36项**，合311项通过，失败/错误/跳过均0。新测试包含268组来自固定e8及11份原生摘要的独立正向对象，以及7项结构/存在性/14 Item类一致性/数值位/错误边界测试。

268组都通过真实生成类的parser解析，并从该生成类进行确定性序列化；不是只用DynamicMessage或自编wire。序列化后的bytes再次由生成类解析，随后JUnit输出这268组实际bytes。来源/fixture名、类型、生成类名由[trace_client72_avatar_wire_adapter.py](../tools/trace_client72_avatar_wire_adapter.py)核对，不允许缺项、重复或未知输出。

该工具在两个全新进程中分别重读三输入/证据、重新提取metadata/AOT并创建e8完整原生上下文，将**268组实际Java bytes全部交给目标原生reader**。预期对象只取自固定的独立原生摘要，不从Java输出获取。所有对象和递归float32位模式一致；负零使用位级比较，不能被Python `-0.0 == 0.0`掩盖。134组wire因顺序、packed或canonical varint发生变化，原生结果仍相同。

范围包括65类完整非空Avatar/AvatarInfo/53824组合、372字段的来源向量、三份同形子字段/列表、不串用、全部oneof切换、tag100重复copy/合并、map整体替换与零覆盖、list追加、Unicode/NUL、int32/int64上下界、uint64高位/max、float负零/最大finite/subnormal和64元素fixture边界。保留scope空壳的旧夹具不作为本适配器完整对象oracle；它们由原研究合同保留，不算通过或跳过的Java测试。

8项新增Python生成/原生结果守卫、4项原库存生成守卫和10项e8关联守卫共22项通过。它们会拒绝schema/oneof/fixture/未知状态漂移、任意一个来源漂移、Java输出对应的原生对象错误及负零丢失。实际 `--check` 无漂移。

## 不等价边界

1. 原生signed-list reader遇到packed长度 `ffffffff0f`，实测沿 `jle` 返回且不追加；protobuf明确拒绝负长度。仅这个固定正向样本ID从等价向量中排除，Java另有拒绝测试，原生行为保留于e4；没有泛化为“忽略所有差异”。
2. 原生跳过未知tag/错wire，protobuf保留unknown fields并重新序列化；Java测试检查保留tag1/100/128，实际输出再给原生reader后已知字段对象仍一致。不声称未知字段策略相同。
3. 原生oneof同分支message有分配/CopyFrom再读的执行过程，protobuf以message合并实现结果。通过独立对象验证省略保留、显式零覆盖、list追加及map替换，不能将两者实现过程或完整异常派发声称一致。

底层托管分配、class/generic metadata、primitive input/codecs/delegates、boxing、UTF8和固定64数组仍沿用e8明确模型；原生reader/ReadMessage/list append/map adapter/setter执行，未执行业务Handler、live ABI或实际客户端。未新增服务、数据库、调试器操作。

## 产物、保护核验与复现

JUnit原始结果保留 `local/migration72/avatar-wire-java-results/TEST-io.grasscutter.Protocol72AvatarWireTest.xml` 和Inventory同名XML；前者SHA `dcfc6641c48b2acc8105caed9944a9556c851e843b25072576a24cc47fe7ac79`。日志 `local/migration72/logs/avatar-wire-{java-first,java-native,java-native2,python-tests,generate-check,protection}.log`。

两次Java→原生完整轨迹逐字节一致，SHA `a2408117b0e9facd7fe2f0a3fecb337a2f53e226cec5337df2d5d1e0d30ada92`；脱敏摘要SHA `f7a414063e8f3a414d2cae54baa3a9e891b2705bb630150e3136aced6a5acf29`。记录为 `local/migration72/evidence/avatar-wire-java-native{,2}.json`、`avatar-wire-java-native-summary{,2}.json` 和 `avatar-wire-determinism.json`。本批没有意外失败或被删去的失败断言；明确差异采用独立断言保留。

与现存磁盘JAR比较Gradle编译输出：237个保护类逐字节相同，包含账号/保存Guard/脚本/Player/GameItem/Avatar、旧Avatar sender/PacketOpcodes、旧共享Item/Avatar Proto及70个原库存raw class。新增Avatar raw编译class为282个。旧源码相对接手基线无差异，证明 `local/migration72/evidence/avatar-wire-protection.json`。本批按主线共享窗口只编译/test，**没有打包JAR或部署**；磁盘JAR继续是先前构建，不能把新增编译class说成运行产物。后续主线集成构建/部署须按全局前置另行核验。

```powershell
python tools/generate_avatar72_wire.py --client $env:GAME_PATH --check
python -m unittest tools.test_generate_avatar72_wire tools.test_generate_inventory72_wire tools.test_client72_avatar_complete_raw -v
./gradlew.bat --no-daemon --offline test --tests io.grasscutter.Protocol72AvatarWireTest --tests io.grasscutter.Protocol72InventoryWireTest -PskipHandbook=1 --console=plain
python tools/trace_client72_avatar_wire_adapter.py --client $env:GAME_PATH --java-results local/migration72/avatar-wire-java-results/TEST-io.grasscutter.Protocol72AvatarWireTest.xml --output local/migration72/evidence/avatar-wire-java-native.json --observations local/migration72/evidence/avatar-wire-java-native-summary.json
```

Gradle/build是共享串行窗口，复制本轮XML后再交下一轮使用；不能以当前build/test-results里被其他任务覆盖的结果冒充本卡证据。只新增独立Proto/生成器/测试/定向原生工具及本文；Project/Harness、业务审核和本地提交由主线负责。

## 最小 producer 的已验范围与待取证清单

以下是[f1消费合同](CLIENT_72_AVATAR_CONTRACT.md)及后续逐项审核允许用于下一独立producer验证的字段，**不是完整入场发送许可**。旧 `Avatar.toProto()` 和 `PacketAvatarDataNotify` 的业务名不能按类型相似直接搬到新raw字段。

| 层 | 当前已验字段/来源 | 下一producer可独立验证 |
| --- | --- | --- |
| 30509/root tag6 | avatar_list→48178，真实typed角色模型初始化入口 | 从同一Player受控角色集合生成列表；实际Java bytes→原生root/model链 |
| 48178 tag1/tag2 | avatar_id/u32、guid/u64，具名Avatar资源与真实GUID模型索引 | 保留现有Avatar ID和完整实例GUID；不同高位GUID/重复模型/资源命中边界 |
| 48178 tag5 | equip_guid_list/u64，真实库存索引和Weapon/Reliquary槽消费者 | 与已接入PlayerStore输出的同一GameItem GUID逐一关联，不能截成32位或另造GUID |
| 48178 tag3→78350 | prop_map，子tag1 property_type/u32、tag2 boxed int64 | 仅1001 EXP、1002 BREAK_LEVEL、4001 LEVEL；map key与子ID明确区分，不发送旧表其余ID |
| root tag5/tag12、26081 tag2/tag12 | [f3队伍](CLIENT_72_AVATAR_TEAM.md)的Team ID映射、当前Team、名称和完整有序成员GUID | 实际Player队伍与角色模型集合关联，仍需完整handler、fallback/override及保存语义 |
| 48178 tag4 | [f4a](CLIENT_72_AVATAR_STATE.md)生命状态枚举/真实模型写入 | 不从省略保留旧状态推定可省略，不批准REVIVE作持久状态 |
| 48178 tag7 | [f4b](CLIENT_72_AVATAR_FIGHT_PROPS.md)float map及7个限定具名ID | 实际HP/MaxHP及受控基础/当前攻防数值来源另审；省略会清旧map |
| 48178 tag11 | [f4c](CLIENT_72_AVATAR_SKILL_DEPOT.md)目标具名depot缓存资源ID | 区分保存当前值、默认Avatar资源和实际目标资源加载；501/701零缺省投影不是loader或energySkill别名证明 |

必须继续取证/验收的缺口：

- root tag3仍不能批准为当前控制角色GUID。[f2a](CLIENT_72_AVATAR_CONTROL.md)证明其Hero消费者与场景管理器+0x284的本地角色选择可以指向不同实体；需要实际场景消息对+0x284的写入、队伍关系及非零原生组合证据。
- root四份同形u32列表、两份其他列表及未闭标量用途仍未知；f3只关闭上述限定Team关系。不能按旧flycloak/costume/trace/temp名字配对。
- AvatarInfo已审1/2/3/4/5/7/11仅各自有限消费；其它字段及技能/天赋、装备扩展、皮肤、复杂53824的业务仍缺证。完整raw不替代实际typed handler分支到具名/独立消费者与省略/清零/错误边界。
- f1至f4c执行的都是明确片段；完整`15184e100`必须先核真实模型构造及必经调用，不能由f1零分配夹具裁定最小集合。场景实体关联、初始化完成条件及完整生产仍待，隔离producer或Store GUID关联不授权替换完整登录链。
- SDK/信任/补丁ABI/实际最小资源、完整P0和D-02新旧号实机继续属于各自卡。适配器没有解除任何门禁。
