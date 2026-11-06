# AstaPS 登录、选人与入场实现审计

2026-11-06，F-01限定交付。参考 [MeChen618/AstaPS](https://github.com/MeChen618/AstaPS) 固定提交 `a49f51aa6f2787ffbc25cee46801b70dee860d23`，root tree `9940bf7756ce5ff8be8a836a4485a645b490c2e1`；本仓审计基线 `c9aafa63835236ef578251b69777644768d9eacc`。本轮先融合有独立证据的范围并维护文档，限定验收后暂停，不推进新实机阶段。总范围见[融合记录](CLIENT_72_ASTAPS_FUSION.md)，任务/验收以 Project 为准。

参考根 LICENSE 是 **GNU GPL v3**；build.gradle 的 Apache 描述不能代替根许可证。本项仅比较实现，未复制对方源码、Proto 或生成 Java。来源、许可证文件哈希、完整输入和集合见[脱敏摘要](../protocol/cn-7.2/astaps-implementation-observations.json)。后续实际采用源码或 Proto 时须另记文件归属并保留对应许可证与署名。

## 实际集合与验收边界

选定集合是既有 A-03b 方法在两仓存在的同 ID 方法，加被审计 recv Handler/born 全方法及 Player 加载/登录方法，再展开同 owner 唯一名称/参数数量的调用候选。它是源码并集，**不是实际客户端必需首包序列**。

| 项目 | 实际结果 |
| --- | --- |
| 源码/构建输入 | 64文件；摘要逐文件保存两仓原始字节SHA-256，缺文件为null |
| 方法候选 | 本仓295、Asta288；完整ID、边数及未关联调用数已保存 |
| 包候选 | 107构造类、107包源码；保存构造位置与动态BasePacket参数 |
| Proto种子 | 166语义种子，含强制补入的22握手根；保存排序集合 |
| 既存import闭包 | 本仓1150文件、Asta1157文件；逐文件哈希/imports/根身份完整保存 |
| descriptor验证 | protoc3.18.1均退出0；JDK17/protobuf3.19.6独立解析文件集合逐项等于闭包；1194/1204消息、4852/4877字段 |
| 重复性 | 本地完整报告、两个源码候选图、两个descriptor及字段清单双跑字节相同 |

descriptor编译明确排除了缺失根，不能据其成功消除缺根或声称Asta Java可编译。本仓联合候选缺 `PushTipsAllDataNotify.proto`；Asta缺13个根，完整名单在摘要，其中握手相关为 **PlayerLoginReq.proto、EnterSceneReadyReq.proto、DoSetPlayerBornDataNotify.proto**。部分包手写wire，缺Proto不等于没有全部业务实现；手写实现也不证明目标schema。

实际runtime闭包仍未完成：Java虚调用/重载/Lombok访问器、PlayerCreationEvent替换、PlayerJoinEvent取消、SendPacketEvent替包、插件回调、异步加载/初始化、活动/任务/资源驱动分支、Lua与场景控制器、UnionCmd/FanOut、Asta登录线程池与出生后台任务均开放。摘要保留 `runtimeClosureComplete=false`、`targetProtocolReady=false`、`runtimeStatus=not-tested`，不修改mappings状态。

## 实现差异与不采用项

下列Java路径均相对各仓 `src/main/java/emu/grasscutter/`；摘要保存170个源码/包文件的两仓哈希及diff hunk坐标。这些是审计基线位置，F-02a之后的修改须与该快照区分。

| 链路/来源 | 核实的差异 | 本仓处理 |
| --- | --- | --- |
| `server/packet/recv/HandlerGetPlayerTokenReq.java:107` | Asta用ProtoRead取4字段，4线程登录池与logic线程交接，另有IP封禁/过载逻辑 | 保留AccountLoginAdmission、稳定账号/UID归属、getOrCreatePlayer及确认保存；不整文件替换或为协议融合引入线程/数据库流程 |
| 同文件`:335–345` | RSA异常后发退化seed/伪sign，仍切session key | 保留本仓失败不切key的行为；成功seed/签名/封帧独立回归 |
| `server/packet/send/PacketGetPlayerTokenRsp.java:127–129` | 连续两次setClientIpStr，第二次OSRELWin+VERSION覆盖真实IP；另加StopServerInfo错误展示 | 不采用覆盖错误或OS渠道默认值；错误展示需独立目标证据 |
| `server/packet/recv/HandlerPlayerLoginReq.java:20` | 忽略payload且缺Req Proto；可配置跳过选人/自动造旅行者，已有角色可自动修补主角 | 忽略请求是占位处理，不是空schema；保留准入、资源ready、头像数/主角一致性、显式选人，不自动修坏档 |
| `server/packet/send/PacketPlayerLoginRsp.java` | 两仓文件原始字节完全相同，仅填ResVersionConfig并强制dispatch key | Asta没有补齐本仓响应逻辑；字段/密钥语义仍需独立验证 |
| `server/packet/recv/HandlerPingReq.java` | Asta用字段常量+ProtoRead，本仓用生成Proto | 正确schema无需导入宽松读取器；ProtoRead首重复字段生效、坏输入/错wire与缺字段混成0或空，不等于protoc读取语义 |
| `server/packet/recv/HandlerSetPlayerBornDataReq.java` | Asta仅player.save，然后Rsp/Nickname/ACTIVE/BornIntroGate；本仓确认保存后立即onLogin | 保留本仓入口校验、重复出生拒绝、装备/Avatar/Player逐项saveGameSync、失败关闭且不重用半保存内存；继续时序仍待目标证据 |
| `server/born/BornIntroGate.java:131` | 明确依赖7.1两次false→true pause周期；先发入场通知，后台onLogin，Ready可延迟，PostRsp后启动出生任务 | 不把7.1启发式当国服7.2证据，不导入后台登录或opcode配置覆盖绕开固定生成源 |
| `server/game/GameSession.java` | Asta缺本仓撤销准入/outboundLock/部分volatile保障，另加手动过场和玩法状态 | 保留撤销/接管/关闭、实际发送顺序header/Combat序列、时钟暂停；双key探测两仓已有，不是Asta新增7.2验收 |
| `server/game/GameServerPacketHandler.java` | Asta有未知包Combine/Offering/头像突破等猜测分流及大量玩法强制注册 | 不导入未知payload驱动业务/经济的fallback；只依据已核实CmdID路由 |
| `server/packet/send/PacketPlayerEnterSceneNotify.java` | Asta加出生重复抑制、场景别名、enter_reason/scene_tags；仍沿用算术常量 | Asta Proto有20000..20003占位且缺目标已观测395/763/1357等tag；目标raw优先，不能抄业务setter和算术映射 |
| `HandlerEnterSceneReadyReq`及Init/Done/Post | Ready忽略payload/缺Proto；其余增加pet/dive/月相/列车/冰生物/委托/神像/TPS/朋友等 | 不扩张玩法；Req/Rsp及真实次序列为后续依赖，保留本仓Lua、场景序列化、World及经济Guard修复 |
| `game/player/Player.java:1691/1754` | Asta用Future加载，onLogin创建World并发送主档/库存/Avatar/任务/活动/家园等，ACTIVE后触发事件和注册 | 不整文件替换；其新增profile/widget/skin/TPS包仅是候选，不能称最小必需包；保留本仓已修保存/经济管理器 |

## 本轮融合与后续阻塞

选人三消息已由本地目标证据接管：DoSetPlayerBornDataNotify空schema，Req的nick_name/tag4与avatar_id/tag11，Rsp的int32 retcode/tag15。Asta相符仅是交叉对照，**新增可晋级选人schema为0**，保留既有3个。Rsp成功空payload来自默认retcode=0，不能推广到错误响应。

场景现有目标证据支持 **8个raw类型/44字段**：BBECFDMDFJC外层26、BGDCNKAKGHI三个float、[六个嵌套类型](../protocol/cn-7.2/scene-nested-research.proto)15字段。外层27748/类型/PlayerEnterSceneNotify身份已核实，字段业务仍未知。可以作为独立raw源接入候选；本项未做接入，不得把unknown字段接Asta业务setter或晋级语义、enum符号、运行状态。新增已验证场景握手语义schema为 **0**。

| 批次 | 本轮/后续范围 | 验收与停止条件 |
| --- | --- | --- |
| F-02a 本轮实施 | 仅四schema从本地固定目标证据生成，Ping回显原始字段；具体生成/测试结果见该批记录 | 独立wire/protoc、非零/64位/错误/边界、生成重复与相关Java回归；本审计不代替该批验收 |
| F-02b 后续阻塞 | 其余Token/Login响应、完整PlayerLoginReq、登录成功继续 | 缺完整语义/嵌套与请求验证来源；Asta缺Req或当前handler忽略payload不能解除阻塞 |
| F-03 后续阻塞 | 场景语义及Ready/Init/Done/Post请求响应/真实首包序列 | raw8类型仅有wire结构，不证明业务值；尚无闭合生产P0，不能以静态构造/编译取代 |
| 本轮收口 | 更新Project/Harness/来源清单、构建与限定验收后交接 | 保留账号/出生与经济保存回归；按HANDOFF要求备份并维护；完成后暂停，不开启新实机 |

Asta声明7.2并将ENTITY_ID_BIT_SHIFT设为27，本仓基线21；版本号和实体ID编码各需独立证据。Asta RegionHandler仍有CN/OS双分区和sdkenv=0/2，RegionVersionConfigLoader读取特定客户端hotfix；它们不能补齐本机CN SDK/信任/key/补丁或资源revision。Asta使用JDK21/protobuf与protoc4.36.2并跟踪src/generated/java，本仓使用JDK17/protobuf3.19.6/protoc3.18.1；不复制生成Java或为候选生成物升级整套构建。修改运行代码/Proto后的相应便携构建及部署核对由实施批执行，前置不足明确记未部署，不启动猜测协议。本项只读审计与脱敏交付无需重启。

## 复现方式

本地编排脚本未提交。摘要明确保存全部输入、方法ID、包候选、根及imports，以下用既有工具组合重建；不依赖原机器固定路径。准备固定参考checkout、本仓审计基线checkout、JDK17、protoc3.18.1和protobuf-java3.19.6；由本机配置设置 `ASTAPS_REFERENCE`、`LUNA_AUDIT_CHECKOUT`、`JAVA_HOME`、`PROTOC`、`PROTOBUF_JAVA_JAR`、`AUDIT_OUTPUT`。输出放忽略目录。先检查参考commit/root tree与摘要相等，再从当前工具checkout运行：

```python
import hashlib, json, os, subprocess, sys
from pathlib import Path
sys.path.insert(0, 'tools')
from client72_java_surface import compile_scanner, scan_sources, source_graph
from review_client72_login_proto import packet_candidates

summary = json.loads(Path('protocol/cn-7.2/astaps-implementation-observations.json').read_text('utf8'))
jdk = Path(os.environ['JAVA_HOME'])
out = Path(os.environ['AUDIT_OUTPUT'])
jar = Path(os.environ['PROTOBUF_JAVA_JAR'])
compile_scanner(jdk, out, Path('tools/Client72JavaSurface.java').resolve())
subprocess.run([str(jdk/'bin/javac.exe'), '-encoding', 'UTF-8', '-cp', str(jar),
    '-d', str(out), str(Path('tools/Client72ProtoDependencies.java').resolve())], check=True)
for label, variable in [('luna', 'LUNA_AUDIT_CHECKOUT'), ('asta', 'ASTAPS_REFERENCE')]:
    repo = Path(os.environ[variable])
    sources = summary['source64SHA256']
    for name, row in sources.items():
        path = repo / name
        actual = hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None
        assert actual == row[label + 'SHA256'], name
    java = [name for name in sources if name.endswith('.java') and (repo / name).is_file()]
    rows = scan_sources(repo, jdk, out, java)
    graph = source_graph(rows, summary['methodCandidates'][label]['ids'], [])
    assert len(graph['methods']) == summary['methodCandidates'][label]['count']
    packets = packet_candidates(repo, graph, rows)  # 两仓javaType并集应为107
    closure = summary['protoImportClosures'][label]
    work = out / label
    work.mkdir(parents=True, exist_ok=True)
    descriptor = work/'dependencies.pb'
    roots = sorted(set(closure['roots']) - set(closure['missing']))
    subprocess.run([os.environ['PROTOC'], '--proto_path='+str(repo/'src/main/proto'),
        '--include_imports', '--descriptor_set_out='+str(descriptor), *roots], check=True)
    parsed = subprocess.check_output([str(jdk/'bin/java.exe'), '-cp',
        str(out)+os.pathsep+str(jar), 'Client72ProtoDependencies', str(descriptor)])
    files = {line.split('\t')[1] for line in parsed.decode('utf8').splitlines()
             if line.startswith('file\t')}
    assert files == set(closure['files'])
    for name, row in closure['files'].items():
        assert hashlib.sha256((repo/'src/main/proto'/name).read_bytes()).hexdigest() == row['sha256']
```

包构造候选的包继承与Proto引用用 `packet_candidates` 复核；候选166种子中的动态BasePacket参数、强制22根和两仓缺失文件另对照摘要。对每仓 `protoImportClosures.roots - missing` 运行 `protoc --proto_path=<checkout>/src/main/proto --include_imports --descriptor_set_out=<output> <排序根>`；以JDK17编译本仓 `tools/Client72ProtoDependencies.java`、指定protobuf JAR运行解析。其file行集合必须逐项等于摘要files集合，imports逐项一致；再校验全部Proto原始文件哈希。不得只比较总数。完整AST、原始diff、日志与descriptor二进制留在本地忽略目录；共享摘要不含机器路径、PID、账号或原始客户端数据。
