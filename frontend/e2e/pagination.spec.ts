import { test, expect } from '@playwright/test';
import { registerAndLogin } from './fixtures/auth';
import { createPostViaAPI } from './fixtures/posts';

const PAGE_SIZE = 10;

test.describe('Pagination', () => {
  test('home page shows "Showing X of Y posts" counter', async ({ page }) => {
    const creds = await registerAndLogin(page, `pgcount_${Date.now()}`);
    await createPostViaAPI(page, creds.token, 'Counter Post');

    await page.goto('/');
    await expect(page.locator('.post-count')).toContainText('Showing');
    await expect(page.locator('.post-count')).toContainText('posts');
  });

  test('home page loads at most PAGE_SIZE posts initially', async ({ page }) => {
    const suffix = Date.now().toString();
    const creds = await registerAndLogin(page, `pgsize_${suffix}`);

    // Create more posts than a single page can hold
    for (let i = 1; i <= PAGE_SIZE + 2; i++) {
      await createPostViaAPI(page, creds.token, `SizeTest Post ${i} ${suffix}`);
    }

    await page.goto('/');

    // Initial load must not exceed PAGE_SIZE items
    await expect(page.locator('.post-card').first()).toBeVisible();
    const count = await page.locator('.post-card').count();
    expect(count).toBeLessThanOrEqual(PAGE_SIZE);
  });

  test('Load More button appears when there are more posts than a single page', async ({ page }) => {
    const suffix = Date.now().toString();
    const creds = await registerAndLogin(page, `pgbtn_${suffix}`);

    // Create enough posts to guarantee a second page regardless of existing DB state
    for (let i = 1; i <= PAGE_SIZE + 2; i++) {
      await createPostViaAPI(page, creds.token, `BtnTest Post ${i} ${suffix}`);
    }

    await page.goto('/');
    await expect(page.locator('.btn-load-more')).toBeVisible();
  });

  test('clicking Load More appends more posts to the list', async ({ page }) => {
    const suffix = Date.now().toString();
    const creds = await registerAndLogin(page, `pgload_${suffix}`);

    // Create enough posts to guarantee at least two pages
    for (let i = 1; i <= PAGE_SIZE + 2; i++) {
      await createPostViaAPI(page, creds.token, `LoadMore Post ${i} ${suffix}`);
    }

    await page.goto('/');
    await expect(page.locator('.btn-load-more')).toBeVisible();

    const countBefore = await page.locator('.post-card').count();
    await page.click('.btn-load-more');

    // After loading more there are strictly more post cards visible
    await expect(async () => {
      const countAfter = await page.locator('.post-card').count();
      expect(countAfter).toBeGreaterThan(countBefore);
    }).toPass();
  });
});
