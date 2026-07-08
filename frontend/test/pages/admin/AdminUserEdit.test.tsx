import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MockedProvider } from '@apollo/client/testing/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { gql } from '@apollo/client'
import AdminUserEdit from '../../../src/pages/admin/AdminUserEdit'

// Same gql documents as AdminUserEdit.tsx so MockedProvider matches exactly.
const ADMIN_USER = gql`
  query AdminUser($id: ID!) {
    admin {
      user(id: $id) {
        id
        username
        email
        name
        isAdmin
        createdAt
      }
    }
  }
`

const ADMIN_UPDATE_USER = gql`
  mutation AdminUpdateUser($input: AdminUpdateUserInput!) {
    admin {
      updateUser(input: $input) {
        id
        username
        email
        name
        isAdmin
      }
    }
  }
`

// __typename is required when MockedProvider normalises responses (the default),
// otherwise the cache can't key entries and the query re-fetches mid-test.
const USER = {
  __typename: 'User',
  id: 'user-1',
  username: 'alice',
  email: 'alice@example.com',
  name: 'Alice',
  isAdmin: false,
  createdAt: '2025-01-01T00:00:00Z',
}

const userQueryMock = {
  request: { query: ADMIN_USER, variables: { id: 'user-1' } },
  // Apollo's observer reruns the query on unrelated state changes; keep the
  // mock reusable so it doesn't exhaust and flip the component back to loading.
  maxUsageCount: 10,
  result: {
    data: {
      admin: { __typename: 'AdminQueries', user: USER },
    },
  },
}

function renderPage(mocks: ReadonlyArray<unknown>) {
  return render(
    <MockedProvider
      mocks={mocks as never}
      defaultOptions={{ watchQuery: { fetchPolicy: 'cache-first', notifyOnNetworkStatusChange: false } }}
    >
      <MemoryRouter initialEntries={['/admin/users/user-1']}>
        <Routes>
          <Route path="/admin/users/:id" element={<AdminUserEdit />} />
          <Route path="/admin/users" element={<div>Users List Page</div>} />
        </Routes>
      </MemoryRouter>
    </MockedProvider>,
  )
}

describe('AdminUserEdit', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows a loading state before the query resolves', () => {
    renderPage([userQueryMock])
    expect(screen.getByText('Loading...')).toBeInTheDocument()
  })

  it('shows an error message when the query fails', async () => {
    const mocks = [
      {
        request: { query: ADMIN_USER, variables: { id: 'user-1' } },
        error: new Error('network down'),
      },
    ]
    renderPage(mocks)
    expect(await screen.findByText(/network down/)).toBeInTheDocument()
  })

  it('shows "User not found" when the user does not exist', async () => {
    const mocks = [
      {
        request: { query: ADMIN_USER, variables: { id: 'user-1' } },
        result: { data: { admin: { __typename: 'AdminQueries', user: null } } },
      },
    ]
    renderPage(mocks)
    expect(await screen.findByText('User not found')).toBeInTheDocument()
  })

  it('initialises the form fields from the loaded user', async () => {
    renderPage([userQueryMock])

    // Wait for the loaded data to populate the form.
    expect(await screen.findByLabelText('Name')).toHaveValue('Alice')
    expect(screen.getByLabelText('Email')).toHaveValue('alice@example.com')
    expect(screen.getByLabelText('Administrator')).not.toBeChecked()
    // Username is read-only and shows the loaded value.
    expect(screen.getByDisplayValue('alice')).toBeDisabled()
  })

  it('submits edited values and navigates back to the user list', async () => {
    const updateMock = {
      request: {
        query: ADMIN_UPDATE_USER,
        variables: {
          input: { id: 'user-1', name: 'Alice Updated', email: 'alice@example.com', isAdmin: true },
        },
      },
      result: {
        data: {
          admin: {
            __typename: 'AdminQueries',
            updateUser: {
              __typename: 'User',
              id: 'user-1',
              username: 'alice',
              email: 'alice@example.com',
              name: 'Alice Updated',
              isAdmin: true,
            },
          },
        },
      },
    }

    renderPage([userQueryMock, updateMock])

    const nameInput = await screen.findByLabelText('Name')
    await userEvent.clear(nameInput)
    await userEvent.type(nameInput, 'Alice Updated')
    await userEvent.click(screen.getByLabelText('Administrator'))
    await userEvent.click(screen.getByRole('button', { name: /Save Changes/ }))

    // Successful save navigates to /admin/users.
    expect(await screen.findByText('Users List Page')).toBeInTheDocument()
  })

  it('surfaces an error message when saving fails', async () => {
    const updateMock = {
      request: {
        query: ADMIN_UPDATE_USER,
        variables: {
          input: { id: 'user-1', name: 'Alice', email: 'alice@example.com', isAdmin: false },
        },
      },
      error: new Error('Save failed'),
    }

    renderPage([userQueryMock, updateMock])

    await screen.findByLabelText('Name')
    await userEvent.click(screen.getByRole('button', { name: /Save Changes/ }))

    expect(await screen.findByText('Save failed')).toBeInTheDocument()
    // Stayed on the edit page (no navigation).
    expect(screen.queryByText('Users List Page')).not.toBeInTheDocument()
  })
})
