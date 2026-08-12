package org.tuchscherer.viadapp.checkedlist.resolvers

import viaduct.api.documents.FragmentFromAnnotation
import viaduct.api.documents.GraphQLFragment
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost

/**
 * Named fragments available to the resolvers in this package.
 *
 * These declarations look unused — nothing references the Kotlin symbols. They are not dead
 * code. KSP registers each `@GraphQLFragment` under the *fragment name inside the annotation*,
 * and resolvers link to it by spreading that name in their `objectValueFragment`. The Kotlin
 * identifier is deliberately kept identical to the fragment name so the two sides are greppable.
 *
 * Deleting [PostIdFragment] fails the build for every resolver that spreads it:
 *
 *     Fragment validation failed for CheckedListPostItemsResolver (CheckedListPost.items):
 *     Validation error (UndefinedFragment@[_]) : Undefined fragment 'PostIdFragment'
 *
 * That build-time check is the reason for declaring fragments this way rather than inlining the
 * selection string into each `@Resolver`: an inline string is only checked at execution time.
 */

/**
 * The only parent field the CheckedListPost field resolvers need: the post's ID, which they
 * convert to a UUID to query the checklist tables.
 *
 * Must be an `object` — KSP rejects a `class` with "@GraphQLFragment must be applied to a
 * Kotlin object declaration".
 */
@GraphQLFragment("fragment PostIdFragment on CheckedListPost { id }")
object PostIdFragment : FragmentFromAnnotation<ViaductCheckedListPost>()
