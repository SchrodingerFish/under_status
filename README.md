# UnderStatus

UnderStatus 是一个 Apache NetBeans 状态栏效率工具插件，提供编辑器状态信息、代码格式化、便签、计算器、编码转换、JWT/JSON/XML/SQL 工具、番茄钟、闹钟和可选天气信息。

## Requirements

- Apache NetBeans 30
- JDK 17 或更高版本

## Build

```text
mvn verify
```

构建完成后，安装包位于 `target/understatus.nbm`。

## Features

- 状态栏时钟、内存、文件指标和只读切换
- 当前编辑器一键格式化
- 番茄钟和可重复闹钟
- 便签及开发者工具箱
- JSON、XML、SQL、Cron、JWT、正则、Diff、哈希和编码工具
- 可选的和风天气当前天气与小时预报

## Weather setup

在状态栏设置中填写和风天气 API Key，并选择城市或启用自动定位。API Key 保存在 NetBeans 用户偏好中；该位置用于方便使用，不等同于系统级安全凭据存储，也不会写入日志。

## Configuration

UnderStatus 使用新的 `com.cn.schrodinger.understatus` 配置节点。升级后不会读取旧版本设置；如需恢复默认值，可在 NetBeans 用户设置中删除 UnderStatus 节点后重新启动 IDE。

## License

详见 [LICENSE](LICENSE)。
