import { type Page } from '@playwright/test';

// Use 127.0.0.1 instead of localhost: Node.js resolves localhost → ::1 (IPv6) on macOS,
// but the Ktor backend only binds to IPv4 (127.0.0.1:8080).
export const API_URL = process.env.API_URL ?? 'http://127.0.0.1:8080';
export const GRAPHQL_URL = `${API_URL}/graphql`;
const AUTH_URL = API_URL;

/**
 * Register a new user via the API and return token + user. Unique username per call.
 *
 * Uses Node.js's native fetch (not page.request) so the HTTP call goes through the
 * Node.js networking stack rather than through webkit's browser network stack.
 * Using page.request.post immediately before page.goto causes a webkit-specific hang
 * late in long sequential test runs: webkit apparently doesn't release the keep-alive
 * connection before starting the navigation, leading to a 60-second TCP timeout.
 * Node.js fetch is completely separate from the browser and avoids this race.
 */
export async function registerUser(_page: Page, suffix: string = Date.now().toString()) {
  const username = `testuser_${suffix}`;
  const response = await fetch(`${AUTH_URL}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username,
      email: `${username}@test.com`,
      name: `Test User ${suffix}`,
      password: 'password123',
    }),
  });
  const data = await response.json() as { token: string; user: object };
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
