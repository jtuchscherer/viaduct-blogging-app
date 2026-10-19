import { expect, type Page } from '@playwright/test';
import type { PostStatus } from '../../src/types';

import { API_URL } from './auth';

const GRAPHQL_URL = `${API_URL}/graphql`;

/** Create setup data without exercising the post form or sharing WebKit's networking stack. */
export async function createPostViaAPI(
  _page: Page,
  token: string,
  title: string,
  content: string = 'Default test content.',
  status: PostStatus = 'PUBLISHED'
): Promise<{ id: string; title: string; content: string; status: PostStatus }> {
  const response = await fetch(GRAPHQL_URL, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      query: `
        mutation CreateFixturePost($input: CreatePostInput!) {
          createPost(input: $input) { id title content status }
        }
      `,
      variables: { input: { title, content, status } },
    }),
  });
  const body = await response.json();
  expect(response.ok, JSON.stringify(body)).toBeTruthy();
  expect(body.errors).toBeUndefined();
  return body.data.createPost;
}
