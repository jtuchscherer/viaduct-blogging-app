package org.tuchscherer.shared

/** Runs a synchronous application command as one atomic unit of work. */
interface TransactionRunner {
    fun <T> run(block: () -> T): T
}
