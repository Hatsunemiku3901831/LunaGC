# R2-PLUGIN-UI-REGISTRATION：准确UI task注册来源

## 有界计划

承接[登录UI分派](CLIENT_72_SDK_CONFIGSYNC_LOGIN_VIEW.md)冻结最终报告25629B / SHA`067ecd76d85de25a84c7318ff5871e3b52f747cf32962a9d4185bbf1616c8417`。目标是核两个选中key（`Login.AccountLogin`、`Login.OverseaAccountLogin`）的task注册来源与原method绑定，先读`PluginUIDispatcher.OnRegisterTask`（class5353 / method39047 / RVA`197f6360..197f63e9`）唯一137B完整pdata窗，验证是否写field25231及参数存法。

固定profile五输入和既有原metadata/注册/literal来源，继承已核Delegate布局；`invoke_impl`不等于业务method。仅凭首窗实际直接caller或已固定metadata usage的准确producer再推进至多两个新完整窗，总预算三窗。不按名称或字符串泛搜SDK，不全PE重扫xref；缺准确边或到真正外部注册输入即停，列最小所缺原件/字段。主线另证的自然registered/once=01不构成UI task或因果证据，不纳入本卡静态producer结论。

仅写本专题和`local/migration72/d02-resume-20261109/r2-plugin-ui-registration/`。验收为独立audit、原bytes/pdata/metadata来源、双fresh逐字节一致、坏SHA负例、source freeze和主线第三命令。不CPU、客户端、REA、Git、构建、DB、patch或观察器；不改旧源/doc/raw/共享索引，不触早期桥并行任务目录或example，不安装或提议共享现场。

## 本卡结果

`OnRegisterTask`确实读取field25231（this+18）所持的字典，把原`strUIName`和原`ShowPluginUITask task`传给准确共享callee，并使用已绑定的`Dictionary<string,ShowPluginUITask>.set_Item`闭合上下文。**本窗不替换this+18的字段引用，不执行task；字典entry实际写入和mode1的内部行为在未读共享callee内，不能把上下文绑定代成具体store已验或自然注册成功。**

两个准确UI key对应的注册value、invoke_impl/业务method仍未找到有源producer。只读一个新完整窗137B；重用一个既验Register窗5706B，没有得到允许继续第二窗的直接caller或metadata producer边。因此停止于准确caller输入缺口，不凭`RegisterTask`、`OnCreateService`等名字试读其它body，也不把缺少内部边解释成“由外部注册”。

## 原窗与参数存法

| 原方法 | 完整pdata范围，末端不含 | 字节 | 原bytes SHA-256 |
| --- | --- | ---: | --- |
| PluginUIDispatcher.OnRegisterTask / class5353 / method39047 | `197f6360..197f63e9` | 137 | `fc97b7b18ea2976b9cc075f50d2a9a9839e7caa84d63602a3cca2c697c1aa37a` |

raw方法flags=`c4`，虚槽5；原code-registration指针为EXE RVA197f6360。参数为`(string strUIName, ShowPluginUITask task)`。首三条保存指令分别为`197f6367: RSI=R8`、`197f636a: RDI=RDX`、`197f636d: RBX=RCX`，没有转换或构造key/task。

正常路径在`197f6379`读`RBX=[RBX+18]`；初始化慢路径也重新读同一字段。原field25231名为`m_dicPluginUIName2ShowTask`，其闭合类型为`Dictionary<string,ShowPluginUITask>`，instanceIndex1056。字段为null进入原异常路径，不会构造另一个字典或替换该引用。

`197f63ad`直接调用EXE RVA`1787d2a0`，调用端参数准确为：

| 位置 | 原输入来源 |
| --- | --- |
| RCX | field25231持有的字典引用 |
| RDX | 原strUIName，来自RDI |
| R8 | 原task，来自RSI |
| R9B | 原数值1 |
| stack+20 | 下述准确MethodSpec cache指针链所得值 |

指针链为：`M=*145974a00; C=*(M+30); H=*(C+88); stack+20=H`（地址/偏移均十六进制）。只把这条原始链写为上下文来源，不给未核native结构字段补名字。共享callee的body没有读取，它与哪一MethodDefinition直接对应也没有绑定；不得仅凭R9B=1命名Insert/Add/覆盖模式或推导失败行为。

本窗没有对task解引用、没有间接call，也没有调用它的Invoke或业务方法。原task可原样交给字典操作；当前没有具体非空value或其invoke_impl/method绑定。`ShowPluginUITask`（5355）→MulticastDelegate（365）→Delegate（347）的布局沿用并重新核实：`invoke_impl +18`、`method +28`、`method_code +40`、`m_target +20`。这些布局不会使当前传入task的值自动变成已知。

## 准确metadata上下文

初始化慢路径传group `1273`（4723）到已有metadata初始化接口。该group仅一项usage row11971：kind6、sourceIndex14411、destination slot4416；注册表method cache base为`14596c000`，故cell为`145974a00`，与原RIP加载地址完全一致。

原MethodSpec14411的12B记录offset为1717000，解码为：

```text
methodDefinition = 10183  (Dictionary`2.set_Item, owner1222)
classInstance    = 1056   (<string, ShowPluginUITask>)
methodArguments  = -1
```

raw `set_Item`参数为泛型`key`和`value`，闭合class参数与field25231相符。其普通method-pointer为0属于本次原注册数据，不据此推定没有泛型实现。调用端共享函数地址与所取MethodSpec上下文分别记录，未把它们强行合并成一个尚未证明的函数身份。

此前尝试按精确共享callee地址和classInstance1056查既有AOT注册表，未取得该callee的准确绑定；这只是该解析方式没有给出结果，不证明它不写字典或没有实现。已保留private尝试，不转而读取其它未授权函数。

## producer边核查与准确停止

原声明中，父类为`IPluginUIService`（5354）。其`OnRegisterTask` method39051/RVA1993d470的raw flags=`1c4`、slot5，参数同为`string, ShowPluginUITask`；这证明对应父虚槽的声明，不证明某个caller已经触发当前override。相邻声明`RegisterTask` method39052/RVA1993d480的raw flags=`84`、slot65535，也有相同两个参数；**本卡未读其body，未认定它转发slot5或就是producer**。

只按这三个准确MethodDefinition（39047、39051、39052）核固定metadata usage表，至末项788696，未找到kind3 method-cache项。该结果不排除直接call、虚调用或其它来源，不能解释为“从未调用”。本卡也没有用相同名称扩成全SDK候选集合。

重用[自然触发卡](CLIENT_72_SDK_CONFIGSYNC_NATURAL_TRIGGER.md)已验`DelegateRegister.Register`原窗`6e36690..6e37cda`（5706B / SHA`de6b089222fdfcb9eec6cb8d90ccdc82166bc10690423a39d603950944073b1e`），重新核pdata、原bytes和全部指令。在此准确旧窗没有到上述三个方法地址的直接call/jump。该有限负结论不覆盖整个EXE，也不排除该窗中未绑定的间接注册。

另仅查39052声明地址的pdata元数据，没有exact entry，也没有包含它的runtime function：前一项为`1993d2c0..1993d450`，后一项为`1993d4a0..1993d6a4`。不能把这个pdata空隙自动当成RegisterTask的完整extent，更不能据同名方法读取未经绑定的第二窗。没有读这个空隙的指令或其它新body。

本窗的key和task完全来自caller，没有生产`Login.AccountLogin`或`Login.OverseaAccountLogin`的literal/委托；唯一init group也不含kind5 literal。两key是[前卡](CLIENT_72_SDK_CONFIGSYNC_LOGIN_VIEW.md)准确选择出的**消费侧selector**，当前不能把消费侧literal usage当成注册value生产来源。现有自然registered/once=01与本链的UI task不构成来源或因果关系，audit没有使用这些scalar。

## 最小解除输入与后继合同

当前本机已有profile固定EXE、global/startup metadata及其structures/signatures缓存，缺的不是账号凭据、URL或另一次客户端运行。**首缺是能将任一选中UI key与一个ShowPluginUITask值配对的准确caller/注册记录**，记录须含其原method、invoke_impl绑定及所属type/字段来源；如来自已存在源码或插件注册材料，还须有与当前固定构建相符的来源/哈希。当前没有证明producer来自外部，不能要求一个未经证实存在的外部文件来填补。

若继续内部静态工作，最窄下一卡可先处理上述**已知声明但未读**的`IPluginUIService.RegisterTask39052/RVA1993d480`：先给叶子body的独立边界依据，再核是否实际经slot5把原key/task转发；不能将pdata gap或方法名本身充作边界/边。若转发成立，仍须一个准确caller把两个key之一及其task交给它，才读对应producer；若不成立或没有准确caller，即按原缺口停止。这是待证明的接口合同，不是当前已存在的调用边。

如需把首窗的具体字典entry store也作为独立验收项，则需要对**已知直接callee RVA1787d2a0**取得准确extent并有界核原实现/参数；当前只允许余窗沿caller或metadata producer，故未用它替代producer研究。两项都无需新的hook、默认计数器、自然现场或真实认证。

仍未建立所选UI task到ConfigSync、SDK native action或已有curl consumer的边，不提议复用transport采集器，也不把当前静态停止点代成R2/D-02通过。

## 复现、测试与冻结

私有目录为`local/migration72/d02-resume-20261109/r2-plugin-ui-registration/`。audit先核profile SHA`a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d`并逐个重算固定五输入；重核前卡最终25629B / SHA`067ecd76d85de25a84c7318ff5871e3b52f747cf32962a9d4185bbf1616c8417`和自然触发旧报告8744B / SHA`f99de6aa2b80367507cc4e17d7bf6b1fdfec44851c436a3c52f96d4e31ea7eb9`。

原structures SHA`fd82e0a55baa69301e87c422465e3797b0f1279778e6b82d938c28446f1b56c5`、原signatures SHA`28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`；各原方法/参数/literal/AOT/PE工具及capstone DLL source pin写入audit并实际重核。

`audit_registration.private.py`为13807B / SHA`860151b22962060d55371337a3b5dffb1e2e2bd1c7a81b20c39fd119d454058a`。`final1.private.json`、`final2.private.json`为两个fresh解释器独立输出，均11229B / SHA`c77c7163937ba99595a59888029d5dd91d1649ac754d0e109331f73578a1b14c`，38项断言通过且逐字节一致。错误expected窗SHA在`exact-new-pdata-and-bytes`处拒绝，不生成成功报告，不修改输入。原bytes/全部指令、精确MethodSpec/usage记录、未读声明的pdata结果、测试和source freeze均留private。

主线第三命令（GAME_PATH指向当前固定原件，输出必须fresh）：

```powershell
python .\local\migration72\d02-resume-20261109\r2-plugin-ui-registration\audit_registration.private.py $env:GAME_PATH .\local\migration72\d02-resume-20261109\r2-plugin-ui-registration\main-third.private.json
```

待主线第三核原PE137B和重用旧窗、MethodSpec/class闭合参数、有限负结论以及报告/source SHA。当前没有主线第三、客户端或部署验收；未执行Git或修改旧doc/raw、Project/Harness、运行代码、patch/观察器或并行早期桥文件。

## 主线入口子项验收

主线实际第三运行冻结audit，38项静态断言退出0；`main-third.private.json`为11,229B/SHA `c77c7163937ba99595a59888029d5dd91d1649ac754d0e109331f73578a1b14c`，与正式双fresh逐字节相同。另6项独立审核核五固定输入、source/pins、原137B PE extent/字节、报告/断言/闭合上下文及全部producer/运行未验状态，见`main-review.private.json`。

只验收本入口的参数与泛型上下文子项，两个UI key到实际注册task的父目标仍未闭；无source就不消耗剩余窗猜producer。准确后继保留上节内部leaf/slot及实际caller合同，不把未知归于外部文件。没有CPU、客户端或运行部署成果，不据本页晋级自然SDK/route或D-02。
