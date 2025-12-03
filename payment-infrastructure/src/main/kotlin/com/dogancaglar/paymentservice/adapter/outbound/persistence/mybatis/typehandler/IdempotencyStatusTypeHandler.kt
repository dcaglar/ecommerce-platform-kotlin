package com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.typehandler

import com.dogancaglar.paymentservice.ports.outbound.IdempotencyStatus
import org.apache.ibatis.type.BaseTypeHandler
import org.apache.ibatis.type.JdbcType
import java.sql.CallableStatement
import java.sql.PreparedStatement
import java.sql.ResultSet

class IdempotencyStatusTypeHandler : BaseTypeHandler<IdempotencyStatus>() {
    override fun setNonNullParameter(
        ps: PreparedStatement,
        i: Int,
        parameter: IdempotencyStatus,
        jdbcType: JdbcType?
    ) {
        ps.setString(i, parameter.name)
    }

    override fun getNullableResult(rs: ResultSet, columnName: String): IdempotencyStatus? {
        val value = rs.getString(columnName)
        return value?.let { IdempotencyStatus.valueOf(it) }
    }

    override fun getNullableResult(rs: ResultSet, columnIndex: Int): IdempotencyStatus? {
        val value = rs.getString(columnIndex)
        return value?.let { IdempotencyStatus.valueOf(it) }
    }

    override fun getNullableResult(cs: CallableStatement, columnIndex: Int): IdempotencyStatus? {
        val value = cs.getString(columnIndex)
        return value?.let { IdempotencyStatus.valueOf(it) }
    }
}

