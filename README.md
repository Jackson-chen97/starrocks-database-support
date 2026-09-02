# StarRocks Database Support

[简体中文](README.zh-CN.md)

StarRocks Database Support is a JetBrains DataGrip/IntelliJ plugin that provides an
independent StarRocks SQL dialect and StarRocks database integration.

The plugin is built on JetBrains' SQL language platform. Its parser grammar,
parser lexer, dialect catalogs, formatting extensions, and supplementary
completion are scoped to the StarRocks language.

- Syntax coverage: [`SYNTAX_COVERAGE.md`](SYNTAX_COVERAGE.md)

## Capabilities

### Data source

- Registers a dedicated StarRocks data source (STARROCKS, MySQL family) in the
  new data source dialog, with bundled driver configuration.
- Integrates JDBC metadata and the StarRocks type system with the Database
  Tools platform.

### SQL language

- Parses StarRocks SQL with a JFlex-generated parser lexer and a
  Grammar-Kit-generated parser.
- Reuses JetBrains SQL PSI element types for standard SQL structures so that
  platform formatting, references, resolution, and database-aware editing keep
  working.
- Highlights StarRocks keywords, identifiers, literals, comments, parameters,
  and operators.
- Publishes StarRocks keywords, scalar and complex types, and a broad built-in
  function catalog to the SQL platform.
- Provides supplementary completion for StarRocks-specific snippets and
  properties while leaving ordinary table, column, type, and function
  completion to the platform SQL completion system.
- Extends the platform SQL formatter with StarRocks SQL support.

### Database objects

- Object model for databases, tables, views, and materialized views, with
  native `SHOW CREATE` definition views (Go to DDL) for tables, views, and
  materialized views.
- Automatically reformats materialized-view DDL when its definition tab
  opens, with one column per line in the column list.
- Resolves table, view, and materialized-view references in SQL with
  schema-qualified search paths, eliminating incorrect "Unable to resolve"
  highlights for materialized views.
- Loads materialized-view status and displays it on the database-tree node.
- Context-menu actions for materialized views: refresh
  (`REFRESH MATERIALIZED VIEW`) and activate/deactivate
  (`ALTER MATERIALIZED VIEW ... ACTIVE/INACTIVE`).

Syntax support is intentionally tracked as structured coverage rather than a
claim of full StarRocks compatibility. The current target baseline is the
StarRocks 4.1 documentation. See the [coverage checklist](SYNTAX_COVERAGE.md)
for implemented, partial, missing, and pending areas.

## Architecture

The parser follows a generated-grammar architecture:

```text
grammar/starrocks-keywords.txt
        -> generated keyword/token registries

grammar/starrocks.flex
        -> JFlex parser lexer

grammar/starrocks.bnf
        -> Grammar-Kit parser

generated lexer + generated parser
        -> DataGrip SQL parser adapter
        -> JetBrains SQL PSI / formatter / resolve / completion
        -> StarRocks database services
```

The main design rules are:

- `grammar/starrocks.flex` is the source of truth for parser lexing.
- `grammar/starrocks.bnf` is the source of truth for parser grammar.
- `StarRocksParser` is an adapter for JetBrains SQL parser hooks; grammar logic
  belongs in the generated parser.
- Standard SQL nodes map to platform SQL PSI wherever possible.
- StarRocks-only behavior is implemented through explicit PSI mappings,
  formatter helpers, supplementary completion, and database services.
- There is no handwritten whole-statement parser fallback.

Generated source code under `build/generated` must not be edited manually.
Change the grammar or keyword catalog and run the generation tasks instead.

## Project layout

```text
grammar/
  starrocks.flex              Parser lexer grammar
  starrocks.bnf               Parser grammar and PSI element mappings
  starrocks-keywords.txt      Canonical keyword catalog

src/main/kotlin/.../
  lang/                       Parser adapter, lexer facade, element mappings
  dialect/                    Dialect identity and function catalogs
  highlight/                  Syntax highlighting
  format/                     SQL platform formatting extensions
  completion/                 StarRocks-specific supplementary completion
  database/                   DBMS, types, metadata, and DDL integration

src/test/kotlin/              Parser, PSI, resolve, formatter, and integration tests
src/testData/sql/             SQL acceptance and regression scenarios
```

## Requirements

- JDK 17
- The bundled Gradle Wrapper, currently Gradle 9.6.1
- DataGrip 2026.1 or later at runtime (`sinceBuild = 261`)
- DataGrip 2026.1.4 (`261.26222.86`) is the default development, test, and
  plugin-verification platform; it can be
  overridden with `intellijPlatformVersion` or `intellijPlatformLocalPath`

## Build and verification

Compile the plugin:

```powershell
.\gradlew.bat compileKotlin --no-daemon
```

Validate the lexer and grammar source assets:

```powershell
.\gradlew.bat validateGrammarSources --no-daemon
```

Generate the parser assets explicitly when working on grammar changes:

```powershell
.\gradlew.bat generateLexer generateParser --no-daemon
```

Run the StarRocks SQL scenario validator:

```powershell
.\gradlew.bat validateStarRocksScenarios --no-daemon
```

Run the complete verification suite:

```powershell
.\gradlew.bat check --no-daemon
```

Build the distributable plugin ZIP:

```powershell
.\gradlew.bat buildPlugin --no-daemon
```

The ZIP is written to `build/distributions/`.

## Development

Use the checked-in run configuration to start a sandbox IDE from IntelliJ IDEA
or DataGrip:

```text
.run/Run IDE with Plugin.run.xml
```

It can also be started from the command line:

```powershell
.\gradlew.bat runIde --no-daemon
```

When adding syntax support:

1. Verify the syntax against the targeted StarRocks documentation.
2. Update `starrocks.flex`, `starrocks.bnf`, or the canonical keyword catalog.
3. Add focused parser tests and a scenario fixture when introducing a new
   statement family.
4. Verify PSI mappings, formatting, and references for object-bearing syntax.
5. Update the syntax coverage checklist.
6. Run the grammar validation, scenarios, and complete test suite.

## License

Licensed under the MIT License. See [`LICENSE`](LICENSE).

This project is derived from "StarRocks Support" (https://github.com/ycyz97/starrocks-datagrip-plugin),
Copyright the original contributors, Apache License 2.0. See [`NOTICE`](NOTICE).

