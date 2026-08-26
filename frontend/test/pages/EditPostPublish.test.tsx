import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MockedProvider } from '@apollo/client/testing/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { gql } from '@apollo/client'
import EditPostPage from '../../src/pages/EditPostPage'
import { AuthProvider } from '../../src/contexts/AuthContext'

/**
 * Publish and unpublish from the edit page (Phase 28).
 *
 * The edit page is where an author decides whether a post is live, so it offers exactly one of the
 * two transitions — whichever one applies — and reflects the new state once it is done.
 */

// The real editor is Lexical; these tests are about the publish control, not the editor.
vi.mock('../../src/components/RichTextEditor', () => ({
  default: ({ onChange }: { onChange: (html: string) => void }) => (
    <textarea data-testid="rich-text-editor" onChange={(e) => onChange(`<p>${e.target.value}</p>`)} />
  ),
}))

vi.mock('../../src/hooks/useAIHealth', () => ({
  useAIHealth: () => ({ ollamaReachable: false, chatModel: '', embeddingModel: '' }),
}))

const GET_NODE_FOR_EDIT = gql`
  query GetNodeForEdit($id: ID!) {
    node(id: $id) {
      __typename
      ... on BlogPost {
        id
        title
        content
        status
        author {
          id
        }
      }
      ... on CheckedListPost {
        id
        title
        description
        status
        author {
          id
        }
      }
    }
  }
`

const PUBLISH_POST = gql`
  mutation PublishPost($postId: ID!) {
    publishPost(postId: $postId) {
      id
      status
      publishedAt
    }
  }
`

const UNPUBLISH_POST = gql`
  mutation UnpublishPost($postId: ID!) {
    unpublishPost(postId: $postId) {
      id
      status
      publishedAt
    }
  }
`

const POST_ID = btoa('BlogPost:00000000-0000-0000-0000-000000000001')

function nodeMock(status: 'DRAFT' | 'PUBLISHED', typename: 'BlogPost' | 'CheckedListPost' = 'BlogPost') {
  const shared = {
    __typename: typename,
    id: POST_ID,
    title: 'Hello World',
    status,
    author: { __typename: 'User', id: 'u1' },
  }
  return {
    request: { query: GET_NODE_FOR_EDIT, variables: { id: POST_ID } },
    result: {
      data: {
        node:
          typename === 'BlogPost'
            ? { ...shared, content: '<p>Body</p>' }
            : { ...shared, description: 'Things to do' },
      },
    },
  }
}

function transitionMock(to: 'PUBLISHED' | 'DRAFT') {
  const publishing = to === 'PUBLISHED'
  return {
    request: {
      query: publishing ? PUBLISH_POST : UNPUBLISH_POST,
      variables: { postId: POST_ID },
    },
    result: {
      data: {
        [publishing ? 'publishPost' : 'unpublishPost']: {
          __typename: 'BlogPost',
          id: POST_ID,
          status: to,
          publishedAt: publishing ? '2026-08-26T10:00:00Z' : null,
        },
      },
    },
  }
}

function renderPage(mocks: unknown[]) {
  return render(
    <MockedProvider mocks={mocks as never}>
      <MemoryRouter initialEntries={[`/edit/${POST_ID}`]}>
        <AuthProvider>
          <Routes>
            <Route path="/edit/:id" element={<EditPostPage />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </MockedProvider>,
  )
}

/** "Unpublish" contains "publish", so the publish control has to be matched exactly. */
const publishButton = () => screen.getByRole('button', { name: /^publish$/i })
const unpublishButton = () => screen.getByRole('button', { name: /^unpublish$/i })

describe('EditPostPage — which transition is offered', () => {
  it('offers Publish for a draft, and not Unpublish', async () => {
    renderPage([nodeMock('DRAFT')])

    await screen.findByDisplayValue('Hello World')
    expect(publishButton()).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^unpublish$/i })).not.toBeInTheDocument()
  })

  it('offers Unpublish for a published post, and not Publish', async () => {
    renderPage([nodeMock('PUBLISHED')])

    await screen.findByDisplayValue('Hello World')
    expect(unpublishButton()).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^publish$/i })).not.toBeInTheDocument()
  })

  it('offers the transition on a checklist too', async () => {
    renderPage([nodeMock('DRAFT', 'CheckedListPost')])

    await screen.findByDisplayValue('Hello World')
    expect(publishButton()).toBeInTheDocument()
  })

  it('shows a draft banner while the post is a draft', async () => {
    renderPage([nodeMock('DRAFT')])

    await screen.findByDisplayValue('Hello World')
    expect(screen.getByTestId('draft-banner')).toBeInTheDocument()
  })

  it('shows no draft banner on a published post', async () => {
    renderPage([nodeMock('PUBLISHED')])

    await screen.findByDisplayValue('Hello World')
    expect(screen.queryByTestId('draft-banner')).not.toBeInTheDocument()
  })
})

describe('EditPostPage — making the transition', () => {
  it('publishing a draft flips the control and clears the banner', async () => {
    // Two node mocks: the refetch after publishing consumes the second.
    renderPage([nodeMock('DRAFT'), transitionMock('PUBLISHED'), nodeMock('PUBLISHED')])
    await screen.findByDisplayValue('Hello World')

    fireEvent.click(publishButton())

    await waitFor(() => expect(unpublishButton()).toBeInTheDocument())
    expect(screen.queryByTestId('draft-banner')).not.toBeInTheDocument()
  })

  it('unpublishing a published post flips the control and shows the banner', async () => {
    renderPage([nodeMock('PUBLISHED'), transitionMock('DRAFT'), nodeMock('DRAFT')])
    await screen.findByDisplayValue('Hello World')

    fireEvent.click(unpublishButton())

    await waitFor(() => expect(publishButton()).toBeInTheDocument())
    expect(screen.getByTestId('draft-banner')).toBeInTheDocument()
  })

  it('reports a failed transition instead of pretending it worked', async () => {
    const failing = {
      request: { query: PUBLISH_POST, variables: { postId: POST_ID } },
      result: { errors: [{ message: 'You are not authorized to publish this post' }] },
    }
    renderPage([nodeMock('DRAFT'), failing])
    await screen.findByDisplayValue('Hello World')

    fireEvent.click(publishButton())

    expect(await screen.findByText(/not authorized to publish/i)).toBeInTheDocument()
    expect(screen.getByTestId('draft-banner')).toBeInTheDocument()
  })
})
