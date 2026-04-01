import { type Page } from '@playwright/test';

const AUTH_URL = 'http://localhost:8080';

/** Register a new user via the API and return token + user. Unique username per call. */
export async function registerUser(page: Page, suffix: string = Date.now().toString()) {
  const username = `testuser_${suffix}`;
  const response = await page.request.post(`${AUTH_URL}/auth/register`, {
    data: {
      username,
      email: `${username}@test.com`,
      name: `Test User ${suffix}`,
      password: 'password123',
    },
  });
  const data = await response.json();
  return { username, password: 'password123', token: data.token, user: data.user };
}

/** Log in through the UI (fills the login form and submits). */
export async function loginViaUI(page: Page, username: string, password: string) {
  await page.goto('/login');
  await page.fill('input#username', username);
  await page.fill('input#password', password);
  await page.click('button[type="submit"]');
  // Wait for redirect away from /login
  await page.waitForURL((url) => !url.pathname.includes('/login'));
}

/** Register a user via API then log them in through the UI. */
export async function registerAndLogin(page: Page, suffix: string = Date.now().toString()) {
  const creds = await registerUser(page, suffix);
  await loginViaUI(page, creds.username, creds.password);
  return creds;
}
