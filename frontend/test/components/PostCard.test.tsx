import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import PostCard from '../../src/components/PostCard'
import type { BlogPostCard, CheckedListPost } from '../../src/types'

const AUTHOR = { id: 'author-1', name: 'Alice Smith', username: 'alice' }

const BLOG_POST: BlogPostCard = {
  __typename: 'BlogPost',
  id: 'post-1',
  title: 'Hello World',
  content: '<p>Some content here</p>',
  author: AUTHOR,
  createdAt: '2024-06-01T10:00:00Z',
  likeCount: 5,
  commentCount: 3,
  readTimeMinutes: 2,
}

const CHECKLIST_POST: CheckedListPost = {
  __typename: 'CheckedListPost',
  id: 'cl-1',
  title: 'My Shopping List',
  description: 'Things to buy this week',
  author: AUTHOR,
  createdAt: '2024-06-02T10:00:00Z',
  updatedAt: '2024-06-02T10:00:00Z',
  likeCount: 2,
  commentCount: 1,
  readTimeMinutes: 1,
  items: [
    { id: 'item-1', text: 'Milk', checked: true, position: 0, createdAt: '2024-06-02T10:00:00Z' },
    { id: 'item-2', text: 'Eggs', checked: false, position: 1, createdAt: '2024-06-02T10:00:00Z' },
    { id: 'item-3', text: 'Bread', checked: false, position: 2, createdAt: '2024-06-02T10:00:00Z' },
  ],
}

function renderCard(post: BlogPostCard | CheckedListPost) {
  return render(
    <MemoryRouter>
      <PostCard post={post} />
    </MemoryRouter>,
  )
}

// ── BlogPost card ─────────────────────────────────────────────────────────────

describe('PostCard — BlogPost', () => {
  it('renders the post title as a link', () => {
    renderCard(BLOG_POST)
    const link = screen.getByRole('link', { name: 'Hello World' })
    expect(link).toBeInTheDocument()
    expect(link).toHaveAttribute('href', '/post/post-1')
  })

  it('renders the author name', () => {
    renderCard(BLOG_POST)
    expect(screen.getByText(/Alice Smith/)).toBeInTheDocument()
  })

  it('renders the like count', () => {
    renderCard(BLOG_POST)
    expect(screen.getByText(/5/)).toBeInTheDocument()
  })

  it('renders the comment count', () => {
    renderCard(BLOG_POST)
    expect(screen.getByText(/3/)).toBeInTheDocument()
  })

  it('renders the read time when provided', () => {
    renderCard(BLOG_POST)
    expect(screen.getByText(/min/)).toBeInTheDocument()
  })

  it('renders a "Read more" link', () => {
    renderCard(BLOG_POST)
    expect(screen.getByText('Read more →')).toBeInTheDocument()
  })

  it('does not show the checklist badge', () => {
    renderCard(BLOG_POST)
    expect(screen.queryByText(/Checklist/)).not.toBeInTheDocument()
  })
})

// ── CheckedListPost card ──────────────────────────────────────────────────────

describe('PostCard — CheckedListPost', () => {
  it('renders the checklist title as a link', () => {
    renderCard(CHECKLIST_POST)
    const link = screen.getByRole('link', { name: 'My Shopping List' })
    expect(link).toBeInTheDocument()
    expect(link).toHaveAttribute('href', '/post/cl-1')
  })

  it('shows the Checklist type badge', () => {
    renderCard(CHECKLIST_POST)
    expect(screen.getByText(/Checklist/)).toBeInTheDocument()
  })

  it('renders the description', () => {
    renderCard(CHECKLIST_POST)
    expect(screen.getByText('Things to buy this week')).toBeInTheDocument()
  })

  it('renders the checked item progress', () => {
    renderCard(CHECKLIST_POST)
    // 1 checked out of 3 total
    expect(screen.getByText(/1\/3/)).toBeInTheDocument()
  })

  it('renders a "View list" link', () => {
    renderCard(CHECKLIST_POST)
    expect(screen.getByText('View list →')).toBeInTheDocument()
  })

  it('renders the author name', () => {
    renderCard(CHECKLIST_POST)
    expect(screen.getByText(/Alice Smith/)).toBeInTheDocument()
  })

  it('does not show progress when items array is not provided', () => {
    const postWithoutItems: CheckedListPost = { ...CHECKLIST_POST, items: undefined }
    renderCard(postWithoutItems)
    // "0/0" should not appear
    expect(screen.queryByText(/\/0 items/)).not.toBeInTheDocument()
  })
})
