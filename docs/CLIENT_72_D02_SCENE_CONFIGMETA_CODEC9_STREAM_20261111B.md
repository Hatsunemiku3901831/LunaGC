# F-03d3s2 / CONFIGMETA-CODEC9-STREAM（2026-11-11B）

## 中文推进计划

| 项目 | 约定 |
| --- | --- |
| 目标 | 将已验两块transformed compressed输入推进为真实payload；先闭合内部流头、块边界、历史与CRC条件，再交成熟最小外置对照执行规格，并定位原native生命周期的第一准确缺口。 |
| 已有 | prefix-only两fresh及主线第三：212058B/SHA `ba5b8ff2a824679258176fb16accf438f03ed1e0c85b9228a14a00cdc1269887`、72360B/SHA `8a739cdf59ce2102df450a40a518bbe4f3558c4a2a61124d036e58bfad681515`；目标输出262144/72852B；新888B原reader与旧完整wrapper/state/create/reset/core来源；固定上游README、commit、分支和源码。 |
| 准确缺口 | 内部流头的实际分派、每块输入完整消费关系、是否跨块依赖、是否要求尚未实现的CRC；外置CLI本机构建及固定样本实际行为；原native的容量/CPU/内存与所选callee执行闭合。 |
| 方法 | 只读新reader完整原件及两实际前缀，按真实可达分支解释；对照固定上游原始stream parser、quantum reader与完整消费入口，不按外层tag9选算法。先纯字段/区间复现，成熟后交Terra构建外置固定源码的最小driver；原native新source只按准确CALL另立有界包。 |
| 修改范围 | 本页及忽略目录`scene-header-conflict/`；已冻结专题、工具和外置clone只读。主线独占Git、Project/Harness、统一构建部署。 |
| 产物 | 有来源的流头/边界/历史/CRC合同、外置固定源码对照request及准确CLI/输入/输出/守卫/失败停止条件；原native最小新source规格。 |
| 验收 | 区分字段静态推导、实际外置输出与原native执行。真实目标两块必须完整消费、输出精确262144/72852B并原序334996B；双fresh及独立审核/主线第三。外置成功不代自然loader或原native生命周期；未知保持未知。 |
| 停止 | A/B拒绝动作及依赖不触；新安全拒绝原文保留并停。任何未知callee、字段/输出/边界不满足均保留实际失败，不伪造成功或自动扩大执行范围。 |

## 本次实际结论

两个已验transformed compressed输入都符合原reader的同一条format4分支，内部type为6。固定上游源码将这个type标为Kraken；这由实际输入及原reader/上游parser共同确定，外层archive tag9本身不能确定算法。本次仅做来源与实际字段算术，未执行reader、外置解码器或新native状态。

### 原reader覆盖与实际可达分支

`scene-codec9-header-reader-window/fresh2/`及主线第三的准确原body为888B/SHA **`718bf9317f95ea38c022c4eac243f472f49ebf65987614766ebcd4bf1bf5abf4`**，范围`141d265c0..141d26938`，由原core精确CALL绑定。对两个实际前缀，原代码只读取2B并返回input+2，写入的六个U32字段为：

| 字段 | 实际值 | 来源含义 |
| --- | ---: | --- |
| format | 4 | 第一字节低4位为12，版本两位为0，经原reader加4 |
| type | 6 | 第二字节低7位；小于7且小于13，进入已保存普通返回 |
| subtype | 0 | 该分支原写0 |
| uncompressed | 0 | 第一字节bit6 |
| restart | 1 | 第一字节bit7 |
| checksum | 0 | 第二字节bit7 |

实际分支不经过间接跳表或错误helper。准确pdata末尾的40B是原间接跳表消费者所引用的10个i32，全部目标落在已保存代码区域；线性反汇编共271行，其中末12行把这40B数据打印成了伪指令。由这些数据打印出的两个外跳不能当成实际指令或本输入的未闭分支。仍不将其他旧格式/错误路径升级为已执行。

### 每块精确量子边界与CRC

固定[ooz原parser](https://github.com/powzix/ooz/blob/05038060aa68f9187ae9923b2388ca8db40e58d1/kraken.cpp#L465-L505)按同一2B流头识别type6。随后3B大端量子字段给出：

| 块 | 24位量子字段 | 压缩payload字节 | 完整输入关系 | 预期输出 |
| --- | --- | ---: | --- | ---: |
| 0 | `0x033c54` | 212053 | `2+3+212053=212058` | 262144 |
| 1 | `0x011aa2` | 72355 | `2+3+72355=72360` | 72852 |

两块量子flags均为0，实际checksum位为0，没有checksum字段；因此本对照不需要调用上游尚未实现的CRC函数。不能将这个结论推广到CRC1输入。压缩payload均小于输出长度，属于实际压缩分支；每块各为一个外层量子，完整消费不需要尝试偏移或裁掉末尾字节。

### 历史范围

两块各使用fresh decoder：真实restart位均为1；已有完整原wrapper每次zero16B局部state、init(-1,0)、reset(0,expected)并正常cleanup，未在两次调用间保留decoder。不能给块1预喂块0或猜一个外部字典。

fresh只限压缩块之间。块0内的128KiB子块仍可引用本量子内已产生的输出，外置调用必须把整个262144B输出作为一个连续buffer并保留其历史；不能继续切成无历史的小块。本次没有声称所有压缩数据都不需要历史。

## 已交回的最小外置payload规格

冻结私有`scene-header-conflict/codec9-ooz-payload-request.private.md`，**10371B/SHA `ac42fdaeb8edffb3b0196bec46c0c958fba24ccb0dab1f765fd3a5d038452a5a`**。Terra已承接机械driver，主线负责统一MSVC构建运行；此页不声称已经得到payload。

固定[powzix/ooz提交](https://github.com/powzix/ooz/tree/05038060aa68f9187ae9923b2388ca8db40e58d1)的GPL研究源码和exe只留外置研究根/忽略目录，不复制到主repo或运行产品。使用已成功的浅clone及固定下载blob；失败残留目录不可作为成功clone。上游算法不编辑，研究driver直接使用原`Kraken_Decompress`并以fresh `Create/DecodeStep/Destroy`记录实际输入消耗、输出推进及收尾，不调用专有DLL。明确保留原要求的64B输出余量、两侧guard与完整输入检查。

规格要求先用固定公开`xml.kraken`与固定`verify/xml`逐byte验证本机构建，再运行已审余输入/缺1B边界和本目标两个固定输入。两块输出精确262144/72852B、完整消费、原序拼接334996B；任何失败保留实际结果，不自动换语言移植、DLL或compressor重试。后继可对真实输出只读核serialized头/目录，脚本schema首缺另立范围，不能由CAB名认定ConfigMeta实例。

## 纯原件审计复现与范围

私有`scene-header-conflict/audit-codec9-stream-facts.private.py`，**9247B/SHA `0c826596a8699f6d6a63691595e0820fbba3517221432b30c5c8bff3aeb13533`**。它只读16份固定原件/来源：新reader、两个实际压缩输入与前缀报告、完整header、原wrapper/initializer/core、固定上游源码/tree及已交规格。复算字段、原跳表数据、两个量子完整边界及总输出关系；不导入emulator、不执行解码器、不读新PE或客户端范围。

两次纯审计及主线第三均 **7410B/SHA `155de7138adb93b7b17b253a902be2c93a151b19dfd501627c5147f6474d841d`**，逐byte一致。这里不是native测试，算法名称为固定上游源码归属，native reader语义为已审原件的实际输入可达分支解释。主线第三入口：

```powershell
python -X utf8 -B local/migration72/d02-resume-20261111b/scene-header-conflict/audit-codec9-stream-facts.private.py --output local/migration72/d02-resume-20261111b/scene-header-conflict/stream-facts-main-third.private.json
```

## 原native生命周期的准确首缺

原state准备读native pool和容量全局；容量的已验writer先调用未知初始化callee，再调用未知size helper，之后才store。两callee的body/效果和实际执行尚未闭合；不能自行写容量、清callback或扩大旧分配模型。此为技术来源/执行缺口，尚非外部阻塞，不依赖A/B。已定位准确CALL，按主线要求本阶段不申请或提取更多source窗。

外置payload出口可以独立推进。原native生命周期、natural loader、ConfigMeta身份、PathHash、MPLevel及Scene/D02仍未验。本页与纯审计入口冻结交主线；下一次续行以真实payload及其schema关键判断为输入，不在本卡等待或提前扩source。

主线后继已实际完成固定外置解码的限定验收，见[真实payload](CLIENT_72_D02_CODEC9_OOZ_PAYLOAD_20261111B.md)。这不改变本页仅据原件解释target reader、未执行目标native的证据范围。
