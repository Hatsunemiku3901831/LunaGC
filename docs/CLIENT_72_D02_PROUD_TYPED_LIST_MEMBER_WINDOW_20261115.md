# R3-PROUD-TYPED-LIST-MEMBER-SOURCE：成员单窗来源验收

状态：**已完成（仅静态来源）**。本任务以已验父窗中直接调用为依据，固定同 owner 的 `69135.m572216 FLANEBGNPID`：flags `0x881`、instance、一个 `List59500` 显式逻辑参数；formal return 与 native ABI 未恢复。父方法直接边及其 11B setup 从已保存的 346B 父窗核对，未重新采集父窗。

新完整成员窗为 856B、211 条指令，SHA-256 `c4345212e44d395f086973395b3e858ab56ef3e2e767aeb3058f7f4d43ec3ddc`；唯一 pdata 范围已核。双 fresh 与独立第三共 13 类文件逐字节一致，独立重算 25 call、45 branch、18 RIP 共 88 项引用，未读取引用目标。AOT 16B、pdata 12B 与 body 856B 共 884B；header 4616B、异常目录 14344080B 和五 stock 文件 hash 读取分别记账。作者五项纯测试记录只读复用，独立审阅未重跑。

独立完成回执 `local/migration72/d02-resume-20261115/launch-timeout/typed-member-integration1/member-completion.private.json` 为 5060B，SHA-256 `157090b9c5112e6770f90d1d892052375d9299f8a94fd9c03f2c54237e184fa3`；同目录的 `member-audit.private.json` SHA-256 `57d1106346e93bcf83193810e542aed627c5380610c2bf63c4b9a290b931f40d`，`member-independent.private.json` SHA-256 `84cb018aa272bb32912221d3df3d0f2123507d41883c891f8d00fb96548bce78`。固定请求及三份来源产物入口位于忽略目录 `local/migration72/d02-resume-20261115/proud-typed-list-member-window/`，只供已授权范围的复核。

第三次采集时 27 项来源 pin 匹配；此后经批准，原语义专题追加独立审阅状态，采集时 SHA `47282e6361168ecdbb07a155265d22dfb5f7c6b5c000f399702ab682e5c5437d` 变为 `309670d1261c23d9060ea7c9e0b512d6d92606f1797029ea492ae5f413387106`。独立回执将此项单独列出，其余 26 项来源保持不变；不将两时点误写为当前 27 项均与采集时相同。

首次准备因以整数查询 JSON 字符串键发生 `KeyError`；对应字符串键存在且指向 `List59500`，按既有格式修正后来源采集成功。原准备失败证据 `preparation-failure.private.json` SHA-256 `8691ef32ca67eeed262e1bbd83bf273c7acbc14db163ba5865e5cee8dbe7159e`，执行工具仅返回合并输出，分离的 stdout/stderr 不可恢复；这不是安全审查拒绝，也不是缺少类型输入。

此项只证明来源与机械边界，不证明三字段业务桥、实际 M、row、current、same epoch 或 resource ready。`R3-PROUD-TYPED-LIST-MEMBER-SEMANTICS` 基于既有成员窗、父窗和 metadata 进行中，不新增 PE、目标进程或 codec 读取。冻结 preview 的间接关系未知，A/B 被拒动作及其依赖继续冻结；自然非空 cache 与 key 仍为实际采集的阻塞条件，D-02 客户端验收仍为 0/6。
