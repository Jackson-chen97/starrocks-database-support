CREATE REPOSITORY repo_s3
WITH BROKER
ON LOCATION "s3://backup/starrocks"
PROPERTIES (
    "aws.s3.region" = "us-east-1",
    "aws.s3.use_instance_profile" = "true"
);

BACKUP SNAPSHOT dws.snapshot_trade_sale
TO repo_s3
ON (
    dws_trade_sale_by_order_ri
)
PROPERTIES (
    "type" = "full"
);

RESTORE SNAPSHOT dws.snapshot_trade_sale
FROM repo_s3
ON (
    dws_trade_sale_by_order_ri
)
PROPERTIES (
    "backup_timestamp" = "2024-01-01-00-00-00"
);

SHOW BACKUP FROM dws;
SHOW RESTORE FROM dws;
DROP REPOSITORY repo_s3;
