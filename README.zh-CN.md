# StarRocks Database Support

[English](README.md)

StarRocks Database Support 是一款面向 JetBrains DataGrip/IntelliJ 的插件，提供独立的 StarRocks SQL 方言和 StarRocks 数据库集成。

插件构建在 JetBrains SQL 语言平台之上。解析语法、解析词法、方言目录、格式化扩展和补充补全均限定在 StarRocks 语言范围内。

- 语法覆盖进度：[`SYNTAX_COVERAGE.zh-CN.md`](SYNTAX_COVERAGE.zh-CN.md)

## 功能

### 数据源

- 在「新建数据源」对话框中注册独立的 StarRocks 数据源（STARROCKS，MySQL 家族），内置驱动配置。
- 将 JDBC 元数据和 StarRocks 类型系统接入 Database Tools 平台。

### SQL 语言

- 使用 JFlex 生成的解析 Lexer 和 Grammar-Kit 生成的 Parser 解析 StarRocks SQL。
- 标准 SQL 结构尽量复用 JetBrains SQL PSI 类型，使平台格式化、引用、解析和数据库感知编辑能力可以继续工作。
- 高亮 StarRocks 关键字、标识符、字面量、注释、参数和运算符。
- 向 SQL 平台发布 StarRocks 关键字、标量类型、复杂类型和较完整的内置函数目录。
- 为 StarRocks 专属片段和属性提供补充补全；普通表、列、类型和函数补全仍由平台 SQL 补全系统负责。
- 基于平台 SQL 格式化器提供 StarRocks SQL 格式化支持。

### 数据库对象

- 支持数据库、表、视图、物化视图的对象模型；表/视图/物化视图支持原生
  `SHOW CREATE` 定义视图（Go to DDL）。
- 物化视图 DDL 打开后自动格式化，列清单每列一行。
- SQL 中表、视图、物化视图的引用解析支持 schema 限定搜索路径，
  消除物化视图错误的 "Unable to resolve" 提示。
- 加载物化视图状态，并在数据库树节点上展示。
- 物化视图右键操作：刷新（`REFRESH MATERIALIZED VIEW`）、
  启用/停用（`ALTER MATERIALIZED VIEW ... ACTIVE/INACTIVE`）。

项目通过结构化清单记录语法支持，不声明已经完全兼容 StarRocks。当前目标基准为 StarRocks 4.1 文档。已实现、部分实现、缺失和待核对内容见[语法覆盖清单](SYNTAX_COVERAGE.zh-CN.md)。

## 架构

Parser 使用生成式语法架构：

```text
grammar/starrocks-keywords.txt
        -> 生成关键字和 Token 注册表

grammar/starrocks.flex
        -> JFlex Parser Lexer

grammar/starrocks.bnf
        -> Grammar-Kit Parser

生成 Lexer + 生成 Parser
        -> DataGrip SQL Parser Adapter
        -> JetBrains SQL PSI / Formatter / Resolve / Completion
        -> StarRocks Database Services
```

主要设计约束：

- `grammar/starrocks.flex` 是解析词法的事实来源。
- `grammar/starrocks.bnf` 是解析语法的事实来源。
- `StarRocksParser` 只负责适配 JetBrains SQL Parser 钩子，语法逻辑属于生成 Parser。
- 标准 SQL 节点尽可能映射到平台 SQL PSI。
- StarRocks 专属能力通过显式 PSI 映射、Formatter Helper、补充补全和数据库服务实现。
- 不保留手写的整语句 Parser 兜底路径。

不得手动修改 `build/generated` 下的生成代码。应修改语法或关键字目录，然后重新运行生成任务。

## 项目结构

```text
grammar/
  starrocks.flex              Parser Lexer 语法
  starrocks.bnf               Parser 语法与 PSI Element 映射
  starrocks-keywords.txt      标准关键字目录

src/main/kotlin/.../
  lang/                       Parser Adapter、Lexer 门面、Element 映射
  dialect/                    方言标识和函数目录
  highlight/                  语法高亮
  format/                     SQL 平台格式化扩展
  completion/                 StarRocks 专属补充补全
  database/                   DBMS、类型、元数据和 DDL 集成

src/test/kotlin/              Parser、PSI、Resolve、Formatter 和集成测试
src/testData/sql/             SQL 验收与回归场景
```

## 环境要求

- JDK 17
- 使用项目自带的 Gradle Wrapper，当前版本为 Gradle 9.6.1
- 运行环境为 DataGrip 2026.1 或更高版本（`sinceBuild = 261`）
- 默认开发、测试和插件验证平台为 DataGrip 2026.1.4
  （`261.26222.86`）；可通过
  `intellijPlatformVersion` 或 `intellijPlatformLocalPath` 覆盖

## 构建与验证

编译插件：

```powershell
.\gradlew.bat compileKotlin --no-daemon
```

验证 Lexer 和 Grammar 源文件：

```powershell
.\gradlew.bat validateGrammarSources --no-daemon
```

修改语法时，可显式生成 Parser 资产：

```powershell
.\gradlew.bat generateLexer generateParser --no-daemon
```

运行 StarRocks SQL 场景校验：

```powershell
.\gradlew.bat validateStarRocksScenarios --no-daemon
```

运行完整验证：

```powershell
.\gradlew.bat check --no-daemon
```

构建可分发的插件 ZIP：

```powershell
.\gradlew.bat buildPlugin --no-daemon
```

ZIP 输出到 `build/distributions/`。

## 开发

可以使用仓库中的运行配置，在 IntelliJ IDEA 或 DataGrip 中启动沙箱 IDE：

```text
.run/Run IDE with Plugin.run.xml
```

也可以通过命令行启动：

```powershell
.\gradlew.bat runIde --no-daemon
```

新增语法支持时：

1. 对照目标 StarRocks 版本的官方文档确认语法。
2. 修改 `starrocks.flex`、`starrocks.bnf` 或标准关键字目录。
3. 增加针对性 Parser 测试；引入新语句族时增加场景 Fixture。
4. 对包含数据库对象的语法检查 PSI 映射、格式化和引用解析。
5. 更新语法覆盖清单。
6. 运行语法校验、场景校验和完整测试。

## 许可证

本项目使用 MIT License，详见 [`LICENSE`](LICENSE)。

本项目派生自 "StarRocks Support"（https://github.com/ycyz97/starrocks-datagrip-plugin，Apache License 2.0），
归属说明见 [`NOTICE`](NOTICE)。

