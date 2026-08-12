package org.tuchscherer.viadapp.checkedlist.resolvers

import viaduct.api.documents.FragmentFromAnnotation
import viaduct.api.documents.GraphQLFragment
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost

/**
 * SPIKE: Viaduct 2.0 named fragment.
 *
 * Declares the parent selection shared by the CheckedListPost field resolvers once, as a
 * named fragment that is validated against the schema at assembly time rather than being an
 * unchecked string inside each `@Resolver` annotation.
 */
@GraphQLFragment("fragment PostId on CheckedListPost { id }")
object PostIdFragment : FragmentFromAnnotation<ViaductCheckedListPost>()
