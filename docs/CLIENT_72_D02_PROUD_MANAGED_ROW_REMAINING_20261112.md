# D02-R3：既存managed Proud行的三列剩余项

任务 R3-PROUD-MANAGED-ROW-REMAINING-TRIAGE。只讨论合法既存标准cache P、M=QWORD[P+40h]非空且为41105时的addProps、paramList、OpenConfig。当前52RPM/298B新范围与ready尚未获得，本包没有扩大或执行现场。

**不是三列全部因A阻塞。现有允许输入足够给出三处中性字段身份、元素/字符串布局、同key条件文件输入及Java消费要求；还不足以批准三业务alias、实际M值或Java完整行。** managed field127898 String可独立准备；两个float列已有完整getter候选listing，唯一来源后继可限定为54724.m454810，不需另起codec树。不为旧prefix数据再造重复binder或Java加载fixture。

## 已证布局与业务名边界

41105继承26072；P+40h由field316249声明为41105。以下是M自身字段，不是NativeClassArray wrapper或packed nativeData：

| 字段 | 布局 | 必要Java列关系 |
| --- | --- | --- |
| 26072.field127896 | M+10h，54724[]；元素4B encodedFloat，field271929 | paramList候选；不是普通float，不能套58209 |
| 26072.field127897 | M+18h，52802[]；unboxed元素enum32419/+0、54724/+4，合计8B | addProps候选 |
| 26072.field127898 | M+20h，System.String | OpenConfig候选，不需要54724 |

52802.field261827声明32419，enum成员保留FIGHT_PROP_*名称；field261828是54724。这些身份不能仅凭形状或相同混淆名升级为业务alias。本次限定核阅未找到三英文字段的准确客户端消费桥，不主张全项目不存在。

System.String固定声明为type293：field1211/m_stringLength在+10h（Int32），field1212/m_firstChar在+14h（Char）。给定合法M/S，可以精确记录field127898的pointer/null、长度、原UTF16及文本；未读、失败或null不改成空串。数组实际采值仍须沿用已验ABI及合法长度/预算门禁，本文不追加reader。

## 已验条件输入：不重新提取payload

直接消费base-prefix最终报告与single-key最终报告，SHA分别为e01108b35842e1c2412e4343dd506b14cbf1715c3de285185154193c298267f6、30b99d804468f9572a54f60b1e3dfc0838d38e55c52e8ae11e285f7fcd1d9a2c；没有重跑工具、原parser或330B提取。

- native base.field295066的count25，25个float输入位样为3e99999a（0.3f）、40c00000（6f）、23个+0。count PC14d5e2838，项读取PC14d5e28e4；[10,110)各raw/span留私有报告。这是setter输入，不是实际54724 encoded或getter结果。
- [112,134)的22B块经已验变换后，ASCII视图为Ayaka_PermanentSkill_1；读取/提交PC14d5e2ba0/14d5e2c8a。原报告stringEncoding=null、nativeStringReturned=false，不能等同已构造managed String。
- native base.field295065的count2；两header在[138,140)、[140,142)，32位sum均10000h、低16位0。两个enum为原direct DWORD0，float setter输入均+0，实际encoded仍null。元素PC14ee1ea46/14ee1ea9a/14ee1ea9d。

native295066/295065到managed127896/127897只作类型/同schema候选，没有证明物理复制。原managed child 149f0c238 e983517e05→14f6f13c0仍未获本包取源/执行许可，不从文件语法跳成实际M字段。

## Java必要消费和单catalog对照

ProudSkillData.onLoad直接使用addProps.length和paramList.length。属性经propType非null且value!=0筛选，再调用FightPropData.onLoad；paramList全部槽位保留，生成从“1”起的paramListMap。Avatar消费属性并加入OpenConfig胚胎；DynamicFloat/AbilityTalentResolver继续消费参数和配置。不能以null、空数组或空ability绕过。

当前只读checkout的resources/BinOutput/Talent/AvatarTalents/ConfigTalent_Ayaka.json精确包含上述候选key；9379B，SHA 0b0e1fbbab1fe4abefca66e3795c243c7516c5c27bec37710203be3dd5dda7f5。条目含UnlockTalentParam及两个ModifyAbility，paramDelta分别引用%1、%2；条件输入0.3f/6f有明确host消费位置。不能裁掉后23槽或将两个原属性元素改成“源数组为空”。

此对照只确认当前host catalog的候选key和参数引用，不证明7.2客户端field127898业务alias、配置同源身份、effect或当前采用。将来若证明float值为0而Java过滤为空，也必须保留原两个元素及读回证据，不能预填空addProps。

## 精确停止边

对既存7674B parser仅作CALL索引，发现以下CALL1405336c0：

| site | 原E8 |
| --- | --- |
| 149f0c450 | e86b7262f6 |
| 149f0cd44 | e8776962f6 |
| 149f0d30b | e8b06362f6 |
| 149f0d741 | e87a5f62f6 |
| 149f0da28 | e8935c62f6 |

停止这些分配返回/构造依赖，没有解释返回或跟后继，不凭site将其归给某个业务列。原冷producer/第二返回/Default归属与B继续冻结；没有新安全拒绝。

删除这些动作及产物后，既存M字段声明、已验文件token/输入位样、Java消费者和单catalog项仍可独立复用；不声称它们是分配后实际对象内容。若M/身份地址只能来自被拒返回或归属结果，停止该输入，不能以managed/cache名义续接。

## 最小后继及准确首缺

**可独立准备field127898 String的输入合同**：外部提供独立合法M，按上述String布局输出field identity、原pointer/长度/UTF16/text，条件对照22字节ASCII候选及catalog精确项。clientAliasApproved和actualRowApproved仍为false。无需NativeCString、allocator、float或managed parser。实际M、API/预算、范围和ready另审，不能追加到当前未获批准的52RPM/298B。本包给布局/合同，不派新采样器。

另两列可先准备raw-array字段/元素身份及条件文件输入比较；完整实际数值仍须认可54724 getter来源及实际存储/同epoch材料。定点复用发现analysis/disasm-14f905140.txt含完整getter listing至1e5错误尾：RCX指向stored DWORD，xor seed145872d30，经同GLOBAL+e970四个inverse quarter后MOVD XMM0返回。公式见私有triage；不从58209名字类推。该文件无原hex/pdata，所示166B仅范围提示，不能自认raw来源已验。准确decl为54724.m454810/14f905140；同文件14f9051fd CALL仅listing，不冒充原E8。

唯一建议的独立Terra来源包是该getter的普通AOT/真实unique pdata/完整原窗验证：cap256B/128条，AOT16B+pdata12B+body最多284B证据；header/exception/五stock哈希另账。先body0诊断，无边界则停，不按RET或邻方法补界。新next-float-getter-source-request.private.json列明固定输入、精确entry/普通m454810、可写文件和停止条件，本包未取新源。它只服务两个必要float列，不取setter、初始化器或其它callee。

listing中的四inverse索引为400h|byte3(y)、500h|byte2(y)、600h|byte1(y)、700h|byte0(y)，y=stored xor seed；组合U32后按IEEE754解释。无A调用被识别在该getter listing中，但它尚非已验raw。同global/seed不代表52RPM/298B已覆盖float数组所需索引，不能自动扩当前scope或声称实际float值。

三业务alias仍需准确客户端field→消费来源；OpenConfig另需目标配置同源身份。这是普通、可限定规划的来源/输入缺口，不把三列泛判A阻塞，也不因M未采就否认静态工作。不为重包旧prefix再排机械任务，不生成Java-ready资源或全unknown121清单。

私有triage.private.json固定16个准确来源，并保存字段声明、25float原span/输入、两元素默认分支、块原hex、catalog精确项和五停止边：24816B，SHA b246d17f58eafd95a7b1883a352033fe70ea3081d306c5926f67b35721b2ab41。实际M/addProps/paramList/OpenConfig均null；resourceReady、D02-R3和D-02不晋级。

没有新PE/global/目标/callee读取、CPU/native/live、实现、旧测试、Git或共享索引修改；Project/Harness/恢复索引交主线维护。
