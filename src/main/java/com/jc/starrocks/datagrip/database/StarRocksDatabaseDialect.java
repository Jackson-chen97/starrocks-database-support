package com.jc.starrocks.datagrip.database;

import com.intellij.database.Dbms;
import com.intellij.database.dataSource.DatabaseConnectionCore;
import com.intellij.database.dialects.DatabaseDialect;
import com.intellij.database.dialects.base.AbstractDatabaseDialect;
import com.intellij.database.dialects.base.TypeHelper;
import com.intellij.database.model.DasObject;
import com.intellij.database.model.ObjectKind;
import com.intellij.database.remote.jdbc.RemoteResultSet;
import com.intellij.database.remote.jdbc.RemoteStatement;
import com.intellij.database.util.DasUtil;
import com.intellij.database.util.DdlBuilder;
import com.intellij.database.util.ObjectPath;
import com.intellij.database.util.SearchPath;
import com.intellij.openapi.progress.ProcessCanceledException;

import java.sql.SQLException;

/** Database Tools behavior needed to preserve the StarRocks connection namespace. */
public final class StarRocksDatabaseDialect extends AbstractDatabaseDialect {
    static final String CURRENT_CATALOG_QUERY = "SELECT CATALOG()";
    static final String CURRENT_DATABASE_QUERY = "SELECT DATABASE()";

    public StarRocksDatabaseDialect() {
        super(new TypeHelper() {
            @Override
            public String getTypeName(int jdbcType, int length, int precision, int scale) {
                return null;
            }
        });
    }

    @Override
    public Dbms getDbms() {
        return StarRocksDbms.INSTANCE;
    }

    @Override
    public String getDisplayName() {
        return "Generic SQL";
    }

    @Override
    public ObjectKind getSearchPathObjectKind() {
        return null;
    }

    @Override
    public boolean similarTo(DatabaseDialect dialect) {
        return dialect instanceof StarRocksDatabaseDialect;
    }

    @Override
    public DdlBuilder qualifiedIdentifier(
        DdlBuilder builder,
        String name,
        DasObject parent,
        DasObject object
    ) {
        DasObject schema = DasUtil.getSchemaObject(object);
        return builder.qualifiedRef(
            parent,
            name,
            schema,
            DasUtil.getName(schema),
            null,
            null,
            null,
            null
        );
    }

    @Override
    public boolean supportsCommonTableExpression() {
        return false;
    }

    @Override
    public SearchPath tryToLoadSearchPath(DatabaseConnectionCore connection) throws SQLException {
        try {
            return loadSearchPath(sql -> queryScalar(connection, sql));
        }
        catch (ProcessCanceledException error) {
            throw error;
        }
        catch (Exception error) {
            if (error instanceof SQLException sqlError) throw sqlError;
            throw new SQLException("Unable to read the current StarRocks catalog and database", error);
        }
    }

    @Override
    public boolean shouldSwitchThroughJdbc(ObjectKind kind) {
        return true;
    }

    static SearchPath loadSearchPath(ScalarQuery query) throws Exception {
        String catalog = query.execute(CURRENT_CATALOG_QUERY);
        if (catalog == null || catalog.isBlank()) return null;

        ObjectPath catalogPath = ObjectPath.create(catalog, ObjectKind.DATABASE);
        String database = query.execute(CURRENT_DATABASE_QUERY);
        if (database == null || database.isBlank()) {
            return SearchPath.of(catalogPath);
        }
        return SearchPath.of(catalogPath.append(database, ObjectKind.SCHEMA));
    }

    private static String queryScalar(DatabaseConnectionCore connection, String sql) throws Exception {
        RemoteStatement statement = null;
        RemoteResultSet resultSet = null;
        try {
            statement = connection.getRemoteConnection().createStatement();
            resultSet = statement.executeQuery(sql);
            return resultSet.next() ? resultSet.getString(1) : null;
        }
        finally {
            close(resultSet);
            close(statement);
        }
    }

    private static void close(RemoteResultSet resultSet) {
        if (resultSet == null) return;
        try {
            resultSet.close();
        }
        catch (Exception ignored) {
        }
    }

    private static void close(RemoteStatement statement) {
        if (statement == null) return;
        try {
            statement.close();
        }
        catch (Exception ignored) {
        }
    }

    @FunctionalInterface
    interface ScalarQuery {
        String execute(String sql) throws Exception;
    }
}
