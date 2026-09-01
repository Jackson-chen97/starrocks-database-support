# StarRocks Parser Regression SQL

These files define parser acceptance scenarios for the StarRocks Grammar-Kit
grammar. They are not executable database tests. They are syntax and resolution
fixtures that protect generated parser, PSI, formatter, and local resolution
behavior.

| File | Milestone | Required coverage |
| --- | --- | --- |
| `query/core-query.sql` | `QUERY` | `CAST_TYPE_SYNTAX`, `QUALIFY_CLAUSE`, `WINDOW_FUNCTION`, `GROUPING_SETS` |
| `query/set-window-query.sql` | `QUERY` | `SET_OPERATION`, `NAMED_WINDOW`, `WINDOW_FUNCTION` |
| `query/nested-query.sql` | `EXPRESSIONS` | `NESTED_QUERY` |
| `ddl/create-table.sql` | `TABLE_DDL` | `STARROCKS_CREATE_TABLE`, `CREATE_TABLE_AS_SELECT`, `STARROCKS_PROPERTIES` |
| `dml/insert-local-ddl.sql` | `DML` | `STARROCKS_CREATE_TABLE`, `LOCAL_DDL_REFERENCE` |
| `ddl/materialized-view.sql` | `MATERIALIZED_VIEW` | `MATERIALIZED_VIEW`, `STARROCKS_PROPERTIES`, `AS_SELECT` |
| `ddl/view.sql` | `TABLE_DDL` | `VIEW_STATEMENT`, `AS_SELECT`, `LOCAL_DDL_REFERENCE` |
| `ddl/schema-index.sql` | `TABLE_DDL` | `SCHEMA_STATEMENT`, `INDEX_STATEMENT`, `PLATFORM_STATEMENT_ENTRYPOINTS` |
| `types/complex-types.sql` | `TYPES` | `CAST_TYPE_SYNTAX`, `COMPLEX_TYPES` |
| `functions/function-families.sql` | `FUNCTIONS` | `OFFICIAL_FUNCTIONS` |
| `admin/catalog-resource.sql` | `CATALOG_RESOURCE` | `CATALOG_STATEMENT`, `RESOURCE_STATEMENT`, `STARROCKS_PROPERTIES`, `PLATFORM_STATEMENT_ENTRYPOINTS` |
| `admin/load-task.sql` | `LOAD_EXPORT` | `LOAD_STATEMENT`, `TASK_STATEMENT`, `PLATFORM_STATEMENT_ENTRYPOINTS` |
| `admin/show-admin.sql` | `ADMINISTRATION` | `ADMIN_STATEMENT`, `PLATFORM_STATEMENT_ENTRYPOINTS` |
| `admin/security-transaction.sql` | `ADMINISTRATION` | `SECURITY_STATEMENT`, `TRANSACTION_STATEMENT`, `CALL_STATEMENT`, `PLATFORM_STATEMENT_ENTRYPOINTS` |
| `dml/mutations.sql` | `DML` | `INSERT_OVERWRITE`, `UPDATE_STATEMENT`, `DELETE_STATEMENT`, `MERGE_STATEMENT` |
| `admin/export-analyze.sql` | `TASK_ANALYZE` | `EXPORT_STATEMENT`, `ANALYZE_STATEMENT` |
| `admin/backup-restore.sql` | `BACKUP_RESTORE` | `BACKUP_RESTORE_STATEMENT`, `PLATFORM_STATEMENT_ENTRYPOINTS` |

## Acceptance Rules

- Parser support must preserve structured PSI instead of consuming whole
  statements as lenient text.
- Parser lexer support must come from `grammar/starrocks.flex`.
- Parser grammar support must come from `grammar/starrocks.bnf`.
- MySQL token substitution must not be used for StarRocks-only syntax.
