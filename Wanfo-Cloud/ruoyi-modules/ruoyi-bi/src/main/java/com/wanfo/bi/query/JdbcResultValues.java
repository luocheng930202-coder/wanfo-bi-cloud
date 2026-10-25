package com.wanfo.bi.query;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;

/** Converts JDBC temporal values to stable JSON-friendly ISO strings. */
public final class JdbcResultValues
{
    private JdbcResultValues() {}

    public static Object read(ResultSet resultSet, int index) throws SQLException
    {
        int type = resultSet.getMetaData().getColumnType(index);
        if (type == Types.DATE)
        {
            java.sql.Date value = resultSet.getDate(index);
            return value == null ? null : value.toLocalDate().toString();
        }
        if (type == Types.TIME)
        {
            Time value = resultSet.getTime(index);
            return value == null ? null : value.toLocalTime().toString();
        }
        if (type == Types.TIMESTAMP)
        {
            Timestamp value = resultSet.getTimestamp(index);
            return value == null ? null : value.toLocalDateTime().toString();
        }
        if (type == Types.TIMESTAMP_WITH_TIMEZONE)
        {
            try
            {
                OffsetDateTime value = resultSet.getObject(index, OffsetDateTime.class);
                return value == null ? null : value.toString();
            }
            catch (SQLFeatureNotSupportedException | AbstractMethodError unsupported)
            {
                Timestamp value = resultSet.getTimestamp(index);
                return value == null ? null : value.toLocalDateTime().toString();
            }
        }
        return resultSet.getObject(index);
    }
}
