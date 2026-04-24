package org.tuchscherer.auth

import org.tuchscherer.database.User

data class RequestContext(val user: User? = null)
