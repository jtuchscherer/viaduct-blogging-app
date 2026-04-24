/**
 * Helpers for Relay-style cursor pagination via Apollo's `fetchMore`.
 *
 * The merge logic — append new edges, take totalCount + pageInfo from
 * the latest page — is identical for every connection in the schema,
 * so callers should reach for `appendConnectionEdges` instead of inlining
 * the spread inside `updateQuery`.
 */

export interface RelayConnection<TNode> {
  totalCount: number;
  pageInfo: { hasNextPage: boolean; endCursor: string | null };
  edges: Array<{ node: TNode }>;
}

/**
 * Combine a previously-loaded connection with the next page returned by
 * `fetchMore`. Edges accumulate; totalCount and pageInfo come from the
 * fresher response so cursors and counts stay accurate.
 */
export function appendConnectionEdges<TConnection extends RelayConnection<unknown>>(
  prev: TConnection,
  next: TConnection,
): TConnection {
  return { ...next, edges: [...prev.edges, ...next.edges] };
}
