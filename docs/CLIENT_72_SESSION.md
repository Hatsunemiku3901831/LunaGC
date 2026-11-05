# 7.2 Token 成功分支与会话研究

输入为 extract_client72.HASHES 固定的三份目标文件。本页只记实际执行范围；不是客户端登录或补丁验收。

## A-04d5a 解密后 seed、整数存储与会话密钥

新增 client72_session_runtime、trace_client72_session 和独立 Java 对照入口 Client72SeedReference。研究从 Token Handler 内 **15A4E064F** 开始，明确输入为前段解密得到的 byte[] 与本机保存的 uint64 client seed；停止在 **15A4E0D4B**，即账号状态写入之前。这不是从 Handler 入口贯通的成功样本。

实际执行的目标行为：

1. 检查输入长度至少8；159B54250 读前8字节，15A4E06C4 交换字节序。长度9的样本表明这里不要求恰好8字节。
2. 14CB58170 解码静态14587D740的16字节整数存储，原生循环按字节 XOR；独立表达式为 `int.from_bytes(decrypted[:8], 'big') ^ client_seed`。14CB58BD0再次编码结果，真实解码检查得到相同64位数。
3. MPPHJAKKLFI 的原生 cctor 14B770850 写 N=312/M=156；初始化14B76FEB0、提取14B770520及两个状态更新函数执行。以seed初始化，首个输出初始化第二实例，丢弃第二实例首个输出，再将512个uint64按大端写成4096字节密钥。
4. 结果存入 GLOBAL+19580 的 byte[]，145878FC0置0。此标记的具体业务名暂不命名；不能仅凭置0断言完整网络状态切换已验收。

托管边界只模拟指定类的对象/数组分配、线程本地System.Random对象及Next值、整数存储用16×256置换表。整数编解码与MT64运算均执行目标代码；两套互逆置换表（恒等/197乘41移位）与不同随机值验证输出不依赖夹具表。数组类、长度、元素宽度不匹配即拒绝。

实际验证：**12组4096字节原生结果与仓库既有Java MersenneTwister64逐字节相等**，覆盖零、高位、64位全1、大小端可区分值和尾随字节。另**6组长度0/1/7输入**到达原生异常路径，密钥未安装、标记未切换。91项工具/映射回归通过；研究校验通过，ready仍拒绝。trace与摘要二次生成逐字节一致。

Java对照只编译MersenneTwister64及离线runner到忽略目录，不加载服务端、数据库或已有存档。工具报告固定Java输入源码SHA-256，不改运行代码或服务端协议。

## 尚未闭合的前段

- Handler入口1507BE0E0成功前缀读取响应+B8；147CF3070在普通ByteString存储路径实际返回+18的数组。此前默认null数组的探索失败属于合成夹具限制。
- 14D581600检查运行时列表是否含5714。两个分支目标literal为`[Shell] Old WarmupSwitch`/`[Shell] New WarmupSwitch`；后者进一步调用15A3CE060（0xCA）。已执行至内部读取140583A00，后续未恢复；没有将此调用模拟为成功。
- 响应+98/+88进入Base64、原生密钥处理和摘要/校验区域；尚未通过完整独立密文与错误签名夹具。上面的解密后片段不能验证前段解密或验签。
- 响应+68写会话+98，1507C2211读取同一会话成员传给151213730；后者写请求+D8。该链已由下一节的独立wire与分段原生执行验证；未据此宣称完整Handler执行。
- Login构造保护续段已越过151213AC6；148A22550跳1421C1F40（目标原生ret），后续需合成1458971C0类初始化状态并追踪下一真实调用。未知helper继续失败，不默认返回成功。

## B-02c2 会话token保存和Login参数

trace_client72_token_flow从独立响应wire `32 <length> <ASCII>` 开始，经真实TokenRsp reader将string写至CLDBKDNJHFG+68（tag6）。随后分别执行以下边界，实际存储的指针贯穿这些片段：

| 边界 | 实际观察 |
| --- | --- |
| 15A4E0D74→15A4E0D9A | 15A4E0D93将响应+68存入HOMIEDIPBBJ+98；缺少会话对象到达原生null错误路径 |
| 1507C21EB→151213730 | 从同一会话+98读出token作为RDX；RCX为GLOBAL+A0D0发送器；R8保留管理器+1D8的uint32，具体含义未知 |
| 151213730→1512137F2 | 原生构造前缀将RDX存至KOHBOMDDFCB+D8；stage值6被捕获；分配和stage辅助操作明确模型化 |
| 完整请求writer152EEB470 | 保存的token内容生成独立预期 `0A <length> <ASCII>`，即tag1；空值省略 |

4组链路样本通过：两组非空、重复tag6取最后值、空token；参数覆盖0、1、80000000、FFFFFFFF。另1组缺会话原生拒绝通过。两字段以`token`进入三层表并标注有界来源；请求业务名仍candidate，不能由字段确认推导整包或发送已完成。

校验器增加session-token-observations三份输入hash完整性、固定store/caller/类型/CmdID/字段集合与wire/映射一致性检查，拒绝缺失/重复/额外字段和替换来源。95项工具回归通过。完整RSA/UID处理、调用者timer/reset、首个token存储后的构造、实际网络发送均在此实验之外。

## 复现

```powershell
# GAME_PATH / JAVA_HOME 先按 Harness 核对本机portable配置
python tools/trace_client72_session.py --client $env:GAME_PATH --java-home $env:JAVA_HOME --output local/migration72/evidence/session-seed-trace.json --summary-output protocol/cn-7.2/session-seed-observations.json
python -m unittest tools.test_client72_session tools.test_client72_login_semantics tools.test_client72_metadata_values tools.test_validate_protocol72 -v
python tools/trace_client72_token_flow.py --client $env:GAME_PATH --output local/migration72/evidence/session-token-trace.json --summary-output protocol/cn-7.2/session-token-observations.json
```

共享摘要：protocol/cn-7.2/session-seed-observations.json。完整trace、重复生成报告：evidence/session-seed-trace.json、session-seed-determinism.json；回归：logs/session-all-tests.log。原始探索留analysis/。本批只改离线工具/测试/研究记录，不需要服务端重启；新旧号、选人、入场、重登均未实机验收。

token链共享摘要为session-token-observations.json；evidence/session-token-trace.json、session-token-determinism.json及logs/session-token-tests.log保存本批复现。seed检查点本地提交0f324eb。
