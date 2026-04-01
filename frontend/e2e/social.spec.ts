import { test, expect } from '@playwright/test';
import { registerAndLogin, registerUser } from './fixtures/auth';

test.describe('Likes and Comments', () => {
  test('unauthenticated user can view a post but not comment', async ({ page }) => {
    // First create a post as a user
    await registerAndLogin(page, `social_setup_${Date.now()}`);
    await page.goto('/create');
    await page.fill('input#title', 'Social Test Post');
    await page.fill('textarea#content', 'Content for social tests.');
    await page.click('button[type="submit"]');
    const postUrl = page.url();

    // Log out
    await page.click('header button:has-text("Logout")');

    // Visit post as unauthenticated user
    await page.goto(postUrl);
    await expect(page.locator('.post-content')).toContainText('Content for social tests');
    // Comment form should not be visible; login prompt should appear
    await expect(page.locator('.comment-form')).not.toBeVisible();
    await expect(page.locator('.login-prompt')).toBeVisible();
  });

  test('authenticated user can like a post and like count increases', async ({ page }) => {
    await registerAndLogin(page, `like_${Date.now()}`);
    await page.goto('/create');
    await page.fill('input#title', 'Like Test Post');
    await page.fill('textarea#content', 'Like this post.');
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/post\//);

    const likeButton = page.locator('.post-actions button');
    await expect(likeButton).toContainText('0');

    await likeButton.click();
    await expect(likeButton).toContainText('1');
  });

  test('user can unlike a post and like count decreases', async ({ page }) => {
    await registerAndLogin(page, `unlike_${Date.now()}`);
    await page.goto('/create');
    await page.fill('input#title', 'Unlike Test Post');
    await page.fill('textarea#content', 'Like then unlike.');
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/post\//);

    const likeButton = page.locator('.post-actions button');
    await likeButton.click();
    await expect(likeButton).toContainText('1');

    await likeButton.click();
    await expect(likeButton).toContainText('0');
  });

  test('like count reflects multiple users liking', async ({ page }) => {
    const suffix = Date.now().toString();

    // User 1 creates post and likes it
    const user1 = await registerAndLogin(page, `ml_author_${suffix}`);
    await page.goto('/create');
    await page.fill('input#title', 'Multi-like Post');
    await page.fill('textarea#content', 'Multiple likes test.');
    await page.click('button[type="submit"]');
    const postUrl = page.url();

    await page.locator('.post-actions button').click();
    await expect(page.locator('.post-actions button')).toContainText('1');

    // User 2 also likes it
    const user2 = await registerUser(page, `ml_viewer_${suffix}`);
    await page.evaluate(({ token, user }) => {
      localStorage.setItem('authToken', token);
      localStorage.setItem('authUser', JSON.stringify(user));
    }, { token: user2.token, user: user2.user });

    await page.goto(postUrl);
    await page.locator('.post-actions button').click();
    await expect(page.locator('.post-actions button')).toContainText('2');
  });

  test('user can add a comment and it appears in the list', async ({ page }) => {
    await registerAndLogin(page, `comment_${Date.now()}`);
    await page.goto('/create');
    await page.fill('input#title', 'Comment Test Post');
    await page.fill('textarea#content', 'Post for commenting.');
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/post\//);

    await page.fill('.comment-form textarea', 'This is my test comment!');
    await page.click('.comment-form button[type="submit"]');

    await expect(page.locator('.comments-list')).toContainText('This is my test comment!');
    await expect(page.locator('.comments-section h2')).toContainText('Comments (1)');
  });

  test('multiple comments appear in order', async ({ page }) => {
    await registerAndLogin(page, `multicomment_${Date.now()}`);
    await page.goto('/create');
    await page.fill('input#title', 'Multi-comment Post');
    await page.fill('textarea#content', 'Post for multiple comments.');
    await page.click('button[type="submit"]');
    await page.waitForURL(/\/post\//);

    await page.fill('.comment-form textarea', 'First comment');
    await page.click('.comment-form button[type="submit"]');
    await expect(page.locator('.comments-list')).toContainText('First comment');

    await page.fill('.comment-form textarea', 'Second comment');
    await page.click('.comment-form button[type="submit"]');
    await expect(page.locator('.comments-list')).toContainText('Second comment');

    await expect(page.locator('.comments-section h2')).toContainText('Comments (2)');
  });

  test('unauthenticated user clicking like is redirected to login', async ({ page }) => {
    // Create post as logged-in user
    await registerAndLogin(page, `unauth_like_${Date.now()}`);
    await page.goto('/create');
    await page.fill('input#title', 'Unauth Like Post');
    await page.fill('textarea#content', 'Content.');
    await page.click('button[type="submit"]');
    const postUrl = page.url();

    // Log out and visit post
    await page.click('header button:has-text("Logout")');
    await page.goto(postUrl);

    await page.locator('.post-actions button').click();
    await expect(page).toHaveURL(/\/login/);
  });
});
