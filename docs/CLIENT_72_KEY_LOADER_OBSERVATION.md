# R2-KEY-LOAD-OBSERVE：实际窄hook的固定只读观察

## 实施前计划

任务归属R2-KEY/F-04，目标是在独立key入口构建及owned验收之后，准备一次新的无凭据自然启动，区分原转发绑定、实际key安装、准确代码变化和SDK页面表现。不是自然key caller或本服登录验收；原callback不增加计数/wrapper，不读认证输入。

现证：零hook透明入口已真实加载/恢复；新key生产DLL已由主线release/offline构建，578048B/SHA `ad18e741052a05850ff7b80b8043af0551981793dc7e3cca89be26e565a2139d`。实际EAT是stock47、数据`Sdk72KeyProxyState@48`和冻结核心既有`sdk72_key_input_inner@49`，后者由主线明确接受；不接受其它导出。新loader的自有模块/材料/真实ilhook失败拒绝尚由主线验收，未安装到客户端。

固定输入：原五stock及profile；冻结clean core/table/asm、key核心、Cargo.lock和ilhook2.3.0；本轮key构建/owned source-artifact proof。现observer `observe_client72_clean_loader.py` SHA `6ece909b020efd46a15124f20198c02b41c43f775637c50203a29ea2fb363dd7`只可复用backend/已验路径和PID合同，不修改其原四窗、零hook断言或旧证据。实际SDK原入口及两caller来源见[key实施](CLIENT_72_SDK_KEY_LOADER.md)。

未知：自然安装是否被目标拒绝、实际页面/校验反应、callback是否自然执行、准确Config选择/route和本服会话。只读状态installed不能填平这些未知。失败保存原始固定状态/代码，不绕校验、修改模块可见性、注入调用或扩大读取。

修改范围仅新`tools/observe_client72_key_loader.py`、对应test及本页，raw仅`local/migration72/d02-resume-20261108/key-loader/observation/`。禁止改任何旧source/core/SDK/runner/Java/Proto/config/资源、Git/build/DB/端口/客户端或安装。主线串行验收和实际启动。实施者先交pure collector/backend mock；不得自行OpenProcess真实客户端、launch或RPM。

观察合同先静态固定，再执行：沿准确EAT确定64B状态位置、47 target表和原callback的自有产物机器码。状态仅接受明确稳定终态；installed对应schema/size、所有guard/held标志及SDK base/size，无任意字符串、凭据或回调计数。读取首尾同一64B状态，只用于拒绝观察期间终态漂移，不声称原子快照或自然业务ready。

成功installed分支读取原47转发目标表376B、准确SDK入口253B、两caller各76B，再检查实际ilhook跳转及原callback。根据冻结ilhook2.3.0 `modify_jmp`只接受准确near E9或far FF25编码，其余原入口字节必须保持；trampoline只在其实际代码指针处读取静态算出的有限长度，核完整prolog/callback ABI/epilog、搬移的原始指令及回原入口。callback在本次自有DLL可执行节内按原assembly唯一精确机器码（包括到已知inner导出的rel32）确定，不猜SDK函数。userdata只检查非零指针，不追读core/材料/请求。

初步预算最多8次、1600B、单次不超过512B；实现须先算精确路径和长度，不能将1600用作任意扫描许可。若精确产物/依赖无法推导trampoline或callback，返回准确缺项给主线；不放宽为“跳到任意proxy地址即成功”。失败终态不跟随未知代码指针，仅保存状态和仍可独立审核的固定原窗/target表。没有状态终态、目标退出或任何绑定/短读/身份失败均保留partial，不做补读/重试/扩大范围。

backend先校验新monitor来源及创建时间、实际image、三个模块的samefile/SizeOfImage/磁盘身份；`OpenProcess(0x101010)`只读/query/wait，句柄关闭。没有debug attach、线程上下文/GS、目标调用、写内存或export远程调用。只在主线新ready和已备份/安装核对后执行90秒module monitor与本固定读取；结束提示用户正常退出，OS确认后恢复stock并核原配置/数据。

产物/验收：精确源/产物/依赖pin、实际预算/逐读证明、near/far及原始不变范围的独立owned样本、callback/返回边/状态/路径/PID/错误拒绝及句柄关闭测试；主线审查运行产物及pure tests后才准备真实wrapper。真实轮记录已装DLL、固定状态/代码、页面、正常退出和恢复，不自动标R2-KEY、信任或D-02完成。

停止条件：源/输入漂移、无法准确固定callback/trampoline、超预算/短读、未审模块、非终态或目标拒绝/异常。按工具、输入、实现、部署和实际目标行为分类；不重复旧SW/HW/ETW。普通已授权实现无需再设用户批准节点，实时客户端动作须新的在场确认。
