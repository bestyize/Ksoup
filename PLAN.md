# Ksoup 实施计划

## 1. 产品经理视角

### 1.1 项目目标
- 在当前 Kotlin Multiplatform 工程中新增 `ksoup` 模块。
- 提供一个不依赖 JVM 的 HTML 解析库，面向 Android、iOS、Desktop 共享使用。
- 对齐 Jsoup 的核心使用方式，迁移时仅将根包名从 `org.jsoup` 改为 `xyz.thewind.ksoup`。
- 首个可交付版本优先覆盖最常用能力，形成可运行、可测试、可迭代的基础版本。

### 1.2 首期范围
- 文本 HTML 解析：`parse`、`parseBodyFragment`
- DOM 树能力：`Document`、`Element`、`TextNode`
- 基础属性访问：`attr`、`hasAttr`、`id`、`classNames`
- 文本与 HTML 输出：`text`、`ownText`、`html`、`outerHtml`
- 基础查询：标签、`#id`、`.class`、`[attr]`、`[attr=value]`、后代选择器、子选择器
- Compose 示例接入：展示解析和选择结果
- 单元测试覆盖核心路径

### 1.3 非首期范围
- HTTP 抓取与 `connect`
- 清洗白名单、序列化高级配置
- 完整 CSS Selector 语法
- 全量 HTML5 容错规则
- 完整 Jsoup API 覆盖

### 1.4 交付标准
- 工程新增 `ksoup` 模块并被 `composeApp` 正常依赖。
- 核心 API 在 `commonMain` 可用。
- 关键能力有自动化测试。
- 示例应用能展示真实解析结果，而不是模板页。

## 2. 资深程序员视角

### 2.1 技术路线
- 采用“分阶段兼容”策略，而不是一次性盲目迁移 Jsoup 全量代码。
- 在 `commonMain` 中手写最小 HTML 解析器，避免 JVM 专有依赖。
- 使用与 Jsoup 接近的类型命名：
  - `xyz.thewind.ksoup.Jsoup`
  - `xyz.thewind.ksoup.nodes.*`
  - `xyz.thewind.ksoup.select.*`
- 优先确保 API 形状和调用习惯稳定，再逐步扩展语法与行为细节。

### 2.2 任务拆解
1. 工程接入
- 新增 `:ksoup` 模块
- 配置 Android/iOS/JVM 三端目标
- 让 `composeApp` 依赖 `:ksoup`

2. DOM 建模
- 实现 `Node`
- 实现 `Element`
- 实现 `Document`
- 实现 `TextNode`
- 实现 `Elements`

3. 解析器
- 实现基础 Token 扫描
- 支持开始标签、结束标签、自闭合标签、注释忽略、文本节点
- 处理基础实体解码
- 构建 `Document/head/body`

4. 选择器
- 实现简单选择器解析器
- 支持标签、ID、类名、属性存在、属性等值
- 支持后代和子节点关系

5. 集成与验证
- 示例界面展示解析结果
- commonTest 增加核心用例
- 运行 Gradle 测试校验

## 3. 技术经理视角

### 3.1 代码质量要求
- 所有核心逻辑放在 `commonMain`，不得偷用 JVM 专属 API。
- 对外 API 命名保持稳定，避免后续迁移成本升高。
- 测试覆盖解析、DOM、选择器三条主路径。
- 明确记录“已兼容”和“未兼容”范围，防止误判成熟度。

### 3.2 风险与控制
- 风险：一次性追求完全兼容导致工程不可控。
  - 控制：先交付最小闭环版本。
- 风险：HTML 容错规则复杂。
  - 控制：先支持常见结构，再逐步补齐边界行为。
- 风险：Selector 语法范围过大。
  - 控制：首期只支持高频子集，并用测试固化。

### 3.3 验收清单
- `PLAN.md` 已产出
- `ksoup` 模块已创建
- `Jsoup.parse` 可返回可查询的 `Document`
- `select` 可处理基础查询
- `composeApp` 已使用新模块
- 测试可运行且通过

## 4. 当前执行策略

本次实现按“最小可用版本”交付：
- 先完成库模块、核心解析、基础选择器和示例接入
- 保持 API 继续向 Jsoup 靠拢
- 未覆盖部分在后续迭代扩展
