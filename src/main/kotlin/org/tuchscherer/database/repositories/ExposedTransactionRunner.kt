package org.tuchscherer.database.repositories

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.tuchscherer.shared.TransactionRunner

/** Nested repository transactions join this command's transaction. */
class ExposedTransactionRunner : TransactionRunner {
    override fun <T> run(block: () -> T): T = transaction { block() }
}
