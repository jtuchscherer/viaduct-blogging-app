import { type Page } from '@playwright/test';

import { API_URL } from './auth';

const GRAPHQL_URL = `${API_URL}/graphql`;

export const ADMIN_CREDS = {
  username: 'e2e_admin',
  password: 'e2eAdminPass1',
} as const;

/** Log in as the seeded E2E admin user and return the auth token. */
export async function loginAsAdmin(page: Page): Promise<string> {
  const response = await page.request.post(`${API_URL}/auth/login`, {
    data: {
      username: ADMIN_CREDS.username,
      password: ADMIN_CREDS.password,
    },
  });
  const data = await response.json();
  return data.token as string;
}

/** Navigate to an admin page as the seeded admin user (sets localStorage token). */
export async function gotoAdminPage(page: Page, path: string): Promise<void> {
  const token = await loginAsAdmin(page);
  await page.goto('/');
  await page.evaluate(
    ({ t, u }) => {
      localStorage.setItem('authToken', t);
      localStorage.setItem('authUser', JSON.stringify({ username: u, isAdmin: true }));
    },
    { t: token, u: ADMIN_CREDS.username }
  );
  await page.goto(path);
}

/** Create a comment via the GraphQL API. */
export async function createCommentViaAPI(
  page: Page,
  token: string,
  postId: string,
  content: string
): Promise<{ id: string; content: string }> {
  const response = await page.request.post(GRAPHQL_URL, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      query: `
        mutation {
          createComment(input: { postId: ${JSON.stringify(postId)}, content: ${JSON.stringify(content)} }) {
            id content
          }
        }
      `,
    },
  });
  const body = await response.json();
  return body.data.createComment;
}
