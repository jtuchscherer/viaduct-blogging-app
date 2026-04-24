package org.tuchscherer.auth

class AuthenticationException(message: String) : Exception(message)
class NotFoundException(message: String) : Exception(message)
class AuthorizationException(message: String) : Exception(message)