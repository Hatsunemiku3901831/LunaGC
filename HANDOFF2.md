# 国服7.2详细接续记录

更新：2026-11-05。用户要求持续推进至D-02完成并验收；目标active，无暂停指令。D-02仍未完成，不能用离线工具通过替代实机验收。

## 当前状态与完成项

本批起点08f8284；新研究提交`c158e0e`完成A-04d5n3限定验收。正式工具trace_client72_dispatch_config同次执行：151210540 JSON→原生RSA/SHA/验签→父/Region reader→两次151204AC0配置函数→两次原生事件分发→Region全局字段写入→1512124B9。没有跳过配置body或把事件函数替换为成功返回；下一SDK前置和SDK函数未执行。

6原生样本：空配置、第一段非零/列表、第二段Int32最大/负列表、Int32最小/溢出、第一/第二段坏JSON。177研究回归通过，trace/摘要两次逐字节一致；研究校验0、ready1。业务命名仍35字段，tag14/tag965未知；A-04d5n/B-02c9、A-04d/B-02仍进行中。

AGENTS.md和resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json为既有修改，保留且未提交。patch仍f2874103ab70afc56260ee12d1eaca96247933b2，resources仍dac2a9d43332ef51879522cf39da2831b0ca0543。只改离线工具/摘要/测试/文档，没有运行源码/Proto/补丁/配置/资源变更，没有操作数据库或客户端，不需重启。未来启动仍须重新核对.env/.env.local/config、进程/端口，使用portable。

## 实际证据和复现

正式工具tools/trace_client72_dispatch_config.py、tools/test_client72_dispatch_config.py，共享protocol/cn-7.2/dispatch-config-observations.json。详细复现与模型边界见CLIENT_72_SESSION末尾A-04d5n3、Harness。

本机local/migration72/evidence/dispatch-config-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json；logs/dispatch-config-{run,repeat,regression}.log。前批n2工具和证据保持不变。

命名静态输入已核对Encoding.utf8Encoding（GLOBAL+24B8）、GlobalVars.SafeOptionalResourceCategories（+6248）和SecureOptionalResourcePolicyPlugins（+6160）。JSON容器、数组、装箱、类初始化与空事件注册为明确夹具；原生scalar getter/List clear/add、两配置body、事件1532D6720→1532ECA40→空字典FindEntry1565EE8D0实际执行。

两次配置从响应tag12/tag13接收ASCII JSON，key/tag11明确为空；非空key解码/XOR未验。列表预填123用于证明缺键清空，覆盖非零/负数/Int32最大。事件ID919携带mode1/2，只证明空监听器分派；真实注册回调未运行。Region tag13/string经+30到GLOBAL+6240/+6078，tag14/uint32经+140到14586CFB4均由非零独立wire验证。

## 当前未完成与下一步

当前没有整体外部硬阻塞，可以继续研究。下一步从1512124B9读取GLOBAL+F2E0开始：缺该singleton时在151212D8E进入原生NullReference分支；不能设任意对象后声称真实初始化。它的正常分支接15275F080，必须恢复该函数真实行为后返回1512124EB，再继续至B0（15121297C）。第二协程和tag965业务来源仍需独立证据。后续B-03/C-01、D-01补丁加载/信任、E-01资源闭包和D-02完整实机顺序均未完成。

SDK函数声明：类型14237/BIFHKCHGMEO，方法IPKPEJOFHLB，entry15275F080；它构造MiHoYo.SDK.JSONObject等，读取SDK对象+68，命名Miscs.GetGameVersion1531DCB70、ConfigChannel.IEDHGBHKFAH14947D950；后续StringBuilder序列化、141447620及其他SDK数据源/字典操作仍未执行。函数元数据初始化组0x7ECB只新增1458AE590→82316/LFLFNKJGBEI，不能假定其他共享类型同组初始化。

静态type cell探索脚本analysis/probe_sdk_type_cells.py按固定目标metadata usage解码（非运行验收），已输出：

| usage行 | cell | 类型 |
| --- | --- | --- |
| 698608 | 145886158 | 452/System.Text.StringBuilder |
| 698933 | 145887150 | Dictionary<string,string>闭合实例 |
| 699017 | 1458875C8 | 4354/MiHoYo.SDK.JSONObject |
| 699018 | 1458875D0 | 4346/MiHoYo.SDK.JSONNode |
| 700517 | 14588F3E8 | 4356/MiHoYo.SDK.JSONString |
| 700519 | 14588F408 | 4358/MiHoYo.SDK.JSONBool |
| 710075 | 1458C3958 | 23189/DJKBKNPDKCL（mode装箱类型） |

SDK JSONObject .ctor1597AE4E0、Add1597C35D0可按已命名容器边界研究，但SDK业务调用不能直接强制成功；MiHoYo.SDK JSON与已有SimpleJSON是两套类型。后续1527571F0/15275F990同属14237；153D34030属82316，保持未验。反汇编已留analysis/disasm-15275f080.txt、disasm-15275f235.txt等。

## 弯路与避坑

- 最初配置返回断言用EAX==1失败；实际15120C059只写DIL=1，15120C06A再mov eax,edi，其他位残留。因此报告保留nativeReturnRAX并只断言已验证的AL=1；不要猜成int32错误码或截断指针。
- 两个静态列表缺省时原生会清空。空集合夹具是按目标读写的+10数组/+18长度/+1C版本构建，不模拟业务清空成功。
- config样本全部key为空；不能说已验证加密配置或实际SDK初始化。坏JSON为显式JSON helper拒绝，不是目标原生JSON parser错误实现验证。
- 可选GLOBAL+E0F0缺省时14F8EF7B0真实返回；非空监听器/UI动作未验。
- analysis/probe_dispatch_config.py、probe_dispatch_continuous.py是探索，正式证据由trace_client72_dispatch_config接管；continuous探索的NullReference在151212D8E，不是可用路径。
- 不将n1/n2/n3相加称完整dispatch；SDK/B0缺口真实存在，不跳未知调用，不用版本文字/健康/编译替代实机。

## 交接与文档

README/Project/Harness/CLIENT_72_SESSION已同步。旧HANDOFF2先备份至local/migration72/handoffs/HANDOFF2.before-dispatch-config-c158e0e.md并核对SHA-256一致；HANDOFF.md只追加。研发未暂停，接续不需重新授权。全部本地提交，未推送；D-02的新旧测试号、选人、初始化/场景握手、心跳、退出重登全部仍待实机验收。
