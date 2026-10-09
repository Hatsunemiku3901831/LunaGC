# 千星镜头配置与批量变体生成

第二十八批已补齐已有镜头 GIA 的提取、GM 编辑及保留来源的还原，见
[镜头提取与编辑](MILIASTRA_CAMERA_EDIT_2026-10-08.md)。本页记录新镜头生成参数及第二十七批验证。

## 使用

GM「千星资产」展开「生成实体 / 像素画 / 镜头 .gia」，选择「镜头 JSON」。
选择模板后点击「填写所选模板」得到可编辑参数；「填写五种模板」与「填写批量变体示例」
分别填入全部模板和四类变体。修改 JSON、输出文件名及可选 GUID 起点，再点击「生成并归档」。
成功后可以下载 GIA；无需玩家 UID，接口沿用 GM 令牌与回环限制。

```json
{
  "cameras": [
    {"preset": "thirdPerson", "name": "跟随镜头", "ignoreCollision": true},
    {"preset": "firstPerson", "viewpointOffset": {"y": 0.4}}
  ],
  "variations": [
    {
      "camera": {"preset": "thirdPerson"},
      "parameter": "distance",
      "from": 1,
      "to": 3,
      "step": 0.1,
      "namePrefix": "视距-"
    }
  ]
}
```

`cameras` 与 `variations` 可以单独或同时使用，总数至少一项。
每个普通镜头可指定正 int32 `guid`；其余镜头由请求中的 `firstGuid` 或自动起点分配。
默认模板为 `thirdPerson`。偏移的部分轴可覆盖，其余轴沿用模板。
未知参数、重复 JSON 属性、错误类型和重复固定编号会拒绝。

## 模板及参数

| preset | 名称 | cameraMode | 默认／最小／最大视距 | 跟随旋转 |
| --- | --- | --- | --- | --- |
| `twoPointFiveD` | 2.5D 镜头 | 2 | 15／3／25 | 是 |
| `backCamera` | 3D 背镜头 | 1 | 3／1／5 | 是 |
| `firstPerson` | 第一人称镜头 | 3 | 0.1／0.1／0.1 | 否 |
| `classic` | 经典镜头 | 5 | 6／1／6 | 否 |
| `thirdPerson` | 第三人称镜头 | 4 | 6／1／30 | 是 |

模型模式 5 沿用参考导出器，编码为 proto 的 0／省略模式字段。
其余模式按 1–4 编码；不将参考命名或编辑提示视作当前客户端已确认的政策。
五种模板的默认视野均为 45；背镜头偏移为 `(0.6, 0.1, 0)`，第一人称为 `(0, 0.3, 0)`。

| 字段 | 规则 |
| --- | --- |
| `name` | 字符串，最多 4096 个 Java 字符 |
| `cameraMode` | 整数 1–5 |
| `defaultDistance`, `minDistance`, `maxDistance` | 0.1–50；默认值位于最小／最大值之间 |
| `fieldOfView` | 30–60 |
| `viewpointOffset` | `x`, `y`, `z` 为有限 float32 数值 |
| `followRotation`, `ignoreCollision` | 布尔值 |
| `horizontalAngle`, `minHorizontalAngle`, `maxHorizontalAngle` | -180–180，范围下限不大于上限 |
| `minPitchAngle`, `maxPitchAngle` | -89–89，范围下限不大于上限 |
| `field14` | 0–1；原协议中的未知字段，保留模板值，不猜测含义 |

参考模型中的编辑能力提示保存在模板目录中，不用作未经验证的服务端禁用条件。
原参考导出器未写入 `ignore_collision`，本实现按原始 MIT proto 的 float 字段写入 `false → 0`、`true → 1`。
非有限数值、float32 溢出及非零数值下溢到零会拒绝。

## 批量变体与编号

`parameter` 支持 `horizontalAngle`、`pitchAngle`、`distance`、`fieldOfView`。
水平／俯仰角变体将对应的最小与最大角度设为当前值；视距变体将三种视距都设为当前值；
视野变体修改视野。水平角变体沿用参考工具的锁定范围方式，不改写单独的 `horizontalAngle`。
基础 `camera` 完整校验，不能包含固定 `guid`。

`from ≤ to`、`step > 0`，按十进制计算 `floor((to-from)/step)+1` 项；
仅在步长落到终点时包含终点。名称追加规范十进制值。
相邻变体若转换为 float32 后相同，会拒绝以免生成重复数值。
示例包含水平角 361 项、俯仰角 179 项、视距 291 项、视野 31 项，共 862 项。

单次输入上限 8 MiB，总数最多 100000，输出最多 32 MiB。
展开过程中按实际 protobuf 结构及最大 GUID 长度预估大小，提前拒绝超限；
最终编码仍检查实际文件大小。接近边界的预估可能略保守。
资产类型为 13，元数据 `field_2` 为 25；每个资产的元数据编号与镜头详情编号一致。

镜头自动分配会扫描归档中的已知主资产、依赖和内部镜头编号；
指定编号与这些编号重合时拒绝，损坏归档会阻止分配。
不猜测未知字段中的编号，也无法自动检查外部编辑器的现有存档。
自动重复生成会得到新编号和新副本；指定已占用编号不会被当作成功去重。
导入完全相同的文件仍使用归档原有的 SHA-256 去重。

## 来源与接口

只读提取 [UGC-File-Generate-Utils](https://github.com/luern0313/UGC-File-Generate-Utils/tree/fba7c267a3752d3f0108b30020c46868d56acdd5)
中 `model/camera_model.py` 的五种模板，不导入或执行参考 Python。
固定提交为 `fba7c267a3752d3f0108b30020c46868d56acdd5`，模板源码 SHA-256 为
`3fc3f0f0e6e06584c146886ca5a6c03b220620068eb14fbbb48ed517e75afbc2`。
`task refs:miliastra-cameras` 可重新提取两份资源；Apache-2.0 完整许可随 JAR 打包。
镜头二进制采用已适配的原始 MIT 资产协议；许可见资产协议说明。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/gm/api/miliastra/camera-templates` | 模板默认值、编辑提示、来源与未核验客户端版本标记 |
| POST | `/gm/api/miliastra/generate/camera?name=镜头.gia&firstGuid=...` | 原始 JSON 请求体，建议 application/json |

新归档返回 201，标准重复归档为 200；参数错误为 400，未授权 403，输入超限 413。
返回资产条目、数量、自动编号数量与自动起点。接口只生成和归档配置，不发布到游戏。

## 验证

先使用真实 HTTP 合约测试复现旧接口 404，再实现生成接口。
新增 11 项测试，专项 147 项／20 套件，全量 1223 项／157 套件均通过；
失败、错误、跳过均为零。覆盖五种模板、全部字段、862 个变体、范围／类型／容量、
float32 重合、混合编号、依赖及内部编号避让、并发生成、重启归档和 GUID 复制。

隔离 GM `http://127.0.0.1:18978/gm`，Chrome 154.0.8037.98／Playwright 1.62.1，
桌面 1365×900、手机 390×844：
「镜头模式 → 五种模板 → 生成／下载 → 第一人称完整参数 → 自定义全部字段 →
862 个变体 → 编号冲突提示 → 自动分配 → 实体／图片模式切换 → 手机编辑」。
32 项检查通过，页面身份、非空、无遮挡错误层、下载名称及 SHA-256、手机宽度均核对。
编号冲突的预期 HTTP 400 和相应资源错误已留档，没有未解释的控制台或运行时错误。
Browser plugin 不可用，使用已有 Chrome 与常规 Playwright，没有新增依赖。

以未修改的四份参考 MIT proto 和已有可信 protoc 独立编译解码器，解码五个实际浏览器下载文件，
逐项核对 870 个镜头的元数据、编号关联、五种默认值、全部自定义字段、862 个变体数值和名称；
规范化 protobuf 负载重新编码一致。
辅助解码器的依赖编译、Map 构造及名称空格断言已修正，初次失败保留，产品源码未因此改变。
测试脚本、日志、截图、样本和 JAR 保存在
`C:\Users\InvokerBot\AppData\Local\Temp\lunagc-miliastra-camera-20261008\`。

4904 个源码／资源摘要与隔离构建一致；JAR 中 451 个相关类、12 个资源与编译产物／输入一致。
六份资产协议及两份镜头模板资源重新提取后字节一致；八个参考库与所需子模块干净并保持固定版本。
最终 JAR SHA-256：`6bb5d2b1ee475768778d4920a70dc3a9346107614b9a1a5e3f8be362223949f2`。

第二十七批验证时已有镜头的配置提取／编辑尚未接入，第二十八批已补齐上述文档中的路径。
体素工具、复杂资产及 UGC 发布／执行／任务／奖励仍待接入。
参考模板和 proto 尚不能证明当前 7.1 客户端导入或实际镜头效果。
本批未提交、推送或部署，日常游戏服务与 Mongo 保持原容器及启动时间；总目标继续进行。
