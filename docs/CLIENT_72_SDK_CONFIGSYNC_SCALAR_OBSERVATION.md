# R2-CONFIGSYNC-SCALAR-OBSERVE：原版进程的两个已证条件位

## 执行前计划

先复用[自然触发审计](CLIENT_72_SDK_CONFIGSYNC_NATURAL_TRIGGER.md)的六窗、主线第三与[callback先后](CLIENT_72_SDK_CONFIGSYNC_ACCOUNT_CALLBACK.md)。上次独立DLL已恢复stock且目标事件未命中；自然配置分支的实际注册位和once位尚未采到。它们可以在原版进程中独立只读，不需要修改DLL、再加hook或输入账号。本项仅问：同一轮R1限量输入采集前后，这两个固定scalar是什么。

固定来源为原EXE、五profile输入和触发审计第三报告（8744B，SHA `f99de6aa2b80367507cc4e17d7bf6b1fdfec44851c436a3c52f96d4e31ea7eb9`）。已证注册位为本轮module base `B+586dbb0`，once为`B+586ba00`；不是SDK地址、历史VA或配置堆指针。分别在R1新collector之前、之后各读1B，总4请求4B，记录准确raw/错误/时刻和0/1/非bool分类。绝不追其后继对象或配置正文，不读取密钥、URL、账号、token或调用远端函数。

允许修改本页、新`tools/sample_client72_avatar_runtime_inputs.py`及其自有测试、本卡与R1新适配器的忽略目录。原collector/adapter/observer保持冻结；新adapter复用既有process生命周期检查并接新纯collector，同一read-only handle采集。进程创建时间、image、module前后身份、90秒完整原版monitor、固定五输入/配置与源hash须通过；不调用OpenProcess写权限，不调试、不写目标、不安装补丁。

与R1的总上限分别列账：R1最多5188读89960B、max208；本项4读4B，合计最多5192读89964B。R1末尾复读只证明其输入span，不把两个scalar的不同读取时刻叫原子快照。4读有错误或短读保留原件；0/1以外按原始值分类为非bool，不能放行成false/true。两个读出相同也不证明没有writer、未发生过同步、同步已成功或曾经过SDK export；尤其末尾once=1不能反推过去事件。R1仍有自己独立的状态与未知边界。

## 验收和停止条件

先完成新adapter实际源审查及自有生命周期/读取负例，核新纯collector来源与预算，再准备只启动固定原版、90秒后一次采集的脚本。必须取得这一准备完成后的新user-ready；旧ready已经消费，不在本计划阶段启动客户端。不输入账号，不安排尚无源码绑定的按钮操作。采集后请用户正常退出，核当前五文件、保护文件、配置和进程，原始材料全部留忽略目录。

本项不是重跑旧DLL实验：新增问题是实际两个条件位，R1同时补当前准确根/owner首缺。若这些事实仍不足区分原分支，记录明确未知，按已知fallback/transport源码继续，不重复相同启动或借官方认证逼事件。R2/完整SDK/本服连接及D-02不由此晋级。

## 实际验证与结果

新adapter的9项自有生命周期/scalar检查通过；实际source gate核五profile、旧12pin、新30pin及原六窗字节，未打开目标。helper源码/AST和来源pin经过审核，CheckOnly核当前五stock/13保护、无相关进程端口/alias后，取得新的user-ready，完成一次原版90秒/44监测与有界采集。使用与R1相同只读handle，在其collector前后各读注册/once两个1B，**四次raw均为01**；错误0，前后相同。

本项恰4读4B；R1独立231读5869B，总235读5873B。报告/来源见[R1实际输入](CLIENT_72_AVATAR_RUNTIME_INPUTS.md#新ready原版实测)，raw SHA `04cbe1ffd685244d4599a384186297fa4fdf19e50c2d7c6864f6b9cb4a3b48ac`。主线14项独立审核包含两个准确root/四raw/相应时刻/总账，未追加目标读取。用户反馈正常退出，实际五stock/13保护/进程端口/alias核验通过；没有安装DLL、输入账号或采配置堆/正文。

这关闭本卡四个实际scalar事实，R2仍未闭。该进程与上次SDK hook未命中的进程不同，不能跨epoch归因；once=1也不证明export/dispatcher/writer调用成功或曾返回。继续审查早期准确桥入口及已有UI注册来源，避免用这些byte代替自然配置内容或重跑相同实验。
