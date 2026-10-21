package org.tuchscherer.checkedlist

import org.tuchscherer.auth.PostVisibility
import org.tuchscherer.checkedlist.port.CheckedListCurrentUserProvider
import org.tuchscherer.checkedlist.port.PostData

/** Apply the same publication policy to node reads as to the core blog tenant. */
fun PostData.isVisibleTo(requestContext: Any?, users: CheckedListCurrentUserProvider): Boolean {
    if (PostVisibility.canView(status, authorId, null)) return true
    val viewer = users.getViewer(requestContext)
    return PostVisibility.canView(status, authorId, viewer?.id, viewer?.isAdmin ?: false)
}
