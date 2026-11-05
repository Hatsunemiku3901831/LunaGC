# E-01b：scene3固定候选与服务端读取依赖

2026-11-06（Australia/Sydney）。本项已完成限定离线审计；E-01和D-02未完成，未安装或启动候选资源。工具不执行Lua、不修改resources或客户端，也不把同ID当作兼容证明。

## 固定输入

继承[E-01a](CLIENT_72_RESOURCE_CANDIDATE.md)的[固定上游提交](https://github.com/DimbreathBot/AnimeGameData/commit/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1)，不跟随分支：

| 层次 | Git对象 |
| --- | --- |
| commit | d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1 |
| 根tree | 5b334e711319726c504da95dbfe6ebb26baaa7d3 |
| BinOutput | c3bbf06db3dcff9d81549031a62a4d9c462f5163 |
| Scene | 8c06ef24e536ef0452ba6e1601de1b97443994fd |
| Point | 15e99d8e4d71c450ffd4d467dedf58fc13908187 |
| scene3_point.json | a6a448614e3237f9913a9cbcb18afd8d6a8411ff；1,095,242字节 |

点位文件SHA-256为`c91eb69a53c81886b265762a0fe6834b30a1b81610e8bb8b54f21c4e0f6070a4`。每次重新核对所有非递归tree的完整性/对象hash与父子关系，并核对点位及4张引用表的blob hash、字节数和SHA-256。复用E-01a的七表/版本输入检查；额外固定GadgetExcelConfigData、DungeonExcelConfigData和DailyDungeonConfigData，原始资源只存忽略目录。

工具同时核对9份当前Java读取源码的规范换行SHA-256。来源变动必须重新人工复核，不能只更新摘要来绕过。当前审计只覆盖源码明确读取的字段和下述引用，不声称已经运行Java资源加载器。

## 点位和引用结果

- 候选1,823点，当前资源1,707点；稳定ID新增150、移除34，共同1,673点全部有字段差异。其中1,615点共同字段的值有差异，1,673点有字段有无差异。原始缺字段/null/显式默认值不合并；这些数量不等于1,673点都存在坐标或业务数值变更。
- 共检查**2,817条正ID引用，缺失0**：gadgetId 1,750、tranSceneId 71、dungeonIds 420、dungeonRandomList 64，再沿64个实际被引用的日程检查七天的512条副本引用。0按无引用处理，不伪造ID0记录；没有扩大到未使用日程或所有玩法。
- PointData当前消费17个规范字段及5个既有别名。候选中另有42种字段未由该类消费，其中7种为未知混淆列；全部保留原名及出现次数，不凭数值/形状追加别名。
- 输入测试覆盖重复JSON键、非规范/越界点位ID、bool冒充整数、null/错误list、非有限坐标、部分坐标、别名冲突、未知字段保留、缺引用及日程错误；不把这些审计规则描述为客户端异常规则。

当前基线点位含同一字段的规范名与旧别名并存。本工具只对候选执行声明字段校验，对基线保留原始对象用于差异比较；不为研究改写基线，也不声称旧别名解析行为已验证。

## Lua与加载器依赖

当前ResourceLoader.loadScenePoints读取`BinOutput/Scene/Point/scene*_point.json`中的points，再以地图key设置PointData.id并更新每日副本。ScenePointConfig只声明points；其他根成员未在此读取。

SceneMeta.load的实际入口为`Scripts/Scene/3/scene3.lua`，读取scene_config、blocks和block_rects；它不从SceneExcel.scriptData自动生成该文件。随后SceneBlock.load读取`scene3_block<blockId>.lua`的groups，SceneGroup.load读取`scene3_group<groupId>.lua`中的实体、套件和触发器，require还依赖`Scripts/Common/<name>.lua`。这些是当前加载器的读取依赖，尚未收敛为完整7.2必需Lua集合；事件/插件覆盖和动态require仍是开放边界。

固定上游根tree**没有Scripts目录**。当前resources虽有scene3.lua，但没有对应7.2来源链；摘要标记`baselineProvenance72=unverified`，不会因为文件存在就通过E-01。没有覆盖用户既有的ScriptSceneData修改，也没有借用旧脚本冒称完成迁移。

客户端内置revision仍为49008628/49008628/48722240，与候选R/S/D集合不等；本机额外发现Persistent/res_revision_eternal内容为0，仅作未解释的本地观察，不将其认作热更新revision或来源匹配证据。完整运行时热更新状态、脚本来源、其他必需资源/加载和实机入场均待验证。

## 复现与验证

```powershell
$env:GAME_PATH = & ./tools/game_path.ps1
$sceneSource = 'local/migration72/sources/AnimeGameData/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1'
python tools/audit_scene_resource72.py --source $sceneSource --baseline resources --client $env:GAME_PATH --output local/migration72/evidence/scene-resource-audit.json --summary-output protocol/cn-7.2/scene-resource-observations.json
python -m unittest tools.test_audit_scene_resource72 tools.test_audit_resource72 -v
```

Scene/Point及引用表按固定Git对象下载到忽略目录，工具自身不联网下载或安装。复现所需完整tree JSON为`resource-source-{tree,BinOutput-tree,ExcelBinOutput-tree,Scene-tree,Scene-Point-tree}.json`和commit报告，均在本机evidence目录；不提供伪造源文件来使审计通过。

新增7项与原资源7项共14项聚焦回归通过。真实审计和摘要均运行两次，逐字节一致；证据为`local/migration72/evidence/scene-resource-{audit,repeat,summary-repeat,determinism}.json`，日志为`local/migration72/logs/scene-resource-{audit,focused-tests}.log`。共享[摘要](../protocol/cn-7.2/scene-resource-observations.json)不含机器路径/原始资源行。仅离线工具/测试/摘要/文档变更，无构建部署或重启要求。

下一项是取得可追溯的7.2 Scene/Block/Group/Common脚本及运行时revision证据，再按实际入场闭包检查引用和加载行为。父E-01保持进行中，不以本项审计替代完整资源或客户端验收。

后续来源核查：现有resources引用的四个公开仓库中，invoker-bot main仍固定为当前dac2a9d且无tag，Merprose2仅7.0分支/历史tag，另外两处API返回404；本机现有候选未增加Scripts来源。原始响应摘要在evidence/scene-lua-source-search.json，不将404推论为所有来源均不存在。

用户随后明确目前没有这些7.2 Lua来源，允许参考LunaGC7.1的实现尝试迁移。E-01c据此转向固定旧实现→实际Lua加载/目标7.2资源ID与接口核对→明确适配的路径；可以编写迁移实现，但来源仍标旧实现/本项目适配，不伪称7.2原始导出，也不自动解除revision、协议或实机验收。

E-01c已实际检查出生块686组，并扩展64块全部非dynamic索引输入：当前fastRequire=true有407顶层错误，现有inline路径离线比较13,616组全部能求值；95,600正实体引用中8条garbage装置缺ID、62个ScriptLib成员缺实现，仍未验证生产序列化/触发器和实机。具体工具、6新增/20聚焦回归与下一步见[脚本迁移](CLIENT_72_SCENE_SCRIPT_MIGRATION.md)，配置未改，E-01c保持进行中。
