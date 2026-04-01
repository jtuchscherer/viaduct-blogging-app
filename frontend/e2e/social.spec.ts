import { test, expect } from '@playwright/test';
import { registerAndLogin, registerUser } from './fixtures/auth';
import { createPostViaAPI } from './fixtures/posts';

test.describe('Likes and Comments', () => {
  test('unauthenticated user can view a post but not comment', async ({ page }) => {
    const creds = await registerAndLogin(page, `social_setup_${Date.now()}`);
    const post = await createPostViaAPI(page, creds.token, 'Social Test Post', 'Content for social tests.');
    await page.click('header button:has-text("Logout")');

    await page.goto(`/post/${post.id}`);
    await expect(page.locator('.post-content')).toContainText('Content for social tests');
    await expect(page.locator('.comment-form')).not.toBeVisible();
    await expect(page.locator('.login-prompt')).toBeVisible();
  });

  test('authenticated user can like a post and like count increases', async ({ page }) => {
    const creds = await registerAndLogin(page, `like_${Date.now()}`);
    const post = await createPostViaAPI(page, creds.token, 'Like Test Post');

    await page.goto(`/post/${post.id}`);
    const likeButton = page.locator('.post-actions button');
    await expect(likeButton).toContainText('0');

    await likeButton.click();
    await expect(likeButton).toContainText('1');
  });

  test('user can unlike a post and like count decreases', async ({ page }) => {
    const creds = await registerAndLogin(page, `unlike_${Date.now()}`);
    const post = await createPostViaAPI(page, creds.token, 'Unlike Test Post');

    await page.goto(`/post/${post.id}`);
    const likeButton = page.locator('.post-actions button');
    await likeButton.click();
    await expect(likeButton).toContainText('1');

    await likeButton.click();
    await expect(likeButton).toContainText('0');
  });

  test('like count reflects multiple users liking', async ({ page }) => {
    const suffix = Date.now().toString();
    const user1 = await registerAndLogin(page, `ml_author_${suffix}`);
    const post = await createPostViaAPI(page, user1.token, 'Multi-like Post');

    // User 1 likes
    await page.goto(`/post/${post.id}`);
    await page.locator('.post-actions button').click();
    await expect(page.locator('.post-actions button')).toContainText('1');

    // User 2 likes
    const user2 = await registerUser(page, `ml_viewer_${suffix}`);
    await page.evaluate(({ token, user }) => {
      localStorage.setItem('authToken', token);
      localStorage.setItem('authUser', JSON.stringify(user));
    }, { token: user2.token, user: user2.user });

    await page.goto(`/post/${post.id}`);
    await page.locator('.post-actions button').click();
    await expect(page.locator('.post-actions button')).toContainText('2');
  });

  test('user can add a comment and it appears in the list', async ({ page }) => {
    const creds = await registerAndLogin(page, `comment_${Date.now()}`);
    const post = await createPostViaAPI(page, creds.token, 'Comment Test Post');

    await page.goto(`/post/${post.id}`);
    await page.fill('.comment-form textarea', 'This is my test comment!');
    await page.click('.comment-form button[type="submit"]');

    await expect(page.locator('.comments-list')).toContainText('This is my test comment!');
    await expect(page.locator('.comments-section h2')).toContainText('Comments (1)');
  });

  test('multiple comments appear in order', async ({ page }) => {
    const creds = await registerAndLogin(page, `multicomment_${Date.now()}`);
    const post = await createPostViaAPI(page, creds.token, 'Multi-comment Post');

    await page.goto(`/post/${post.id}`);

    await page.fill('.comment-form textarea', 'First comment');
    await page.click('.comment-form button[type="submit"]');
    await expect(page.locator('.comments-list')).toContainText('First comment');

    await page.fill('.comment-form textarea', 'Second comment');
    await page.click('.comment-form button[type="submit"]');
    await expect(page.locator('.comments-list')).toContainText('Second comment');

    await expect(page.locator('.comments-section h2')).toContainText('Comments (2)');
  });

  test('unauthenticated user clicking like is redirected to login', async ({ page }) => {
    const creds = await registerAndLogin(page, `unauth_like_${Date.now()}`);
    const post = await createPostViaAPI(page, creds.token, 'Unauth Like Post');
    await page.click('header button:has-text("Logout")');

    await page.goto(`/post/${post.id}`);
    await page.locator('.post-actions button').click();
    await expect(page).toHaveURL(/\/login/);
  });
});
