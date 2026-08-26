import { describe, it, expect } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MockedProvider } from '@apollo/client/testing/react'
import { MemoryRouter } from 'react-router-dom'
import MyPostsPage from '../../src/pages/MyPostsPage'
import { GET_MY_POSTS } from '../../src/graphql/posts'
import { AuthProvider } from '../../src/contexts/AuthContext'

/**
 * My Posts is the only place an author sees their own drafts alongside their published work
 * (Phase 28), so it needs both a marker telling the two apart and a way to narrow to one of them.
 */

const blogPost = (id: string, title: string, status: 'DRAFT' | 'PUBLISHED') => ({
  __typename: 'BlogPost',
  id,
  title,
  content: '<p>Body</p>',
  status,
  createdAt: '2026-08-01T10:00:00Z',
  likeCount: 0,
  commentCount: 0,
})

const checklistPost = (id: string, title: string, status: 'DRAFT' | 'PUBLISHED') => ({
  __typename: 'CheckedListPost',
  id,
  title,
  description: 'A list',
  status,
  createdAt: '2026-08-02T10:00:00Z',
  likeCount: 0,
  commentCount: 0,
})

function mockWith(myPosts: unknown[], myCheckedListPosts: unknown[] = []) {
  return {
    request: { query: GET_MY_POSTS },
    maxUsageCount: 5,
    result: { data: { myPosts, myCheckedListPosts } },
  }
}

function renderPage(mocks: unknown[]) {
  return render(
    <MockedProvider mocks={mocks as never}>
      <MemoryRouter>
        <AuthProvider>
          <MyPostsPage />
        </AuthProvider>
      </MemoryRouter>
    </MockedProvider>,
  )
}

describe('MyPostsPage — draft badge', () => {
  it('marks a draft and leaves a published post unmarked', async () => {
    renderPage([mockWith([blogPost('p1', 'A Draft', 'DRAFT'), blogPost('p2', 'Live One', 'PUBLISHED')])])

    await screen.findByText('A Draft')
    expect(screen.getAllByTestId('draft-badge')).toHaveLength(1)
  })

  it('marks a draft checklist as well as a draft blog post', async () => {
    renderPage([
      mockWith([blogPost('p1', 'A Draft', 'DRAFT')], [checklistPost('c1', 'Draft List', 'DRAFT')]),
    ])

    await screen.findByText('A Draft')
    expect(screen.getAllByTestId('draft-badge')).toHaveLength(2)
  })

  it('marks nothing when every post is published', async () => {
    renderPage([mockWith([blogPost('p1', 'Live One', 'PUBLISHED')])])

    await screen.findByText('Live One')
    expect(screen.queryByTestId('draft-badge')).not.toBeInTheDocument()
  })
})

describe('MyPostsPage — status filter', () => {
  const both = () =>
    mockWith([blogPost('p1', 'A Draft', 'DRAFT'), blogPost('p2', 'Live One', 'PUBLISHED')])

  it('shows both statuses by default', async () => {
    renderPage([both()])

    expect(await screen.findByText('A Draft')).toBeInTheDocument()
    expect(screen.getByText('Live One')).toBeInTheDocument()
  })

  it('narrows to drafts', async () => {
    renderPage([both()])
    await screen.findByText('A Draft')

    fireEvent.change(screen.getByLabelText(/show/i), { target: { value: 'DRAFT' } })

    expect(screen.getByText('A Draft')).toBeInTheDocument()
    expect(screen.queryByText('Live One')).not.toBeInTheDocument()
  })

  it('narrows to published posts', async () => {
    renderPage([both()])
    await screen.findByText('A Draft')

    fireEvent.change(screen.getByLabelText(/show/i), { target: { value: 'PUBLISHED' } })

    expect(screen.getByText('Live One')).toBeInTheDocument()
    expect(screen.queryByText('A Draft')).not.toBeInTheDocument()
  })

  it('goes back to showing everything', async () => {
    renderPage([both()])
    await screen.findByText('A Draft')

    const filter = screen.getByLabelText(/show/i)
    fireEvent.change(filter, { target: { value: 'DRAFT' } })
    fireEvent.change(filter, { target: { value: 'ALL' } })

    expect(screen.getByText('A Draft')).toBeInTheDocument()
    expect(screen.getByText('Live One')).toBeInTheDocument()
  })

  it('filters checklists by status too', async () => {
    renderPage([
      mockWith(
        [blogPost('p1', 'Live Post', 'PUBLISHED')],
        [checklistPost('c1', 'Draft List', 'DRAFT')],
      ),
    ])
    await screen.findByText('Draft List')

    fireEvent.change(screen.getByLabelText(/show/i), { target: { value: 'DRAFT' } })

    expect(screen.getByText('Draft List')).toBeInTheDocument()
    expect(screen.queryByText('Live Post')).not.toBeInTheDocument()
  })

  it('says the filter is empty rather than claiming the author has no posts', async () => {
    renderPage([mockWith([blogPost('p1', 'Live One', 'PUBLISHED')])])
    await screen.findByText('Live One')

    fireEvent.change(screen.getByLabelText(/show/i), { target: { value: 'DRAFT' } })

    expect(screen.getByText(/no drafts/i)).toBeInTheDocument()
    expect(screen.queryByText(/haven't created any posts/i)).not.toBeInTheDocument()
  })

  it('offers no filter at all when the author has no posts', async () => {
    renderPage([mockWith([])])

    await screen.findByText(/haven't created any posts/i)
    expect(screen.queryByLabelText(/show/i)).not.toBeInTheDocument()
  })
})
