package org.tuchscherer.viadapp.resolvers

/**
 * Server-side validation for post inputs. The GraphQL API is a public
 * contract — every mutation that accepts a title or content must run
 * the same checks regardless of who's calling (regular user create,
 * regular user update, admin update). Centralising the rules here
 * keeps them in lockstep.
 *
 * `validateTitle` and `validateContent` throw `IllegalArgumentException`
 * via `require`, which Viaduct surfaces as a GraphQL error to the client.
 */
object PostValidation {
    const val MAX_TITLE_LENGTH = 500
    const val MAX_CONTENT_LENGTH = 100_000

    // Pre-formatted with thousands separator so error messages remain
    // human-readable ("100,000" not "100000"). The e2e validation suite
    // matches against the comma-formatted version.
    private val MAX_TITLE_LENGTH_DISPLAY = "%,d".format(MAX_TITLE_LENGTH)
    private val MAX_CONTENT_LENGTH_DISPLAY = "%,d".format(MAX_CONTENT_LENGTH)

    fun validateTitle(title: String) {
        require(title.isNotBlank()) { "Title cannot be blank" }
        require(title.length <= MAX_TITLE_LENGTH) {
            "Title cannot exceed $MAX_TITLE_LENGTH_DISPLAY characters"
        }
    }

    fun validateContent(content: String) {
        require(content.isNotBlank()) { "Content cannot be blank" }
        require(content.length <= MAX_CONTENT_LENGTH) {
            "Content cannot exceed $MAX_CONTENT_LENGTH_DISPLAY characters"
        }
    }
}
