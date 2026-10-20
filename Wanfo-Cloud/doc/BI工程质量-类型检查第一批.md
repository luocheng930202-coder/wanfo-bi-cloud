# 类型检查第一批：恢复真实声明与 BI 诊断门槛

2026-10-17。移除 global.d.ts 中覆盖 Vue 导出的 ambient module，以及遮蔽 Element Plus 声明的空模块。用显式 import Vue 的模块增强声明补齐实际注册的全局方法，不重定义 ComponentInternalInstance.proxy。补齐菜单排序/树、字典查询、密码策略等已有后端字段及操作日志类型路径。

BI 接口通过 BiResult<T> 描述成功时存在 data 的控制器契约，失败仍由请求拦截器拒绝；保留宿主 AjaxResult 原定义，不全局强制所有若依接口有 data。补齐数据源写入及连接测试响应类型，画布通过泛型保留具体 Panel/ViewPanel 插槽类型，收窄日期/文本筛选输入和开关事件类型。

新增 npm run typecheck（全量，保留非零失败退出）和 npm run typecheck:bi（运行同一全量编译器，仅对 BI 所属页面/接口/工具源码设门槛，并完整记录宿主诊断到 node_modules/.cache/bi-typecheck.log）。归属识别只检查诊断文件路径，宿主错误消息中引用 BI 模块不计作 BI 文件错误；3 项诊断归属断言通过。未关闭 strict，未加 ts-ignore 或重建 any Vue 壳。

验收时 BI 诊断为 0，宿主仍有 270 条诊断，全量检查未通过；原始 220 条因 Vue 遮蔽造成的错误数不能与恢复真实类型后直接比作回归数量。生产构建通过。宿主剩余包括 getCurrentInstance 可空、Element Plus 事件/组件参数、表单构建器和历史响应模型等；QA-02 仍为部分完成。axios 等旧 ambient 模块声明也需后续专项治理，不称本批已实现全工程类型安全。

本记录针对进入 D 前的质量检查；后续 D 新代码须再次通过 BI 门槛与生产构建。
