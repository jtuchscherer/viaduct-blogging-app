package org.tuchscherer.database.repositories

/** Database-wide persistence operations used during startup and readiness checks. */
interface DatabaseMaintenanceRepository {
    fun initializeSchema()
    fun healthCheck(): Boolean
}
