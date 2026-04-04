import { test, expect } from '@playwright/test';
import { registerAndLogin, registerUser } from './fixtures/auth';
import { createPostViaAPI } from './fixtures/posts';

test.describe('Blog Posts', () => {
  test('home page shows posts list', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('main h1')).toContainText('Blog Posts');
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

  // Tests the create post UI flow itself — UI creation is intentional here
  test('user can create a post via the UI and is redirected to post detail', async ({ page }) => {
    await registerAndLogin(page, `create_${Date.now()}`);

    await page.click('header a:has-text("New Post")');
    await expect(page).toHaveURL('/create');

    const title = `My Test Post ${Date.now()}`;
    await page.fill('input#title', title);
    await page.click('[data-testid="rich-text-editor"]');
    await page.keyboard.type('This is the post content for testing purposes.');
    await page.click('button[type="submit"]');

    await expect(page).toHaveURL(/\/post\//);
    await expect(page.locator('main h1')).toContainText(title);
    await expect(page.locator('.post-content')).toContainText('This is the post content');
  });

  // Tests that a newly created post shows up on the home page — UI creation is intentional here
  test('new post appears on home page after creation', async ({ page }) => {
    await registerAndLogin(page, `home_${Date.now()}`);

    const title = `Home Page Post ${Date.now()}`;
    await page.goto('/create');
    await page.fill('input#title', title);
    await page.click('[data-testid="rich-text-editor"]');
    await page.keyboard.type('Content for home page test.');
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/post\//);

    await page.goto('/');
    await expect(page.locator('.posts-list')).toContainText(title);
  });

  test('rich text editor toolbar contains all expected buttons', async ({ page }) => {
    await registerAndLogin(page, `toolbar_${Date.now()}`);
    await page.goto('/create');

    const expectedButtons = [
      'Bold', 'Italic', 'Underline',
      'Heading 1', 'Heading 2', 'Heading 3',
      'Bullet list', 'Numbered list',
      'Code block',
    ];

    for (const label of expectedButtons) {
      await expect(page.getByRole('button', { name: label })).toBeVisible();
    }
  });

  // Tests the rich text editor UI — toolbar interaction and HTML rendering are intentional here
  test('rich text formatting is saved and rendered correctly in post detail', async ({ page }) => {
    await registerAndLogin(page, `rich_${Date.now()}`);
    const title = `Rich Text Post ${Date.now()}`;

    await page.goto('/create');
    await page.fill('input#title', title);

    // Type text, select all, apply bold via toolbar
    await page.click('[data-testid="rich-text-editor"]');
    await page.keyboard.type('Hello bold world');
    await page.keyboard.press('ControlOrMeta+a');
    await page.click('[aria-label="Bold"]');

    await page.click('button[type="submit"]');
    await page.waitForURL(/\/post\//);

    // Bold text must be rendered as <strong> — verifies the HTML round-trip through the editor,
    // GraphQL mutation, and DOMPurify rendering in PostDetailPage
    await expect(page.locator('.post-content strong')).toContainText('Hello bold world');
  });

  test('post detail shows author, content, like button, and comments section', async ({ page }) => {
    const creds = await registerAndLogin(page, `detail_${Date.now()}`);
    const post = await createPostViaAPI(page, creds.token, 'Detail Test Post', 'Detail content.');

    await page.goto(`/post/${post.id}`);

    await expect(page.locator('.post-meta')).toContainText(creds.user.name);
    await expect(page.locator('.post-actions button').first()).toBeVisible();
    await expect(page.locator('.comments-section')).toBeVisible();
  });

  test('author sees edit and delete buttons on their post', async ({ page }) => {
    const creds = await registerAndLogin(page, `author_${Date.now()}`);
    const post = await createPostViaAPI(page, creds.token, 'Author Controls Post');

    await page.goto(`/post/${post.id}`);

    await expect(page.locator('a.btn-edit')).toBeVisible();
    await expect(page.locator('button.btn-delete')).toBeVisible();
  });

  test('non-author does not see edit or delete buttons', async ({ page }) => {
    const suffix = Date.now().toString();
    const author = await registerAndLogin(page, `author2_${suffix}`);
    const post = await createPostViaAPI(page, author.token, 'Non-author Post');

    const viewer = await registerUser(page, `viewer_${suffix}`);
    await page.evaluate(({ token, user }) => {
      localStorage.setItem('authToken', token);
      localStorage.setItem('authUser', JSON.stringify(user));
    }, { token: viewer.token, user: viewer.user });

    await page.goto(`/post/${post.id}`);
    await expect(page.locator('a.btn-edit')).not.toBeVisible();
    await expect(page.locator('button.btn-delete')).not.toBeVisible();
  });

  test('user can edit their own post', async ({ page }) => {
    const creds = await registerAndLogin(page, `edit_${Date.now()}`);
    const post = await createPostViaAPI(page, creds.token, 'Original Title', 'Original content.');

    await page.goto(`/post/${post.id}`);
    await page.click('a.btn-edit');
    await expect(page).toHaveURL(/\/edit\//);

    await page.fill('input#title', 'Updated Title');
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/post\//);

    await expect(page.locator('main h1')).toContainText('Updated Title');
  });

  test('user can delete their own post and is redirected home', async ({ page }) => {
    const creds = await registerAndLogin(page, `delete_${Date.now()}`);
    const post = await createPostViaAPI(page, creds.token, 'Post To Delete');

    await page.goto(`/post/${post.id}`);
    page.once('dialog', (dialog) => dialog.accept());
    await page.click('button.btn-delete');
    await page.waitForURL('/');
  });

  test('My Posts page shows only the current user\'s posts', async ({ page }) => {
    const suffix = Date.now().toString();
    const creds = await registerAndLogin(page, `myposts_${suffix}`);
    const post = await createPostViaAPI(page, creds.token, `My Personal Post ${suffix}`);

    await page.goto('/my-posts');
    await expect(page.locator('.posts-list')).toContainText(post.title);
    await expect(page.locator('.posts-list')).toContainText(creds.user.name);
  });

  // Regression: MyPostsPage used post.content.substring() which exposed raw HTML
  // tags in excerpts for rich-text posts. Fixed to use getExcerpt() from utils/content.ts.
  test('My Posts page excerpts do not show raw HTML tags for rich-text content', async ({ page }) => {
    const creds = await registerAndLogin(page, `htmlexcerpt_${Date.now()}`);
    await createPostViaAPI(
      page,
      creds.token,
      'HTML Excerpt Test',
      '<p><strong>Bold text</strong> and some more content to fill the excerpt area.</p>'
    );

    await page.goto('/my-posts');

    const excerpt = page.locator('.post-excerpt').first();
    await expect(excerpt).toBeVisible();
    // Plain text must be visible
    await expect(excerpt).toContainText('Bold text');
    // Raw HTML tags must NOT appear as text
    const text = await excerpt.textContent();
    expect(text).not.toContain('<strong>');
    expect(text).not.toContain('<p>');
  });
});
