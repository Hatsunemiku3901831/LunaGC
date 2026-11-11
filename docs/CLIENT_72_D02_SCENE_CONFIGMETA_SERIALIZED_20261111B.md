# F-03d3s2 / CONFIGMETA-SERIALIZED（2026-11-11B）

## 中文推进计划

| 项目 | 约定 |
| --- | --- |
| 目标 | 从已验334996B真实payload恢复限定v17目录、脚本PPtr和ConfigMeta对象身份，再据同源字段/消费者定位PathHash.bin及实际reader起点，最终推进MPLevel分组。 |
| 已有 | 外置固定解码器三轮同字节content，SHA `5dbd48d5856e05ee877932dc7282a51ce30e03e4208174fd43c327f3a68f5a5e`；实际metadata193/dataOffset4096/v17/little-endian、engine CString为`2017.4.30f1\n2`、platform19/tree0；旧15catalog、MonoScript/PPtr和metadata来源。 |
| 准确缺口 | 旧目录parser仅覆盖无换行的内置engine常量，旧payload parser未覆盖class114 script hash，均正确停止。新文件的对象/外部脚本引用、serialized字段与metadata归属、实际PathHash输入尚未验。 |
| 方法 | 复用已验v17 grammar和Reader，在新独有parser中精确接纳本实际engine常量；完整EOF/extent/脚本哈希/索引/PPtr交叉验证，不改旧工具。先交目录机械规格，再分析未知字段；serialized offset由实际目录及字段序列给出，runtime offset不作依据。 |
| 修改范围 | 本新专题及忽略目录`scene/`；成熟工具交Terra实现。旧专题/工具/输入只读，Project/Harness/Git/build/live由主线管理。 |
| 产物/验收 | 固定输入/grammar/identity pins、限定目录与对象机械规格、双fresh及主线第三、入口/边界测试；随后准确PathHash/payload/reader规格或可复现的schema首缺。局部通过不升级Scene/D02。 |
| 停止 | A/B拒绝及依赖不触；新安全拒绝保留原文并停止。未知字段/引用保持未知，不猜名字、候选地址或alias，不启新native容量研究。 |

## 本轮实际结果与冻结出口

新限定目录的主线第三已与fresh4七件同字节，7测试及原件audit通过。实际3对象为class115/142/114；同CAB内105B MonoScript与旧目标脚本逐byte同SHA `6425b85708815abe7565930dc1379ae2d6f00bcdfeddb7787a97de645336fc8f`。class114经scriptIndex和实际PPtr绑定到该脚本，身份为ConfigMetaConfig实例，m_Name为MetaConfig。其330612B对象SHA `28334d219f581b798dc48ecd8113714fa55b79a316472e75c0e0d6dea52ebe02`；标准序列化前缀后的实际游标为object+44，未用runtime offset定位字段。

Sol按固定metadata声明序探索182个public PathHash[]，共20615对，准确EOF330612B。以已验consumer的field386052选择ordinal7，实际count1、第二U64/bin为`ce23884493554189`；原链继续接60257.method500134(bin,0,0)→reader42005→70954.method587454。该字段/schema桥及完整walk的机械双fresh和第三尚待后继，不能把探索报告当验收。

该bin在完整既有索引中唯一命中31049741、variant(0,1)、bundle1/entry44935541，唯一location为block24230448/A0/B0/category7。物理源仅做完整hash及原12B signature读取：139134667B/SHA `cb1a68de51f3baeba7afc771284d46410a7b6cb993aa3d085a0c8a7813e14768`，manifest大小/MD5一致。原headerBytes13709；下一包只允许header前13737B，codec、compressed blocks和content尚未知，未解header或139MB payload。

两个冻结机械规格均在本批`scene/`私有目录：目录规格6083B/SHA `156300e9e6e3431cea49c7fd61664149aa181d92b62cc5989deb27867fe3a9f5`；fields→bin→header-only规格8619B/SHA `cf6ecf21cfd8f0d7c13204990d5317997260f104fb2a77418538e8b0bffd8bf1`。主线另供accepted-directory pins，1314B/SHA `d3f282eb36ae60524342f587b87bcfb54fe7accb54ac4b7ac141d295ff8713e7`，供下一实现串接已验目录，旧parser及两原失败保持。

本页和规格冻结交主线；后继header结果回来后再限定payload/entry及reader起点。MPLevel完整分组key/原序名称、natural loader、World实际返回对象/slot114、Scene/D02仍未验。本轮无native执行、运行材料改动或Git提交。
