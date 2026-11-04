# 7.2 静态元数据恢复记录

2026-11-05，目标由 `config.ini` 声明为 7.2.0 / channel=1 / sub_channel=1。以下地址、参数均从本次目标文件重新定位；不复用 7.1 profile。可执行工具为 `tools/extract_client72.py`，只接受固定哈希，不支持绕过或旧版本模式。历史 extractor 保留为研究资料，不构成双版本支持。

## 输入与安装一致性

| 文件（客户端相对路径） | SHA-256 |
| --- | --- |
| YuanShen.exe | 8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5 |
| YuanShen_Data/Managed/Metadata/global-metadata.dat | 45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a |
| YuanShen_Data/Managed/Metadata/startup-metadata.dat | b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105 |
| YuanShen_Data/Plugins/AccountPlatNative.dll | adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25 |
| YuanShen_Data/Plugins/Astrolabe.dll | 87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442 |

`verify_client_manifest.py` 实际完整校验：pkg_version 2,794、beyond_pkg_version 4,858、Audio_Chinese_pkg_version 178 条，全部大小/MD5 一致（合计 7,830）。MD5 采用清单中的十六进制数值，补足被省略的前导零。根目录仍有 zip_tmp，不能仅凭残留文件认定缺件。该结果证明与所提供本地清单一致，不认证清单来源，也不证明运行/热更新资源完整。

原始报告：`local/migration72/evidence/client-audit.json`、`manifest-full.json`；清单 SHA-256 在报告内。没有修复、解压或改写目标客户端。

## 定位与交叉验证

工具独立于 Capstone/pefile；研究时使用的 Capstone 5.0.9 和 pefile 2024.8.26 仅位于忽略目录 `local/migration72/python/`。原始反汇编在 `local/migration72/analysis/`，可按本节 VA 与输入哈希复现。

| 证据 | 目标 VA / 观察 |
| --- | --- |
| PE 内唯一 MHY 头 | VA 0x142806A80，文件偏移 0x2805080 |
| 静态注册 | 0x1402F8450–0x1402F8496：CodeRegistration=0x1422F98E0、MetadataRegistration=0x1428A53C8、头指针分别写入全局；没有借用旧 VA |
| 文件加载 | 0x14053EFAE/0x14053EFBD 引用 global/startup 文件名；0x14053EFD9 对 global 数据增加 0x210，startup 保留原基址 |
| 类型查找 | 0x140542DC6 引用明文 RuntimeType，并在 0x140542DCD 调用 0x14051F560；其类型循环读取 70 字节记录与两种名称索引 |
| 名称解码 | 0x140518F30；实际解码分支 0x140519D86–0x140519FB5，长度为 index>>24，偏移为 index&0xFFFFFF；8 字节块异或并步进 |
| 字段建立 | 0x14051C6A8–0x14051C8D2：字段数量、起始索引、8 字节记录、字段类型引用、名称解码；另有 0x1405268DB–0x140526926 独立名称读取路径 |
| 方法建立 | 0x14051E455–0x14051E667：方法起点、26 字节记录、注册表 +0x70 基础指针、参数数量；0x14051E89E–0x14051E8AC 解码方法名称 |
| 方法数量 | 0x15A4D7414–0x15A4D742D：类型 +0x3A 读取 word 后加 0xBF1A，写入运行类 +0xC2；与全表连续跨度总数交叉吻合 |

可复现的目标参数（u32/u64 运算按对应位宽截断）：

- 类型表：`0x210 + u32(header[0x64] + 0xA7932555)`；字节数 `header[0xBC] XOR 0x1E6FC854`；stride=70。
- 字符串基址：`0x210 + (header[0x70] XOR 0x18202CCC)`；名称索引 `type[0x18]+0xA285A06D`，命名空间索引 `type[0x14] XOR 0x149E96F1`。
- 名称块密钥：`((offset*0x3EEED96861341169 XOR 0x30F39D3F643E5C2D)*0xDD6ACDB15078B176+0x34847BCE8197A491)`；每块增加 `0x11E1A912269F936D`。
- 字段表：`0x210 + (header[0x194] XOR 0x536B48B9)`，stride=8；每类型 start=`type[8] XOR 0x72239B10`，count=`u16(type[0x38]+0x3664)`；字段 key/name/type 算法见工具（均对应上表指令）。
- 方法表：`0x210 + (header[0xB0] XOR 0x51324A1F)`；start=`type[0xC] XOR 0x5ABB91BD`，count=`u16(type[0x3A]+0xBF1A)`；name=`u32(method[8]-key+0xDE01C441)`。
- 方法 key=`u64(((index*0x1639) XOR 0x5E01C03B)*0x106B250C17F34CCB+0x069FFDCBE218FAF4)`；参数数量=`u8((method[0x18] XOR 0xF9)-key)`。
- 方法区长度 `u32(header[0x1C]+0x85490F40)=19,266,548`，包含 `741,021*26` 字节及 2 字节对齐。工具检查类型计数与 4 字节对齐长度一致，不误判多出一条记录。
- 类型引用：MetadataRegistration +0 指针，数量为 `u32(reg[0x8C]+0xCE67355C)`，每项16字节；字段 class/value-type 的定义索引全部校验。

## 实际输出与边界

完整运行已恢复 **89,748 类型、445,462 字段、741,021 方法、690,935 类型引用槽**。所有名称通过 UTF-8/控制字符检查，成员跨度连续；class/value-type 引用在范围内，非零注册方法地址均在文件支持的可执行节。类型锚点包括 System.RuntimeType（284）、System.Object（371）、UnityEngine.Object（2374）、MoleMole.MonoCrucibleEndPage（51754）。

输出：`local/migration72/evidence/client72-metadata.json`，摘要 `extraction-summary.json`。此数据不提交。

基础提取对零参方法仅统计严格 `mov ax, imm16; ret` 候选：AMPEGOHOBJM 4,012 个；该名称共有 4,013 个方法，另一个为 `xor eax,eax; ret`。其他命中包括目录分隔符 getter，证明“返回 ushort 常量”不能自动当作 CmdID。基础工具仍输出 `literalU16Candidate`；后续 `extract_client72_commands.py` 结合父类/虚槽和发送封包证据确认静态网络用途，见 [网络研究](CLIENT_72_NETWORK.md)。业务名称和wire分层继续验证。

泛型、父类和静态布局已按下节恢复；运行时方法替换、闭合泛型的实际布局与 ABI 尚未验证。没有更新服务端 PacketOpcodes/Proto/版本准入。客户端登录和入场未执行。

测试包含取自固定目标元数据的三个小名称密文向量（无编码器自编自解）、哈希不匹配、名称和 PE 边界、不可执行地址、字面常量严格形状及方法表对齐回归。它们是提取器测试，不是游戏 wire 兼容测试。

## 复现

```powershell
python -m unittest tools.test_verify_client_manifest tools.test_extract_client72 -v
python tools/verify_client_manifest.py --client $env:GAME_PATH --output local/migration72/evidence/manifest-full.json
python tools/extract_client72.py --client $env:GAME_PATH --members --output local/migration72/evidence/client72-metadata.json
python tools/extract_client72.py --client $env:GAME_PATH --structures --output local/migration72/evidence/client72-structures.json
python -m unittest tools.test_extract_client72 tools.test_client72_structures -v
```

公开来源检索未获得可与当前目标关联的 7.2 协议/资源证据。核查线索包括 [Il2CppDumper-Genshin](https://github.com/R0xdeadc0de/Il2CppDumper-Genshin)、[ProtoDumper](https://github.com/66hh/ProtoDumper) 和 [cmdids-extraction](https://github.com/umaru-233/cmdids-extraction)，没有把工具的通用说明当作本目标已经支持的证据。本次恢复以本机目标反汇编和结构校验为依据。

## A-04b 泛型、父类和静态字段布局

以下均绑定本页三份输入哈希。完整输出 `client72-structures.json`，摘要 `structures-summary.json`，反汇编留在 `local/migration72/analysis/`。

| 结构 | 7.2 解码与独立指令证据 |
| --- | --- |
| 泛型实例 | 0x14053F377–0x14053F476：startup 表偏移 `u32(header[D0]+E1492096)`，长度 `u32(header[44]+E42E197F)`，每8字节为 **instance、definition**；startup不加0x210。实例从 MetadataRegistration+70 的16字节 argc/argv 项取参，argv 指向注册类型引用。 |
| 泛型/数组类型 | 0x140517680 的类型分派：kind21是泛型索引，kind29的data为元素引用指针，kind20的data指向元素指针/rank描述。VAR/MVAR保留参数索引，多维数组bounds仍未知。 |
| 父类 | 0x15A4D746E–0x15A4D74C1：`u32(type[0]+A0A35767)`为父类型引用，FFFFFFFF表示无父类；type[10]是declaring type，不能当父类。System.Enum→ValueType→Object及容器/消息基类交叉核查。 |
| 字段偏移 | 0x14051C6A8–0x14051C8D2：lookup=`210+u32(header[168]+B01C236D)`；layout=`210+u32(header[14C]+DE642132)`；offsets=`210+u32(header[154]+936A8A40)`。lookup按类型索引取有符号项，layout每12字节的+8给出首偏移索引。 |

全量恢复411,433条泛型记录；所有实参与定义索引均校验，按实际字段/父类遍历解析92,612条类型引用。父链接88,083条，其中泛型父类1,571条；检查全图无环。76,011个含字段类型有静态布局记录：331,713个instance字段、1,264个static字段、75,382个literal字段、37,103个runtime待定字段。

上述字段数相加为445,462。literal不分配对象存储；泛型定义中的零偏移及高位运行时标记不输出为有效instance offset。无字段类型不读取无意义layout项。原始size/flags字保留供后续研究，**不把它们宣称为完整对象大小或闭合泛型ABI**。结构测试6项加既有提取测试7项，共13项通过；包含嵌套泛型、7.2表顺序、越界/错位引用、递归/父类环、布局未知分类与越界失败。
