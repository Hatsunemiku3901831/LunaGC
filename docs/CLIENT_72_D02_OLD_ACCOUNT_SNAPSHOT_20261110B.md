# E-01d1 / R3：所选旧号固定快照计划

用户已明确选择本轮旧测试号，身份仅留忽略目录。已验[离线依赖driver](CLIENT_72_D02_OLD_SAVE_DEPENDENCIES_20261110.md)可消费固定BSON/Extended JSON；当前仍缺该旧号实际行、关联和资源引用，不能以出生11101或零阶验收替代。

## 本包范围、验收与恢复

主线 Sol xhigh 独占本包的 Java/Mongo/27027；子代理仅做各自冻结材料，不启动共享环境。本包只新增忽略目录内的导出driver、独立物理副本/日志/原BSON，以及本专题。原 `mongodata/`、`local/data/`、配置、resources 和客户端均不写；不启动GS/客户端，不安装DLL，不读取或输出密码。

1. 重新核当前配置的本机Mongo路由、运行数据目录、进程和端口。原物理数据锁非空，末尾日志无正常关闭记录；不能称为干净冷备。当前进程不存在也不修原锁、不据旧PID停止进程。
2. 在新忽略目录保留完整原物理数据/运行数据/配置的不可覆盖证据副本；逐文件SHA、大小与源前后对照，源在复制前后均无相关进程。再从证据副本建立第二份独立工作副本，仅后者交同版本便携Mongo自然journal恢复。它是离线原件复制及隔离恢复实验；恢复失败保留两副本，不执行repair或回写原库。
3. 先编译独立只读导出器，再启动工作副本于127.0.0.1:27027；核实际PID/EXE/dbpath/命令行/端口和getCmdLineOpts。只使用list/find/count/listIndexes/getCmdLineOpts等读取接口，不调用生产Account/Player加载、createIndex、save、迁移、建号或业务认证。
4. 在副本中按用户所选用户名定位实际账号，记录当前配置库与其他匹配库的归属/重复，不把预留UID等同已有Player。找到多个不一致候选则交明确选择，不拼不同库/时间点。按原类型导出相关数据库全部集合BSON、Extended JSON、索引/数量，并由原始Player.accountId及ownerId核关联。原件含身份/凭据，只留忽略目录。
5. 通过getCmdLineOpts再次核owned目标，使用已验shutdown helper正常关闭并等待原PID退出，核shutdown日志和27027释放。复核原物理文件/运行数据/配置SHA保持。固定导出SHA后再运行依赖driver，整理额外资源/条件路径，清楚区分实际保存行、只读恢复成功、业务完整恢复与客户端验收。

产物根：`local/migration72/d02-resume-20261110b/old-account-snapshot/`。验收出口是来源一致的所选旧号原始快照、真实关联与必要资源ID；没有账号/Player、多个歧义候选、版本不兼容或恢复失败均保留准确结果，不补默认行或用新号代替旧号。完成即交明确资源族/真实恢复任务；R3、D-02及六项客户端验收仍未完成。

无需新的客户端ready；用户已授权本轮独立副本研究。本包没有部署材料变更，不机械重启GS。实际证据和结论完成后再更新本页、Project/Harness并本地提交。

## 实际限定验收（2026-11-11）

独立Java导出器先便携JDK17编译通过，随后两次都只启动第二工作副本，核PID/EXE/dbpath/27027/getCmdLineOpts并正常shutdown。首轮按输入大小写精确用户名未匹配；原件保留。第二轮仅为定位按大小写无关匹配，找到旧业务库的唯一账号和唯一Player；这不是改变认证大小写规则。当前研究配置库与该旧业务库不同，不修改原配置以迁就查找。

原账号reservedPlayerId为缺省0；初审harness误要求其等于实际Player UID，失败保留。改以原Player.accountId、account_uid_owners._id→ownerId、player_uid_reservations.ownerId→实际UID/kind及所有角色/Item.ownerId独立核对，全部一致。没有运行生产UID分配、创建索引、保存、认证或修复。活动常规队伍含4个角色；所选原始121个Avatar均level100/phase6，12254条Item中471条有正装备归属。没有用新号或出生模型替代。

完整数据库18集合的BSON/Extended JSON、索引/计数及选定原行保留在`run-20261110-2/export/`，45文件完整SHA见`run-proof.private.json`。两轮各自原142文件在复制/恢复/导出前后SHA保持；副本内部journal恢复写入不回传原库，证据副本也未启动。之后用户对AGENTS的独立编辑另行保留，不把它误归数据库变化或回退。

第二轮执行证明SHA `363ec11e7b2e0cc597f064abfcabe16b4f2c3f9b4557d9a0d44e07b3030f8bf9`；选定快照验收`acceptance.private.json` SHA `80a39b3943d0e4658cdfae3bc561cafebd0ddcbbc755aeefc7d0df2d4f447667`；私有manifest SHA `9fffdc25ddb24376552e3163db9ed6de441548d843fbda82649996fb59d4910b`；依赖输出SHA `3ef64138cd0525442df1625e823bb41a66089c3ea114deb99c7da24f201ec0b7`。原库锁/不干净退出证据不改，本项只能称隔离副本恢复/读取成功，不能称原库干净冷备或完整Player业务恢复。没有GS部署、客户端或六项验收新增。

下一步按实际行划分Avatar/Depot/Skill、Item资源族、phase6 Promote/Talent/Proud/OpenConfig和完整manager恢复；快照已充分，不重复启动数据库取同样输入。原物理库、工作副本、导出及两个run均保留。
