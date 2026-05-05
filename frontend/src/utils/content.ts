import DOMPurify from 'dompurify';

export function isContentEmpty(html: string): boolean {
  if (!html) return true;
  return new DOMParser().parseFromString(html, 'text/html').body.textContent?.trim() === '';
}

/**
 * Get a truncated HTML preview that preserves formatting.
 * Keeps the first `maxElements` block-level elements.
 */
export function getHtmlPreview(html: string, maxElements = 3): string {
  if (!html) return '';
  const trimmed = html.trim();

  // Handle legacy plain-text content (no HTML tags)
  if (!trimmed.startsWith('<')) {
    const lines = trimmed.split('\n').slice(0, maxElements);
    return lines.map((line) => `<p>${line.replace(/</g, '&lt;').replace(/>/g, '&gt;')}</p>`).join('');
  }

  const doc = new DOMParser().parseFromString(trimmed, 'text/html');
  const children = Array.from(doc.body.children);
  const preview = children.slice(0, maxElements);

  const previewHtml = preview.map((el) => el.outerHTML).join('');
  return DOMPurify.sanitize(previewHtml);
}

export function formatReadTime(minutes: number): string {
  return `${Math.max(1, Math.round(minutes))} min read`;
}
