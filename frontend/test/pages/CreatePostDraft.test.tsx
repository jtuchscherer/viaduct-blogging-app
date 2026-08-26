import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MockedProvider } from '@apollo/client/testing/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { gql } from '@apollo/client'
import CreatePostPage from '../../src/pages/CreatePostPage'
import { AuthProvider } from '../../src/contexts/AuthContext'

/**
 * "Save draft" on the create page (Phase 28).
 *
 * The mutation mocks below match on variables, so a test that reaches the post page has also
 * proved the form sent the status it claims to send: a form that sent PUBLISHED where the mock
 * expects DRAFT would find no matching mock and never navigate.
 */

vi.mock('../../src/hooks/useAIHealth', () => ({
  useAIHealth: () => ({ ollamaReachable: false, chatModel: '', embeddingModel: '' }),
}))

// The real editor is Lexical, which has its own tests. A textarea keeps these tests about the
// draft/publish decision rather than about editor internals.
vi.mock('../../src/components/RichTextEditor', () => ({
  default: ({ onChange, disabled }: { onChange: (html: string) => void; disabled?: boolean }) => (
    <textarea
      data-testid="rich-text-editor"
      disabled={disabled}
      onChange={(e) => onChange(`<p>${e.target.value}</p>`)}
    />
  ),
}))

const CREATE_BLOG_POST = gql`
  mutation CreatePost($input: CreatePostInput!) {
    createPost(input: $input) {
      id
      title
      content
      status
    }
  }
`

const CREATE_CHECKLIST_POST = gql`
  mutation CreateCheckedListPost($input: CreateCheckedListPostInput!) {
    createCheckedListPost(input: $input) {
      id
      title
      status
    }
  }
`

const NEW_POST_ID = btoa('BlogPost:00000000-0000-0000-0000-000000000009')

function blogMock(status: 'DRAFT' | 'PUBLISHED') {
  return {
    request: {
      query: CREATE_BLOG_POST,
      variables: { input: { title: 'A Title', content: '<p>Body text</p>', status } },
    },
    result: {
      data: {
        createPost: {
          __typename: 'BlogPost',
          id: NEW_POST_ID,
          title: 'A Title',
          content: '<p>Body text</p>',
          status,
        },
      },
    },
  }
}

function checklistMock(status: 'DRAFT' | 'PUBLISHED') {
  return {
    request: {
      query: CREATE_CHECKLIST_POST,
      variables: {
        input: { title: 'A List', description: 'Why', items: ['first item'], status },
      },
    },
    result: {
      data: {
        createCheckedListPost: {
          __typename: 'CheckedListPost',
          id: NEW_POST_ID,
          title: 'A List',
          status,
        },
      },
    },
  }
}

function renderPage(mocks: unknown[] = []) {
  return render(
    <MockedProvider mocks={mocks as never}>
      <MemoryRouter initialEntries={['/create']}>
        <AuthProvider>
          <Routes>
            <Route path="/create" element={<CreatePostPage />} />
            <Route path="/post/:id" element={<div>landed on the post page</div>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </MockedProvider>,
  )
}

function switchToChecklist() {
  fireEvent.click(screen.getByLabelText(/Checklist/i, { selector: 'input[type=radio]' }))
}

function fillBlogForm() {
  fireEvent.change(screen.getByLabelText('Title'), { target: { value: 'A Title' } })
  fireEvent.change(screen.getByTestId('rich-text-editor'), { target: { value: 'Body text' } })
}

function fillChecklistForm() {
  fireEvent.change(screen.getByLabelText('Title'), { target: { value: 'A List' } })
  fireEvent.change(screen.getByLabelText(/Description/i), { target: { value: 'Why' } })
  fireEvent.change(screen.getByPlaceholderText('Item 1…'), { target: { value: 'first item' } })
}

const landed = () => screen.findByText('landed on the post page')

describe('CreatePostPage — saving a blog post as a draft', () => {
  beforeEach(() => vi.clearAllMocks())

  it('offers Save draft alongside the publishing button', () => {
    renderPage()

    expect(screen.getByRole('button', { name: /save draft/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /create post/i })).toBeInTheDocument()
  })

  it('saves as a draft and opens the new post', async () => {
    renderPage([blogMock('DRAFT')])
    fillBlogForm()

    fireEvent.click(screen.getByRole('button', { name: /save draft/i }))

    expect(await landed()).toBeInTheDocument()
  })

  it('publishes when the primary button is used', async () => {
    renderPage([blogMock('PUBLISHED')])
    fillBlogForm()

    fireEvent.click(screen.getByRole('button', { name: /create post/i }))

    expect(await landed()).toBeInTheDocument()
  })

  it('refuses to save a draft without a title', async () => {
    renderPage([blogMock('DRAFT')])
    fireEvent.change(screen.getByTestId('rich-text-editor'), { target: { value: 'Body text' } })

    fireEvent.click(screen.getByRole('button', { name: /save draft/i }))

    expect(await screen.findByText(/title and content are required/i)).toBeInTheDocument()
    expect(screen.queryByText('landed on the post page')).not.toBeInTheDocument()
  })
})

describe('CreatePostPage — saving a checklist as a draft', () => {
  beforeEach(() => vi.clearAllMocks())

  it('offers Save draft on the checklist form too', () => {
    renderPage()
    switchToChecklist()

    expect(screen.getByRole('button', { name: /save draft/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /create checklist/i })).toBeInTheDocument()
  })

  it('saves the checklist as a draft and opens it', async () => {
    renderPage([checklistMock('DRAFT')])
    switchToChecklist()
    fillChecklistForm()

    fireEvent.click(screen.getByRole('button', { name: /save draft/i }))

    expect(await landed()).toBeInTheDocument()
  })

  it('refuses to save a checklist draft without a title', async () => {
    renderPage([checklistMock('DRAFT')])
    switchToChecklist()
    fireEvent.change(screen.getByPlaceholderText('Item 1…'), { target: { value: 'first item' } })

    fireEvent.click(screen.getByRole('button', { name: /save draft/i }))

    expect(await screen.findByText(/title is required/i)).toBeInTheDocument()
    expect(screen.queryByText('landed on the post page')).not.toBeInTheDocument()
  })
})

describe('CreatePostPage — draft button state', () => {
  it('disables Save draft while a publish is in flight', async () => {
    // Never resolves, so the form stays in its loading state and the assertion cannot race
    // navigation away from the page.
    renderPage([{ ...blogMock('PUBLISHED'), delay: Infinity }])
    fillBlogForm()

    fireEvent.click(screen.getByRole('button', { name: /create post/i }))

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /save draft/i })).toBeDisabled()
    })
  })
})
