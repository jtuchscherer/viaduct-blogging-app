/**
 * CheckedList module e2e tests.
 *
 * Exercises the GraphQL API contract for the checkedlist tenant module:
 *   - createCheckedListPost mutation
 *   - checkedListPosts query
 *   - addCheckedListItem mutation
 *   - toggleCheckedListItem mutation
 *   - deleteCheckedListItem mutation
 *   - node(id) resolution for CheckedListPost
 *
 * There is no checkedlist UI yet — all assertions go through page.request (the
 * Playwright HTTP client). Tests follow the same API-first pattern as analytics.spec.ts.
 */

import { test, expect } from '@playwright/test';
import { API_URL, registerUser } from './fixtures/auth';

const GRAPHQL_URL = `${API_URL}/graphql`;

// ── Helpers ───────────────────────────────────────────────────────────────────

async function gql(
  page: Parameters<typeof registerUser>[0],
  query: string,
  token?: string,
) {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await page.request.post(GRAPHQL_URL, { headers, data: { query } });
  return res.json();
}

async function createCheckedListPost(
  page: Parameters<typeof registerUser>[0],
  token: string,
  title: string,
  items: string[],
): Promise<{ id: string; title: string }> {
  const itemsArg = items.map((i) => `"${i}"`).join(', ');
  const body = await gql(
    page,
    `mutation { createCheckedListPost(input: { title: "${title}", items: [${itemsArg}] }) { id title } }`,
    token,
  );
  return body.data.createCheckedListPost;
}

async function addItem(
  page: Parameters<typeof registerUser>[0],
  token: string,
  postId: string,
  text: string,
): Promise<{ id: string; text: string; checked: boolean; position: number }> {
  const body = await gql(
    page,
    `mutation { addCheckedListItem(input: { postId: "${postId}", text: "${text}" }) { id text checked position } }`,
    token,
  );
  return body.data.addCheckedListItem;
}

// ── createCheckedListPost ─────────────────────────────────────────────────────

test.describe('CheckedList — createCheckedListPost', () => {
  test('creates a post and returns id and title', async ({ page }) => {
    const { token } = await registerUser(page, `cl_create_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'My Grocery List', ['Milk', 'Bread']);
    expect(post.id).toBeTruthy();
    expect(post.title).toBe('My Grocery List');
  });

  test('requires authentication', async ({ page }) => {
    const body = await gql(
      page,
      'mutation { createCheckedListPost(input: { title: "No Auth", items: [] }) { id } }',
    );
    expect(body.errors).toBeTruthy();
  });

  test('rejects blank title', async ({ page }) => {
    const { token } = await registerUser(page, `cl_blank_${Date.now()}`);
    const body = await gql(
      page,
      'mutation { createCheckedListPost(input: { title: "   ", items: ["Item"] }) { id } }',
      token,
    );
    expect(body.errors).toBeTruthy();
  });

  test('rejects title exceeding 500 characters', async ({ page }) => {
    const { token } = await registerUser(page, `cl_longtitle_${Date.now()}`);
    const longTitle = 'a'.repeat(501);
    const body = await gql(
      page,
      `mutation { createCheckedListPost(input: { title: "${longTitle}", items: [] }) { id } }`,
      token,
    );
    expect(body.errors).toBeTruthy();
  });
});

// ── checkedListPosts query ────────────────────────────────────────────────────

test.describe('CheckedList — checkedListPosts query', () => {
  test('returns created post with its initial items', async ({ page }) => {
    const { token } = await registerUser(page, `cl_query_${Date.now()}`);
    const title = `Task List ${Date.now()}`;
    await createCheckedListPost(page, token, title, ['Task A', 'Task B']);

    const body = await gql(page, '{ checkedListPosts { id title items { text checked position } } }');
    const myPost = body.data.checkedListPosts.find((p: any) => p.title === title);
    expect(myPost).toBeTruthy();
    expect(myPost.items).toHaveLength(2);
    expect(myPost.items[0].text).toBe('Task A');
    expect(myPost.items[0].checked).toBe(false);
    expect(myPost.items[0].position).toBe(0);
    expect(myPost.items[1].position).toBe(1);
  });

  test('is accessible without authentication', async ({ page }) => {
    const body = await gql(page, '{ checkedListPosts { id title } }');
    expect(body.errors).toBeUndefined();
    expect(Array.isArray(body.data.checkedListPosts)).toBe(true);
  });
});

// ── addCheckedListItem ────────────────────────────────────────────────────────

test.describe('CheckedList — addCheckedListItem', () => {
  test('adds item with correct initial state', async ({ page }) => {
    const { token } = await registerUser(page, `cl_add_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'Shopping', []);
    const item = await addItem(page, token, post.id, 'Eggs');

    expect(item.id).toBeTruthy();
    expect(item.text).toBe('Eggs');
    expect(item.checked).toBe(false);
    expect(item.position).toBe(0);
  });

  test('assigns sequential positions across multiple adds', async ({ page }) => {
    const { token } = await registerUser(page, `cl_seq_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'Sequential', []);

    for (const [i, text] of ['Alpha', 'Beta', 'Gamma'].entries()) {
      const item = await addItem(page, token, post.id, text);
      expect(item.position).toBe(i);
    }
  });

  test('requires authentication', async ({ page }) => {
    const { token } = await registerUser(page, `cl_add_auth_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'Auth Check', []);

    const body = await gql(
      page,
      `mutation { addCheckedListItem(input: { postId: "${post.id}", text: "Sneaky" }) { id } }`,
    );
    expect(body.errors).toBeTruthy();
  });

  test('rejects blank item text', async ({ page }) => {
    const { token } = await registerUser(page, `cl_blank_item_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'Blank Item', []);

    const body = await gql(
      page,
      `mutation { addCheckedListItem(input: { postId: "${post.id}", text: "   " }) { id } }`,
      token,
    );
    expect(body.errors).toBeTruthy();
  });
});

// ── toggleCheckedListItem ─────────────────────────────────────────────────────

test.describe('CheckedList — toggleCheckedListItem', () => {
  test('flips checked false → true → false', async ({ page }) => {
    const { token } = await registerUser(page, `cl_toggle_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'Toggle Test', []);
    const item = await addItem(page, token, post.id, 'Toggle Me');

    const toggle1 = await gql(
      page,
      `mutation { toggleCheckedListItem(id: "${item.id}") { checked } }`,
      token,
    );
    expect(toggle1.data.toggleCheckedListItem.checked).toBe(true);

    const toggle2 = await gql(
      page,
      `mutation { toggleCheckedListItem(id: "${item.id}") { checked } }`,
      token,
    );
    expect(toggle2.data.toggleCheckedListItem.checked).toBe(false);
  });

  test('requires authentication', async ({ page }) => {
    const { token } = await registerUser(page, `cl_toggle_auth_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'Toggle Auth', []);
    const item = await addItem(page, token, post.id, 'Protected');

    const body = await gql(
      page,
      `mutation { toggleCheckedListItem(id: "${item.id}") { checked } }`,
    );
    expect(body.errors).toBeTruthy();
  });
});

// ── deleteCheckedListItem ─────────────────────────────────────────────────────

test.describe('CheckedList — deleteCheckedListItem', () => {
  test('removes item and returns true', async ({ page }) => {
    const { token } = await registerUser(page, `cl_del_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'Delete Test', []);
    const item = await addItem(page, token, post.id, 'To Delete');

    const body = await gql(
      page,
      `mutation { deleteCheckedListItem(id: "${item.id}") }`,
      token,
    );
    expect(body.data.deleteCheckedListItem).toBe(true);
  });

  test('returns false for an already-deleted item', async ({ page }) => {
    const { token } = await registerUser(page, `cl_del_ne_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'Delete Twice', []);
    const item = await addItem(page, token, post.id, 'Delete Me Twice');

    await gql(page, `mutation { deleteCheckedListItem(id: "${item.id}") }`, token);

    const body = await gql(
      page,
      `mutation { deleteCheckedListItem(id: "${item.id}") }`,
      token,
    );
    expect(body.data.deleteCheckedListItem).toBe(false);
  });

  test('requires authentication', async ({ page }) => {
    const { token } = await registerUser(page, `cl_del_auth_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'Delete Auth', []);
    const item = await addItem(page, token, post.id, 'Protected');

    const body = await gql(
      page,
      `mutation { deleteCheckedListItem(id: "${item.id}") }`,
    );
    expect(body.errors).toBeTruthy();
  });
});

// ── node(id) resolution ───────────────────────────────────────────────────────

test.describe('CheckedList — node(id) resolution', () => {
  test('resolves CheckedListPost via the Node interface', async ({ page }) => {
    const { token } = await registerUser(page, `cl_node_${Date.now()}`);
    const title = `Node Test Post ${Date.now()}`;
    const post = await createCheckedListPost(page, token, title, []);

    const body = await gql(
      page,
      `{ node(id: "${post.id}") { id __typename ... on CheckedListPost { title } } }`,
    );
    expect(body.data.node.__typename).toBe('CheckedListPost');
    expect(body.data.node.title).toBe(title);
    expect(body.data.node.id).toBe(post.id);
  });
});
