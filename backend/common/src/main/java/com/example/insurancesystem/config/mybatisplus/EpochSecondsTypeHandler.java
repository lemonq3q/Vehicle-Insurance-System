package com.example.insurancesystem.config.mybatisplus;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/** Converts the public API's epoch-second values to MySQL datetime values. */
public class EpochSecondsTypeHandler extends BaseTypeHandler<Long> {
    private static final long MILLIS_PER_SECOND = 1000L;

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Long parameter, JdbcType jdbcType) throws SQLException {
        ps.setTimestamp(i, new Timestamp(parameter * MILLIS_PER_SECOND));
    }

    @Override
    public Long getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return seconds(rs.getTimestamp(columnName));
    }

    @Override
    public Long getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return seconds(rs.getTimestamp(columnIndex));
    }

    @Override
    public Long getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return seconds(cs.getTimestamp(columnIndex));
    }

    private Long seconds(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.getTime() / MILLIS_PER_SECOND;
    }
}
