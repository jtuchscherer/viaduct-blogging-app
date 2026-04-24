package org.tuchscherer.viadapp.resolvers

/**
 * Safe Long→Int conversion for GraphQL count fields.
 * GraphQL Int is 32-bit; repository counts are Long. Throws if the value exceeds Int.MAX_VALUE
 * so overflow is caught explicitly rather than wrapping silently.
 */
internal fun Long.toCountInt(): Int {
    require(this <= Int.MAX_VALUE) { "Count value $this exceeds GraphQL Int range (${Int.MAX_VALUE})" }
    return toInt()
}
