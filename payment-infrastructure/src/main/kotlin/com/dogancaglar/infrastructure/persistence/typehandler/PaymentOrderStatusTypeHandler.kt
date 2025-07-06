package com.dogancaglar.infrastructure.persistence.typehandler

import com.dogancaglar.payment.domain.model.PaymentOrderStatus
import org.apache.ibatis.type.BaseTypeHandler
import org.apache.ibatis.type.JdbcType
import java.sql.CallableStatement
import java.sql.PreparedStatement
import java.sql.ResultSet

class PaymentOrderStatusTypeHandler : BaseTypeHandler<PaymentOrderStatus>() {
    override fun setNonNullParameter(
        ps: PreparedStatement,
        i: Int,
        parameter: PaymentOrderStatus,
        jdbcType: JdbcType?
    ) {
        ps.setString(i, parameter.name)
    }

    override fun getNullableResult(rs: ResultSet, columnName: String): PaymentOrderStatus? {
        val value = rs.getString(columnName)
        return value?.let { PaymentOrderStatus.valueOf(it) }
    }

    override fun getNullableResult(rs: ResultSet, columnIndex: Int): PaymentOrderStatus? {
        val value = rs.getString(columnIndex)
        return value?.let { PaymentOrderStatus.valueOf(it) }
    }

    override fun getNullableResult(cs: CallableStatement, columnIndex: Int): PaymentOrderStatus? {
        val value = cs.getString(columnIndex)
        return value?.let { PaymentOrderStatus.valueOf(it) }
    }
}

