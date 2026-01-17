package com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.typehandler

import com.dogancaglar.paymentservice.ports.outbound.InitialRequestStatus
import org.apache.ibatis.type.BaseTypeHandler
import org.apache.ibatis.type.JdbcType
import java.sql.CallableStatement
import java.sql.PreparedStatement
import java.sql.ResultSet

class IdempotencyStatusTypeHandler : BaseTypeHandler<InitialRequestStatus>() {
    override fun setNonNullParameter(
        ps: PreparedStatement,
        i: Int,
        parameter: InitialRequestStatus,
        jdbcType: JdbcType?
    ) {
        ps.setString(i, parameter.name)
    }

    override fun getNullableResult(rs: ResultSet, columnName: String): InitialRequestStatus? {
        val value = rs.getString(columnName)
        return value?.let { InitialRequestStatus.valueOf(it) }
    }

    override fun getNullableResult(rs: ResultSet, columnIndex: Int): InitialRequestStatus? {
        val value = rs.getString(columnIndex)
        return value?.let { InitialRequestStatus.valueOf(it) }
    }

    override fun getNullableResult(cs: CallableStatement, columnIndex: Int): InitialRequestStatus? {
        val value = cs.getString(columnIndex)
        return value?.let { InitialRequestStatus.valueOf(it) }
    }
}

