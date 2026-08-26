import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MockedProvider } from '@apollo/client/testing/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { gql } from '@apollo/client'
import PostDetailPage from '../../src/pages/PostDetailPage'
import { AuthProvider } from '../../src/contexts/AuthContext'

/**
 * The post detail page in the presence of drafts (Phase 28).
 *
 * Two things matter here. A draft its reader is entitled to see must say so, otherwise the author
 * cannot tell a draft from a live post. And a draft the reader is *not* entitled to see arrives as
 * a not-found error, which must read as "Post not found" rather than as a raw exception string —
 * the same thing a reader would see for a post that never existed.
 */

const GET_NODE = gql`
  query GetNode($id: ID!) {
    node(id: $id) {
      __typename
      ... on BlogPost {
        id
        title
        content
        status
        publishedAt
        author {
          id
          name
          username
        }
        createdAt
        likeCount
        isLikedByMe
        viewCount
        readTimeMinutes
        comments {
          id
          content
          author {
            id
            name
            username
          }
          createdAt
        }
      }
      ... on CheckedListPost {
        id
        title
        description
        status
        publishedAt
        author {
          id
          name
          username
        }
        createdAt
        likeCount
        isLikedByMe
        viewCount
        readTimeMinutes
        items {
          id
          text
          checked
          position
          createdAt
        }
        comments {
          id
          content
          author {
            id
            name
            username
          }
          createdAt
        }
      }
    }
  }
`

const RECORD_POST_VIEW = gql`
  mutation RecordPostView($postId: ID!) {
    recordPostView(postId: $postId)
  }
`

const POST_ID = btoa('BlogPost:00000000-0000-0000-0000-000000000001')

const viewMock = {
  request: { query: RECORD_POST_VIEW, variables: { postId: POST_ID } },
  maxUsageCount: 5,
  result: { data: { recordPostView: true } },
}

function blogPostMock(status: 'DRAFT' | 'PUBLISHED') {
  return {
    request: { query: GET_NODE, variables: { id: POST_ID } },
    maxUsageCount: 5,
    result: {
      data: {
        node: {
          __typename: 'BlogPost',
          id: POST_ID,
          title: 'Hello World',
          content: '<p>Some body text.</p>',
          status,
          publishedAt: status === 'PUBLISHED' ? '2026-08-01T10:00:00Z' : null,
          author: { __typename: 'User', id: 'u1', name: 'Alice', username: 'alice' },
          createdAt: '2026-08-01T10:00:00Z',
          likeCount: 0,
          isLikedByMe: false,
          viewCount: 1,
          readTimeMinutes: 1,
          comments: [],
        },
      },
    },
  }
}

function checklistMock(status: 'DRAFT' | 'PUBLISHED') {
  return {
    request: { query: GET_NODE, variables: { id: POST_ID } },
    maxUsageCount: 5,
    result: {
      data: {
        node: {
          __typename: 'CheckedListPost',
          id: POST_ID,
          title: 'A Checklist',
          description: 'Things to do',
          status,
          publishedAt: status === 'PUBLISHED' ? '2026-08-01T10:00:00Z' : null,
          author: { __typename: 'User', id: 'u1', name: 'Alice', username: 'alice' },
          createdAt: '2026-08-01T10:00:00Z',
          likeCount: 0,
          isLikedByMe: false,
          viewCount: 1,
          readTimeMinutes: 1,
          items: [],
          comments: [],
        },
      },
    },
  }
}

/** What the backend sends when the viewer may not see the post — a draft, or a deleted post. */
const notFoundMock = {
  request: { query: GET_NODE, variables: { id: POST_ID } },
  maxUsageCount: 5,
  result: {
    data: { node: null },
    errors: [
      {
        message:
          'Exception while fetching data (/node) : Post not found: 00000000-0000-0000-0000-000000000001',
      },
    ],
  },
}

function renderPage(mocks: unknown[]) {
  return render(
    <MockedProvider mocks={mocks as never}>
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

describe('PostDetailPage — draft banner', () => {
  it('marks a draft blog post as unpublished', async () => {
    renderPage([blogPostMock('DRAFT'), viewMock])

    await screen.findByText('Hello World')
    expect(screen.getByTestId('draft-banner')).toBeInTheDocument()
  })

  it('marks a draft checklist as unpublished', async () => {
    renderPage([checklistMock('DRAFT'), viewMock])

    await screen.findByText('A Checklist')
    expect(screen.getByTestId('draft-banner')).toBeInTheDocument()
  })

  it('shows no banner on a published post', async () => {
    renderPage([blogPostMock('PUBLISHED'), viewMock])

    await screen.findByText('Hello World')
    expect(screen.queryByTestId('draft-banner')).not.toBeInTheDocument()
  })
})

describe('PostDetailPage — a post the viewer may not see', () => {
  it('reads as not found rather than as a failure', async () => {
    renderPage([notFoundMock, viewMock])

    expect(await screen.findByText(/post not found/i)).toBeInTheDocument()
  })

  it('shows nothing of the underlying exception', async () => {
    renderPage([notFoundMock, viewMock])

    await screen.findByText(/post not found/i)
    // The raw message would otherwise confirm the post exists and leak the field path.
    expect(screen.queryByText(/exception while fetching/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/^Error:/)).not.toBeInTheDocument()
  })

  it('still reports a genuine failure as an error', async () => {
    const failing = {
      request: { query: GET_NODE, variables: { id: POST_ID } },
      maxUsageCount: 5,
      result: { errors: [{ message: 'Something went badly wrong' }] },
    }
    renderPage([failing, viewMock])

    expect(await screen.findByText(/something went badly wrong/i)).toBeInTheDocument()
    expect(screen.queryByText(/post not found/i)).not.toBeInTheDocument()
  })
})
