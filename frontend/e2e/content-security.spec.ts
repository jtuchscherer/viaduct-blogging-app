import { test, expect } from '@playwright/test';
import { registerUser } from './fixtures/auth';
import { createPostViaAPI } from './fixtures/posts';

test('post previews and full posts remove executable markup and preserve rich text', async ({ page }) => {
  const suffix = Date.now();
  const author = await registerUser(page, `safe_html_${suffix}`);
  const title = `Safe HTML ${suffix}`;
  const payload = '<p><strong>Safe formatting</strong></p>'
    + '<img src="data:," onerror="document.documentElement.dataset.xss=\'executed\'">'
    + '<a href="javascript:document.documentElement.dataset.xss=\'executed\'">Unsafe link</a>'
    + '<script>document.documentElement.dataset.xss="executed"</script>';
  const post = await createPostViaAPI(page, author.token, title, payload);

  for (const path of ['/', `/post/${post.id}`]) {
    await page.goto(path);
    const content = path === '/'
      ? page.locator('.post-card').filter({ hasText: title }).locator('.post-preview')
      : page.locator('.post-content');

    await expect(content.locator('strong')).toHaveText('Safe formatting');
    await expect(content.locator('img')).toBeAttached();
    await expect(content.locator('script, [onerror]')).toHaveCount(0);
    const unsafeLink = content.locator('a', { hasText: 'Unsafe link' });
    await expect(unsafeLink).not.toHaveAttribute('href');
    await unsafeLink.click();
    await expect(page.locator('html')).not.toHaveAttribute('data-xss', 'executed');
  }
});
