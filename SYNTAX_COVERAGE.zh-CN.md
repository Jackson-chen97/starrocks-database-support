# StarRocks SQL 语法支持进度

English version: [`SYNTAX_COVERAGE.md`](SYNTAX_COVERAGE.md)

本文档用于持续跟踪插件对 StarRocks SQL 语法的实现情况，是一份工作清单，不代表项目已经实现完整兼容。

## 基准与状态定义

- 目标版本：项目 README 声明的 StarRocks 4.1。
- 官方来源：[StarRocks SQL statements 文档目录][official-statements]。
- 本次文档快照：2026-07-16，取自官方仓库的 `main/docs/en/sql-reference/sql-statements` 目录树。
- 语法定义来源：[`grammar/starrocks.bnf`](grammar/starrocks.bnf)。
- 解析词法来源：[`grammar/starrocks.flex`](grammar/starrocks.flex)。
- 回归场景目录：[`src/testData/sql`](src/testData/sql)。

官方文档仓库在本次快照之后仍可能变化，因此每一项被标记为“完整”前，都必须再次对照实际的 StarRocks 4.1 文档。

状态含义：

| 状态 | 含义 |
| --- | --- |
| 完整 | 官方语句及其重要分支均有结构化语法和解析测试。 |
| 部分 | 已有语句入口，但缺少官方子句、变体或当前实现有所简化。 |
| 缺失 | 未找到对应的顶层语法入口。 |
| 待核对 | 尚未人工确认文档映射关系或该语法是否适用于 4.1。 |
| 不适用 | 页面不是 SQL 解析入口，例如 HTTP 导入接口或概念说明。 |

一项语法只有同时满足以下条件，才可以改为“完整”：

- 存在结构化 BNF 规则，且没有整条语句兜底解析；
- StarRocks 专属语法具备相应词法 Token；
- 至少有一个无 PSI 错误的正向解析测试；
- 重要可选分支和容易产生前缀歧义的写法已有测试；
- 涉及 SQL 对象时，已检查格式化、PSI 结构和引用解析。

## 当前概要

当前插件对核心查询、常用表 DDL/DML、视图、物化视图、Catalog 和 Resource 的支持较强；对集群管理、统计任务、导入生命周期命令以及较新的托管对象语法支持明显不足。

核对时，官方目录共有 196 个 Markdown 页面。该数字不能直接作为兼容率分母，因为其中同时包含 SELECT 子句、功能说明、非 SQL 入口和独立 SQL 语句。

| 领域 | 当前评估 | 主要依据或限制 |
| --- | --- | --- |
| SELECT 与表达式 | 部分，覆盖较高 | 已覆盖 CTE、JOIN、子查询、集合运算、窗口、`QUALIFY` 和 grouping sets；`OFFSET`、`PIVOT`、`SELECT * EXCLUDE` 尚待核对或实现。 |
| 表 DDL | 部分，覆盖较高 | 已建模 CREATE、DROP、TRUNCATE、CTAS、LIKE、分区、分桶、索引、常用属性及官方文档中的 `ALTER TABLE` 动作类别。 |
| DML | 部分，覆盖较高 | INSERT、UPDATE、DELETE、MERGE 已有结构化入口和场景；仍需逐分支对照官方变体。 |
| View | 部分 | 已有 CREATE、ALTER、DROP、SHOW CREATE；官方可选子句仍需核对。 |
| Materialized View | 部分 | 已部分覆盖 CREATE、ALTER、DROP、REFRESH、CANCEL REFRESH 和 SHOW（含 SHOW ALTER MATERIALIZED VIEW）。 |
| Database/Schema | 部分 | 已有 CREATE、ALTER、DROP、USE 和常用 SHOW；SHOW DATA、SHOW CREATE DATABASE 等仍需补充。 |
| Catalog | 部分 | 已有 CREATE、ALTER、DROP、SHOW CATALOGS、SHOW CREATE CATALOG；缺少 SET CATALOG。 |
| Resource | 部分 | 已有 CREATE、ALTER、DROP、SHOW RESOURCES；官方变体仍需核对。 |
| 账户与权限 | 部分 | 已有常用用户/角色 DDL、GRANT、REVOKE、SET PASSWORD；角色切换、模拟执行及多个 SHOW 语句缺失。 |
| Backup/Restore | 部分 | BACKUP、RESTORE、Repository、RECOVER 当前为简化语法。 |
| 导入、导出与任务 | 部分 | 已部分建模 Broker Load、CANCEL LOAD、Routine Load、SUBMIT TASK、EXPORT；大部分生命周期和 Pipe 命令缺失。 |
| 统计信息 | 部分，覆盖较低 | 已有 ANALYZE TABLE 和部分 SHOW；统计任务生命周期语句大多缺失。 |
| 集群管理 | 部分，覆盖较低 | 已有 SET、KILL、SYNC、EXPLAIN、SHOW PROC 以及少量 ADMIN SHOW；官方大部分集群管理语句缺失。 |
| Function DDL | 缺失 | 函数调用和函数目录支持不等于支持 CREATE/DROP FUNCTION。 |
| Pipe 与 Dictionary | 缺失 | 未找到对应顶层语法族。 |
| Storage Volume 与 Resource Group | 缺失 | 未找到对应顶层语法族。 |

## 详细核对清单

### 查询与表达式

- [x] 部分 — SELECT 核心子句。
- [x] 部分 — CTE，包括递归语法的接受能力。
- [x] 部分 — JOIN、派生表、LATERAL UNNEST、SEMI JOIN、ANTI JOIN。
- [x] 部分 — UNION、INTERSECT、EXCEPT、MINUS。
- [x] 部分 — 窗口定义、分析函数和窗口帧。
- [x] 部分 — QUALIFY、GROUPING SETS、ROLLUP、CUBE。
- [x] 部分 — 子查询、CASE、CAST、IN、BETWEEN、LIKE、REGEXP、数组和复杂类型。
- [ ] 缺失/待核对 — `SELECT ... OFFSET`。
- [ ] 缺失/待核对 — `PIVOT`。
- [ ] 缺失/待核对 — `SELECT * EXCLUDE (...)`。
- [ ] 缺失/待核对 — `TRANSLATE TRINO`。
- [ ] 待核对 — 逐项比较官方 SELECT 修饰符和表达式运算符。

### 表、索引与 DML

- [x] 部分 — CREATE TABLE、CTAS、CREATE TABLE LIKE。
- [x] 部分 — 当前文法列出的 OLAP 和外表引擎变体。
- [x] 部分 — Key 模型、分区、分布、桶、Rollup、索引、生成列和属性。
- [x] 部分 — DROP TABLE、TRUNCATE TABLE、CREATE INDEX、DROP INDEX。
- [x] 部分 — INSERT、INSERT OVERWRITE、UPDATE、DELETE、MERGE。
- [x] 覆盖较高 — ALTER TABLE。支持逗号分隔动作、重命名与注释、分区与分桶、列与 STRUCT 字段、Rollup、索引、表属性、SWAP、Compaction 和持久化索引删除。
- [ ] 缺失/待核对 — CANCEL ALTER TABLE。
- [ ] 缺失/待核对 — REFRESH EXTERNAL TABLE。
- [ ] 缺失/待核对 — SHOW ALTER、SHOW DELETE、SHOW DYNAMIC PARTITION TABLES、SHOW FULL COLUMNS、SHOW INDEX、SHOW TABLET。
- [ ] 待核对 — 比较所有分区、Rollup、索引和属性变更分支。

### Database、Catalog、View 与 Materialized View

- [x] 部分 — CREATE/ALTER/DROP DATABASE 或 SCHEMA，以及 USE。
- [ ] 缺失/待核对 — SHOW CREATE DATABASE、SHOW DATA。
- [x] 部分 — CREATE/ALTER/DROP CATALOG、SHOW CATALOGS、SHOW CREATE CATALOG。
- [ ] 缺失 — SET CATALOG。
- [x] 部分 — CREATE/ALTER/DROP VIEW、SHOW CREATE VIEW。
- [x] 部分 — CREATE/ALTER/DROP/REFRESH/CANCEL REFRESH MATERIALIZED VIEW 及常用 SHOW（含 SHOW ALTER MATERIALIZED VIEW）。
- [ ] 待核对 — 比较物化视图的刷新、分区、分布和属性变体。

### Function、Resource、Storage 与 Dictionary

- [ ] 缺失 — CREATE FUNCTION。
- [ ] 缺失 — DROP FUNCTION。
- [ ] 缺失 — SHOW CREATE FUNCTION。
- [x] 部分 — 通过通用 SHOW 文法支持 SHOW FUNCTIONS。
- [x] 部分 — CREATE/ALTER/DROP RESOURCE、SHOW RESOURCES。
- [ ] 缺失 — CREATE/ALTER/DROP RESOURCE GROUP。
- [ ] 缺失 — SHOW RESOURCE GROUP、SHOW USAGE RESOURCE GROUPS。
- [ ] 缺失 — CREATE/ALTER/DROP STORAGE VOLUME。
- [ ] 缺失 — SET DEFAULT STORAGE VOLUME、DESC STORAGE VOLUME、SHOW STORAGE VOLUMES。
- [ ] 缺失 — CREATE/DROP/REFRESH DICTIONARY。
- [ ] 缺失 — CANCEL REFRESH DICTIONARY、SHOW DICTIONARY。

### 账户与权限

- [x] 部分 — CREATE/ALTER/DROP USER。
- [x] 部分 — CREATE/ALTER/DROP ROLE。
- [x] 部分 — GRANT、REVOKE。
- [x] 部分 — SET PASSWORD。
- [x] 部分 — 通过通用 SHOW 文法支持 SHOW GRANTS、SHOW ROLES、SHOW USERS。
- [ ] 缺失 — EXECUTE AS。
- [ ] 缺失 — SET ROLE、SET DEFAULT ROLE。
- [ ] 缺失 — SHOW AUTHENTICATION、SHOW PROPERTY。
- [ ] 待核对 — 权限对象范围、用户/角色身份形式、认证选项和 GRANT OPTION 变体。

### 导入、导出与任务

- [x] 部分 — Broker Load、CANCEL LOAD。
- [x] 部分 — CREATE、ALTER ROUTINE LOAD。
- [x] 部分 — SUBMIT TASK。
- [x] 部分 — EXPORT、CANCEL EXPORT。
- [ ] 缺失/待核对 — ALTER LOAD、SHOW LOAD、SHOW TRANSACTION。
- [ ] 缺失 — ALTER TASK、DROP TASK。
- [ ] 缺失 — PAUSE、RESUME、STOP ROUTINE LOAD。
- [ ] 缺失 — SHOW ROUTINE LOAD、SHOW ROUTINE LOAD TASK。
- [ ] 缺失 — CREATE/ALTER/DROP PIPE。
- [ ] 缺失 — SUSPEND/RESUME PIPE、RETRY FILE、SHOW PIPES。
- [ ] 待核对/不适用 — 根据实际入口区分 Spark Load、Stream Load 是 SQL 语句还是外部协议。

### 统计信息

- [x] 部分 — ANALYZE TABLE。
- [x] 部分 — 通过 SHOW 文法支持 SHOW ANALYZE STATUS、SHOW STATS META。
- [ ] 缺失 — CREATE ANALYZE。
- [ ] 缺失 — DROP ANALYZE、DROP STATS。
- [ ] 缺失 — KILL ANALYZE。
- [ ] 缺失 — SHOW ANALYZE JOB。
- [ ] 待核对 — Histogram、FULL/SAMPLE、Predicate Column 和 PROPERTIES 变体。

### Backup 与 Restore

- [x] 部分 — BACKUP、RESTORE。
- [x] 部分 — CREATE、DROP REPOSITORY。
- [x] 部分 — RECOVER。
- [x] 部分 — 通过通用 SHOW 文法支持 SHOW BACKUP、SHOW RESTORE。
- [ ] 缺失/待核对 — CANCEL BACKUP、CANCEL RESTORE。
- [ ] 缺失/待核对 — SHOW REPOSITORIES、SHOW SNAPSHOT。
- [ ] 待核对 — 对象列表、分区、时间戳、属性和 Repository Location 分支。

### 集群管理

- [x] 部分 — SET、UNSET、KILL、SYNC、EXPLAIN、DESCRIBE。
- [x] 部分 — ADMIN SHOW Frontend/Backend/Broker 的配置或状态。
- [x] 部分 — SHOW VARIABLES、SHOW PROC、SHOW TABLE STATUS。
- [ ] 缺失 — ADMIN SET CONFIG、REFRESH CONNECTIONS。
- [ ] 缺失 — ALTER SYSTEM、CANCEL DECOMMISSION。
- [ ] 缺失 — ADD/DELETE BACKEND BLACKLIST。
- [ ] 缺失 — ADD/DELETE/SHOW SQLBLACKLIST。
- [ ] 缺失 — CREATE/DROP FILE、SHOW FILE。
- [ ] 缺失 — INSTALL/UNINSTALL PLUGIN、SHOW PLUGINS。
- [ ] 缺失/待核对 — SHOW COMPUTE NODES、SHOW PROCESSLIST、SHOW RUNNING QUERIES 和 Profile 语句。
- [ ] 缺失 — Tablet/Replica Repair、Check、Version、Status、Distribution 和 Transaction 管理命令。
- [ ] 待核对 — 逐项分类官方 `cluster-management` 目录中的所有页面。

### Prepared Statement 与事务

- [x] 部分 — BEGIN/START TRANSACTION、COMMIT、ROLLBACK。
- [ ] 缺失/待核对 — Prepared Statement 文档中的 PREPARE、EXECUTE、DEALLOCATE PREPARE。
- [ ] 待核对 — 事务修饰符以及官方记录的隔离级别和访问模式。

## 现有回归覆盖

当前场景清单包含 17 个 SQL Fixture 文件，覆盖核心查询、嵌套查询、集合与窗口查询、表 DDL、物化视图、视图、复杂类型、函数、Catalog/Resource、Load/Task、管理、安全与事务、DML 变更、Export/Analyze、Backup/Restore。

[`StarRocksParsingTest.testEveryDeclaredScenarioBuildsAnErrorFreePsiTree`](src/test/kotlin/com/jc/starrocks/datagrip/StarRocksParsingTest.kt) 要求每个已声明场景都能构造没有解析错误的 PSI 树。它只能证明清单中的 Fixture，不代表官方全量语法支持。

## 维护流程

每批语法实现应遵循以下步骤：

1. 打开对应的 StarRocks 4.1 官方语句页面。
2. 把官方记录的语法分支补充到对应清单项。
3. 在 `grammar/starrocks.bnf` 增加或扩展结构化规则，并按需在 `grammar/starrocks.flex` 增加 Token。
4. 添加针对性解析测试，包括可选子句和错误恢复。
5. 如果属于新的语法领域，增加场景 Fixture。
6. 运行 `validateGrammarSources`、`validateStarRocksScenarios` 和 `test`。
7. 只有文法和测试覆盖官方重要分支后，才将状态改成“完整”。
8. 在清单项中补充简短证据或准确的文法/测试位置。

在官方页面全部被归类为“完整、部分、缺失、待核对、不适用”，并且使用稳定的 4.1 文档快照之前，不计算整体支持百分比。

[official-statements]: https://github.com/StarRocks/starrocks/tree/main/docs/en/sql-reference/sql-statements
