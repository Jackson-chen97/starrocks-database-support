package com.jc.starrocks.datagrip.database

enum class StarRocksDataType(val sqlName: String, val family: Family) {
    BOOLEAN("BOOLEAN", Family.BOOLEAN),
    TINYINT("TINYINT", Family.INTEGER),
    SMALLINT("SMALLINT", Family.INTEGER),
    INT("INT", Family.INTEGER),
    BIGINT("BIGINT", Family.INTEGER),
    LARGEINT("LARGEINT", Family.INTEGER),
    FLOAT("FLOAT", Family.FLOATING),
    DOUBLE("DOUBLE", Family.FLOATING),
    DECIMAL32("DECIMAL32", Family.DECIMAL),
    DECIMAL64("DECIMAL64", Family.DECIMAL),
    DECIMAL128("DECIMAL128", Family.DECIMAL),
    DATE("DATE", Family.DATE_TIME),
    DATETIME("DATETIME", Family.DATE_TIME),
    TIME("TIME", Family.DATE_TIME),
    CHAR("CHAR", Family.STRING),
    VARCHAR("VARCHAR", Family.STRING),
    STRING("STRING", Family.STRING),
    VARBINARY("VARBINARY", Family.BINARY),
    JSON("JSON", Family.SEMI_STRUCTURED),
    VARIANT("VARIANT", Family.SEMI_STRUCTURED),
    ARRAY("ARRAY", Family.COMPLEX),
    MAP("MAP", Family.COMPLEX),
    STRUCT("STRUCT", Family.COMPLEX),
    BITMAP("BITMAP", Family.SKETCH),
    HLL("HLL", Family.SKETCH),
    PERCENTILE("PERCENTILE", Family.SKETCH);

    enum class Family {
        BOOLEAN,
        INTEGER,
        FLOATING,
        DECIMAL,
        DATE_TIME,
        STRING,
        BINARY,
        SEMI_STRUCTURED,
        COMPLEX,
        SKETCH
    }
}
