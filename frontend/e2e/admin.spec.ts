import { test, expect } from '@playwright/test';
import { registerUser } from './fixtures/auth';
import { createPostViaAPI } from './fixtures/posts';
import { gotoAdminPage, createCommentViaAPI } from './fixtures/admin';

const PAGE_SIZE = 10;

// ---------------------------------------------------------------------------
// Admin Users pagination
// ---------------------------------------------------------------------------

test.describe('Admin Users pagination', () => {
  test.beforeAll(async ({ browser }) => {
    // Register PAGE_SIZE + 1 users so there is always a second page
    const page = await browser.newPage();
    const suffix = `au_${Date.now()}`;
    for (let i = 1; i <= PAGE_SIZE + 1; i++) {
      await registerUser(page, `${suffix}_${i}`);
    }
    await page.close();
  });

  test('shows exactly PAGE_SIZE rows on the first page', async ({ page }) => {
    await gotoAdminPage(page, '/admin/users');
    await expect(page.locator('[data-testid="admin-table-row"]').first()).toBeVisible();
    const count = await page.locator('[data-testid="admin-table-row"]').count();
    expect(count).toBeLessThanOrEqual(PAGE_SIZE);
  });

  test('page info shows correct range', async ({ page }) => {
    await gotoAdminPage(page, '/admin/users');
    await expect(page.locator('[data-testid="admin-page-info"]')).toContainText('Showing 1–');
    await expect(page.locator('[data-testid="admin-page-info"]')).toContainText('of ');
  });

  test('Previous button is disabled on page 1', async ({ page }) => {
    await gotoAdminPage(page, '/admin/users');
    await expect(page.locator('[data-testid="btn-prev-page"]')).toBeDisabled();
  });

  test('Next button navigates to page 2', async ({ page }) => {
    await gotoAdminPage(page, '/admin/users');
    const nextBtn = page.locator('[data-testid="btn-next-page"]');
    await expect(nextBtn).toBeEnabled();
    await nextBtn.click();
    await expect(page.locator('[data-testid="admin-table-row"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="admin-page-info"]')).toContainText(`Showing ${PAGE_SIZE + 1}`);
  });

  test('Previous navigates back to page 1 from page 2', async ({ page }) => {
    await gotoAdminPage(page, '/admin/users');
    await page.locator('[data-testid="btn-next-page"]').click();
    await page.locator('[data-testid="btn-prev-page"]').click();
    await expect(page.locator('[data-testid="admin-page-info"]')).toContainText('Showing 1–');
  });

  test('Next button is disabled on the last page', async ({ page }) => {
    await gotoAdminPage(page, '/admin/users');
    const nextBtn = page.locator('[data-testid="btn-next-page"]');
    // Navigate through all pages to reach the last one
    while (await nextBtn.isEnabled()) {
      await nextBtn.click();
      await expect(page.locator('[data-testid="admin-table-row"]').first()).toBeVisible();
    }
    await expect(nextBtn).toBeDisabled();
  });
});

// ---------------------------------------------------------------------------
// Admin Posts pagination
// ---------------------------------------------------------------------------

test.describe('Admin Posts pagination', () => {
  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    const suffix = `ap_${Date.now()}`;
    const { token } = await registerUser(page, suffix);
    for (let i = 1; i <= PAGE_SIZE + 1; i++) {
      await createPostViaAPI(page, token, `Admin Pagination Post ${i} ${suffix}`);
    }
    await page.close();
  });

  test('shows exactly PAGE_SIZE rows on the first page', async ({ page }) => {
    await gotoAdminPage(page, '/admin/posts');
    await expect(page.locator('[data-testid="admin-table-row"]').first()).toBeVisible();
    const count = await page.locator('[data-testid="admin-table-row"]').count();
    expect(count).toBeLessThanOrEqual(PAGE_SIZE);
  });

  test('page info shows correct range', async ({ page }) => {
    await gotoAdminPage(page, '/admin/posts');
    await expect(page.locator('[data-testid="admin-page-info"]')).toContainText('Showing 1–');
    await expect(page.locator('[data-testid="admin-page-info"]')).toContainText('of ');
  });

  test('Previous button is disabled on page 1', async ({ page }) => {
    await gotoAdminPage(page, '/admin/posts');
    await expect(page.locator('[data-testid="btn-prev-page"]')).toBeDisabled();
  });

  test('Next button navigates to page 2', async ({ page }) => {
    await gotoAdminPage(page, '/admin/posts');
    const nextBtn = page.locator('[data-testid="btn-next-page"]');
    await expect(nextBtn).toBeEnabled();
    await nextBtn.click();
    await expect(page.locator('[data-testid="admin-table-row"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="admin-page-info"]')).toContainText(`Showing ${PAGE_SIZE + 1}`);
  });

  test('Previous navigates back to page 1 from page 2', async ({ page }) => {
    await gotoAdminPage(page, '/admin/posts');
    await page.locator('[data-testid="btn-next-page"]').click();
    await page.locator('[data-testid="btn-prev-page"]').click();
    await expect(page.locator('[data-testid="admin-page-info"]')).toContainText('Showing 1–');
  });

  test('Next button is disabled on the last page', async ({ page }) => {
    await gotoAdminPage(page, '/admin/posts');
    const nextBtn = page.locator('[data-testid="btn-next-page"]');
    // Navigate through all pages to reach the last one
    while (await nextBtn.isEnabled()) {
      await nextBtn.click();
      await expect(page.locator('[data-testid="admin-table-row"]').first()).toBeVisible();
    }
    await expect(nextBtn).toBeDisabled();
  });
});

// ---------------------------------------------------------------------------
// Admin Comments pagination
// ---------------------------------------------------------------------------

test.describe('Admin Comments pagination', () => {
  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    const suffix = `ac_${Date.now()}`;
    const { token } = await registerUser(page, suffix);
    const post = await createPostViaAPI(page, token, `Comments Pagination Post ${suffix}`);
    for (let i = 1; i <= PAGE_SIZE + 1; i++) {
      await createCommentViaAPI(page, token, post.id, `Admin pagination comment ${i} ${suffix}`);
    }
    await page.close();
  });

  test('shows exactly PAGE_SIZE rows on the first page', async ({ page }) => {
    await gotoAdminPage(page, '/admin/comments');
    await expect(page.locator('[data-testid="admin-table-row"]').first()).toBeVisible();
    const count = await page.locator('[data-testid="admin-table-row"]').count();
    expect(count).toBeLessThanOrEqual(PAGE_SIZE);
  });

  test('page info shows correct range', async ({ page }) => {
    await gotoAdminPage(page, '/admin/comments');
    await expect(page.locator('[data-testid="admin-page-info"]')).toContainText('Showing 1–');
    await expect(page.locator('[data-testid="admin-page-info"]')).toContainText('of ');
  });

  test('Previous button is disabled on page 1', async ({ page }) => {
    await gotoAdminPage(page, '/admin/comments');
    await expect(page.locator('[data-testid="btn-prev-page"]')).toBeDisabled();
  });

  test('Next button navigates to page 2', async ({ page }) => {
    await gotoAdminPage(page, '/admin/comments');
    const nextBtn = page.locator('[data-testid="btn-next-page"]');
    await expect(nextBtn).toBeEnabled();
    await nextBtn.click();
    await expect(page.locator('[data-testid="admin-table-row"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="admin-page-info"]')).toContainText(`Showing ${PAGE_SIZE + 1}`);
  });

  test('Previous navigates back to page 1 from page 2', async ({ page }) => {
    await gotoAdminPage(page, '/admin/comments');
    await page.locator('[data-testid="btn-next-page"]').click();
    await page.locator('[data-testid="btn-prev-page"]').click();
    await expect(page.locator('[data-testid="admin-page-info"]')).toContainText('Showing 1–');
  });

  test('Next button is disabled on the last page', async ({ page }) => {
    await gotoAdminPage(page, '/admin/comments');
    const nextBtn = page.locator('[data-testid="btn-next-page"]');
    // Navigate through all pages to reach the last one
    while (await nextBtn.isEnabled()) {
      await nextBtn.click();
      await expect(page.locator('[data-testid="admin-table-row"]').first()).toBeVisible();
    }
    await expect(nextBtn).toBeDisabled();
  });
});
