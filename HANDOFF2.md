# D-02 持续开发活动检查点（2026-11-07）

**这是活动检查点，不表示暂停。用户已恢复持续研究、实现、回归、必要实机、Windows portable隔离部署和联调直到D-02全部实测通过，覆盖此前暂停/仅离线/模型阶段限制。D-02仍未完成。** 当前应继续可独立工作；只有精确需要用户交互时请求一次动作并等待实际反馈，不把沉默当完成。

## 1. 接手与保护

接手HEAD `52999ce`、分支`7.2-dev`。主仓库起始只有resources原dirty；patch `3675ea7` clean、resources `dac2a9d`加原ScriptSceneData差异保持。不提交他人resources差异。五份profile逐文件SHA-256匹配，AGENTS/.env/config/JAR与资源差异hash核同，.env.local不存在。原mongodata/local/data保留，未清库。实际初始没有游戏、Java、MongoDB或调试器，8088/22101/27017/27027无监听；历史“原27017/两个调试器/保留pause”均不适用。机器绝对路径/配置/进程命令行只在`local/migration72/d02-active/main/initial-inventory.private.json`。

先备份核验原HANDOFF2和HANDOFF，再写本页；具体备份路径/hash/字节证明见`local/migration72/d02-active/main/checkpoint-written.json`。HANDOFF本次只追加，已核旧字节前缀。

## 2. 已完成与真实运行结果

复用C-01b网络修复、已验适配器、Avatar/Scene精确原生片段及资源/章节/保存和127份证据；旧计数不当本轮重跑。当前JAR仍原f475dbf6…8cade8，无新构建/服务部署/补丁安装，SDK和完整P0/资源合同未齐。

主线独占实机，新建正确路径的管理员调试器会话；四个debugger输入核同。第一次真实load仅到新系统断点，未执行任何continue，旧观察器因自动TLS点前置归属拒绝。10个自动singleshoot点由各自live PE TLS数组准确核实，不以名字认领；3旧系统终止点RVA与当前export不同且disabled，旧GlobalDispatch软件点仍在。先核dd64逐字节等于已备份和原e受审hash，再按四个精确名字/模块/RVA/属性清理；11个TLS+EP点保留，全GPR/flags/DR保持，目标同一暂停现场。

Terra修复研究观察器：独立TLS数组命中+singleshoot才保留；新增仅接受本次固定旧源码/run前归属拒绝的`--resume-preflight-from`，重核本次原始输入、无命令/停点/观察/HW操作/自有点、OS活跃暂停及实时PID/CIP/RSP/TID和非DR寄存器。普通HW旧resume/continue限制不变。主线审diff/真实raw/清理证明，31项守卫通过，四核心分类/返回函数AST等于接手HEAD。原始失败、隔离旧源码红测、绿测各自保存，未混记。

同target专用续跑实际38.875秒后退出：54停点=1system+15精确TLS+1EP+37匹配首机会AV（逐个con1），EP后仅一次slot0硬件安装，无自然HTTP入口/返回或高层80000004事件；OS/PUB/log均exit80000004。Windows Error1000同PID/创建时间，fault RVA112250e0精确对应HW位置，1001同报告APPCRASH。不能视为业务入口成功或直接归因保护/PUB丢失。客户端五输入保持，未改参数、返回、校验、信任或原文件。

退出后只读API无target、四表空；当前dd64有3当前系统终止点和1 GlobalDispatch HW点，准确ledger为`main/debugger-after-hw.private.json`，原db副本为`YuanShen.after-hw.dd64`。下次load前须核这份新ledger，不能按旧SW四点或API空认定持久库空。不要run已退出目标，不原样重启。

SDK与Scene源码交叉审计已由主线阅读：SceneTeam对每个活动Avatar要求非空武器；候选主角5/7初始11101引用存在仍不等于实际加载/装备/实体；Done在spawn和Rsp之前置LOADED，空其他实体列表仍发送MEET。目标SceneTeam/EntityAppear协议及完整SceneInfo生命周期未闭。专题`docs/CLIENT_72_D02_SDK_ACTIVE.md`、`CLIENT_72_D02_SCENE_ACTIVE.md`，总计划`CLIENT_72_D02_ACTIVE.md`。

## 3. 活动分工与文件归属

- 主线：共享文档、Git、现场、集成与审核；唯一实时客户端/调试器负责人。当前没有游戏target，管理员调试器仍在。Git/Gradle/DB/固定端口/构建/安装串行，无自有服务或Mongo。
- Astra avatar_contract：`tools/client72_avatar_live_contract.py`、对应test、`docs/CLIENT_72_AVATAR_LIVE_CONTRACT.md`；原始输出`d02-active/avatar`。新发现enum flag真实leaf producer1402f8450→写145bc8ef0=1422f9988，固定首byte1；准确Int32Enum type351和HashSet ctor156fbc0b0，正在独立原生验证warm/cold和连续ctor。未审核接收，不提前称完整构造/g2。不得接实时。
- Sol resources_contract：`audit_d02_resource_sources72.py`及test/`CLIENT_72_D02_RESOURCE_SOURCES.md`；原始`d02-active/resources`。已从固定候选commit取两张Ornament、13来源输入逐blob验证，安装manifest/ctable追溯进行中；ctable persistent/streaming相同，LoadBinDataZipFile实际loader来源继续。候选JSON→binary桥梁、revision、accept规则及自然loadAll未闭。不得改resources/配置/实时。
- Terra sdk_contract：完成两份SDK/Scene审计和观察器修复，文件已冻结；未操作实时、Git或构建。观察器SHA d7ca80bb…681d8b，测试8397e8f3…2af71；31检查原始在`d02-active/sdk/observer-{red,green}.log`。
- Astra startup_exit：新HW矛盾只读攻坚，`audit_client72_hw_exit.py`/test及`CLIENT_72_D02_HW_EXIT.md`，原始`d02-active/hw-exit`；审固定TitanEngine/x64dbg/Automate低层事件分界和新raw/WER，禁止实时控制。新dump取得后交其分析，不做无新信息的重复fixture或load。

各代理无共享生产sender/Proto/config/patch/资源权限，无Git暂存提交、Gradle或DB操作。未受审的代理新增文件保持待审核，不按其总结自动接收。

## 4. 阻塞、等待与避坑

普通权限读取本次WER归档被OS拒绝。主线已写只复制本次Error1000/1001对应报告的管理员PowerShell helper，启动可能等待UAC；用户问题已发：看到管理员提示时允许这一读取，并回复已允许/无提示/拒绝。实际结果必须核`main/wer-elevated-copies.private.json`和`wer-copy/`，不能说已取得dump。另已询问刚才39秒实际最后画面/错误弹窗，等待真实反馈。两项都不阻碍Avatar和资源静态研究。

首次新API审核漏传构造器路径发生FileNotFoundError，未连接或改target；已显式绝对路径修正。不要将工具返回值或is_running在no-target的true当存活；必须OS/PID/事件联核。新的高ASLR基址按本轮raw换算，绝不抄旧绝对地址。当前HW异常精确WER地址却无高层事件是矛盾，必须找最早分界，不默认被保护、不改校验。

资源ctable只是存在/hash/静态选择证据，不等于自然revision就绪；Ornament候选只含新角色行，不批准主角默认空list。Avatar新producer仍待独立证据审核，cold与装备/技能cache不自动闭合。完整Avatar不代表SceneTeam/实体等P0闭包。ready/required/三层不改求绿。

## 5. 下一动作与验收

继续R1/R3独立新证据；审核并第三复验各代理原始输出，分项更新Project/Harness/专题并本地提交（不push）。先将已验观察器修复及当前记录提交，其他文件只按归属审后提交。管理员helper若获系统确认并实际完成，复制并核本次dump/Report.wer交Astra；若不可读则记录准确最小输入，不重发无效请求。根据低层事件新证据规划下一最小实验，主线仍唯一实机负责人。

R1/R2/R3合同满足后R4核完整登录/初始化/场景/队伍/世界/实体消息和必要资源/顺序，执行对应wire/账号/经济/保存/Lua/生成检查；R5正常构建portable隔离库/data/资源、安装已验补丁，核日志/健康/TCP/UDP/PID/运行JAR SHA和实际DLL/信任。之后R6先旧号，再新号显式选人命名，验证完整握手、实际角色入世界、连续心跳/会话保持和两类号退出重登归属/保存。当前上述所有客户端条件仍待，不以健康、LOADED、截图或研究片段宣布D-02完成。
