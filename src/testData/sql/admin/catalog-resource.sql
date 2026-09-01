CREATE EXTERNAL CATALOG hive_catalog
COMMENT 'Hive catalog'
PROPERTIES (
    "type" = "hive",
    "hive.metastore.uris" = "thrift://localhost:9083"
);

ALTER CATALOG hive_catalog SET PROPERTIES (
    "enable_metastore_cache" = "true"
);

SHOW CREATE CATALOG hive_catalog;
SHOW CATALOGS;

CREATE RESOURCE spark_resource
PROPERTIES (
    "type" = "spark",
    "spark.master" = "yarn"
);

ALTER RESOURCE spark_resource SET PROPERTIES (
    "spark.executor.memory" = "4g"
);

SHOW RESOURCES;
