import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import React from 'react'
import { AuthProvider, useAuth } from '../../src/contexts/AuthContext'

const REGULAR_USER = { id: '1', username: 'alice', email: 'alice@example.com', name: 'Alice', isAdmin: false }
const ADMIN_USER = { id: '2', username: 'bob', email: 'bob@example.com', name: 'Bob', isAdmin: true }

function TestConsumer() {
  const auth = useAuth()
  return (
    <div>
      <span data-testid="is-authenticated">{String(auth.isAuthenticated)}</span>
      <span data-testid="is-admin">{String(auth.isAdmin)}</span>
      <span data-testid="username">{auth.user?.username ?? ''}</span>
      <span data-testid="token">{auth.token ?? ''}</span>
      <button onClick={() => auth.login('tok-regular', REGULAR_USER)}>Login</button>
      <button onClick={() => auth.login('tok-admin', ADMIN_USER)}>Login Admin</button>
      <button onClick={() => auth.logout()}>Logout</button>
    </div>
  )
}

beforeEach(() => {
  localStorage.clear()
})

describe('AuthProvider', () => {
  it('starts unauthenticated when localStorage is empty', () => {
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    )
    expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
    expect(screen.getByTestId('is-admin')).toHaveTextContent('false')
    expect(screen.getByTestId('username')).toHaveTextContent('')
  })

  it('restores persisted auth from localStorage on mount', () => {
    localStorage.setItem('authToken', 'stored-token')
    localStorage.setItem('authUser', JSON.stringify(REGULAR_USER))

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    )

    expect(screen.getByTestId('is-authenticated')).toHaveTextContent('true')
    expect(screen.getByTestId('username')).toHaveTextContent('alice')
    expect(screen.getByTestId('token')).toHaveTextContent('stored-token')
  })

  it('login updates state and persists to localStorage', async () => {
    const user = userEvent.setup()
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    )

    await user.click(screen.getByText('Login'))

    expect(screen.getByTestId('is-authenticated')).toHaveTextContent('true')
    expect(screen.getByTestId('username')).toHaveTextContent('alice')
    expect(localStorage.getItem('authToken')).toBe('tok-regular')
    expect(JSON.parse(localStorage.getItem('authUser')!).username).toBe('alice')
  })

  it('logout clears state and removes localStorage entries', async () => {
    const user = userEvent.setup()
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    )

    await user.click(screen.getByText('Login'))
    await user.click(screen.getByText('Logout'))

    expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
    expect(screen.getByTestId('token')).toHaveTextContent('')
    expect(localStorage.getItem('authToken')).toBeNull()
    expect(localStorage.getItem('authUser')).toBeNull()
  })

  it('isAdmin is false for a regular user', async () => {
    const user = userEvent.setup()
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    )

    await user.click(screen.getByText('Login'))
    expect(screen.getByTestId('is-admin')).toHaveTextContent('false')
  })

  it('isAdmin is true for an admin user', async () => {
    const user = userEvent.setup()
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    )

    await user.click(screen.getByText('Login Admin'))
    expect(screen.getByTestId('is-admin')).toHaveTextContent('true')
  })
})

describe('useAuth', () => {
  it('throws when used outside AuthProvider', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(() => render(<TestConsumer />)).toThrow('useAuth must be used within an AuthProvider')
    spy.mockRestore()
  })
})
