import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MockedProvider } from '@apollo/client/testing/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { gql } from '@apollo/client'
import PostDetailPage from '../../src/pages/PostDetailPage'
import { AuthProvider } from '../../src/contexts/AuthContext'
import type { AIHealth } from '../../src/types'

vi.mock('../../src/hooks/useAIHealth', () => ({
  useAIHealth: vi.fn(),
}))

import { useAIHealth } from '../../src/hooks/useAIHealth'

// Queries/mutations must match the component's gql documents exactly for MockedProvider matching.

const GET_NODE = gql`
  query GetNode($id: ID!) {
    node(id: $id) {
      __typename
      ... on BlogPost {
        id
        title
        content
        author { id name username }
        createdAt
        likeCount
        isLikedByMe
        viewCount
        readTimeMinutes
        comments { id content author { id name username } createdAt }
      }
      ... on CheckedListPost {
        id
        title
        description
        author { id name username }
        createdAt
        likeCount
        isLikedByMe
        viewCount
        readTimeMinutes
        items { id text checked position createdAt }
        comments { id content author { id name username } createdAt }
      }
    }
  }
`

const RECORD_POST_VIEW = gql`
  mutation RecordPostView($postId: ID!) {
    recordPostView(postId: $postId)
  }
`

const SUGGEST_CHECKLIST_ITEM = gql`
  mutation SuggestChecklistItem($existingItems: [String!]!) {
    suggestChecklistItem(existingItems: $existingItems) {
      suggestedText
    }
  }
`

const POST_ID = btoa('CheckedListPost:00000000-0000-0000-0000-000000000009')
const AUTHOR = { __typename: 'User', id: 'u1', name: 'Alice', username: 'alice' }

function mockAIHealth(reachable: boolean) {
  vi.mocked(useAIHealth).mockReturnValue({
    ollamaReachable: reachable,
    chatModel: 'llama3.2',
    embeddingModel: 'nomic-embed-text',
  } satisfies AIHealth)
}

const makeItem = (i: number) => ({
  __typename: 'CheckedListItem',
  id: `item-${i}`,
  text: `Item ${i}`,
  checked: false,
  position: i,
  createdAt: '2025-01-15T10:00:00Z',
})

const makeChecklistMock = (itemCount: number) => ({
  request: { query: GET_NODE, variables: { id: POST_ID } },
  maxUsageCount: 10,
  result: {
    data: {
      node: {
        __typename: 'CheckedListPost',
        id: POST_ID,
        title: 'My Checklist',
        description: 'A list',
        author: AUTHOR,
        createdAt: '2025-01-15T10:00:00Z',
        likeCount: 0,
        isLikedByMe: false,
        viewCount: 1,
        readTimeMinutes: 1.0,
        items: Array.from({ length: itemCount }, (_, i) => makeItem(i)),
        comments: [],
      },
    },
  },
})

const viewMock = {
  request: { query: RECORD_POST_VIEW, variables: { postId: POST_ID } },
  maxUsageCount: 5,
  result: { data: { recordPostView: true } },
}

function renderPage(mocks: unknown[]) {
  return render(
    <MockedProvider
      mocks={mocks as never}
      defaultOptions={{ watchQuery: { fetchPolicy: 'cache-first', notifyOnNetworkStatusChange: false } }}
    >
      <MemoryRouter initialEntries={[`/post/${POST_ID}`]}>
        <AuthProvider>
          <Routes>
            <Route path="/post/:id" element={<PostDetailPage />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </MockedProvider>,
  )
}

describe('PostDetailPage — checklist suggest button', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // The suggest controls only render for the post author.
    localStorage.setItem('authToken', 'test-token')
    localStorage.setItem('authUser', JSON.stringify({ username: 'alice', name: 'Alice', isAdmin: false }))
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('is not rendered when Ollama is offline', async () => {
    mockAIHealth(false)
    renderPage([makeChecklistMock(3), viewMock])

    await screen.findByText('My Checklist')
    expect(screen.queryByRole('button', { name: /suggest/i })).not.toBeInTheDocument()
  })

  it('is disabled with fewer than 3 items and explains why', async () => {
    mockAIHealth(true)
    renderPage([makeChecklistMock(2), viewMock])

    await screen.findByText('My Checklist')
    const button = await screen.findByRole('button', { name: /suggest/i })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('title', expect.stringContaining('at least 3 items'))
  })

  it('is enabled once 3 or more items exist', async () => {
    mockAIHealth(true)
    renderPage([makeChecklistMock(3), viewMock])

    await screen.findByText('My Checklist')
    expect(await screen.findByRole('button', { name: /suggest/i })).toBeEnabled()
  })

  it('fills the new-item input with the returned suggestion on click', async () => {
    mockAIHealth(true)
    const suggestMock = {
      request: {
        query: SUGGEST_CHECKLIST_ITEM,
        variables: { existingItems: ['Item 0', 'Item 1', 'Item 2'] },
      },
      result: {
        data: {
          suggestChecklistItem: {
            __typename: 'SuggestedChecklistItem',
            suggestedText: 'Item 3 (AI)',
          },
        },
      },
    }
    renderPage([makeChecklistMock(3), viewMock, suggestMock])

    await screen.findByText('My Checklist')
    fireEvent.click(await screen.findByRole('button', { name: /suggest/i }))

    await waitFor(() => {
      expect(screen.getByPlaceholderText('Add a new item…')).toHaveValue('Item 3 (AI)')
    })
  })
})
