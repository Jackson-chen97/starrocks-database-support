package com.jc.starrocks.datagrip.lang

object StarRocksStatementWordsClassifier {
    fun classify(words: List<String>): StarRocksStatementFamily? {
        return classify { offset -> words.getOrNull(offset)?.uppercase() }
    }

    fun classify(wordAt: (Int) -> String?): StarRocksStatementFamily? {
        return when (wordAt(0)) {
            "SELECT", "WITH", "VALUES" -> StarRocksStatementFamily.QUERY
            "INSERT", "UPDATE", "DELETE", "MERGE" -> StarRocksStatementFamily.DML
            "CREATE" -> classifyCreate(wordAt)
            "ALTER" -> classifyAlter(wordAt)
            "DROP" -> classifyDrop(wordAt)
            "SHOW" -> classifyShow(wordAt)
            "LOAD" -> StarRocksStatementFamily.LOAD
            "CANCEL" -> classifyCancel(wordAt)
            "REFRESH" -> if (wordAt(1) == "MATERIALIZED" && wordAt(2) == "VIEW") {
                StarRocksStatementFamily.MATERIALIZED_VIEW
            } else {
                null
            }
            "TRUNCATE" -> if (wordAt(1) == "TABLE") StarRocksStatementFamily.TABLE_DDL else null
            "SUBMIT" -> if (wordAt(1) == "TASK") StarRocksStatementFamily.TASK else null
            "EXPORT" -> StarRocksStatementFamily.EXPORT
            "BACKUP", "RESTORE", "RECOVER" -> StarRocksStatementFamily.BACKUP_RESTORE
            "ANALYZE" -> StarRocksStatementFamily.ADMIN
            "ADMIN" -> StarRocksStatementFamily.ADMIN
            "GRANT", "REVOKE", "CALL", "BEGIN", "COMMIT", "ROLLBACK" -> StarRocksStatementFamily.ADMIN
            "START" -> if (wordAt(1) == "TRANSACTION") StarRocksStatementFamily.ADMIN else null
            "SET", "UNSET", "KILL", "SYNC" -> StarRocksStatementFamily.ADMIN
            else -> null
        }
    }

    private fun classifyCreate(wordAt: (Int) -> String?): StarRocksStatementFamily? {
        return when {
            wordAt(1) == "TABLE" -> StarRocksStatementFamily.TABLE_DDL
            wordAt(1) == "TEMPORARY" && wordAt(2) == "TABLE" -> StarRocksStatementFamily.TABLE_DDL
            wordAt(1) == "VIEW" -> StarRocksStatementFamily.VIEW
            wordAt(1) == "OR" && wordAt(2) == "REPLACE" && wordAt(3) == "VIEW" -> StarRocksStatementFamily.VIEW
            wordAt(1) == "MATERIALIZED" && wordAt(2) == "VIEW" -> {
                StarRocksStatementFamily.MATERIALIZED_VIEW
            }
            wordAt(1) == "EXTERNAL" && wordAt(2) == "CATALOG" -> StarRocksStatementFamily.CATALOG
            wordAt(1) == "CATALOG" -> StarRocksStatementFamily.CATALOG
            wordAt(1) == "RESOURCE" -> StarRocksStatementFamily.RESOURCE
            wordAt(1) == "ROUTINE" && wordAt(2) == "LOAD" -> StarRocksStatementFamily.ROUTINE_LOAD
            wordAt(1) == "REPOSITORY" -> StarRocksStatementFamily.BACKUP_RESTORE
            wordAt(1) == "USER" || wordAt(1) == "ROLE" -> StarRocksStatementFamily.ADMIN
            wordAt(1) == "DATABASE" || wordAt(1) == "SCHEMA" -> StarRocksStatementFamily.TABLE_DDL
            wordAt(1) == "INDEX" -> StarRocksStatementFamily.TABLE_DDL
            wordAt(1) == "BITMAP" && wordAt(2) == "INDEX" -> StarRocksStatementFamily.TABLE_DDL
            else -> null
        }
    }

    private fun classifyAlter(wordAt: (Int) -> String?): StarRocksStatementFamily? {
        return when (wordAt(1)) {
            "TABLE" -> StarRocksStatementFamily.TABLE_DDL
            "VIEW" -> StarRocksStatementFamily.VIEW
            "MATERIALIZED" -> if (wordAt(2) == "VIEW") {
                StarRocksStatementFamily.MATERIALIZED_VIEW
            } else {
                null
            }
            "CATALOG" -> StarRocksStatementFamily.CATALOG
            "RESOURCE" -> StarRocksStatementFamily.RESOURCE
            "ROUTINE" -> if (wordAt(2) == "LOAD") StarRocksStatementFamily.ROUTINE_LOAD else null
            "USER", "ROLE" -> StarRocksStatementFamily.ADMIN
            "DATABASE", "SCHEMA" -> StarRocksStatementFamily.TABLE_DDL
            else -> null
        }
    }

    private fun classifyDrop(wordAt: (Int) -> String?): StarRocksStatementFamily? {
        return when (wordAt(1)) {
            "TABLE" -> StarRocksStatementFamily.TABLE_DDL
            "VIEW" -> StarRocksStatementFamily.VIEW
            "MATERIALIZED" -> if (wordAt(2) == "VIEW") {
                StarRocksStatementFamily.MATERIALIZED_VIEW
            } else {
                null
            }
            "CATALOG" -> StarRocksStatementFamily.CATALOG
            "RESOURCE" -> StarRocksStatementFamily.RESOURCE
            "REPOSITORY" -> StarRocksStatementFamily.BACKUP_RESTORE
            "USER", "ROLE" -> StarRocksStatementFamily.ADMIN
            "DATABASE", "SCHEMA", "INDEX" -> StarRocksStatementFamily.TABLE_DDL
            else -> null
        }
    }

    private fun classifyShow(wordAt: (Int) -> String?): StarRocksStatementFamily? {
        return when (wordAt(1)) {
            "DATABASES",
            "TABLES",
            "PARTITIONS",
            "FRONTENDS",
            "BACKENDS",
            "VARIABLES",
            "STATUS",
            "STATS",
            "ANALYZE" -> StarRocksStatementFamily.ADMIN
            "CATALOGS" -> StarRocksStatementFamily.CATALOG
            "RESOURCES" -> StarRocksStatementFamily.RESOURCE
            "BACKUP", "RESTORE" -> StarRocksStatementFamily.BACKUP_RESTORE
            "PROC" -> StarRocksStatementFamily.ADMIN
            "MATERIALIZED" -> if (wordAt(2) == "VIEWS") {
                StarRocksStatementFamily.MATERIALIZED_VIEW
            } else {
                null
            }
            "CREATE" -> when (wordAt(2)) {
                "TABLE" -> StarRocksStatementFamily.TABLE_DDL
                "DATABASE", "SCHEMA" -> StarRocksStatementFamily.TABLE_DDL
                "VIEW" -> StarRocksStatementFamily.VIEW
                "MATERIALIZED" -> if (wordAt(3) == "VIEW") {
                    StarRocksStatementFamily.MATERIALIZED_VIEW
                } else {
                    null
                }
                "CATALOG" -> StarRocksStatementFamily.CATALOG
                else -> null
            }
            else -> null
        }
    }

    private fun classifyCancel(wordAt: (Int) -> String?): StarRocksStatementFamily? {
        return when (wordAt(1)) {
            "LOAD" -> StarRocksStatementFamily.LOAD
            "EXPORT" -> StarRocksStatementFamily.EXPORT
            "REFRESH" -> if (wordAt(2) == "MATERIALIZED" && wordAt(3) == "VIEW") {
                StarRocksStatementFamily.MATERIALIZED_VIEW
            } else {
                null
            }
            else -> null
        }
    }
}
