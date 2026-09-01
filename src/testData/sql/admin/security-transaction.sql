CREATE USER 'etl_user' IDENTIFIED BY 'pw';
ALTER USER 'etl_user' IDENTIFIED BY 'pw2';
DROP USER 'etl_user';

CREATE ROLE analyst_role;
ALTER ROLE analyst_role SET COMMENT "read only analysts";
DROP ROLE analyst_role;

SET PASSWORD FOR 'etl_user' = PASSWORD('pw3');
GRANT SELECT_PRIV ON TABLE dws.sample_orders TO ROLE analyst_role;
REVOKE SELECT_PRIV ON TABLE dws.sample_orders FROM ROLE analyst_role;

CALL refresh_order_stats();

BEGIN;
START TRANSACTION;
COMMIT;
ROLLBACK;
