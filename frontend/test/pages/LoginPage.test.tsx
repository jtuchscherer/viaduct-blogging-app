import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import LoginPage from '../../src/pages/LoginPage'
import { AuthProvider } from '../../src/contexts/AuthContext'

// useNavigate is called after a successful login; mock it so the test
// doesn't crash when navigate('/') is invoked.
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const original = await importOriginal<typeof import('react-router-dom')>()
  return { ...original, useNavigate: () => mockNavigate }
})

function renderPage() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  localStorage.clear()
})

// ── Form rendering ────────────────────────────────────────────────────────────

describe('LoginPage — rendering', () => {
  it('renders the Login heading', () => {
    renderPage()
    expect(screen.getByRole('heading', { name: 'Login' })).toBeInTheDocument()
  })

  it('renders a username input', () => {
    renderPage()
    expect(screen.getByLabelText('Username')).toBeInTheDocument()
  })

  it('renders a password input of type password', () => {
    renderPage()
    const input = screen.getByLabelText('Password')
    expect(input).toBeInTheDocument()
    expect(input).toHaveAttribute('type', 'password')
  })

  it('renders a submit button', () => {
    renderPage()
    expect(screen.getByRole('button', { name: 'Login' })).toBeInTheDocument()
  })

  it('renders a link to the register page', () => {
    renderPage()
    const link = screen.getByRole('link', { name: /Register here/i })
    expect(link).toHaveAttribute('href', '/register')
  })
})

// ── Successful login ──────────────────────────────────────────────────────────

describe('LoginPage — successful login', () => {
  it('calls fetch with the provided credentials', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        token: 'test-token',
        user: { id: '1', username: 'alice', email: 'a@a.com', name: 'Alice', is_admin: false },
      }),
    })
    vi.stubGlobal('fetch', fetchMock)

    renderPage()

    await userEvent.type(screen.getByLabelText('Username'), 'alice')
    await userEvent.type(screen.getByLabelText('Password'), 'password123')
    await userEvent.click(screen.getByRole('button', { name: 'Login' }))

    expect(fetchMock).toHaveBeenCalledOnce()
    const [, options] = fetchMock.mock.calls[0]
    const body = JSON.parse(options.body)
    expect(body.username).toBe('alice')
    expect(body.password).toBe('password123')
  })

  it('redirects to / after a successful login', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        token: 'tok',
        user: { id: '1', username: 'alice', email: 'a@a.com', name: 'Alice', is_admin: false },
      }),
    }))

    renderPage()
    await userEvent.type(screen.getByLabelText('Username'), 'alice')
    await userEvent.type(screen.getByLabelText('Password'), 'pass')
    await userEvent.click(screen.getByRole('button', { name: 'Login' }))

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/')
    })
  })
})

// ── Failed login ──────────────────────────────────────────────────────────────

describe('LoginPage — failed login', () => {
  it('shows an error message when the server returns 401', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      json: async () => ({ message: 'Invalid credentials' }),
    }))

    renderPage()
    await userEvent.type(screen.getByLabelText('Username'), 'alice')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Login' }))

    await waitFor(() => {
      expect(screen.getByText('Invalid credentials')).toBeInTheDocument()
    })
  })

  it('shows a generic error message when fetch rejects', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network error')))

    renderPage()
    await userEvent.type(screen.getByLabelText('Username'), 'alice')
    await userEvent.type(screen.getByLabelText('Password'), 'pass')
    await userEvent.click(screen.getByRole('button', { name: 'Login' }))

    await waitFor(() => {
      expect(screen.getByText('Network error')).toBeInTheDocument()
    })
  })

  it('does not navigate after a failed login', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      json: async () => ({ message: 'Bad credentials' }),
    }))

    renderPage()
    await userEvent.type(screen.getByLabelText('Username'), 'alice')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Login' }))

    await waitFor(() => screen.getByText('Bad credentials'))
    expect(mockNavigate).not.toHaveBeenCalled()
  })
})

// ── Loading state ─────────────────────────────────────────────────────────────

describe('LoginPage — loading state', () => {
  it('disables the submit button while the request is in flight', async () => {
    // Never resolves so we can inspect the in-flight state
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise(() => {})))

    renderPage()
    await userEvent.type(screen.getByLabelText('Username'), 'alice')
    await userEvent.type(screen.getByLabelText('Password'), 'pass')
    await userEvent.click(screen.getByRole('button', { name: 'Login' }))

    expect(screen.getByRole('button', { name: 'Logging in...' })).toBeDisabled()
  })
})
