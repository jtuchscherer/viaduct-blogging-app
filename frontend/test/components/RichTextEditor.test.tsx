import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import RichTextEditor from '../../src/components/RichTextEditor'

// Hoisted toggle for the $generateNodesFromDOM mock. Tests flip
// `shouldThrow` to drive the fallback path on demand.
const lexicalHtmlMock = vi.hoisted(() => ({ shouldThrow: false }))

vi.mock('@lexical/html', async () => {
  const actual = await vi.importActual<typeof import('@lexical/html')>('@lexical/html')
  return {
    ...actual,
    $generateNodesFromDOM: (...args: Parameters<typeof actual.$generateNodesFromDOM>) => {
      if (lexicalHtmlMock.shouldThrow) throw new Error('lexical import boom')
      return actual.$generateNodesFromDOM(...args)
    },
  }
})

describe('RichTextEditor', () => {
  it('renders without crashing', () => {
    render(<RichTextEditor initialContent="" onChange={() => {}} />)
  })

  it('renders all toolbar buttons', () => {
    render(<RichTextEditor initialContent="" onChange={() => {}} />)

    expect(screen.getByTitle('Bold')).toBeInTheDocument()
    expect(screen.getByTitle('Italic')).toBeInTheDocument()
    expect(screen.getByTitle('Underline')).toBeInTheDocument()
    expect(screen.getByTitle('Heading 1')).toBeInTheDocument()
    expect(screen.getByTitle('Heading 2')).toBeInTheDocument()
    expect(screen.getByTitle('Heading 3')).toBeInTheDocument()
    expect(screen.getByTitle('Bullet list')).toBeInTheDocument()
    expect(screen.getByTitle('Numbered list')).toBeInTheDocument()
    expect(screen.getByTitle('Code block')).toBeInTheDocument()
  })

  it('renders the default placeholder text', () => {
    render(<RichTextEditor initialContent="" onChange={() => {}} />)
    expect(screen.getByText('Write your post content…')).toBeInTheDocument()
  })

  it('renders a custom placeholder', () => {
    render(<RichTextEditor initialContent="" onChange={() => {}} placeholder="Type here..." />)
    expect(screen.getByText('Type here...')).toBeInTheDocument()
  })

  it('renders the content-editable area', () => {
    render(<RichTextEditor initialContent="" onChange={() => {}} />)
    expect(screen.getByTestId('rich-text-editor')).toBeInTheDocument()
  })

  it('disables all toolbar buttons when disabled prop is true', () => {
    render(<RichTextEditor initialContent="" onChange={() => {}} disabled />)
    expect(screen.getByTitle('Bold')).toBeDisabled()
    expect(screen.getByTitle('Italic')).toBeDisabled()
    expect(screen.getByTitle('Underline')).toBeDisabled()
  })

  it('applies the disabled CSS class to the wrapper when disabled', () => {
    const { container } = render(<RichTextEditor initialContent="" onChange={() => {}} disabled />)
    expect(container.querySelector('.editor-wrapper')).toHaveClass('editor-disabled')
  })

  describe('initial-content parsing fallback', () => {
    beforeEach(() => {
      vi.spyOn(console, 'error').mockImplementation(() => {})
      lexicalHtmlMock.shouldThrow = false
    })
    afterEach(() => {
      vi.restoreAllMocks()
      lexicalHtmlMock.shouldThrow = false
    })

    it('renders parsed content normally when Lexical accepts the HTML', () => {
      const { container } = render(
        <RichTextEditor
          initialContent="<p>hello <strong>world</strong></p>"
          onChange={() => {}}
        />,
      )
      const editorBody = container.querySelector('[data-testid="rich-text-editor"]')
      expect(editorBody?.textContent).toContain('hello world')
    })

    it('falls back to plain text and logs the error when $generateNodesFromDOM throws', () => {
      lexicalHtmlMock.shouldThrow = true

      const { container } = render(
        <RichTextEditor
          initialContent="<p>important <strong>content</strong> that must survive</p>"
          onChange={() => {}}
        />,
      )

      // Content is preserved as plain text rather than the editor opening empty.
      const editorBody = container.querySelector('[data-testid="rich-text-editor"]')
      expect(editorBody?.textContent).toContain('important content that must survive')

      // Failure was logged with the underlying cause so it shows up in dev tools.
      expect(console.error).toHaveBeenCalledWith(
        expect.stringContaining('failed to import HTML into Lexical'),
        expect.any(Error),
      )
    })
  })
})
