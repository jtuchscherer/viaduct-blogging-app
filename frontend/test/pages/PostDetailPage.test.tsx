import { describe, it, expect } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MockedProvider } from '@apollo/client/testing/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { gql } from '@apollo/client'
import PostDetailPage from '../../src/pages/PostDetailPage'
import { AuthProvider } from '../../src/contexts/AuthContext'

// Queries must match the component's gql documents exactly for MockedProvider matching.

const GET_NODE = gql`
  query GetNode($id: ID!) {
    node(id: $id) {
      __typename
      ... on BlogPost {
        id
        title
        content
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

// A realistic Viaduct global ID (base64 "BlogPost:some-uuid")
const POST_ID = btoa('BlogPost:00000000-0000-0000-0000-000000000001')

const makePostMock = (viewCount = 7, readTimeMinutes = 2.0) => ({
  request: { query: GET_NODE, variables: { id: POST_ID } },
  maxUsageCount: 10,
  result: {
    data: {
      node: {
        __typename: 'BlogPost',
        id: POST_ID,
        title: 'Hello World',
        content: '<p>Some body text.</p>',
        author: { __typename: 'User', id: 'u1', name: 'Alice', username: 'alice' },
        createdAt: '2025-01-15T10:00:00Z',
        likeCount: 3,
        isLikedByMe: false,
        viewCount,
        readTimeMinutes,
        comments: [],
      },
    },
  },
})

const makeViewMock = (result = true) => ({
  request: { query: RECORD_POST_VIEW, variables: { postId: POST_ID } },
  maxUsageCount: 5,
  result: { data: { recordPostView: result } },
})

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

describe('PostDetailPage — recordPostView mutation', () => {
  it('fires recordPostView on mount with the post ID from the URL', async () => {
    let mutationCalled = false
    const mocks = [
      makePostMock(),
      {
        request: { query: RECORD_POST_VIEW, variables: { postId: POST_ID } },
        result: () => {
          mutationCalled = true
          return { data: { recordPostView: true } }
        },
      },
    ]

    renderPage(mocks)

    await waitFor(() => {
      expect(mutationCalled).toBe(true)
    })
  })

  it('displays viewCount after post loads', async () => {
    renderPage([makePostMock(7), makeViewMock()])

    await screen.findByText('Hello World')
    expect(screen.getByText(/7 views/)).toBeInTheDocument()
  })

  it('displays "1 view" (singular) when viewCount is 1', async () => {
    renderPage([makePostMock(1), makeViewMock()])

    await screen.findByText('Hello World')
    expect(screen.getByText(/1 view/)).toBeInTheDocument()
    expect(screen.queryByText(/1 views/)).not.toBeInTheDocument()
  })

  it('displays readTimeMinutes as a rounded whole number', async () => {
    renderPage([makePostMock(7, 2.0), makeViewMock()])

    await screen.findByText('Hello World')
    expect(screen.getByText(/2 min read/)).toBeInTheDocument()
  })

  it('displays "1 min read" for sub-minute read times', async () => {
    renderPage([makePostMock(7, 0.5), makeViewMock()])

    await screen.findByText('Hello World')
    expect(screen.getByText(/1 min read/)).toBeInTheDocument()
  })
})
