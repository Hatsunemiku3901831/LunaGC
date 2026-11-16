# D02-M0-PEER-JAVA：原始字段与实际身份关系

只接已证的加载token和localAuthorityKey，不发送完整Peer通知，不改变Ready/Init/Done放行。其它两字段保留raw，尚无业务值映射；不借用旧9633或旧四字段名字。

## 固定来源

[Producer](CLIENT_72_D02_M0_PEER_PRODUCER_20261116.md) SHA `3ec119d701b985d7fcbc7923ac450c6e4239595c9349056116cdc05380fd9779`：独立typed Cmd28392/type19176消费者普通路径，root+24原值实际写OOJ/current authority key；root+18用于loading-task token查询。完整1464B、raw候选与REX内部假起点按原证据分开。

[Wire](CLIENT_72_D02_M0_PEER_WIRE_20261116.md) SHA `141464687c7cb4f2db66c47594741e3dd54c0b584227aac63ce4f87c2a1086a4`：唯一reader静态为保护跳板，另授原样owned33样本独立恢复四字段。raw4 OHNANNOOMCJ→+1C、raw10 AANCEPCPHKJ→+24、raw11 MCCMGLDOECH→+18、raw12 OHAKDDILKKB→+20，均uint32/wire0；0/1/1357/80000000/FFFFFFFF全identity。保留静态边界与owned原生结果区别，不取A返回或扩大模型。

## Java接入和实际回归

`Client72M0PeerRaw.proto`只声明这四个raw字段，经Gradle/protoc生成Java。`M0PeerIdentityProjection`是未发送的DTO投影：token来自同Player.enterSceneToken；localAuthorityKey使用正常World成员分配的Player.peerId。M0实际World.host就是此Player，SceneInfo44512/tag1的业务owner key来自World.getHostPeerId，与该local key相等；这是一组真实对象身份关系，不是恢复官方英文peer字段名。目标+42C/OOJ的比较消费按Authority/Peer来源读取。

新增7项独立Peer wire，覆盖四tag各自非零/高位/u32最大、四个不同值、重复后值、显式0/不存在及Java截断错误。连M0真实出生/确认保存/冷读World、SceneEntry wire和SceneHandshake共26项最终8秒全过，失败/错误/跳过0。实际fixture逐项核Peer token/key、SceneInfo owner key、非零World/Team两实体身份；未知Peer tag4/tag12没有赋值，未增加完整Packet sender或替换opcode。

同时真实核World/Team的instanced abilities均0，五个PlayerGirl基础embryo全部保留。首次补充测试误把“五基础”写成“场景总数必须5”，失败XML保留；正常applyAbilities会再追加Level_BigWorld候选GrapplingHookSkill_Ability，实际总6，现分别核基础集合及场景追加计数，没有修改运行/资源讨好测试。这个Avatar场景候选不是Team状态或未知83320 hash的供给。

原件位于`local/migration72/d02-m0-integration-20261116/peer-identity-java-results/`、`m0-peer-identity-tests.log`及`m0-team-policy-first-failure.xml`。正式构建在后续实际结果登记；M0普通新实体null/空列表最小性按新的独立source分项接入，不能把这组DTO回归当完整入场或客户端验收。
