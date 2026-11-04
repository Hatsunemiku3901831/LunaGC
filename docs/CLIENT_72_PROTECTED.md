# 7.2 保护读写入口：离线观测

输入固定为 [CLIENT_72_EXTRACTION](CLIENT_72_EXTRACTION.md) 中的三份SHA-256。工具 `trace_client72_token.py` 使用Unicorn 2.1.4，仅在CPU模拟器的内存中执行指定函数；不启动Windows客户端、不注入进程、不修改客户端文件或安装补丁。API依据 [Unicorn官方教程](https://www.unicorn-engine.org/docs/tutorial.html)；依赖固定在 `tools/requirements-client72-trace.txt`，安装到忽略目录。

## 已可复现：Token请求原始字段

元数据类型NJAALDCMHEP（82491）、受保护WriteTo入口1500C2660，通过15A4DCF40进入保护分派。以前入口后线性反汇编失败；本次跟随真实控制流执行，已完整返回至模拟调用方。

模拟环境明确限定如下：

- PE头及文件支持的节装入对应虚拟地址；BSS/运行全局初始为零，选取基础非IFix路径。
- 消息字段偏移从目标元数据恢复；构造独立合成对象、字符串及4096字节输出缓冲区。
- 只模拟4个已审查的外部helper：15171FE80的ASCII字符串路径、1517662A0的uint32 varint、151766960的uint64 varint、151765E10的单字节写入。没有模拟认证、版本、签名或完整性校验。
- 字符串helper的15171FEB8–FEDD计算字节长度并写长度，15171FF9F以后的ASCII路径逐字节复制；本工具不验证非ASCII路径。其他helper证据见网络研究。
- tag及字段选择/缺省条件由保护函数本身执行；根据各字段独立的输入值反查对象偏移，再关联目标元数据类型。未知helper、错误流对象、未知字符串地址、未映射访问、中断/系统指令及执行预算耗尽均失败，不输出完成映射。

三组实际执行结果：

| 合成输入 | 执行指令数 | 结果 |
| --- | --- | --- |
| 每字段非零、各string/uint值唯一 | 3,594,486 | 完整返回，28字段，183字节 |
| 相同唯一ASCII字符串、接近uint32上界的另一组唯一值 | 3,594,486 | 完整返回，28字段，tag/类型/对象偏移与第一组一致 |
| 零标量、空字符串 | 587,850 | 完整返回，输出为空 |

28个字段为16 string、11 uint32、1 bool；tag为1–15、83、561、623、645、793、884、924、965、1184、1206、1283、1300、1730。原始映射 [token-wire-observations.json](../protocol/cn-7.2/token-wire-observations.json) 和离线 [protected-token.proto](../protocol/cn-7.2/protected-token.proto) 已生成。字段均标为 `emulated-observation`，业务含义仍unknown；**这不是完整业务schema或实机运行证据**。

`check_client72_token_proto.py` 用protoc编译研究Proto，比较三组模拟器输出，另有不由编码器生成的独立字节断言（tag1、623、1184）。全部通过。报告、映射、Proto再次运行逐字节一致；摘要分别为 `token-protected-summary.json`、`token-protoc-check.json`、`token-determinism.json`。完整合成trace在忽略目录，未提交客户端二进制或抓包。

## 继续进行的三个入口

| 入口 | 本批实际观察 | 尚缺 / 下一步 |
| --- | --- | --- |
| TokenRsp.Merge 148B7EA10→15A4DBA53 | 离线执行22,187条指令，到达14A42BD90，RCX/RDX为受控消息/reader地址 | 模型化reader并按受控tag/value观察目标字段存储；尚无完整字段表 |
| LoginRsp.Merge 14B89EC90→15A4DC5A8 | 离线执行20,365条指令到达相同检查函数 | 同上；还需嵌套/列表的真实目标结构 |
| LoginReq.WriteTo 152EEB470→15A4E48B5 | 初始实验在空ByteString对象触发1404C2BE0；修正合成对象为有效字节容器后，执行7,464,789条指令并完整返回，输出52字段/343字节 | 尚有3个嵌套消息设为null，6个bool共享值1需逐字段扰动消歧；未发布这52字段的完整映射 |

14A42BD90实际读取对象+14引用计数；小于2时在14A42C0E5–F5返回false。这个函数不是登录认证检查。后续应执行或严格模型化其真实可变对象分支，不能将未知状态直接当成功。

LoginReq缺失嵌套类型为NBFMIBMFJIO（45258）、HMLAJHBHLOC（21921）、PBBDOCEDNIE（68918）；三个PPGGBLPHADP字段是字节容器，不能误建为嵌套Proto。实验脚本 `analysis/probe_protected.py` 与 `protected-probe-*.json` 属本机探索材料，尚不是完整可发布提取器。正式工具目前只支持TokenReq选定入口。

## 可复现与测试

```powershell
python -m pip install --target local/migration72/python -r tools/requirements-client72-trace.txt
python tools/trace_client72_token.py --client $env:GAME_PATH --output local/migration72/evidence/token-protected-trace.json --mapping-output protocol/cn-7.2/token-wire-observations.json --proto-output protocol/cn-7.2/protected-token.proto
python tools/check_client72_token_proto.py --protoc $env:PROTOC72 --trace local/migration72/evidence/token-protected-trace.json
python -m unittest tools.test_client72_protected tools.test_validate_protocol72 -v
```

本批47项工具/结构/映射测试通过；已有包头/心跳11项独立wire检查通过；Token另有3次模拟与protoc比较、1条独立wire断言。映射普通校验通过，ready实际失败，未修改服务端协议源或部署。本页中的完整返回只指指定合成模型下的离线函数执行，不等于客户端登录或进入世界。
