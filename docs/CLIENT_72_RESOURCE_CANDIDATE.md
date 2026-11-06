# 7.2 固定资源候选审计（E-01a）

2026-11-07[资源深入研究](CLIENT_72_RESOURCE_DEEP_RESEARCH.md)已关闭指定depot/条件parser默认、完整LHFO外层和BGH的GameTime selector子合同；实际候选JSON→字段/flag桥梁、acceptCond和运行时revision选择仍缺，不批准转换或resourceReady。候选与原resources均未改。

本页是候选输入与差异验收，**不是E-01最小资源集或D-02实机验收**。没有修改resources子模块、其引用、运行配置或现有数据。

## 来源固定

上游[AnimeGameData固定提交](https://github.com/DimbreathBot/AnimeGameData/commit/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1)，提交说明为`CNRELWin7.2.0_R49086080_S49210135_D49230962`，提交时间2026-11-04T01:04:00Z。固定内容，不跟随main变化；不将版本文字当作与本机客户端一致的证明。

| 输入 | 固定对象 |
| --- | --- |
| commit | d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1 |
| 根tree | 5b334e711319726c504da95dbfe6ebb26baaa7d3 |
| ExcelBinOutput tree | a7767d2efe82980a7dbefe716853e76e9c9a4276，2279项，未截断 |
| BinOutput tree | c3bbf06db3dcff9d81549031a62a4d9c462f5163，132项，未截断 |
| 现有资源基线 | dac2a9d43332ef51879522cf39da2831b0ca0543；既有flat.luas.scenes.full_globals.lua.json差异保留 |

audit_resource72重建根/Excel Git tree对象SHA-1，核对commit→根→Excel→选定blob链，逐文件核对Git blob SHA-1、大小并记录SHA-256；拒绝截断树、重复成员、错误类型、缺失/重复稳定ID和重复JSON键。API以commit请求tree时，返回字段sha仍可能为请求的commit别名；已改用真正的tree SHA重新取证，而不是放宽tree内容校验。原始别名响应保留在忽略目录。

七个原始文件只下载到local/migration72/sources/AnimeGameData/<commit>/，原始数据不进入Git。共同来源证明内容固定，不认证上游发布者或替代客户端提取验证。

## 稳定ID与差异

| 表 | 现有 / 候选行数 | 新增 / 移除ID | 共同字段值发生变化的行数 |
| --- | --- | --- | --- |
| AvatarExcelConfigData | 167 / 169 | 2 / 0 | 4 |
| AvatarSkillDepotExcelConfigData | 169 / 171 | 2 / 0 | 164 |
| AvatarSkillExcelConfigData | 1149 / 1176 | 27 / 0 | 818 |
| WeaponExcelConfigData | 287 / 288 | 1 / 0 | 214 |
| PlayerLevelExcelConfigData | 60 / 60 | 0 / 0 | 27 |
| SceneExcelConfigData | 2377 / 2431 | 54 / 0 | 2 |
| WorldLevelExcelConfigData | 9 / 9 | 0 / 0 | 0 |

按id/level对齐，不按行序。报告分别保存共同字段值差异与字段有无差异；缺字段、显式null、显式0不擅自视为等价。候选省略了许多旧表显式默认字段，因此“整行不同”不能直接等同于业务数值变更。混淆列也没有按相似值自动重命名。

选人相关avatar 10000005/10000007、scene3、player level1、world level1均存在。所查5种引用（avatar.skillDepotId/initialWeapon，depot.skills/energySkill/attackModeSkill）共**849条正ID，缺失0**。0按声明的空引用处理；不构造虚假的ID0记录。这只覆盖表中明确列出的引用，不覆盖天赋、突破、怪物、关卡、任务和其他资源依赖。

scene3确有共同字段值变化，例如boundaryPointVec不同；不能仅因为id相同就判定旧场景资源可直接使用。其他变更的完整ID/字段列表在忽略报告中。

## 版本与运行缺口

本机config.ini实际读取到国服7.2.0、channel/sub_channel=1。内置StreamingAssets的res_revision/data_revision均为49008628，silence_revision为48722240；来源提交标签的R/S/D为49086080/49210135/49230962，数值集不同。这里只检查内置文件，**未证实运行时热更新状态或标签与目标资源的一致性**，不能强行改版本/配置抹平差异。

上游顶层包含ExcelBinOutput、BinOutput、TextMap等，但没有Scripts目录。仍需核验实际必需Lua、Scene/BinOutput、文本和其余表，以及服务端加载模型。尚未将候选写入任何运行资源位置；resourceReady固定为false，以免一个审计成功被误读成允许部署。

## 复现与证据

先按Harness确认GAME_PATH；源树必须是上面固定快照并已取得完整根/Excel tree JSON。

```powershell
python tools/audit_resource72.py --source local/migration72/sources/AnimeGameData/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1 --baseline resources --commit-report local/migration72/evidence/resource-source-commit.json --root-tree local/migration72/evidence/resource-source-tree.json --excel-tree local/migration72/evidence/resource-source-ExcelBinOutput-tree.json --client $env:GAME_PATH --output local/migration72/evidence/resource-candidate-audit.json --summary-output protocol/cn-7.2/resource-candidate-observations.json
python -m unittest tools.test_audit_resource72 -v
```

7项资源审计测试、与现有研究工具合计113项通过；真实七表审计与摘要二次运行逐字节一致。共享摘要resource-candidate-observations.json不含原始资源行或机器路径。本机evidence/resource-candidate-{audit,determinism}.json、resource-source-*.json与logs/resource-candidate-all-tests.log保存完整证据。

E-01b已接续完成固定BinOutput/Scene/Point子树、scene3点位及9份当前读取源码的限定审计：1,823点、2,817条正引用无缺失，新增7项及共14项资源回归通过，两次产物一致。Lua来源/运行时revision/实际加载仍未齐，见[scene3资源审计](CLIENT_72_SCENE_RESOURCES.md)。不能以这些离线通过代替E-01或D-02。
