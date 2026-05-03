package org.tuchscherer.viadapp.checkedlist.resolvers

import org.tuchscherer.checkedlist.port.PostData
import org.tuchscherer.checkedlist.repositories.CheckedListItemData
import viaduct.api.context.ExecutionContext
import viaduct.api.grts.CheckedListItem as ViaductCheckedListItem
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost

/**
 * Safe Long→Int conversion for GraphQL count fields.
 * GraphQL Int is 32-bit; repository counts are Long. Throws explicitly if the value
 * exceeds Int.MAX_VALUE so overflow is caught rather than wrapping silently.
 * Mirrors [org.tuchscherer.viadapp.resolvers.toCountInt] in the root project.
 */
internal fun Long.toCountInt(): Int {
    require(this <= Int.MAX_VALUE) { "Count value $this exceeds GraphQL Int range (${Int.MAX_VALUE})" }
    return toInt()
}

/**
 * Builds a [ViaductCheckedListPost] from [PostData] scalar fields.
 * Used wherever the same four scalar fields (id, title, createdAt, updatedAt) need
 * to be populated: the node batch resolver, the list query resolver, and the create mutation.
 */
internal fun PostData.toViaductPost(ctx: ExecutionContext): ViaductCheckedListPost {
    val d = this
    return ViaductCheckedListPost.of(ctx) {
        id(ctx.globalIDFor(ViaductCheckedListPost.Reflection, d.id.toString()))
        title(d.title)
        createdAt(d.createdAt)
        updatedAt(d.updatedAt)
    }
}

/**
 * Builds a [ViaductCheckedListItem] from [CheckedListItemData].
 * Used in the items field resolver, addCheckedListItem, and toggleCheckedListItem.
 */
internal fun CheckedListItemData.toViaductItem(ctx: ExecutionContext): ViaductCheckedListItem {
    val d = this
    return ViaductCheckedListItem.of(ctx) {
        id(ctx.globalIDFor(ViaductCheckedListItem.Reflection, d.id.toString()))
        text(d.text)
        checked(d.checked)
        position(d.position)
        createdAt(d.createdAt)
    }
}
