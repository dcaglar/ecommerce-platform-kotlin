package com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.typehandler

import org.apache.ibatis.type.BaseTypeHandler
import org.apache.ibatis.type.JdbcType
import java.sql.CallableStatement
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * MyBatis TypeHandler for converting between Instant and TIMESTAMP WITHOUT TIME ZONE.
 * 
 * This handler ensures that TIMESTAMP WITHOUT TIME ZONE values are always interpreted
 * as UTC, regardless of the JVM's default timezone. This provides deterministic
 * behavior in tests and production.
 */
class InstantTypeHandler : BaseTypeHandler<Instant>() {
    
    override fun setNonNullParameter(ps: PreparedStatement, i: Int, parameter: Instant, jdbcType: JdbcType?) {
        // Convert Instant to LocalDateTime in UTC, then to Timestamp
        // This ensures the timestamp is stored as UTC in TIMESTAMP WITHOUT TIME ZONE
        val localDateTime = LocalDateTime.ofInstant(parameter, ZoneOffset.UTC)
        ps.setTimestamp(i, Timestamp.valueOf(localDateTime))
    }

    override fun getNullableResult(rs: ResultSet, columnName: String): Instant? {
        val timestamp = rs.getTimestamp(columnName)
        return timestamp?.let { 
            // Interpret the timestamp as UTC (since it's TIMESTAMP WITHOUT TIME ZONE)
            // Convert to LocalDateTime first, then to Instant at UTC
            it.toLocalDateTime().toInstant(ZoneOffset.UTC)
        }
    }

    override fun getNullableResult(rs: ResultSet, columnIndex: Int): Instant? {
        val timestamp = rs.getTimestamp(columnIndex)
        return timestamp?.let {
            // Interpret the timestamp as UTC (since it's TIMESTAMP WITHOUT TIME ZONE)
            it.toLocalDateTime().toInstant(ZoneOffset.UTC)
        }
    }

    override fun getNullableResult(cs: CallableStatement, columnIndex: Int): Instant? {
        val timestamp = cs.getTimestamp(columnIndex)
        return timestamp?.let {
            // Interpret the timestamp as UTC (since it's TIMESTAMP WITHOUT TIME ZONE)
            it.toLocalDateTime().toInstant(ZoneOffset.UTC)
        }
    }
}

