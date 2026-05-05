import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MockedProvider } from '@apollo/client/testing/react'
import { MemoryRouter } from 'react-router-dom'
import { gql } from '@apollo/client'
import HomePage from '../../src/pages/HomePage'
import { AuthProvider } from '../../src/contexts/AuthContext'

const GET_POSTS_CONNECTION = gql`
  query GetPostsConnection($first: Int, $after: String) {
    postsConnection(first: $first, after: $after) {
      totalCount
      pageInfo {
        hasNextPage
        endCursor
      }
      edges {
        node {
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
          commentCount
          readTimeMinutes
        }
      }
    }
  }
`

const GET_TRENDING = gql`
  query GetTrending($limit: Int) {
    trending(limit: $limit) {
      id
      title
      ... on BlogPost {
        content
        readTimeMinutes
      }
      author {
        id
        name
        username
      }
      createdAt
      likeCount
      commentCount
    }
  }
`

const emptyConnectionMock = {
  request: { query: GET_POSTS_CONNECTION, variables: { first: 10, after: null } },
  maxUsageCount: 5,
  result: {
    data: {
      postsConnection: {
        __typename: 'PostsConnection',
        totalCount: 0,
        pageInfo: { hasNextPage: false, endCursor: null },
        edges: [],
      },
    },
  },
}

const emptyTrendingMock = {
  request: { query: GET_TRENDING, variables: { limit: 10 } },
  maxUsageCount: 5,
  result: {
    data: { trending: [] },
  },
}

function renderPage(mocks: unknown[] = [emptyConnectionMock, emptyTrendingMock]) {
  return render(
    <MockedProvider
      mocks={mocks as never}
      defaultOptions={{ watchQuery: { fetchPolicy: 'cache-first', notifyOnNetworkStatusChange: false } }}
    >
      <MemoryRouter>
        <AuthProvider>
          <HomePage />
        </AuthProvider>
      </MemoryRouter>
    </MockedProvider>,
  )
}

describe('HomePage — sort control', () => {
  it('renders both New and Trending buttons', () => {
    renderPage()
    expect(screen.getByRole('button', { name: 'New' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Trending' })).toBeInTheDocument()
  })

  it('New button is active by default', () => {
    renderPage()
    expect(screen.getByRole('button', { name: 'New' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Trending' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('clicking Trending switches the active button to Trending', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: 'Trending' }))
    expect(screen.getByRole('button', { name: 'Trending' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'New' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('clicking Trending then New switches back to New active', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: 'Trending' }))
    await userEvent.click(screen.getByRole('button', { name: 'New' }))
    expect(screen.getByRole('button', { name: 'New' })).toHaveAttribute('aria-pressed', 'true')
  })
})
