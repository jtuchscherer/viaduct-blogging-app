import { describe, it, expect } from 'vitest'
import { getHtmlPreview, isContentEmpty } from '../../src/utils/content'

describe('isContentEmpty', () => {
  it('returns true for an empty string', () => {
    expect(isContentEmpty('')).toBe(true)
  })

  it('returns true for whitespace-only text', () => {
    expect(isContentEmpty('   \n  ')).toBe(true)
  })

  it('returns true for HTML with no visible text', () => {
    expect(isContentEmpty('<p></p><p>   </p>')).toBe(true)
  })

  it('returns false for plain text', () => {
    expect(isContentEmpty('Hello world')).toBe(false)
  })

  it('returns false for HTML with text content', () => {
    expect(isContentEmpty('<p>Hello</p>')).toBe(false)
  })
})

describe('getHtmlPreview', () => {
  it('returns empty string for empty input', () => {
    expect(getHtmlPreview('')).toBe('')
  })

  it('wraps a plain-text line in a paragraph tag', () => {
    const result = getHtmlPreview('Hello world')
    expect(result).toContain('<p>Hello world</p>')
  })

  it('escapes angle brackets in plain text', () => {
    const result = getHtmlPreview('A <script> tag')
    expect(result).toContain('&lt;script&gt;')
    expect(result).not.toContain('<script>')
  })

  it('limits plain text to maxElements lines', () => {
    const text = 'line1\nline2\nline3\nline4\nline5'
    const result = getHtmlPreview(text, 3)
    expect(result).toContain('<p>line1</p>')
    expect(result).toContain('<p>line3</p>')
    expect(result).not.toContain('<p>line4</p>')
  })

  it('returns first N block elements from HTML', () => {
    const html = '<p>one</p><p>two</p><p>three</p><p>four</p>'
    const result = getHtmlPreview(html, 2)
    expect(result).toContain('one')
    expect(result).toContain('two')
    expect(result).not.toContain('three')
  })

  it('defaults to 3 elements from HTML', () => {
    const html = '<p>a</p><p>b</p><p>c</p><p>d</p>'
    const result = getHtmlPreview(html)
    expect(result).toContain('a')
    expect(result).toContain('c')
    expect(result).not.toContain('d')
  })

  it('strips script tags from HTML to prevent XSS', () => {
    const html = '<p>Safe</p><script>alert("xss")</script>'
    const result = getHtmlPreview(html)
    expect(result).not.toContain('<script>')
    expect(result).toContain('Safe')
  })
})
