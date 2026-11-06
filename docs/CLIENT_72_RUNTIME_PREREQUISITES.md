# F-04a：恢复后的运行前置现场核对

2026-11-06。用户已恢复持续推进至D-02；历史的“融合后暂停/暂不实机”不再限制本轮。此项完成的是现场核对，不是F-04、补丁加载、资源ready或D-02验收。

## 实际现场

固定`extract_client72.HASHES`的EXE/global/startup三文件均逐文件重读且SHA-256匹配。Asta参考HEAD仍为`a49f51aa6f2787ffbc25cee46801b70dee860d23`，工作区干净；patch/resources锁定提交未变。`.env`为portable，`.env.local`不存在；根config及AGENTS/resources既有差异保持。具体机器路径、进程与原始报告只在忽略目录。

HTTP TCP8088、游戏UDP22101及独立测试Mongo27027均无监听，无游戏或服务Java进程；原Mongo27017与两个既有调试器保留。通过Automate发现并连接与本仓库固定调试器路径相符的一个本地API会话，实时`is_debugging=false`，随后仅断开API连接。没有load、run、attach游戏目标、设置断点、安装补丁或操作原数据库。不再沿用历史target PID或暂停地址。

实际执行`patch_game.ps1 -Mode status`退出1：目标7.2与源码遗留7.1版本守卫不匹配。`local_windows.ps1 -Mode status`退出1：没有服务。这两个退出码均不表示部署成功；不通过改版本或关闭守卫放行。`audit_client.py`的旧extractor profile=false是旧提取器不匹配，另以目标三哈希独立核对成功，不能混称固定7.2输入失败。

## 补丁的确切缺口

只读重扫当前patch源码特征：MakeInitialUrl、Browser、SDK RSA及两个native security特征各唯一命中；Browser命中RVA为`1A3662F0`，仍需加源码`+10`才对应已验入口`15A366300`。SDK Get/Post、product/price及free-recharge特征零命中；后者不作为P0登录必须钩子。静态命中不认证ABI、实际加载或成功路由。

另按本批已固定的Astrolabe文件SHA解析实际PE导出表：47个named export与当前`patch/build.rs`47声明集合一致，没有缺项或多项，证据`evidence/resume-d02-plugin-exports.json`。这是名称集合核对，不是47个函数ABI或实际代理加载通过。当前proxy还有5个旧NEUTRALIZE导出，lib中的ccp/memguard等控制没有本批7.2实机证据；F-04c只接入已验security信任输入，不能凭本卡称整个代理、SDK或D-01已验。

当前`patch/src/modules/security.rs::after_key_sign_check`仍直接将RAX置1，`on_mhy_rsa`仍用旧R12/R13/R8与268长度合同；不得安装并称保留验签。已有[原生密码学](CLIENT_72_SESSION.md)在明确本地root/签名输入下执行真实RSA/SHA/验签并拒绝坏签名，但未证明运行补丁提供了这些输入。下一步必须把受信root、服务端公钥及两类key签名供给连成可复现合同，再验证钩子寄存器与实际加载；不能把离线夹具直接当运行配置。

字符串工厂仅到`PtrToStringAnsi→14052FBF0`前缀，完整托管分配/GC与URL实际重写仍未验。[入口ABI](CLIENT_72_PATCH_ABI.md)保留边界；没有因本次重扫而晋级。

后续已做一次工厂体离线探索：复用已验seed工具的NativeCrypto/TLS/字符串分配存储，空NUL串经原生Marshal与工厂返回长度0；非空ASCII URL在native `141CAD123`读取未映射`120`而停止。该结果是当前离线CRT初始化上下文尚缺的复现，不是客户端故障，也不证明非空转换成功。保留`analysis/probe_marshal_factory.py`、`evidence/marshal-factory-exploration.json`和`logs/marshal-factory-exploration*.log`；没有替换未知返回值或以空串案例晋级工厂/GC。解除条件是定位该访问的真实初始化来源，再以非空ASCII/UTF-8/NUL/边界及真实ABI核对继续。

## 资源revision核对

本机名称含revision的文件及config候选只读搜索到6个：config.ini、Persistent/audio_revision、Persistent/res_revision_eternal及StreamingAssets的data/res/silence_revision。内置data/res与audio为49008628，silence为48722240；eternal为0，含义未知。固定候选标签R49086080/S49210135/D49230962仍不能据此判为同一运行时热更新状态。

未改资源、revision、根fastRequire=true或运行数据。固定七表、scene3与继承Lua的已验部分仍有效；E-01c6仅修已复现的调用者bindings恢复，不自动解决local defs、缺ID、未实现ScriptLib业务或revision。资源父项继续未完成。

## 证据与后续

本机报告位于`local/migration72/evidence/resume-d02-{runtime-inventory,debugger-sessions,client-audit,input-revisions,status-checks}.json`；日志为`local/migration72/logs/resume-d02-{patch-status,portable-status}.log`。脚本API仅做实时查询；没有新实机登录/场景结果。

F-02c继续收帧selector、F-03d继续初始化三层、E-01c6修复真实Lua缺陷，可在上述缺口独立推进。前置满足后才构建便携隔离部署，依次核对日志/健康/TCP/UDP/PID/运行JAR SHA-256，再做新号显式选人、旧测试号、初始化/场景握手、心跳及退出重登。D-02仍未完成，用户的持续推进目标没有被改为暂停。
