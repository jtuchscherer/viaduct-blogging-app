import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MockedProvider } from '@apollo/client/testing/react'
import { MemoryRouter } from 'react-router-dom'
import CreatePostPage from '../../src/pages/CreatePostPage'
import { AuthProvider } from '../../src/contexts/AuthContext'
import type { AIHealth } from '../../src/types'

vi.mock('../../src/hooks/useAIHealth', () => ({
  useAIHealth: vi.fn(),
}))

import { useAIHealth } from '../../src/hooks/useAIHealth'

// ── Helpers ───────────────────────────────────────────────────────────────────

function mockAIHealth(reachable: boolean) {
  vi.mocked(useAIHealth).mockReturnValue({
    ollamaReachable: reachable,
    chatModel: 'llama3.2',
    embeddingModel: 'nomic-embed-text',
  } satisfies AIHealth)
}

function renderPage(mocks: unknown[] = []) {
  return render(
    <MockedProvider mocks={mocks as never} addTypename={false}>
      <MemoryRouter>
        <AuthProvider>
          <CreatePostPage />
        </AuthProvider>
      </MemoryRouter>
    </MockedProvider>,
  )
}

async function switchToChecklist() {
  const checklistLabel = screen.getByLabelText(/Checklist/i, { selector: 'input[type=radio]' })
  fireEvent.click(checklistLabel)
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('CreatePostPage — checklist suggest button', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('suggest button is not rendered when Ollama is offline', async () => {
    mockAIHealth(false)
    renderPage()
    await switchToChecklist()

    expect(screen.queryByRole('button', { name: /suggest item/i })).not.toBeInTheDocument()
  })

  it('suggest button is rendered when Ollama is reachable', async () => {
    mockAIHealth(true)
    renderPage()
    await switchToChecklist()

    expect(screen.getByRole('button', { name: /suggest item/i })).toBeInTheDocument()
  })

  it('suggest button is disabled with fewer than 3 non-empty items', async () => {
    mockAIHealth(true)
    renderPage()
    await switchToChecklist()

    // Default: 1 empty item row → button is disabled
    expect(screen.getByRole('button', { name: /suggest item/i })).toBeDisabled()
  })

  it('suggest button is enabled when 3 or more non-empty items exist', async () => {
    mockAIHealth(true)
    renderPage()
    await switchToChecklist()

    const addBtn = screen.getByRole('button', { name: /\+ add item/i })

    // Add 2 more rows so we have 3 total
    fireEvent.click(addBtn)
    fireEvent.click(addBtn)

    // Fill all 3 items
    const inputs = screen.getAllByPlaceholderText(/Item \d+…/)
    fireEvent.change(inputs[0], { target: { value: 'First item' } })
    fireEvent.change(inputs[1], { target: { value: 'Second item' } })
    fireEvent.change(inputs[2], { target: { value: 'Third item' } })

    expect(screen.getByRole('button', { name: /suggest item/i })).toBeEnabled()
  })

  it('suggest button remains disabled when items exist but fewer than 3 are non-empty', async () => {
    mockAIHealth(true)
    renderPage()
    await switchToChecklist()

    const addBtn = screen.getByRole('button', { name: /\+ add item/i })
    fireEvent.click(addBtn)
    fireEvent.click(addBtn)

    // Only fill 2 out of 3 rows
    const inputs = screen.getAllByPlaceholderText(/Item \d+…/)
    fireEvent.change(inputs[0], { target: { value: 'First item' } })
    fireEvent.change(inputs[1], { target: { value: 'Second item' } })

    expect(screen.getByRole('button', { name: /suggest item/i })).toBeDisabled()
  })
})
