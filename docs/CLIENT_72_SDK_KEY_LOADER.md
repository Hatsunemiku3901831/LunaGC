# R2-KEY-LOAD：独立入口接入已验key核心

## 实施前计划（2026-11-08）

目标是在已实际通过的透明入口上，独立构建一个只接准确SDK Crypto key输入的显式实验产物。复用未改的核心与47导出转发，不把旧ext.dll或其它模块带入。当前已有核心自有ilhook ABI、同一核心→真实caller/PEM/RSA/Base64桥梁、真实零hook加载、实际SDK入口253B与两caller各76B绑定；自然key回调、本服route、信任与完整运行仍未验。

任务分两步：先实现/构建/自有测试并审可运行产物，再准备新用户ready后的正常加载、实际hook状态与校验影响观察。第一轮不输入凭据；真实认证待准确route/HTTP与运行接收方案汇合，不能把SDK页面或hook installed记为D-02。若系统/客户端拒绝该窄hook，保留失败并回退，不改变完整性验证、异常门或模块可见性。

固定源：核心 `patch/src/modules/sdk72_key_input.rs` SHA `67af556fea165d1597741cee2bbf2d310542c6c5c9637e44ee8877f4cc885bcd`；独立透明入口源/asm见 `clean-loader/offline-artifacts-main1.json`。核心只接受两caller返回RVA34eeb7/34ef88、this VT6d1cb8和固定stock public，唯一写入为入口stack+28第五参数；R8内容不读。原SDK磁盘SHA及原入口/callerSHA按[真实加载](CLIENT_72_CLEAN_LOADER.md)，不能仅GetModuleHandle非空便装hook。原公共材料两文件在既有sdk-key-input-sol目录，长度/hash由核心入口核验；不读取私钥、不调用目标getter取材料。

允许新 `patch/examples/sdk72_key_proxy.rs`、必要的同前缀私有子目录/owned fixture、新 `tools/verify_sdk72_key_proxy.py`、对应test和本页。Cargo最小example声明由主线添加，依赖/lock及旧lib/modules/clean-loader核心/asm/SDK key核心全部冻结；不加旧ccp/memguard/swap/NEUTRALIZE、异常处理器、route/trust、注入器或调试器。原始材料/产物/日志仅 `local/migration72/d02-resume-20261108/key-loader/`，不自动安装。

入口继续在DllMain仅记录本模块，不在loader lock内加载/改代码。首次正常转发解析原库后只启动一次普通有界初始化worker；worker只等待已加载SDK（总预算90秒），绝不LoadLibrary SDK。它不声称自然Init完成或深读Config安全区间。绑定实际路径/文件身份、SizeOfImage、SDK磁盘及三个准确原窗后，准备同一核心并用已验ilhook2.3.0 JmpBack/原callback安装唯一3950c0入口。核心、hook及其模块都需在回调整个进程期保持；不建立通用并发卸载管理器，不重试未知身份或覆盖已有hook。

SDK未加载/材料错/原窗不符/安装失败均记录固定状态且不装hook，保留原转发；该状态不被当成本服ready，研究时不输入凭据。可提供一个只含schema、初始化阶段/错误、实际模块绑定和安装结果的固定数据export供主线RPM，不放账号/key/token/body/cipher或其hash；不远程调用export或目标函数。复用原callback，不为统计复制分类逻辑或引入未验ABI wrapper。若实际必须新增callback状态采集，先返回明确必要性及自有ABI验收方案，不悄悄加进本卡。

验收出口：主线离线release构建与导出/源/产物身份，最小owned加载/错误拒绝/材料和原窗守卫，未改核心的既有ABI/原生证明仍对应同一源码/依赖；新loader逻辑须有有效失败与相应回归。随后实际实验再核安装产物、进程/模块/代码变化/固定状态、SDK页表现、正常退出和恢复。只证据充分的子出口标完成，R2-KEY/F-04/D-02继续未完成。

停止条件：任何固定源漂移、实际loaded identity或原指令不符、未知初始化/所有权边、目标校验拒绝或回调透明性失败。先查可验证输入，不重复旧HW/SW/ETW，不为不明确的生产生命周期添加复杂机制。主线独占Git/Cargo/Gradle/DB/端口/client/安装；子代理只交源码、自有输入/测试与精确构建命令。
