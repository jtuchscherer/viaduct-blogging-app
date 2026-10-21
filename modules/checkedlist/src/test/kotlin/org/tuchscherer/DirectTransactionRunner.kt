package org.tuchscherer

/** Unit-test adapter: no database is involved. */
object DirectTransactionRunner : org.tuchscherer.shared.TransactionRunner {
    override fun <T> run(block: () -> T): T = block()
}
