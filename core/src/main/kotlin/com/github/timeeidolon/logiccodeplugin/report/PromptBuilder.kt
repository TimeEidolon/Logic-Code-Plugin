package com.github.timeeidolon.logiccodeplugin.report

import com.intellij.openapi.project.Project

class PromptBuilder(private val project: Project) {

    companion object {
        const val REPORT_MARKER = "---FILE: PROJECT_ANALYSIS_REPORT.md---"
        const val PLAYBOOK_MARKER = "---FILE: PROJECT_EXCEPTION_PLAYBOOK.md---"
        const val ENTRYPOINT_GUIDE_MARKER = "---FILE: PROJECT_ENTRYPOINT_GUIDE.md---"
    }

    fun build(projectPath: String, inventory: String, entrypoints: String, sourceContext: String): String {
        val workflow = TemplateLoader.loadWorkflow(project)
        val reportTemplate = TemplateLoader.loadReportTemplate(project)

        return """
你是资深 Java 项目分析助手。请基于下面的项目盘点、入口清单、源码上下文、工作流和模板，生成三份最终 Markdown 文档。

输出必须严格使用以下文件分隔符：

$REPORT_MARKER
[这里输出完整 PROJECT_ANALYSIS_REPORT.md 内容]

$PLAYBOOK_MARKER
[这里输出完整 PROJECT_EXCEPTION_PLAYBOOK.md 内容]

$ENTRYPOINT_GUIDE_MARKER
[这里输出完整 PROJECT_ENTRYPOINT_GUIDE.md 内容]

要求：
- 文件分隔符必须**独占一行**，不要放在 ``` 代码块里，不要添加前后空格或其它字符。
- 只基于提供的源码上下文和明确推断，不要编造不存在的类、接口、数据库表或外部系统。
- 如果无法静态确认，明确写"静态分析无法确认"。
- 主报告必须覆盖整体架构、功能梳理、关键数据模型、配置、项目级风险、新人阅读路径、总结；不要再单独生成"主要链路分析"章节。
- 主报告第 4 章用于“入口导航”，不展开逐入口细节：
  - 必须按入口类型统计数量：Spring Web（Controller Mapping）、XXL-Job、Spring `@Scheduled`、消息消费（Kafka/RabbitMQ/JMS/Pulsar）、应用事件监听（`EventListener`/事务事件）、RPC（gRPC 风格注解若有）、以及 Jakarta/JAX-RS（`Path` + HTTP 动词）等；并用一句话概括覆盖面与典型链路。
  - 必须提供跳转链接到 `PROJECT_ENTRYPOINT_GUIDE.md`（例如：`[详见入口手册](./PROJECT_ENTRYPOINT_GUIDE.md)`），并按类型与类分组列出入口索引（类名 + 方法名 + 触发元信息 + 链接锚点）。
  - 第 4 章不要绘制每个入口的 Mermaid 图，也不要逐入口拆解变量与逻辑（这些全部放在入口手册）。
- `PROJECT_ENTRYPOINT_GUIDE.md` 才是“入口全量导览”完整版：至少覆盖上述各类入口；并且**每个入口方法**都要包含：触发元信息（HTTP、handlerName/cron、topic/queue、事件名、verb+path 等）、调用链（Service/Client 等）、Mermaid 流程图/逻辑图、功能点主要实现逻辑、关键变量用途。
- Controller 入口必须全量列出所有带映射注解的 Controller 方法（不能只列核心接口或示例接口），按 Controller 类分组；并在入口手册中为每个入口提供图与拆解。
- 每个入口方法在入口手册中都必须有 Mermaid 流程图或逻辑图，节点统一采用"代码标识英文 + 说明中文"，推荐格式：`NodeId[ Class.method 中文动作说明 ]`
- 异常手册必须按链路列出触发条件、表现现象、可能原因、影响范围、日志线索、排查步骤、临时处理、长期修复建议。
- 每个异常场景必须同时从代码逻辑视角和运行时数据视角分析。
- 不要输出真实密钥、token、password、api key。
- 为减轻长文阅读疲劳：`#` / `##` 级章节标题可适度前置一个 Emoji（如 📌 总结、⚠️ 风险、🚪 入口导航、🗄️ 数据与存储），每章最多一个；正文保持专业简洁。重要提示可用 Markdown 引用：`> **提示：** …`。

# Project

`$projectPath`

# Inventory

$inventory

# Entrypoint Inventory

$entrypoints

# Workflow

$workflow

# Report Template

$reportTemplate

# Source Context

$sourceContext
""".trimIndent()
    }
}
