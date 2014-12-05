package liquibase.statement.prepared.database;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import liquibase.change.ColumnConfig;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.statement.prepared.AbstractPreparedStatement;
import liquibase.statement.prepared.InsertExecutablePreparedStatementChange;

public class MysqlUpsertExecutablePreparedStatement extends AbstractPreparedStatement {

	private final Database database;
	private final InsertExecutablePreparedStatementChange change;

	private Info statement;
	private final List<ColumnConfig> cols = new ArrayList<ColumnConfig>();

	public MysqlUpsertExecutablePreparedStatement(Database database,
			InsertExecutablePreparedStatementChange change) {
		this.database = database;
		this.change = change;
	}

	public Info getStatement() {
		if (statement != null) {
			return statement;
		}

		String tableName = database.escapeTableName(change.getCatalogName(),
				change.getSchemaName(), change.getTableName());

		StringBuilder insertColumnSql = new StringBuilder();
		StringBuilder insertValueSql = new StringBuilder();
		StringBuilder updateSql = new StringBuilder();

		List<String> primaryKeys = getPrimaryKey(change.getPrimaryKey());
		for (ColumnConfig column : change.getColumns()) {
			if (database.supportsAutoIncrement()
					&& Boolean.TRUE.equals(column.isAutoIncrement())) {
				continue;
			}

			String columnName = database.escapeColumnName(
					change.getCatalogName(), change.getSchemaName(),
					change.getTableName(), column.getName());

			insertColumnSql.append(columnName).append(",");

			boolean pkcloum = primaryKeys.contains(columnName);
			if (column.getValueObject() == null
					&& column.getValueBlobFile() == null
					&& column.getValueClobFile() == null) {
				insertValueSql.append("null,");
				if (!pkcloum) {
					updateSql.append(columnName + "=null,");
				}
			} else if (column.getValueComputed() != null) {
				String value = column.getValueComputed().getValue();
				insertValueSql.append(value + ",");
				if (!pkcloum) {
					updateSql.append(columnName + "=values(" + value + "),");
				}
			} else {
				cols.add(column);

				insertValueSql.append("?,");
				if (!pkcloum) {
					updateSql.append(columnName + "=values(" + columnName + "),");
				}
			}
		}

		insertColumnSql.deleteCharAt(insertColumnSql.lastIndexOf(","));
		insertValueSql.deleteCharAt(insertValueSql.lastIndexOf(","));
		updateSql.deleteCharAt(updateSql.lastIndexOf(","));

		StringBuilder mergeSql = new StringBuilder();
		mergeSql.append("insert into " + tableName + "(" + insertColumnSql + ")");
		mergeSql.append(" values(" + insertValueSql + ") ");
		mergeSql.append(" on duplicate key update " + updateSql);

		String s = mergeSql.toString();
		statement = new Info(s, cols, getParameters(cols, change.getChangeSet()
				.getFilePath()));
		return statement;
	}

	@Override
	public void setParameter(PreparedStatement stmt) throws DatabaseException {
		try {
			setParameter(stmt, 1, cols, change.getChangeSet().getChangeLog()
					.getPhysicalFilePath(), change.getResourceAccessor());
		} catch (SQLException e) {
			throw new DatabaseException(e);
		}
	}
}
