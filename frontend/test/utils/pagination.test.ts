import { describe, it, expect } from 'vitest'
import { appendConnectionEdges, type RelayConnection } from '../../src/utils/pagination'

interface TestNode {
  id: string
}

function makeConnection(opts: {
  ids: string[]
  totalCount: number
  hasNextPage: boolean
  endCursor: string | null
}): RelayConnection<TestNode> {
  return {
    totalCount: opts.totalCount,
    pageInfo: { hasNextPage: opts.hasNextPage, endCursor: opts.endCursor },
    edges: opts.ids.map((id) => ({ node: { id } })),
  }
}

describe('appendConnectionEdges', () => {
  it('concatenates the edges from prev and next in order', () => {
    const prev = makeConnection({ ids: ['1', '2'], totalCount: 5, hasNextPage: true, endCursor: 'c2' })
    const next = makeConnection({ ids: ['3', '4'], totalCount: 5, hasNextPage: true, endCursor: 'c4' })

    const merged = appendConnectionEdges(prev, next)

    expect(merged.edges.map((e) => e.node.id)).toEqual(['1', '2', '3', '4'])
  })

  it('takes totalCount and pageInfo from next so the cursor advances', () => {
    const prev = makeConnection({ ids: ['1'], totalCount: 5, hasNextPage: true, endCursor: 'c1' })
    const next = makeConnection({ ids: ['2'], totalCount: 6, hasNextPage: false, endCursor: 'c2' })

    const merged = appendConnectionEdges(prev, next)

    // Fresher counts and pageInfo win — caller's "Showing X of Y" and
    // "Load More" disabled state stay honest.
    expect(merged.totalCount).toBe(6)
    expect(merged.pageInfo.endCursor).toBe('c2')
    expect(merged.pageInfo.hasNextPage).toBe(false)
  })

  it('does not mutate either input', () => {
    const prev = makeConnection({ ids: ['1'], totalCount: 2, hasNextPage: true, endCursor: 'c1' })
    const next = makeConnection({ ids: ['2'], totalCount: 2, hasNextPage: false, endCursor: 'c2' })
    const prevBefore = JSON.stringify(prev)
    const nextBefore = JSON.stringify(next)

    appendConnectionEdges(prev, next)

    expect(JSON.stringify(prev)).toBe(prevBefore)
    expect(JSON.stringify(next)).toBe(nextBefore)
  })

  it('handles an empty next page (last page already loaded)', () => {
    const prev = makeConnection({ ids: ['1', '2'], totalCount: 2, hasNextPage: false, endCursor: 'c2' })
    const next = makeConnection({ ids: [], totalCount: 2, hasNextPage: false, endCursor: 'c2' })

    const merged = appendConnectionEdges(prev, next)

    expect(merged.edges.map((e) => e.node.id)).toEqual(['1', '2'])
    expect(merged.pageInfo.hasNextPage).toBe(false)
  })
})
