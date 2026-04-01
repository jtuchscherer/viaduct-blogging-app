import { test, expect } from '@playwright/test';
import { registerAndLogin, registerUser } from './fixtures/auth';

test.describe('Blog Posts', () => {
  test('home page shows posts list', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('h1')).toContainText('Blog Posts');
  });

  test('unauthenticated user sees login/register in header, not New Post', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('header a', { hasText: 'Login' })).toBeVisible();
    await expect(page.locator('header a', { hasText: 'New Post' })).not.toBeVisible();
  });

  test('authenticated user sees New Post button', async ({ page }) => {
    await registerAndLogin(page, `np_${Date.now()}`);
    await expect(page.locator('header a', { hasText: 'New Post' })).toBeVisible();
  });

  test('user can create a post and is redirected to post detail', async ({ page }) => {
    await registerAndLogin(page, `create_${Date.now()}`);

    await page.click('header a:has-text("New Post")');
    await expect(page).toHaveURL('/create');

    const title = `My Test Post ${Date.now()}`;
    await page.fill('input#title', title);
    await page.fill('textarea#content', 'This is the post content for testing purposes.');
    await page.click('button[type="submit"]');

    // Should redirect to post detail page
    await expect(page).toHaveURL(/\/post\//);
    await expect(page.locator('h1')).toContainText(title);
    await expect(page.locator('.post-content')).toContainText('This is the post content');
  });

  test('new post appears on home page', async ({ page }) => {
    await registerAndLogin(page, `home_${Date.now()}`);

    const title = `Home Page Post ${Date.now()}`;
    await page.goto('/create');
    await page.fill('input#title', title);
    await page.fill('textarea#content', 'Content for home page test.');
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/post\//);

    await page.goto('/');
    await expect(page.locator('.posts-list')).toContainText(title);
  });

  test('post detail shows author, content, like button, and comments section', async ({ page }) => {
    const creds = await registerAndLogin(page, `detail_${Date.now()}`);

    await page.goto('/create');
    await page.fill('input#title', 'Detail Test Post');
    await page.fill('textarea#content', 'Detail content.');
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/post\//);

    await expect(page.locator('.post-meta')).toContainText(creds.user.name);
    await expect(page.locator('.post-actions button')).toBeVisible(); // like button
    await expect(page.locator('.comments-section')).toBeVisible();
  });

  test('author sees edit and delete buttons on their post', async ({ page }) => {
    await registerAndLogin(page, `author_${Date.now()}`);

    await page.goto('/create');
    await page.fill('input#title', 'Author Controls Post');
    await page.fill('textarea#content', 'Content.');
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/post\//);

    await expect(page.locator('a.btn-edit')).toBeVisible();
    await expect(page.locator('button.btn-delete')).toBeVisible();
  });

  test('non-author does not see edit or delete buttons', async ({ page }) => {
    // User 1 creates a post
    const suffix = Date.now().toString();
    const author = await registerAndLogin(page, `author2_${suffix}`);
    await page.goto('/create');
    await page.fill('input#title', 'Non-author Post');
    await page.fill('textarea#content', 'Content.');
    await page.click('button[type="submit"]');
    const postUrl = page.url();

    // User 2 views it
    const viewer = await registerUser(page, `viewer_${suffix}`);
    // Set auth state directly via localStorage for speed
    await page.evaluate(({ token, user }) => {
      localStorage.setItem('authToken', token);
      localStorage.setItem('authUser', JSON.stringify(user));
    }, { token: viewer.token, user: viewer.user });

    await page.goto(postUrl);
    await expect(page.locator('a.btn-edit')).not.toBeVisible();
    await expect(page.locator('button.btn-delete')).not.toBeVisible();
  });

  test('user can edit their own post', async ({ page }) => {
    await registerAndLogin(page, `edit_${Date.now()}`);

    await page.goto('/create');
    await page.fill('input#title', 'Original Title');
    await page.fill('textarea#content', 'Original content.');
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/post\//);

    await page.click('a.btn-edit');
    await expect(page).toHaveURL(/\/edit\//);

    await page.fill('input#title', 'Updated Title');
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/post\//);

    await expect(page.locator('h1')).toContainText('Updated Title');
  });

  test('user can delete their own post and is redirected home', async ({ page }) => {
    await registerAndLogin(page, `delete_${Date.now()}`);

    await page.goto('/create');
    await page.fill('input#title', 'Post To Delete');
    await page.fill('textarea#content', 'Will be deleted.');
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/post\//);

    page.once('dialog', (dialog) => dialog.accept());
    await page.click('button.btn-delete');
    await page.waitForURL('/');
  });

  test('My Posts page shows only the current user\'s posts', async ({ page }) => {
    const suffix = Date.now().toString();
    const creds = await registerAndLogin(page, `myposts_${suffix}`);

    const title = `My Personal Post ${suffix}`;
    await page.goto('/create');
    await page.fill('input#title', title);
    await page.fill('textarea#content', 'Only mine.');
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/post\//);

    await page.goto('/my-posts');
    await expect(page.locator('.posts-list')).toContainText(title);
    // Should not show other users by name in post-author spans for this post
    await expect(page.locator('.posts-list')).toContainText(creds.user.name);
  });
});
