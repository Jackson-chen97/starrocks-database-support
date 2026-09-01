# StarRocks SQL Syntax Coverage

中文版：[`SYNTAX_COVERAGE.zh-CN.md`](SYNTAX_COVERAGE.zh-CN.md)

This document tracks the StarRocks SQL syntax implemented by the plugin. It is
intended as a working checklist rather than a claim of full compatibility.

## Baseline and status definitions

- Target baseline: StarRocks 4.1, as declared by the project README.
- Official source: [StarRocks SQL statements documentation][official-statements].
- Documentation snapshot reviewed: 2026-07-16, from the official repository's
  `main/docs/en/sql-reference/sql-statements` tree.
- Grammar source of truth: [`grammar/starrocks.bnf`](grammar/starrocks.bnf).
- Parser lexer source of truth: [`grammar/starrocks.flex`](grammar/starrocks.flex).
- Regression scenarios: [`src/testData/sql`](src/testData/sql).

Because the official documentation repository can change after this snapshot,
each item must be checked against the actual StarRocks 4.1 documentation before
being marked complete.

Status meanings:

| Status | Meaning |
| --- | --- |
| Complete | The documented statement and its important branches have structured grammar and parser tests. |
| Partial | A statement entry exists, but documented clauses or variants are missing or simplified. |
| Missing | No corresponding top-level grammar entry was found. |
| Review | The mapping or version applicability still needs manual confirmation. |
| N/A | The page is not a SQL parser entry, for example an HTTP loading API or a concept page. |

An item should only move to **Complete** when all of the following are present:

- a structured BNF rule without a whole-statement fallback;
- lexer tokens for StarRocks-specific syntax;
- at least one positive parser test with no PSI errors;
- tests for important optional branches and ambiguous prefixes;
- formatter and PSI/reference checks when the statement exposes SQL objects.

## Current summary

The plugin currently provides strong coverage for core queries, common table
DDL/DML, views, materialized views, catalogs, and resources. Coverage is much
less complete for cluster administration, statistics jobs, loading lifecycle
commands, and newer managed object families.

The official directory contained 196 Markdown pages at the review snapshot.
This is not a denominator for a compatibility percentage: it includes SELECT
subclauses, feature pages, and non-SQL entry points as well as standalone SQL
statements.

| Area | Current assessment | Main evidence or limitation |
| --- | --- | --- |
| SELECT and expressions | Partial, high coverage | CTE, joins, subqueries, set operations, windows, `QUALIFY`, and grouping sets have regression coverage. `OFFSET`, `PIVOT`, and `SELECT * EXCLUDE` need review or implementation. |
| Table DDL | Partial, high coverage | CREATE, DROP, TRUNCATE, CTAS, LIKE, partitioning, distribution, indexes, common table properties, and the documented `ALTER TABLE` action families are modeled. |
| DML | Partial, high coverage | INSERT, UPDATE, DELETE, and MERGE have structured entries and scenarios; documented variants still require branch-by-branch comparison. |
| Views | Partial | CREATE, ALTER, DROP, and SHOW CREATE are represented; official optional clauses require verification. |
| Materialized views | Partial | CREATE, ALTER, DROP, REFRESH, and SHOW forms are partly covered; cancellation and status-management variants remain. |
| Databases and schemas | Partial | CREATE, ALTER, DROP, USE, and common SHOW forms exist; SHOW DATA and SHOW CREATE DATABASE require work. |
| Catalogs | Partial | CREATE, ALTER, DROP, SHOW CATALOGS, and SHOW CREATE CATALOG exist; SET CATALOG is missing. |
| Resources | Partial | CREATE, ALTER, DROP, and SHOW RESOURCES exist; documented variants need comparison. |
| Accounts and privileges | Partial | Common user/role DDL, GRANT, REVOKE, and SET PASSWORD exist; role switching, impersonation, and several SHOW forms are missing. |
| Backup and restore | Partial | BACKUP, RESTORE, repository operations, and RECOVER have simplified grammar. |
| Loading, export, and tasks | Partial | Broker Load, CANCEL LOAD, Routine Load, SUBMIT TASK, and EXPORT are partly modeled. Lifecycle and Pipe commands are mostly absent. |
| Statistics | Partial, low coverage | ANALYZE TABLE and selected SHOW forms exist. Analyze-job lifecycle statements are mostly absent. |
| Cluster administration | Partial, low coverage | SET, KILL, SYNC, EXPLAIN, SHOW PROC, and a small ADMIN SHOW subset exist. Most official cluster-management commands are absent. |
| Function DDL | Missing | Function invocation/catalog support does not imply CREATE/DROP FUNCTION syntax support. |
| Pipes and dictionaries | Missing | No top-level grammar families were found. |
| Storage volumes and resource groups | Missing | No top-level grammar families were found. |

## Detailed tracking checklist

### Query and expressions

- [x] Partial — SELECT core clauses.
- [x] Partial — CTE, including recursive syntax acceptance.
- [x] Partial — JOIN, derived tables, lateral UNNEST, semi join, and anti join.
- [x] Partial — UNION, INTERSECT, EXCEPT, and MINUS.
- [x] Partial — Window definitions, analytic functions, and frames.
- [x] Partial — QUALIFY and GROUPING SETS/ROLLUP/CUBE.
- [x] Partial — Subqueries, CASE, CAST, IN, BETWEEN, LIKE, REGEXP, arrays, and complex types.
- [ ] Missing/review — `SELECT ... OFFSET`.
- [ ] Missing/review — `PIVOT`.
- [ ] Missing/review — `SELECT * EXCLUDE (...)`.
- [ ] Missing/review — `TRANSLATE TRINO`.
- [ ] Review — compare every documented SELECT modifier and expression operator.

### Tables, indexes, and DML

- [x] Partial — CREATE TABLE, CTAS, and CREATE TABLE LIKE.
- [x] Partial — OLAP/external engine variants currently listed by the grammar.
- [x] Partial — key models, partitions, distribution, buckets, rollups, indexes, generated columns, and properties.
- [x] Partial — DROP TABLE, TRUNCATE TABLE, CREATE INDEX, and DROP INDEX.
- [x] Partial — INSERT, INSERT OVERWRITE, UPDATE, DELETE, and MERGE.
- [x] Substantial — ALTER TABLE. Covers comma-separated actions; rename and comments; partition and bucket operations; column, STRUCT field, Rollup, and index changes; table properties; SWAP; compaction; and persistent-index removal.
- [ ] Missing/review — CANCEL ALTER TABLE.
- [ ] Missing/review — REFRESH EXTERNAL TABLE.
- [ ] Missing/review — SHOW ALTER, SHOW DELETE, SHOW DYNAMIC PARTITION TABLES, SHOW FULL COLUMNS, SHOW INDEX, and SHOW TABLET.
- [ ] Review — compare all partition, rollup, index, and property mutation branches.

### Databases, catalogs, views, and materialized views

- [x] Partial — CREATE/ALTER/DROP DATABASE or SCHEMA and USE.
- [ ] Missing/review — SHOW CREATE DATABASE and SHOW DATA.
- [x] Partial — CREATE/ALTER/DROP CATALOG, SHOW CATALOGS, and SHOW CREATE CATALOG.
- [ ] Missing — SET CATALOG.
- [x] Partial — CREATE/ALTER/DROP VIEW and SHOW CREATE VIEW.
- [x] Partial — CREATE/ALTER/DROP/REFRESH MATERIALIZED VIEW and common SHOW forms.
- [ ] Missing/review — CANCEL REFRESH MATERIALIZED VIEW.
- [ ] Missing/review — SHOW ALTER MATERIALIZED VIEW.
- [ ] Review — compare every documented materialized-view refresh, partition, distribution, and property variant.

### Functions, resources, storage, and dictionaries

- [ ] Missing — CREATE FUNCTION.
- [ ] Missing — DROP FUNCTION.
- [ ] Missing — SHOW CREATE FUNCTION.
- [x] Partial — SHOW FUNCTIONS through the generic SHOW grammar.
- [x] Partial — CREATE/ALTER/DROP RESOURCE and SHOW RESOURCES.
- [ ] Missing — CREATE/ALTER/DROP RESOURCE GROUP.
- [ ] Missing — SHOW RESOURCE GROUP and SHOW USAGE RESOURCE GROUPS.
- [ ] Missing — CREATE/ALTER/DROP STORAGE VOLUME.
- [ ] Missing — SET DEFAULT STORAGE VOLUME, DESC STORAGE VOLUME, and SHOW STORAGE VOLUMES.
- [ ] Missing — CREATE/DROP/REFRESH DICTIONARY.
- [ ] Missing — CANCEL REFRESH DICTIONARY and SHOW DICTIONARY.

### Accounts and privileges

- [x] Partial — CREATE/ALTER/DROP USER.
- [x] Partial — CREATE/ALTER/DROP ROLE.
- [x] Partial — GRANT and REVOKE.
- [x] Partial — SET PASSWORD.
- [x] Partial — SHOW GRANTS, SHOW ROLES, and SHOW USERS through generic SHOW forms.
- [ ] Missing — EXECUTE AS.
- [ ] Missing — SET ROLE and SET DEFAULT ROLE.
- [ ] Missing — SHOW AUTHENTICATION and SHOW PROPERTY.
- [ ] Review — privilege object scopes, role/user identity forms, authentication options, and grant-option variants.

### Loading, unloading, and tasks

- [x] Partial — Broker Load and CANCEL LOAD.
- [x] Partial — CREATE and ALTER ROUTINE LOAD.
- [x] Partial — SUBMIT TASK.
- [x] Partial — EXPORT and CANCEL EXPORT.
- [ ] Missing/review — ALTER LOAD, SHOW LOAD, and SHOW TRANSACTION.
- [ ] Missing — ALTER TASK and DROP TASK.
- [ ] Missing — PAUSE, RESUME, and STOP ROUTINE LOAD.
- [ ] Missing — SHOW ROUTINE LOAD and SHOW ROUTINE LOAD TASK.
- [ ] Missing — CREATE/ALTER/DROP PIPE.
- [ ] Missing — SUSPEND/RESUME PIPE, RETRY FILE, and SHOW PIPES.
- [ ] Review/N/A — Spark Load and Stream Load pages must be classified by actual SQL entry point versus external protocol.

### Statistics

- [x] Partial — ANALYZE TABLE.
- [x] Partial — SHOW ANALYZE STATUS and SHOW STATS META through SHOW grammar.
- [ ] Missing — CREATE ANALYZE.
- [ ] Missing — DROP ANALYZE and DROP STATS.
- [ ] Missing — KILL ANALYZE.
- [ ] Missing — SHOW ANALYZE JOB.
- [ ] Review — histogram, full/sample, predicate-column, and properties variants.

### Backup and restore

- [x] Partial — BACKUP and RESTORE.
- [x] Partial — CREATE and DROP REPOSITORY.
- [x] Partial — RECOVER.
- [x] Partial — SHOW BACKUP and SHOW RESTORE through generic SHOW grammar.
- [ ] Missing/review — CANCEL BACKUP and CANCEL RESTORE.
- [ ] Missing/review — SHOW REPOSITORIES and SHOW SNAPSHOT.
- [ ] Review — object lists, partitions, timestamps, properties, and repository-location branches.

### Cluster administration

- [x] Partial — SET, UNSET, KILL, SYNC, EXPLAIN, and DESCRIBE.
- [x] Partial — ADMIN SHOW frontend/backend/broker config or status.
- [x] Partial — SHOW VARIABLES, SHOW PROC, and SHOW TABLE STATUS.
- [ ] Missing — ADMIN SET CONFIG and REFRESH CONNECTIONS.
- [ ] Missing — ALTER SYSTEM and CANCEL DECOMMISSION.
- [ ] Missing — ADD/DELETE BACKEND BLACKLIST.
- [ ] Missing — ADD/DELETE/SHOW SQLBLACKLIST.
- [ ] Missing — CREATE/DROP FILE and SHOW FILE.
- [ ] Missing — INSTALL/UNINSTALL PLUGIN and SHOW PLUGINS.
- [ ] Missing/review — SHOW COMPUTE NODES, SHOW PROCESSLIST, SHOW RUNNING QUERIES, and profile statements.
- [ ] Missing — tablet/replica repair, check, version, status, distribution, and transaction administration commands.
- [ ] Review — classify every page under the official `cluster-management` directory.

### Prepared statements and transactions

- [x] Partial — BEGIN/START TRANSACTION, COMMIT, and ROLLBACK.
- [ ] Missing/review — PREPARE, EXECUTE, and DEALLOCATE PREPARE from the prepared-statement documentation.
- [ ] Review — transaction modifiers and documented isolation/access modes.

## Existing regression coverage

The current scenario manifest covers 17 SQL fixture files and includes core
query, nested query, set/window query, table DDL, materialized views, views,
complex types, functions, catalogs/resources, load/tasks, administration,
security/transactions, DML mutations, export/analyze, and backup/restore.

[`StarRocksParsingTest.testEveryDeclaredScenarioBuildsAnErrorFreePsiTree`](src/test/kotlin/com/jc/starrocks/datagrip/StarRocksParsingTest.kt)
requires each declared scenario to build a PSI tree without parser errors. This
proves the declared fixtures, not full official syntax coverage.

## Maintenance workflow

For each implementation batch:

1. Open the corresponding official StarRocks 4.1 statement page.
2. Record the documented syntax branches in the relevant checklist item.
3. Add or extend structured rules in `grammar/starrocks.bnf` and tokens in
   `grammar/starrocks.flex` where required.
4. Add focused parser tests, including optional clauses and error recovery.
5. Add a scenario fixture when the statement represents a new coverage family.
6. Run `validateGrammarSources`, `validateStarRocksScenarios`, and `test`.
7. Change an item to Complete only after the grammar and tests cover the
   documented important branches.
8. Add a short evidence note or link to the exact grammar/test location.

Do not calculate an overall percentage until every official page has been
classified as Complete, Partial, Missing, Review, or N/A using a stable 4.1
documentation snapshot.

[official-statements]: https://github.com/StarRocks/starrocks/tree/main/docs/en/sql-reference/sql-statements
