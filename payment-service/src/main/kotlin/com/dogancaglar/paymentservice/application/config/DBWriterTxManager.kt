package com.dogancaglar.paymentservice.application.config


import org.springframework.jdbc.datasource.DataSourceTransactionManager

class DBWriterTxManager(
    dataSource: javax.sql.DataSource,
    private val stmtMs: Long,
    private val lockMs: Long,
    private val idleMs: Long?=0
) : DataSourceTransactionManager(dataSource) {

    override fun prepareTransactionalConnection(
        con: java.sql.Connection,
        definition: org.springframework.transaction.TransactionDefinition
    ) {
        // keep Spring’s default prep (isolation, read-only, etc.)
        super.prepareTransactionalConnection(con, definition)

        // transaction-local limits (rolled back with the tx; do not leak)
        con.createStatement().use { st ->
            st.execute("SET LOCAL statement_timeout = '${stmtMs}ms'")
            st.execute("SET LOCAL lock_timeout = '${lockMs}ms'")
            if(idleMs!!>0) {
                st.execute("SET LOCAL idle_in_transaction_session_timeout = '${idleMs}ms'")
            }
        }
    }
}