// Helpers for Relay-style cursor pagination via Apollo's fetchMore.

export interface RelayConnection<TNode> {
  totalCount: number;
  pageInfo: { hasNextPage: boolean; endCursor: string | null };
  edges: Array<{ node: TNode }>;
}

/** Append next-page edges to prev; pageInfo and totalCount come from next. */
export function appendConnectionEdges<TConnection extends RelayConnection<unknown>>(
  prev: TConnection,
  next: TConnection,
): TConnection {
  return { ...next, edges: [...prev.edges, ...next.edges] };
}
