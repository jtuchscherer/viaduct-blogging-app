export function isContentEmpty(html: string): boolean {
  if (!html) return true;
  return new DOMParser().parseFromString(html, 'text/html').body.textContent?.trim() === '';
}

export function getExcerpt(html: string, maxLength = 200): string {
  if (!html) return '';
  const text = new DOMParser().parseFromString(html, 'text/html').body.textContent ?? '';
  return text.length > maxLength ? `${text.substring(0, maxLength)}...` : text;
}
