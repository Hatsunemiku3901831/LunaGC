# D02-R1/R2：cold-arrays 有界快照计划

目标是为主线下一次自然启动准备独立 `cold-arrays` 只读采样，取得 [Avatar cold接口](CLIENT_72_AVATAR_COLD_INTERFACES.md) 缺失的 Array117、Type314 和 Type[] rank1 cache 首候选，以及 [SDK 传输](CLIENT_72_SDK_TRANSPORT.md) 的少量已证根；不在本卡访问游戏。v5 采样器 SHA `5adcd7d1981aa074d7266693bfca710a76dbf16d0bac5b3c32bdd84339e16f8b`、原测试 `9dcc33cd00bad59b19674fd895b42d00c12cc790ce6aade52ae7bbc771bbecc0`，先逐字节备份到忽略目录 `main/cold-arrays-tool/`。旧 all/archive-state 顺序、语义及既有归档保持。

输入是主线既有monitor报告、同进程 PID/image/creation FILETIME、五profile与配置、当前模块表以及固定客户端 DLL；绝不据历史PID或静态首选base读取。主EXE B+5bc8f18 缓存只采13个指定类及精确头、metadata、有界接口和继承；三个默认/usage cell、registry、options及canonical分别限定。每个底层请求最大256字节，保留原始读、错误和时间；null、部分或失败均不追子节点。独立判定 RuntimeType284 两虚槽，enum20060 underlying 只采一次头，不再递归。

Type[] cache 仅在 Type314 header descriptor 为 kind18/index314 时计算首桶；occupied bit 为1才用 occupied 低位 popcount 选16字节 entry，解释为两个指针；同时保留 tombstone 原值、不跳过或探碰撞。空、未读、错误类型、不匹配和上限分别报告 unknown，不宣称 cache 完整命中或 ready。所有结果为非原子观察，主线须核跨读取时序，不能以指针非零证明初始化成功。

SDK 仅在实际模块表唯一匹配游戏 Plugins 的 AccountPlatNative 且文件身份/entry46字节与已审静态来源及实际进程相同时，读取 engine、NetworkManager、aggregate 三根及各16字节头，engine+70 ThreadPool指针和非空时的+70..8f队列描述；aggregate和线程池没有vtable，不把它们首字段当vtable，零队列不说明无活跃HTTP。不取node、easy、URL或字符串，也不触发任何方法。静态输入 `rpm-roots-contract.json` SHA `c2620c938c2d5be83c6e1dc96eeba862cc9a85886a8f1f2b08c678bdc97071da`；SDK固定SHA `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`。若入口或模块身份不匹配，SDK子采样停止并报原因，不阻止独立R1读取。

验收使用可控内存映射，独立覆盖scope隔离、64位地址、occupied+tombstone/packed双指针、partial/null和上限，以及SDK入口不符不追根；只跑原monitor和采样器有关守卫。无目标进程、无DLL调用、无读写真实内存、网络、数据库、调试器或部署。实际客户端状态、SDK自然调用与D02/g2保持未知；本卡源码审核后由主线负责运行与验收。

## 本次实现与离线验证

`collect(..., scope='cold-arrays')` 是与旧 all/archive-state 分开的早返回路径。13个类的0xd0头含所要求的完整字段；metadata `+0x43 xor 0x25` 的 count 和 class depth 各仅在1–16时读取相应指针数组。`classSampling` 区分空指针、读失败、空、超限和读成；候选的空、错key、头不完整、probeLimit为0及首桶空均不晋级完整lookup。`typeArrayProbe` 记录 mask、blocks、probeLimit、bucket、rank、occupied；单独记录 tombstone。选中key/value只读各自0xd0头，不沿二者继续扩展。读取本身逐项留 bytes/hash/error/UTC，全部不是原子快照。

只对 cold scope，实时入口还比较当前 profile/五文件和配置哈希与monitor报告；现有 PID、image、creation FILETIME、存活和主模块检查继续适用。SDK模块以 `GAME_PATH` 下 `YuanShen_Data/Plugins/AccountPlatNative.dll` 的完整路径在当前模块快照中唯一匹配，磁盘固定身份由五输入检查，另校对磁盘和live的入口46字节；不匹配或缺模块时只停该分支，主类快照继续。engine header 的vtable不匹配则不追ThreadPool；NetworkManager记录其vtable是否匹配，aggregate不声明vtable。queue 仅32字节原始描述，未读取请求、节点或字符串。

重新独立读取固定 SDK 文件：SHA `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`，入口46字节 SHA `80022f69e8fd0827f04f839a6be12b86b6d562d2618acb1e36fc151981c49f2c`；与合同的7个 producer/reader 代码窗口均逐hash匹配，证明 `main/cold-arrays-tool/sdk-static-proof.json`。这仅是磁盘静态证据，不是运行中的 SDK 状态。

共26项定向测试通过：原 sampler 7、新增8、原 monitor 11；覆盖64位指针、首桶occupied/tombstone和两个指针、错descriptor、错key、部分entry、null packed、类上限、默认类/虚槽、SDK入口和vtable拒绝，以及CLI输出scope隔离。去掉新增 cold 分支并恢复原函数参数后，`collect` AST 与备份v5完全一致；旧两scope测试通过。Terra交付源 `698a7c37…6bbcc` 与测试 `7cdfab5b…46ff2` 已逐字节备份；主线对照原生producer和70字节record合同发现原实现把十进制70写成0x70（112字节）。先以真实70字节fixture复现接口判定失败，再只改metadata读取宽度为70，SDK的+0x70偏移保持。26项重新通过，证明 `main/cold-arrays-tool/width-main-review.json`，红绿日志 `width-red.log`、`width-green-correct-module.log`；首次绿测调用拼错monitor测试模块，失败日志保留，不计作通过。现源码 SHA `2eb415af7e410b53df35340426310a52e72f7e9a69692c2158c4dadb92362aae`、测试 SHA `184ccce26d606f11fbc19a1700cc4ca41e09cd6df52860fd21f8a78983e340b5`。主线重核旧collect AST完全相同，原备份及旧执行源不变；新源仅作为planned-v6归档，尚未实机执行。

主线对源码和现场完成独立审核后，才可对**当次**自有、仍存活且身份匹配的 monitor 结果执行；变量必须由主线给出，输出选新文件：

```powershell
python tools/sample_client72_initialization.py --scope cold-arrays --observation $monitorObservation --output local/migration72/d02-active/main/cold-arrays-tool/cold-next.json
```

离线复核无需目标：`python -m unittest tools.test_sample_client72_initialization tools.test_client72_uninstrumented -v`。

## 主线实际自然启动与限定采样（2026-11-08）

用户再次明确ready后，仅一次原始90秒启动，原文件/参数/工作目录、creationFlags0，无debugger/hook/补丁/本服。`plain/run-elevated3.json` SHA `303db4bc6daa85d3fda6d73f52cbe339021ba84cfaa45152bb4bf92a1746c58a`，43样本，监测源仍e0b4134e…33c6e。随后同helper执行本页新scope：`main/cold-arrays-tool/live-elevated1.json` 为46,981字节，SHA `63d3aaf80a37ca1f4f724a73ba2ee84354da94846acde16f27deccd116eef8c0`；99项逻辑span、0读取错误、每项<=256字节、metadata均70字节。PID/image/creation FILETIME、实际模块base、五文件/profile与配置逐项一致。执行源v6与既有monitor-v1逐字节归档，证明 `plain/source-snapshots/executed-source-proof-cold-v6.json`；不覆盖历史八报告的原proof。

自然Array117接口6/depth2、Type314接口0/depth3、Type[] usage接口5/depth3；三默认/usage及13指定cache类头均取得。Type[]首桶mask16383/bucket1404/rank11，occupied且key指针等于实际Type314，key/value头均读成；这只是首候选，不能冒充完整lookup/构造或default comparer写回。SDK三个已证根非空，engine/manager vtable匹配，磁盘及live入口46字节匹配；ThreadPool仅取队列描述，无node/easy/URL或凭据。

用户实际反馈停在登录页、无报错、未输入账号；取证后按UI正常退出并再次核OS无游戏/调试器、固定端口无监听。没有句柄观测用户退出码。此次关闭最小自然类状态缺输入，已交Avatar/SDK离线后继，不证明完整cold、SDK自然请求、本服登录或D-02。原JAR/配置/资源/补丁保持，无生产部署。
