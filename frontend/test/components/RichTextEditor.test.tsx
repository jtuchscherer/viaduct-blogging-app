import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import RichTextEditor from '../../src/components/RichTextEditor'

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
})
