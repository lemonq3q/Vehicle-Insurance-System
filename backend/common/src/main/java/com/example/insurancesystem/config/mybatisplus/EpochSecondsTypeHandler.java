package com.example.insurancesystem.config.mybatisplus;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * 在接口使用的 Unix 秒级时间戳与 MySQL DATETIME/TIMESTAMP 字段之间完成双向转换。
 * 写入时将秒换算为 JDBC 毫秒时间，读取时再恢复为秒，保证前端时间协议与数据库字段类型解耦。
 */
public class EpochSecondsTypeHandler extends BaseTypeHandler<Long> {
    private static final long MILLIS_PER_SECOND = 1000L;

    @Override
    /**
     * 将业务 Long 秒级时间戳扩大为 JDBC Timestamp 所需毫秒值后写入预编译参数。
     */
    public void setNonNullParameter(PreparedStatement ps, int i, Long parameter, JdbcType jdbcType) throws SQLException {
        ps.setTimestamp(i, new Timestamp(parameter * MILLIS_PER_SECOND));
    }

    @Override
    /**
     * 按列名读取时间字段并转换为秒级时间戳，数据库 NULL 保持为 Java null。
     */
    public Long getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return seconds(rs.getTimestamp(columnName));
    }

    @Override
    /**
     * 按列序号读取普通查询结果中的时间字段并转换为秒。
     */
    public Long getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return seconds(rs.getTimestamp(columnIndex));
    }

    @Override
    /**
     * 从存储过程输出参数读取时间字段并转换为秒。
     */
    public Long getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return seconds(cs.getTimestamp(columnIndex));
    }

    /**
     * 集中处理 Timestamp 到 Unix 秒的转换，避免三个读取入口出现不同的空值或单位逻辑。
     */
    private Long seconds(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.getTime() / MILLIS_PER_SECOND;
    }
}
