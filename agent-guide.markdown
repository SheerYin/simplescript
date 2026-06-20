# 项目协作提示

本文件仅供协作参考，不是强制规格或完整规范；实际情况以当前项目配置、Gradle 缓存、本机环境和用户最新要求为准。

本项目按职责拆分协作提示：

- 通用协作规则：`agent-guide.markdown`
- Canvas 服务端开发：`agent-canvas-guide.markdown`
- Velocity 代理端开发：`agent-velocity-guide.markdown`

## 编码与文件

- 文本文件使用 UTF-8（no BOM）。
- Windows 专用脚本或注册表文件按系统默认编码处理，例如 `.bat`、`.cmd`、`.reg`。
- 读取已有文件时保留原编码。

## 通用开发习惯

- 除非明确需要，一般不主动执行完整构建。
- 代码以可读性优先，避免过度使用难懂的语法糖。
