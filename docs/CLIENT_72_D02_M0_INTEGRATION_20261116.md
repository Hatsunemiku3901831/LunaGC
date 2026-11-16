# D02-M0：实际集成与验证

本轮从 `fcdd46af7965980dd4d318b13a646fac943e60fb` 接手；用户原AGENTS、patch/resources差异和两项未跟踪E8工具保持排除。只有国服7.2/Windows portable；没有部署或客户端验收，D-02六项仍0/6。最小规格、来源分类和固定输入分别见[SCOPE](CLIENT_72_D02_M0_SCOPE_20261116.md)、[DATA](CLIENT_72_D02_M0_DATA_20261116.md)、[复用](CLIENT_72_D02_M0_REUSE_20261116.md)。

## 已实现与实际测试

- `server.game.m0.enabled`默认false，portable入口要求显式InstanceRoot。M0读取该独立配置的资源/数据路径，通过原ResourceLoader的真实文件解析/onLoad实现有限加载，验证女主10000007、701、11101、Scene3、六技能、ability embryo/group、曲线/phase0与正值Avatar重算。全量`loadedAll`始终false；登录准入和加载等待使用只适用于显式M0的出生就绪。此状态仅为birth-ready。
- M0 Avatar sender使用目标Cmd20143和独立raw Proto：角色列表tag6、Team map/tag5及current Team/tag12；角色ID/GUID、三普通属性1001/1002/4001、同实例装备GUID、已审七FightProp、实际当前depot及当前HP决定的ALIVE/DEAD。没有投影Hero控制GUID或未知附属字段。
- 定向JUnit312项通过：新真实sender1项、独立Avatar raw275项、Inventory raw36项；新sender检查高位GUID、Store同装备实例、显式0属性、非零HP与死亡态。随后真实隔离资源/缺文件拒绝与sender复验2项通过。资源输入更新后的保存/冷读和场景对象验证继续。
- Windows portable夹具27项全部通过，包括M0原runtime拒绝、独立实例允许与已有端口/JAR/DB路径保护；夹具不运行真实Java/Mongo。最终日志`local/migration72/d02-m0-integration-20261116/portable-file-wrapper-tests.log`。

首次sender测试因未先初始化Grasscutter触发静态配置循环，失败XML保留后修夹具。首次资源测试的负例揭示FileUtils静态缓存路径：事后改配置未切换资源，首个正向不能算独立DATA通过。M0 loader现按显式Path调用同ResourceLoader，后续正负通过；原data/AbilityEmbryos.json不存在，加载方法只有读文件/内存写，无缓存写入，未恢复或覆盖原数据。

便携夹具初始EncodedCommand包装超时无输出，改直接多行Command后仍有3项既有实例例超时。最终将同一测试脚本写到临时文件并用`-File`执行，27项全过；前两份失败日志均保留，不追认为通过。未改实际launcher的停止、构建、端口或数据库归属策略。

## 当前未部署原因与下一动作

M0完整生命周期、SceneInfo实际对象投影、真实出生装备保存/冷读仍在集成；SceneTeam/Entity出现、SceneInfo Team/level/ability子图及自然SDK路由有准确必要缺口。现没有可请求用户启动的实机包；不能以birth-ready、编译或健康替代入场。后续先完成允许独立验证，再按部署手册构建/准备独立实例；必要wire/会话未齐时不启动猜测协议。

原始JUnit、失败、构建和运行材料只留上述忽略目录。Project维护任务与完成范围；Harness索引本报告。M0通过也不关闭完整D-02。
