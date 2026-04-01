import { type Page } from '@playwright/test';

const GRAPHQL_URL = 'http://localhost:8080/graphql';

/** Create a post via the GraphQL API. Skips the UI for tests that don't test post creation itself. */
export async function createPostViaAPI(
  page: Page,
  token: string,
  title: string,
  content: string = 'Default test content.'
): Promise<{ id: string; title: string; content: string }> {
  const response = await page.request.post(GRAPHQL_URL, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      query: `
        mutation {
          createPost(input: { title: ${JSON.stringify(title)}, content: ${JSON.stringify(content)} }) {
            id title content
          }
        }
      `,
    },
  });
  const body = await response.json();
  return body.data.createPost;
}
