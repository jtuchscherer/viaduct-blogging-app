import { test, expect } from '@playwright/test';
import { registerUser, loginViaUI, registerAndLogin, withTokenExpiration, API_URL, GRAPHQL_URL } from './fixtures/auth';

test.describe('Authentication', () => {
  test('register page renders correctly', async ({ page }) => {
    await page.goto('/register');
    await expect(page.locator('h2')).toContainText('Register');
    await expect(page.locator('input#username')).toBeVisible();
    await expect(page.locator('input#email')).toBeVisible();
    await expect(page.locator('input#name')).toBeVisible();
    await expect(page.locator('input#password')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeVisible();
  });

  test('user can register via UI and is logged in', async ({ page }) => {
    const suffix = `reg_${Date.now()}`;
    const username = `testuser_${suffix}`;

    await page.goto('/register');
    await page.fill('input#username', username);
    await page.fill('input#email', `${username}@test.com`);
    await page.fill('input#name', `Test User ${suffix}`);
    await page.fill('input#password', 'password123');
    await page.click('button[type="submit"]');

    // Should redirect to home after registration
    await page.waitForURL('/');
    // Header should show the user's name and logout button
    await expect(page.locator('header')).toContainText(`Test User ${suffix}`);
    await expect(page.locator('header button', { hasText: 'Logout' })).toBeVisible();
  });

  test('duplicate username shows error', async ({ page }) => {
    const creds = await registerUser(page, `dup_${Date.now()}`);

    await page.goto('/register');
    await page.fill('input#username', creds.username);
    await page.fill('input#email', `other_${creds.username}@test.com`);
    await page.fill('input#name', 'Another User');
    await page.fill('input#password', 'password123');
    await page.click('button[type="submit"]');

    await expect(page.locator('.error-message')).toBeVisible();
  });

  test('login page renders correctly', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('h2')).toContainText('Login');
    await expect(page.locator('input#username')).toBeVisible();
    await expect(page.locator('input#password')).toBeVisible();
  });

  test('user can log in with valid credentials', async ({ page }) => {
    const creds = await registerUser(page, `login_${Date.now()}`);
    await loginViaUI(page, creds.username, creds.password);

    await expect(page.locator('header')).toContainText(creds.user.name);
    await expect(page.locator('header button', { hasText: 'Logout' })).toBeVisible();
  });

  test('invalid credentials show error', async ({ page }) => {
    await page.goto('/login');
    await page.fill('input#username', 'nonexistent_user_xyz');
    await page.fill('input#password', 'wrongpassword');
    await page.click('button[type="submit"]');

    await expect(page.locator('.error-message')).toBeVisible();
    // Should remain on login page
    await expect(page).toHaveURL(/\/login/);
  });

  test('user can log out', async ({ page }) => {
    await registerAndLogin(page, `logout_${Date.now()}`);

    await page.click('header button:has-text("Logout")');

    // Should show login/register links after logout
    await expect(page.locator('header a', { hasText: 'Login' })).toBeVisible();
    await expect(page.locator('header a', { hasText: 'Register' })).toBeVisible();
    // Should NOT show logout button
    await expect(page.locator('header button', { hasText: 'Logout' })).not.toBeVisible();
  });

  test('authenticated-only routes redirect unauthenticated users to login', async ({ page }) => {
    await page.goto('/create');
    await expect(page).toHaveURL(/\/login/);

    await page.goto('/my-posts');
    await expect(page).toHaveURL(/\/login/);
  });

  test('login/register redirect to home when already authenticated', async ({ page }) => {
    await registerAndLogin(page, `redir_${Date.now()}`);

    await page.goto('/login');
    await expect(page).toHaveURL('/');

    await page.goto('/register');
    await expect(page).toHaveURL('/');
  });

  // ── Regression: /auth/me safety ───────────────────────────────────────────

  // Regression for: principal!! force-unwrap + .first() crash in /auth/me.
  // Fixed to use safe access + firstOrNull() returning 401/404 instead of 500.
  test('/auth/me returns 401 without a token', async ({ page }) => {
    const response = await page.request.get(`${API_URL}/auth/me`);
    expect(response.status()).toBe(401);
  });

  test('/auth/me returns 200 with a valid token', async ({ page }) => {
    const creds = await registerUser(page, `authme_${Date.now()}`);
    const response = await page.request.get(`${API_URL}/auth/me`, {
      headers: { Authorization: `Bearer ${creds.token}` },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.username).toBe(creds.username);
  });

  test('expired tokens cannot authenticate REST requests or create posts', async ({ page }) => {
    const creds = await registerUser(page, `expired_${Date.now()}`);
    const now = Math.floor(Date.now() / 1000);
    const validToken = withTokenExpiration(creds.token, now + 3600);
    const expiredToken = withTokenExpiration(creds.token, now - 3600);

    // Positive control: prove the fixture uses the server's signing key and issuer.
    const validResponse = await page.request.get(`${API_URL}/auth/me`, {
      headers: { Authorization: `Bearer ${validToken}` },
    });
    expect(validResponse.status()).toBe(200);
    expect((await validResponse.json()).username).toBe(creds.username);

    const expiredResponse = await page.request.get(`${API_URL}/auth/me`, {
      headers: { Authorization: `Bearer ${expiredToken}` },
    });
    expect(expiredResponse.status()).toBe(401);

    const mutationResponse = await page.request.post(GRAPHQL_URL, {
      headers: { Authorization: `Bearer ${expiredToken}` },
      data: { query: 'mutation { createPost(input: { title: "Expired token post", content: "Forbidden" }) { id } }' },
    });
    expect(mutationResponse.status()).toBe(200);
    const body = await mutationResponse.json();
    expect(body.errors).toEqual(expect.arrayContaining([
      expect.objectContaining({ message: expect.stringContaining('Authentication required') }),
    ]));
    expect(body.data?.createPost).toBeFalsy();

    const postsResponse = await page.request.post(GRAPHQL_URL, {
      headers: { Authorization: `Bearer ${validToken}` },
      data: { query: '{ myPosts { id } }' },
    });
    const posts = await postsResponse.json();
    expect(posts.errors).toBeUndefined();
    expect(posts.data.myPosts).toEqual([]);
  });
});
