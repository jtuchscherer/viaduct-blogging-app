/**
 * Draft / publish end-to-end tests (Phase 28).
 *
 * Written before the implementation, so these describe the intended contract:
 *
 *   - "Save draft" sits alongside the publishing submit button on the create page
 *   - a draft is reachable by its author and shows a draft banner
 *   - a draft is absent from the home feed
 *   - My Posts shows the author's drafts with a badge, and can filter to them
 *   - another user opening the draft's URL gets "Post not found" — not an error, so the
 *     response cannot be used to confirm that the post exists
 *   - publish / unpublish from the edit page moves the post in and out of the feed
 *
 * Selectors the implementation must provide: [data-testid="draft-banner"],
 * [data-testid="draft-badge"], select#status-filter, and buttons labelled
 * "Save draft" / "Publish" / "Unpublish".
 */

import { test, expect, type Page } from '@playwright/test';
import { registerAndLogin } from './fixtures/auth';

/** Fill in the blog form on /create. Returns the title used. */
async function fillBlogForm(page: Page, title: string, body: string) {
  await page.goto('/create');
  await page.fill('input#title', title);
  await page.click('[data-testid="rich-text-editor"]');
  await page.keyboard.type(body);
  return title;
}

/** Save the currently filled create form as a draft and return the resulting post URL. */
async function saveAsDraft(page: Page): Promise<string> {
  await page.click('button:has-text("Save draft")');
  await expect(page).toHaveURL(/\/post\//);
  return page.url();
}

test.describe('Drafts — authoring', () => {
  test('the create page offers Save draft alongside publishing', async ({ page }) => {
    await registerAndLogin(page, `draft_ui_${Date.now()}`);
    await page.goto('/create');

    await expect(page.locator('button:has-text("Save draft")')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();
  });

  test('saving a draft lands on the post and shows a draft banner', async ({ page }) => {
    await registerAndLogin(page, `draft_save_${Date.now()}`);
    const title = `Draft Post ${Date.now()}`;
    await fillBlogForm(page, title, 'Still working on this one.');
    await saveAsDraft(page);

    await expect(page.locator('main h1')).toContainText(title);
    await expect(page.locator('[data-testid="draft-banner"]')).toBeVisible();
  });

  test('a draft does not appear on the home feed', async ({ page }) => {
    await registerAndLogin(page, `draft_feed_${Date.now()}`);
    const title = `Hidden Draft ${Date.now()}`;
    await fillBlogForm(page, title, 'Nobody should see this yet.');
    await saveAsDraft(page);

    await page.goto('/');
    await expect(page.locator('main')).not.toContainText(title);
  });

  test('My Posts lists the draft with a badge', async ({ page }) => {
    await registerAndLogin(page, `draft_mine_${Date.now()}`);
    const title = `My Draft ${Date.now()}`;
    await fillBlogForm(page, title, 'Mine only.');
    await saveAsDraft(page);

    await page.goto('/my-posts');
    await expect(page.locator('main')).toContainText(title);
    await expect(page.locator('[data-testid="draft-badge"]').first()).toBeVisible();
  });

  test('My Posts can filter down to drafts', async ({ page }) => {
    await registerAndLogin(page, `draft_filter_${Date.now()}`);

    const published = `Published One ${Date.now()}`;
    await fillBlogForm(page, published, 'This one goes live.');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/\/post\//);

    const draft = `Draft One ${Date.now()}`;
    await fillBlogForm(page, draft, 'This one stays back.');
    await saveAsDraft(page);

    await page.goto('/my-posts');
    await page.selectOption('select#status-filter', 'DRAFT');

    await expect(page.locator('main')).toContainText(draft);
    await expect(page.locator('main')).not.toContainText(published);
  });
});

test.describe('Drafts — visibility to others', () => {
  test('another user opening a draft URL sees Post not found', async ({ page, browser }) => {
    await registerAndLogin(page, `draft_owner_${Date.now()}`);
    const title = `Private Draft ${Date.now()}`;
    await fillBlogForm(page, title, 'Author eyes only.');
    const draftUrl = await saveAsDraft(page);

    const otherContext = await browser.newContext();
    const otherPage = await otherContext.newPage();
    await registerAndLogin(otherPage, `draft_other_${Date.now()}`);
    await otherPage.goto(draftUrl);

    await expect(otherPage.locator('main')).toContainText('Post not found');
    await expect(otherPage.locator('main')).not.toContainText(title);
    await otherContext.close();
  });

  test('a signed-out visitor opening a draft URL sees Post not found', async ({ page, browser }) => {
    await registerAndLogin(page, `draft_anon_owner_${Date.now()}`);
    const title = `Anon Hidden Draft ${Date.now()}`;
    await fillBlogForm(page, title, 'Not for the public.');
    const draftUrl = await saveAsDraft(page);

    const anonContext = await browser.newContext();
    const anonPage = await anonContext.newPage();
    await anonPage.goto(draftUrl);

    await expect(anonPage.locator('main')).toContainText('Post not found');
    await anonContext.close();
  });
});

test.describe('Drafts — publish and unpublish', () => {
  test('publishing a draft puts it on the home feed', async ({ page }) => {
    await registerAndLogin(page, `draft_pub_${Date.now()}`);
    const title = `To Publish ${Date.now()}`;
    await fillBlogForm(page, title, 'Ready now.');
    const draftUrl = await saveAsDraft(page);
    const postId = draftUrl.split('/post/')[1];

    await page.goto(`/edit/${postId}`);
    await page.click('button:has-text("Publish")');

    await page.goto('/');
    await expect(page.locator('main')).toContainText(title);
  });

  test('unpublishing a published post removes it from the home feed', async ({ page }) => {
    await registerAndLogin(page, `draft_unpub_${Date.now()}`);
    const title = `To Unpublish ${Date.now()}`;
    await fillBlogForm(page, title, 'Live for a moment.');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/\/post\//);
    const postId = page.url().split('/post/')[1];

    await page.goto('/');
    await expect(page.locator('main')).toContainText(title);

    await page.goto(`/edit/${postId}`);
    await page.click('button:has-text("Unpublish")');

    await page.goto('/');
    await expect(page.locator('main')).not.toContainText(title);
  });
});

test.describe('Drafts — checklists', () => {
  test('a checklist can be saved as a draft and stays off the feed', async ({ page }) => {
    await registerAndLogin(page, `draft_cl_${Date.now()}`);
    await page.goto('/create');
    await page.locator('label').filter({ hasText: 'Checklist' }).click();

    const title = `Draft Checklist ${Date.now()}`;
    await page.fill('input#cl-title', title);
    await page.fill('input[placeholder="Item 1…"]', 'first item');
    await page.click('button:has-text("Save draft")');
    await expect(page).toHaveURL(/\/post\//);

    await expect(page.locator('[data-testid="draft-banner"]')).toBeVisible();

    await page.goto('/');
    await expect(page.locator('main')).not.toContainText(title);

    await page.goto('/my-posts');
    await expect(page.locator('main')).toContainText(title);
  });
});
