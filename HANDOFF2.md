# 国服7.2持续工作检查点

更新：2026-11-06。当前目标仍是按Project依赖逐项实际开发、测试、本地提交，持续到D-02实机验收。**本文件是长任务接续检查点，不是暂停/停止或D-02完成。** 最新状态以Project/Harness与本机实时归属为准。此前要求限定停止的记录已被继续指令覆盖。

## 必须保留的约束

- 用户禁止computer-use/UI操作；继续仅脚本/API，不调用CUA、不截图/模拟输入、不重复启动无关客户端。已有管理员x64dbg Automate由用户启动，动态调试授权持续有效。
- 只面向国服7.2，Windows portable；不Docker/WSL、不过版本校验、不给未知协议号/tag/业务名补猜测。用户确认没有可追溯7.2 Lua，允许参考LunaGC7.1实现迁移；旧Lua与历史离线JAR依赖不冒称7.2导出，不维护旧客户端运行环境。
- 用户授权重复工作、边界/规划/验收完整的任务交Sol，性价比优先；困难且值得才用Astra xhigh。当前两个Sol各自文件归属分开，不让其改共享摘要/矩阵/根配置。
- 不推送。保留原mongodata/local/data；AGENTS.md既有11行和resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json差异不提交、不覆盖。patch固定f2874103ab70afc56260ee12d1eaca96247933b2，resources固定dac2a9d43332ef51879522cf39da2831b0ca0543。
- 每次继续核对GAME_PATH、env/config、进程/端口/子模块；本机PID/路径/令牌/原始日志/数据库只留忽略目录。未知语义保持未知，编译/健康不代替实机。仅文档/离线工具/测试无需重启；运行改动前置未齐则记录不部署。

## 当前提交与完成项

本轮从a09e201继续，最新检查点代码/证据为d2969b5，随后cb74432只增加下一任务卡。均本地提交，未推送。关键提交：026ad9d场景资源、b68a8ab选人继续、dfd11d6继承Lua顶层、b5309b5场景通知reader/等待、caaf8ad阶段纠名、9408f41生产序列化修复、cd13b15正常消费、af573b9场景query、4e0aaf8列表消费、52f1945接口来源、69c3672 owner UID、d2969b5整合及六类非空嵌套。详细顺序用git log核对。

- B-02e1：原born39保留，继续33原生样本，媒体/计时器只登记/显式离线回调。20353仅两个uint32字段，业务名unknown。旧identity request称谓错误已纠正为stageValue等，原执行路径不变。
- B-02e2：27748/type46311/BBECFDMDFJC身份为PlayerEnterSceneNotify，26外层wire、三float非空内容、clone/Copy与缓存等待；128发现/32正式。只晋级消息/指令身份，26业务字段未知。网络先1507AD120包装，config105/完整调度/入场未验。
- B-02e3：14C8AE9A0是阶段时间写入，85949值10为OnPlayerEnterSceneNotify。13计时样本；正常14样本执行原reader、stage10、task allocation/reset/字典至153A18E80。uint64/list/字符串/高位保留，业务字段不命名。
- B-02e4：19 query样本，实际153A18E80/153A357D0/156632D10，owner+90为Dictionary<uint32,uint32>，正式return声明未知。冷源GLOBAL+34730为Dictionary<uint32,type70859>；非空记录+80(hex)到14AEF9DB0止。两正常分支分别到153A3CB70或15881E210。
- B-02e5：24列表样本（14返回/7错误/1IFix/2fallback），两消费者/InsertRange/类型查找执行原指令；Count/CopyTo/数组为明确模型。旧List身份、容量复用/扩容、size/version、partial clear均独立断言。首轮nonempty误判空的生成器问题已修，独立expectedItems目录防回归，失败快照保留。
- B-02e6：6类（66040/36137/67761/25866/34133/69612）15字段，13writer/24reader/3拒绝与13独立protoc，双跑trace/摘要/Proto一致。两native错误helper和一输入模型拒绝分开。INTMAX空子消息仅在明确stream模型中返回，不声称实机接受或安全拒绝。六构造器分别执行；保护父构造分发未恢复，父分配只提供分别执行构造器已观测的默认存储。详见CLIENT_72_SCENE_NESTED。

## 资源/生产迁移结果

E-01b固定DimbreathBot/AnimeGameData提交d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1，CNRELWin7.2.0_R49086080_S49210135_D49230962；scene3点1823、正引用2817无缺。来源没有Scene/Block/Group/Common Lua。

固定继承脚本64块17806声明组，13616非dynamic实际执行，4190 dynamic仅声明。当前新JAR三CLI重跑：出生686成功（403静态/283dynamic）、5492正ID无缺；全静态inline13616成功，fast仅13209成功、407错误；95500以上引用中8缺ID70200012..19都在garbages.gadgets，不删组/补记录。脚本manifest13785文件/104 Common不变。

E-01c2（9408f41）只修已复现四处：LuaSerializer数值字符串必须先判真实TSTRING，SceneRegion.radius改float，两个boolean显式setter。7生产JUnit先6失败后全过。实际全13616组/20871套件、34970格/60884成员两次完成，原5组异常、30字符串污染/同根30转换、1210截断、144+868漏绑消除；仅8缺ID保留。未执行trigger/action/完整SceneScriptManager/getGroupGrids/cache安装。

E-01c3历史反射62缺口对应1390位置/1503组，其中2项实际经GroupLuaCall userdata注册。E-01c4仅新增4行GetSceneOwnerUid，返回当前Scene真正host.uid；42位置/317组正常只读契约有来源。真实LuaJ/userdata/event context测试9次先4失败后全过，owner不同于guest/首玩家，含1/10001/MAX_INT及后续avatar查询；没有猜缺owner或无效gadget返回。旧220方法正文/签名/调用选择器完全未变。当前public207、反射61缺口、2wrapper已注册、59未见入口/1468组；GetGadgetConfigId等仍未知。

## 当前构建、测试与门禁

当前根JAR（遗留文件名LunaGC-7.1.0.jar）SHA-256为2566f46604df80ddaad05801fb182a7217b9d057a2c8619e21fc54e49db083fe，BuildConfig为69c3672；portable构建24秒。六class与当次编译相同，四序列化class相对上个4dd1c47a…bbe7字节完全相同。旧4dd1 JAR备份仅作历史离线依赖，未启动旧版本。

366完整研究回归通过，结构校验0错误，ready仍228缺口。此前28套580项Java（原495账号/经济/保存+85区域/脚本）失败/错误/跳过均0；本次owner独立9项红绿另计，不能称同一589项执行。完整Java集未重跑，历史217失败保留。独立测试Mongo随机库正常关闭，复查仅admin/config/local，原Mongo27017保留；收尾辅助JVM因fat JAR logger死锁仅终止该已确认辅助进程，修正独有日志配置，未杀DB/服务/调试目标。

现63研究输入，唯一ScriptLib源码hash3b554d175c4028e0d30bc4727035f5f04d44f76a3d88b562b0d03227b4cfcdf2经人工复核。原dependency/matrix/API/JAR守卫均先实际拒绝；真实新compiled父报告与源一致后仅更新相应hash，不改算法。60→62替换五份摘要并增current API/list，62→63增nested；三层矩阵/候选/描述符/开放边界保持，recover双跑相同。A03完整/P0和父B03/C01仍未完成，raw wire新增不等于业务已恢复。

没有部署/启动服务。最近核对无Java服务、HTTP TCP8088/游戏UDP22101/测试27027监听；健康和运行JAR无对象，不标通过。原Mongo27017属workspace。根config hash未变；配置fastRequire仍true。SDK/补丁/完整协议/资源前置未齐，不启动猜测协议。

## 实机动态状态

已有管理员x64dbg Automate会话与原游戏目标保留，最新只读核对running=true；三输入和实际代码两段32/26字节相同。阶段开关/IFix为0，真实数组长16且全零，两key槽空。开关关闭意味着全零不能证明没有请求/阶段；SDK认证、HTTP路由、补丁加载、登录/选人/入场均未通过。

早期连续150秒观察40次首次异常均核对事件/ex.code/addr后con1+普通run交应用，结束存活；此前分段重连事件缺口和80000003/80000004退出根因仍unknown。不要erun无条件传递所有首次异常，不盲目run或重复load；API.detach_session只断观察连接。若目标暂停先查当前异常/断点/事件。当前状态必须重新发现归属，不能沿用本机历史PID。

## 正在进行与下一步

cb74432已先建卡并委派两个Sol，Root继续主线，任务未停：

1. B-02e7 /root/scene_normal_native：仅新增scene-data工具/测试/专题，恢复153A16AA0从原+48/type69612消费。初读+28非零才进153A16B30，进一步到153A3CD60更新owner+1F8列表，零query分支止14AF3D130；25866在主body尾段，尚未到。原生body不建模，true/false实参和对象指针独立断言。新SceneNestedReader Graph格式与旧normal不同，不能修改旧fixture或偷换对象称连续成功。
2. E-01c5 /root/scene_serialization_audit：仅新增实际生产ScriptLoader加载审计/测试/专题，独立workingDir/user.dir/config/data，不改根配置/生产/缓存。先合成local defs及真实407代表组，真实init/getScript/bindings/eval/必要Group.load，fast/inline各新进程；RequireFunction会吞Common错误、Group先loaded=true，必须看实际bindings/套件/独立attempt记录。整场可加只拒绝业务调用的观察边界，但不能当完整生产环境或返回假成功；require吞哨异常也必须在外侧记为未通过/范围外。
3. Root正只读核对14AF3D130与14AEF9DB0声明/代码：前者是type26814静态uint32/string/79078列表相关方法（2参数），后者type58209单uint32值类型方法（0业务参数），**不是已证明同一包装类型**。目前仅缓存元数据探索，尚无正式执行结论；需固定三输入和参数签名后再建卡。冷源70859表的业务名未知，不按结构猜SceneExcel字段。

每个独立项实际验收后本地提交并更新Project/Harness。若新运行/配置更改，先保留旧证据、复核源码/JAR/真实新父输入再更新守卫；不要为绿色测试自动刷新hash。D-02依赖仍不满足，继续可独立研究/开发，不把交接检查点当目标完成。

## 证据入口与避坑

共享相对路径：protocol/cn-7.2/*observations.json、login-proto-gap-matrix/review、scene-nested-research.proto；专题CLIENT_72_SCENE_{WAIT,NORMAL,QUERY,LIST,NESTED,SCRIPT_API,OWNER_UID}等。原始证据/备份/JAR/日志/数据库在忽略local/migration72。重点scene-owner-{impact,delivery,build-artifact}、scene-owner-list-login-proto-review-check、scene-nested-{determinism,login-proto-review-check}.json及scene-owner-list-nested-research-regression.log。

精确重复测试原始文件时注意Windows raw CRLF hash与规范换行/JSON digest不同，不能混用。API工具默认父报告现在已与新JAR同步；旧baseline仍保留旧source/JAR hash，不能拿新源覆盖历史再宣称没变。原ScriptLib差异仅正常owner查询，GetGadgetConfigId错误契约仍未恢复；不能把gadget资源ID当configId。Native父构造默认存储/EOF模型与真runtime分开，绝不能以INTMAX夹具接受或compiled字段命中冒称实机验收。
