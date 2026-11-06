# 7.2玩家初始化通知的用途与有限发送合同（F-03d1b）

本项从目标7193→79263/BIMKHOFCKHB的实际分发和消费链，确认其用途为登录阶段的玩家数据初始化，并采用`PlayerDataNotify`作为稳定业务别名。这是**原生用途证据上的明确推断**，不声称恢复客户端原始英文拼写。不是依据Asta同名或单独字符串：真实分发进入typed handler，重置并应用玩家属性、同步时钟、保存昵称，再把同一模型值交给`login_will_enter_game`调用边界。纯改名或增量属性更新无法解释这一组合；`PlayerInfo`等另一种拼写只属同用途命名差异。

消息用途、五个字段绑定分别记录，**完整schema语义、全部玩家属性枚举、初始化顺序/P0及D-02仍未完成**。F-03d1b1本身只交付研究证据；后续F-03d1c的有限生产接入记录见本文末节。

## 固定输入与证据

工具`tools/client72_player_data_contract.py`每次重新校验三输入后提取metadata/签名/枚举，不读取Asta实现：

| 输入（相对GAME_PATH） | SHA-256 |
| --- | --- |
| YuanShen.exe | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| YuanShen_Data/Managed/Metadata/global-metadata.dat | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| YuanShen_Data/Managed/Metadata/startup-metadata.dat | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |

已验raw输入`player-data-wire-observations.json`固定`455a353974185c885622759247a19b94c905c037545232b29e3cede189bb8cf6`，复用其2类9字段及非空map/oneof，不重复扫描或改raw结果。新脱敏摘要为`protocol/cn-7.2/player-data-contract-observations.json`，原始trace/失败日志/本机路径只留忽略`local/migration72/`。摘要分别保留type/typed消费者、选定enum默认值来源、SDK literal usage行/目的cell、原生调用/模型名单和代码/证据哈希。

## 已闭合的用途与字段

| 目标字段 | 限定业务别名 | 独立来源与实际执行 |
| --- | --- | --- |
| 79263 tag8 / FPOLBCIBIOB / +20 | nickname | reader→1507BC0E0写58013模型+70→实际1527670B0前缀把原指针交给`role_name`；另一实际更新入口1507CEC80写同槽并发目标enum75464.RefreshNickname=842 |
| 79263 tag6 / IKIFIBAEDGD / +18 | prop_map | 实际152F7F670枚举已解码子对象→实际152F7ED50值换算/编码字典存储/解码→58013虚槽20回调；PROP_PLAYER_LEVEL=10013写模型+11c，再输出SDK `role_level` |
| 78350 tag1 / NOEEBKJAFAG / +24 | property_type | 通用属性应用读取子tag1作为真实setter的uint32属性ID；外map key=7而子ID=10013仍到玩家等级。独立目标enum62096给出名称和值 |
| 78350 tag2 / boxed int64、判别2 | int64_value | 通用属性应用只在判别2时原生解箱int64，随后实际转换、编码、存储、解码及模型回调；非零/负值/>2^53/覆盖验证 |
| 79263 tag13 / KFICMDLCEHE / +30 | server_time，毫秒 | handler把帧接收后的有界延迟加到该值，实际DateTime.Add毫秒→时钟偏移→epoch秒getter；1970-01-01来源由.cctor片段及DateToTicks实际执行确认 |

`property_type`和`int64_value`是用途别名；不宣称恢复混淆字段原名。字段5/7只有raw存储验证：分别写模型58013+178/+15c；空输入会覆盖先前非零值，但这不能给出`region_id`或`is_first_login_today`名称。子tag3仍为raw float分支，子tag4仍为独立raw int64，不批准旧`val`含义。

通用属性通路使用**子tag1**选择属性；这不表示外map key全无作用：同一个handler另有按外key查找的特殊分支，本卡选用7/8/9等独立外key来隔离并验证通用路径。未执行这些特殊属性的完整消费，不把一条等级样本推广到全部属性。

## 实际链及模型边界

新链从真实`1507CC240`分发入口接收Cmd7193和原reader对象，原生调用`1507BC0E0(manager, type79263, type22292-frame)`；第三参数是帧对象，**不是PacketHead**。其+28为接收时间字段；本项声明该值为输入，不替换延迟计算函数。

属性路径执行`152F7F670`、`152F7ED50`、`14B1046F0`、`14A1B7EC0`、`14A1B8BF0`及`152178DB0`，实际模型回调读取并存储值。map枚举器读取实际raw节点；managed集合查找/插入、pool、16×256可逆表及Random是明确夹具。两种表排列下实际编码/解码一致，不声称恢复真实启动随机表或泛型ABI。

SDK路径执行至`1471D59A0`入口，实际参数的事件名为`login_will_enter_game`；JSON节点/集合/序列化、数字格式化、SDK环境回调和事件接收边界为模型。未调用真实SDK事件、未执行其异步callback或完整Handler返回。callback元数据绑定独立核对usage740031→method105470→1527694D0，而非填入猜测函数。

时间路径执行`14E697940`、`14E699670`、具名`DateTime.Add`及`14E65CCD0`；DateTime月份数组与UTC本地epoch/当前时钟存储是显式library环境。实际执行.cctor的1970/1/1调用和`DateToTicks`，未执行真实时区初始化/ToLocalTime全路径。SDK还把同一调整时间放入名为`role_create_time`的键；这**不批准**“角色创建时间”字段，`server_time`名称来自独立的时钟同步用途。

## 实际边界结果

- 通用属性枚举用外key7、子ID10013，值42到玩家等级与`role_level="42"`；独立更新入口同模型昵称槽发RefreshNickname。
- 两个不同外key、相同子ID分别42/99，实际属性字典覆盖为99；不是按外key分成两个属性。
- 子float分支2.5、或只有独立tag4=987时，通用int64通路使用0；tag2=43和tag4=987并存时使用43。不能把tag4当tag2兜底。
- wire int64=9007199254740993完整到setter；转double后为9007199254740992。负值-1也真实消费；这些是边界观察，不建议向业务等级发送异常值。
- 独立目标enum62096仅本卡消费`PROP_PLAYER_LEVEL=10013`、`PROP_CUR_CLIMATE_METER=10035`。10035的setter除数来自目标`142240D30` double常量**100.0**；123456789得到1234567.89，再经原生编码/解码。服务器旧PlayerProperty的其它ID和值未批准。
- 接收延迟0..100000毫秒（含100000）保留；100001或未来接收时间产生的无符号差值归0。u64加法保留目标行为：UINT64_MAX+1→0；9007199254740993毫秒则原生进入DateTime范围错误159B975C8，尚未写昵称/属性或到SDK事件。
- 空字符串/空消息、已存非零值覆盖、正常时间与64位边界均覆盖。14个样本到命名事件边界，1个原生日期范围错误；另有独立昵称更新完整返回及每组epoch片段。

首次缩放预期误写10000，由独立断言拒绝；读取目标常量及原生结果后纠正为100，失败日志保留。另三次探索分别暴露未供给的DateTime月份表、气候storage class及SDK callback MethodInfo，均按明确storage模型补齐；没有替换property setter、模型回调、clock或SDK业务路径。停止点先终止CPU导致后注册观察hook未见event参数，后改为在停止后读取保留寄存器；并未改变native路径。

## 复现与后续

```powershell
python tools/client72_player_data_contract.py --client $env:GAME_PATH --output local/migration72/evidence/player-contract-native.json --summary-output local/migration72/evidence/player-contract-summary.json
python tools/client72_player_data_contract.py --client $env:GAME_PATH --output local/migration72/evidence/player-contract-native-repeat.json --summary-output local/migration72/evidence/player-contract-summary-repeat.json
$env:PYTHONPATH = 'tools'
python -m unittest tools.test_client72_player_data_contract tools.test_client72_player_data tools.test_client72_graph tools.test_client72_session tools.test_client72_login_semantics tools.test_client72_reader tools.test_extract_client_literals -v
```

2026-11-06实际验收：15样本完整trace与摘要两轮逐字节相同；摘要SHA-256为`e239ee03cc645d61dfa87ad87db827eb268273f049c496f3730a213153c29689`。新增7项及6个关联模块共59项通过，失败/错误/跳过均0；核对见本机`local/migration72/evidence/player-contract-determinism.json`。新测试先红测（缺工具导入失败），再以真实native产物做scope、独立值、未知不晋级和反模型守卫。仅离线研究、测试、文档和摘要改变，无需服务重启；没有操作客户端、调试器、服务或数据库。

下一可执行项：主线先审核用途及五字段进入三层模型，随后按独立子卡接生产最小合同；实际builder需明确使用已验属性ID与子tag1/tag2，不照搬旧全部enum。继续tag5/tag7需从模型+178/+15c的独立consumer或具名输入回证；当前已查58013、56238及SDK owner14237相关方法，未找到可命名闭环。完整特殊属性、后续SDK/Handler与实际初始化序列另验，不因本卡用途已明而整体晋级。

## F-03d1c有限生产接入

三层仅接管`PlayerDataNotify`/7193与嵌入用途别名`PropValue`：2类9个raw字段、五个已验业务绑定；两条schema均保持candidate，四个未知业务字段保留unresolved。生成器`tools/generate_player_data72_sources.py`每次核验实际三输入及完整raw字段/oneof/map codec、限定消费合同和三层模型，拒绝未知命名、合同漂移、正包号碰撞。没有把原始英文拼写恢复或完整初始化晋级。

生产root改用tag6 map、tag8 nickname、tag13 server_time；tag5/7用原名。嵌入值使用独立物理适配器`Client72PlayerPropValue`，子tag1/2使用已验别名，tag2/3保留同一oneof，tag4独立。共享旧`PropValue.proto`被其他未迁移消息引用，本卡不修改它；逻辑用途别名不表示该旧文件已全部批准。所有Java由Gradle/protoc生成，没有手改生成类。普通标量使用proto3隐式存在性；本卡未声称所有对象级mergeFrom与目标运行时等价。

实际sender只发送nickname、当前毫秒时钟及`PROP_PLAYER_LEVEL=10013`；外map key和子property_type均10013，int64_value即真实`Player.getLevel()`。不发送全部旧PlayerProperty枚举、未知region/first-login或子tag4；服务器属性/账号/经济/保存数据保持原业务行为。独立原生先验证canonical key=childId、未知外5/7省略后的等级0/1/60，再从JUnit真实producer输出提取三包交给实际目标reader/dispatch/consumer：三包分别得到SDK role_level=0/1/60、同一Tester昵称、实际时钟加50ms及login_will_enter_game边界。沿用本专题显式存储/SDK模型，不能称实机收包。

改前有效Java基线11项10失败（旧号3272及旧字段）；首次夹具调用setNickname曾触发未初始化数据库，这次夹具误用另行保留，已改为专用昵称getter而不改变生产或旧断言。改后11项全过，包括int64负数/>2^53、显式oneof零、float切换/独立tag4、重复map替换、UTF8字符串、截断/错wire。新生成器7项及映射/上游审核关联47项全部通过。扩大45套778 Java时，初轮40项因隔离工作目录缺相对资源/src而NoSuchFileException；保留全部失败，补齐只读输入junction后45套778项全部通过，失败/错误/跳过均0（44秒），不删改断言。独立Mongo关闭前仅admin/config/local并正常退出，原27017/运行数据未改。完整研究回归564项通过（约61秒），六个生成检查均无漂移；这些分别为独立执行，不相加为一次测试。

统一人工review旧守卫先拒绝，复核后1267→1268源码/Proto、79→84证据，只有PlayerData sender/root及新nested源变化，已有其它源码与77既有证据hash不变。新增5份已验摘要，mappings与Asta审计2份更新；151根/矩阵、1121描述符文件、1258类型/4810字段，两次recover一致。未知physical文件矩阵行保持unknown；开放边界、P0闭包和runtime不晋级。原始证据位于`local/migration72/evidence/player-data-*`及同前缀logs/results。

上游固定参考仍为a49f51aa6f2787ffbc25cee46801b70dee860d23，原21目标行/源码tree不变，新增两行9字段。对照248字段中156同tag同外形、92缺tag、0外形冲突；标记的5个已验名称差异包括既有两MD5及本卡nickname/nick_name、property_type/type、int64_value/ival三种用途别名差异，不把后者称新wire矛盾。审计工具原先把具名map值`PropValue`和种类`message`比较，已用独立合成用例先红后修正，只比较外层种类，不宣称递归内容相同。
