# D02-R1 Ornament 非空条件数组原生合同

任务 `F-03d3f4d2-ornament-conditions`，2026-11-07。独立接续[Ornament scalar/string parser](CLIENT_72_AVATAR_ORNAMENT_CONTRACT.md)，冻结旧三件。只新增工具、测试和本文；无客户端/调试器/运行代码/Proto/配置/资源/构建/数据库操作。

**主线已审核的新结果：原生Ornament parser→58937数组wrapper→虚槽4元素parser→非空string数组连续返回。** 7组成功与5组拒绝、9项独立断言通过；两次新进程完整/摘要逐字节相同。候选英文alias、条件业务求值、完整cache-builder、实际资源revision、Java业务来源和g2仍未完成。

## 计划、固定输入和typed模型

目标是解除上卡`14e7e6570`的非空数组停止点。先核三个具体allocation cell、58937虚槽4、82900 enum原始default records，再沿原生循环/字符串变换/store运行独立样本。模型仅复用已审`client72_resource_deep_nested.py`的具体managed分配和严格class一致性，不给未知原生业务默认成功。非零、uint32高位、Unicode、五元素、截断/null/wrong-class分别验证；候选param/type/unlockCond同形并不自动批准alias。

每进程核三客户端SHA-256：EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`；global `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`；startup `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`。前项工具源码固定为`e810efee76f619b1b911c5f0aec77cb3fd654afdfbf34b4a6e4aecb6da8c3f89`。

候选Ornament原文件1630 bytes，SHA-256 `aad42c01df12aaa4956374226e26db11cdbd3d72a5586e4c9bf2e4415ae2287a`，Git blob `4418e8163af74ca1ea06e76386e41bc1aca8cd70`均重新计算核对；来源及revision限制沿用前项。

| 准确cell/调用 | 独立metadata绑定 | 模型范围 |
| --- | --- | --- |
| `1458b1048`，wrapper `14e7e65dd` | 58937引用数组 | 8字节element storage、明确count，header含正确array class |
| `1458b1050`，wrapper `14e7e65f7` | 58937 BPEGMCJEAFG对象 | 32字节对象、正确或刻意错误class；正确/错误class均给实际slot4，类型拒绝另验证 |
| `145886120`，element `14e7e6786` | string数组 | 8字节引用存储，不伪造字符串parser结果 |
| element method489549 / `14e7e66b0` | virtualSlot4 | wrapper真实经class+f0调用，非成功桩 |

`14051b7b0`类型兼容helper仍是已审fixture合同：expected type来自array class+ac的relative registry链，actual仅允许正确/刻意错误类，独立反例必须返回false。没有声称执行IL2CPP完整类型兼容算法。数组/对象分配、UTF8转换及memcpy/memset是明确模型，native wrapper/element/scalar变换和字符串循环均实际执行。五元素是本夹具范围，不是客户端资源上限。

## 原始binary结构

外层 `14e7e6570` 先取u32 count并加`eddfd92d`；非零分配typed array，逐项分配58937并调用slot4，再实际检查类型和数组边界后保存引用。零count写已有共享空数组。

58937只有两个字段：`MENIHLEKBHJ/+10 = string[]`，`IELLPMKLAIO/+18 = enum82900`。元素parser的16位header加`7881`后：

- mask10：读取u32字符串数并加`50e7c0e3`；每串u16长度加`d6b8`，每8字节块xor`9e1ff050437dd6b8`，随后把实际UTF8转换结果写入对应array位置。缺flag/零count写共享空数组。
- mask80：读取u32并xor`b04e4f35`写入+18；缺flag写0。保留raw uint32全部bit，不把未知数值当合法业务范围。

独立literal包含：Ornament条件省略`367c`；明确空array `307cd3262012`；两个缺省元素 `307cd52620127f877f87`。三者分别消费2、6、10字节，分别得到零、零、两个默认元素，不混为同一输入。

## 实际非空、候选形状与拒绝

两个独立非零元素完整解析为`73/["220141","1","风"]`和`ffffffff/["4294967295"]`；外层PBM标量77仍准确，消费58字节。五个明确空string数组保留为五个元素，原生slot4和最终element store各执行五次。

目标enum82900独立default records确认：`NONE=0`；`ITEM_COUNT_GREATER_EQUAL=7201`，field412715/defaultRow70954/data637872。只说明原始selector身份；没有执行物品归属/数量比较、资源条件求值或解锁。

两组**候选形状投影假设**保留全部五项和五字符串位置：

- 101：五项各`["","","","",""]`，fixture省略kind，各原生得到0；外层精确同名PBM/DENC保留，消费155字节。
- 102：第一项显式目标symbol7201与`["220141","1","","",""]`，其余四项各五空串、fixture省略kind；外层PBM/DENC保留，消费167字节。

这里的English `unlockCond/param/type`到目标字段映射尚无独立consumer/exporter桥；因此报告明确`candidateJSONAliasesVerified=false`、`aliasesApproved=[]`。这些原生可读bytes不等于候选真实blob，也不批准JSON缺type意味着目标kind0，更未批准把条件丢掉。精确同名PBM/DENC的已验范围不因这层假设扩大。

五个错误样本真实到达：wrong-class→`1404c2c70`，null object/array→`1404c2be0`，截断首元素或明确非零kind→`1404c2650`。错误helper均在执行前止，不在models中；wrong-class恰有一次严格类型比较失败，反例没有被放宽。

## 验收、失败与复现

产物在`local/migration72/d02-active/avatar/ornament/conditions/`：

| 产物 | SHA-256 / bytes |
| --- | --- |
| first.json / repeat.json | `40c5006267e1b101ccb908218e2a08749f11fc76d84dd9f490aac31be5f53438` / 173891 |
| first-summary.json / repeat-summary.json | `bc0af136b76a4be1c12899860a82d006ab3b04938071acb6ec95907dd0a381d8` / 33310 |
| 工具源 | `271458410bcbcf28858cebbb5530f0641c576e9909d4c286e29116d7ac2310b2` |

`determinism.json`逐字节一致；`tests-first.log`9项通过。首次`probe1.log`保留：数组cell手工换算误写为1458b0fe8，三个typed声明校验在原生执行前拒绝。按真实指令`14e7e65d0+7-8f3558f`改为1458b1048并保持三项校验，未扩大兼容范围、改旧工具或跳过错误。

```powershell
$avatarConditionsClient = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_avatar_live_contract_ornament_conditions.py --client $avatarConditionsClient --output local/migration72/d02-active/avatar/ornament/conditions/first.json --observations local/migration72/d02-active/avatar/ornament/conditions/first-summary.json
python tools/client72_avatar_live_contract_ornament_conditions.py --client $avatarConditionsClient --output local/migration72/d02-active/avatar/ornament/conditions/repeat.json --observations local/migration72/d02-active/avatar/ornament/conditions/repeat-summary.json
python -m unittest tools.test_client72_avatar_live_contract_ornament_conditions -v
```

第三回放选择独立输出，测试用`CLIENT72_ORNAMENT_CONDITIONS_REPORT`指定该raw报告。没有真实报告不skip。仅当前9项，不把原scalar/冷构造已验数量重记。无运行文件变更，无部署/重启要求，本代理没有Git暂存或提交。

下一缺口已从“非空58937 parser未知”缩到条件consumer/exporter桥和完整cache-builder/真实revision。Java仍无已审核tag38 getter；`Avatar.getCostume()`或旧技能map不能接替。未取得这些输入前不发空/默认Ornament、不接生产sender，不以完整raw parser替代完整Avatar handler或D-02新旧号验收。

## 2026-11-08 主线审核

主线审查真实工具、模型/停止边界及原始证明，以独立新进程第三回放和9项定向检查接收本限定子合同。完整raw `conditions-third.json` SHA-256 `40c5006267e1b101ccb908218e2a08749f11fc76d84dd9f490aac31be5f53438`，与独立双跑逐字节一致；证明/日志位于`local/migration72/d02-active/main/`的`pending-contracts-main-proof.json`和`conditions-main-tests.log`。12组连续非空数组/元素/字符串及拒绝路径已验；条件求值/exporter/运行revision/完整handler仍未知。 本项仅离线研究工具/测试/脱敏文档，无运行代码、Proto、配置、补丁或资源变更，因此无需服务重启；不改变ready/required/runtime或D-02客户端验收状态。
