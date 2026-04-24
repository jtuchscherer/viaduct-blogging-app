import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MockedProvider } from '@apollo/client/testing/react'
import { MemoryRouter } from 'react-router-dom'
import { GraphQLError } from 'graphql'
import AdminUsers from '../../../src/pages/admin/AdminUsers'

// Operation names matched by MockedProvider come from the gql documents in
// AdminUsers.tsx: AdminUsers, AdminUserContentCounts, AdminDeleteUser.
// We import the same gql documents here so the query match is exact.
import { gql } from '@apollo/client'

const ADMIN_USERS = gql`
  query AdminUsers($limit: Int!, $offset: Int!) {
    admin {
      users(limit: $limit, offset: $offset) {
        totalCount
        users { id username email name isAdmin createdAt }
      }
    }
  }
`

const ADMIN_USER_CONTENT_COUNTS = gql`
  query AdminUserContentCounts($userId: ID!) {
    admin {
      userContentCounts(userId: $userId) {
        postCount
        commentCount
        likeCount
      }
    }
  }
`

// __typename is required when MockedProvider normalises responses (the default).
// Without it, the Apollo cache can't key entries and every observer rerun
// re-fetches via the link — which exhausts a single-use mock and resets the
// component to its loading state mid-test.
const USER = {
  __typename: 'User',
  id: 'user-1',
  username: 'alice',
  email: 'alice@example.com',
  name: 'Alice',
  isAdmin: false,
  createdAt: '2025-01-01T00:00:00Z',
}

const usersPageMock = {
  request: { query: ADMIN_USERS, variables: { limit: 10, offset: 0 } },
  // Bumped past 1 because Apollo's observer reruns the query on subscription
  // changes triggered by unrelated state updates in the page component.
  maxUsageCount: 10,
  result: {
    data: {
      admin: {
        __typename: 'AdminQueries',
        users: {
          __typename: 'AdminUsersPage',
          totalCount: 1,
          users: [USER],
        },
      },
    },
  },
}

function renderPage(mocks: ReadonlyArray<unknown>) {
  // cache-first ensures useQuery returns synchronously from the cache once
  // the initial fetch resolves, instead of going back to loading on each
  // subsequent observer subscription/rerender — which had been causing the
  // page to flicker back to its loading state mid-test.
  return render(
    <MockedProvider
      mocks={mocks as never}
      defaultOptions={{ watchQuery: { fetchPolicy: 'cache-first', notifyOnNetworkStatusChange: false } }}
    >
      <MemoryRouter>
        <AdminUsers />
      </MemoryRouter>
    </MockedProvider>,
  )
}

describe('AdminUsers delete-confirmation dialog', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows impact counts and enables Delete when the counts query succeeds', async () => {
    const mocks = [
      usersPageMock,
      {
        request: { query: ADMIN_USER_CONTENT_COUNTS, variables: { userId: USER.id } },
        result: {
          data: {
            admin: {
              __typename: 'AdminQueries',
              userContentCounts: {
                __typename: 'UserContentCounts',
                postCount: 3,
                commentCount: 7,
                likeCount: 12,
              },
            },
          },
        },
      },
    ]

    renderPage(mocks)

    // Wait for the user row to render
    await screen.findByText('alice')

    await userEvent.click(screen.getByRole('button', { name: /^Delete$/ }))

    const warning = await screen.findByText(/will also delete/i)
    expect(warning.textContent).toMatch(/3 posts/)
    expect(warning.textContent).toMatch(/7 comments/)
    expect(warning.textContent).toMatch(/12 likes/)

    const confirmBtn = screen.getByRole('button', { name: /Delete User/ })
    expect(confirmBtn).toBeEnabled()
    expect(screen.queryByTestId('content-counts-error')).not.toBeInTheDocument()
  })

  it('surfaces an error and disables Delete when the counts query fails', async () => {
    const mocks = [
      usersPageMock,
      {
        request: { query: ADMIN_USER_CONTENT_COUNTS, variables: { userId: USER.id } },
        result: { errors: [new GraphQLError('backend boom')] },
      },
    ]

    renderPage(mocks)
    await screen.findByText('alice')

    await userEvent.click(screen.getByRole('button', { name: /^Delete$/ }))

    // Error shown, no misleading "0 posts, 0 comments, 0 likes" fallback
    await screen.findByTestId('content-counts-error')
    expect(screen.queryByText(/0 posts/)).not.toBeInTheDocument()
    expect(screen.queryByText(/0 comments/)).not.toBeInTheDocument()
    expect(screen.queryByText(/0 likes/)).not.toBeInTheDocument()

    // Delete is blocked until the admin knows the impact
    expect(screen.getByRole('button', { name: /Delete User/ })).toBeDisabled()

    // Retry is offered
    expect(screen.getByTestId('btn-retry-counts')).toBeInTheDocument()

    // And the error got logged so it's visible in dev tools / Sentry
    expect(console.error).toHaveBeenCalledWith(
      'Failed to fetch user content counts:',
      expect.anything(),
    )
  })

  it('closes the dialog and clears error state on Cancel', async () => {
    const mocks = [
      usersPageMock,
      {
        request: { query: ADMIN_USER_CONTENT_COUNTS, variables: { userId: USER.id } },
        result: { errors: [new GraphQLError('backend boom')] },
      },
    ]

    renderPage(mocks)
    await screen.findByText('alice')

    await userEvent.click(screen.getByRole('button', { name: /^Delete$/ }))
    await screen.findByTestId('content-counts-error')

    await userEvent.click(screen.getByRole('button', { name: /Cancel/ }))

    await waitFor(() => {
      expect(screen.queryByTestId('content-counts-error')).not.toBeInTheDocument()
    })
  })
})
