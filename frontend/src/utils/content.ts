export function isContentEmpty(html: string): boolean {
  if (!html) return true;
  return new DOMParser().parseFromString(html, 'text/html').body.textContent?.trim() === '';
}
