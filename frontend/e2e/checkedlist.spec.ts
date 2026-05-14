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
import { GRAPHQL_URL, registerUser, registerAndLogin } from './fixtures/auth';

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
    const myPost = body.data.checkedListPosts.find((p: { title: string }) => p.title === title);
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

  test('non-author cannot toggle item', async ({ page }) => {
    const suffix = Date.now();
    const { token: authorToken } = await registerUser(page, `cl_toggle_owner_${suffix}`);
    const { token: otherToken } = await registerUser(page, `cl_toggle_other_${suffix}`);
    const post = await createCheckedListPost(page, authorToken, 'Owner Test', []);
    const item = await addItem(page, authorToken, post.id, 'Protected Item');

    const body = await gql(
      page,
      `mutation { toggleCheckedListItem(id: "${item.id}") { checked } }`,
      otherToken,
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

// ── updateCheckedListPost ─────────────────────────────────────────────────────

test.describe('CheckedList — updateCheckedListPost', () => {
  test('updates the title of a post', async ({ page }) => {
    const { token } = await registerUser(page, `cl_upd_title_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'Original Title', []);

    const body = await gql(
      page,
      `mutation { updateCheckedListPost(input: { id: "${post.id}", title: "Updated Title" }) { id title } }`,
      token,
    );
    expect(body.errors).toBeUndefined();
    expect(body.data.updateCheckedListPost.title).toBe('Updated Title');
    expect(body.data.updateCheckedListPost.id).toBe(post.id);
  });

  test('updates the description of a post', async ({ page }) => {
    const { token } = await registerUser(page, `cl_upd_desc_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'My List', []);

    const body = await gql(
      page,
      `mutation { updateCheckedListPost(input: { id: "${post.id}", description: "New description" }) { id description } }`,
      token,
    );
    expect(body.errors).toBeUndefined();
    expect(body.data.updateCheckedListPost.description).toBe('New description');
  });

  test('rejects blank title', async ({ page }) => {
    const { token } = await registerUser(page, `cl_upd_blank_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'Valid Title', []);

    const body = await gql(
      page,
      `mutation { updateCheckedListPost(input: { id: "${post.id}", title: "   " }) { id } }`,
      token,
    );
    expect(body.errors).toBeTruthy();
  });

  test('rejects description exceeding 10000 characters', async ({ page }) => {
    const { token } = await registerUser(page, `cl_upd_longdesc_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'List', []);
    const longDesc = 'a'.repeat(10_001);

    const body = await gql(
      page,
      `mutation { updateCheckedListPost(input: { id: "${post.id}", description: "${longDesc}" }) { id } }`,
      token,
    );
    expect(body.errors).toBeTruthy();
  });

  test('requires authentication', async ({ page }) => {
    const { token } = await registerUser(page, `cl_upd_auth_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'My List', []);

    const body = await gql(
      page,
      `mutation { updateCheckedListPost(input: { id: "${post.id}", title: "Hacked" }) { id } }`,
    );
    expect(body.errors).toBeTruthy();
  });

  test('returns error for a non-existent post ID', async ({ page }) => {
    const { token } = await registerUser(page, `cl_upd_ne_${Date.now()}`);
    // Use the post ID format but with a UUID that doesn't exist
    const fakePost = await createCheckedListPost(page, token, 'Temp', []);
    // Delete the post then try to update it
    await gql(page, `mutation { deleteCheckedListPost(id: "${fakePost.id}") }`, token);

    const body = await gql(
      page,
      `mutation { updateCheckedListPost(input: { id: "${fakePost.id}", title: "Ghost" }) { id } }`,
      token,
    );
    expect(body.errors).toBeTruthy();
  });
});

// ── deleteCheckedListPost ─────────────────────────────────────────────────────

test.describe('CheckedList — deleteCheckedListPost', () => {
  test('deletes a post and returns true', async ({ page }) => {
    const { token } = await registerUser(page, `cl_del_post_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'To Delete', []);

    const body = await gql(
      page,
      `mutation { deleteCheckedListPost(id: "${post.id}") }`,
      token,
    );
    expect(body.errors).toBeUndefined();
    expect(body.data.deleteCheckedListPost).toBe(true);
  });

  test('post no longer appears in checkedListPosts after deletion', async ({ page }) => {
    const { token } = await registerUser(page, `cl_del_verify_${Date.now()}`);
    const title = `Delete Verify ${Date.now()}`;
    const post = await createCheckedListPost(page, token, title, ['Item 1']);

    await gql(page, `mutation { deleteCheckedListPost(id: "${post.id}") }`, token);

    const listBody = await gql(page, '{ checkedListPosts { id title } }');
    const found = listBody.data.checkedListPosts.find((p: { id: string }) => p.id === post.id);
    expect(found).toBeUndefined();
  });

  test('requires authentication', async ({ page }) => {
    const { token } = await registerUser(page, `cl_del_post_auth_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'Protected', []);

    const body = await gql(
      page,
      `mutation { deleteCheckedListPost(id: "${post.id}") }`,
    );
    expect(body.errors).toBeTruthy();
  });
});

// ── updateCheckedListItem ─────────────────────────────────────────────────────

test.describe('CheckedList — updateCheckedListItem', () => {
  test('updates the text of an item', async ({ page }) => {
    const { token } = await registerUser(page, `cl_upd_item_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'Update Item List', []);
    const item = await addItem(page, token, post.id, 'Original text');

    const body = await gql(
      page,
      `mutation { updateCheckedListItem(input: { id: "${item.id}", text: "Updated text" }) { id text } }`,
      token,
    );
    expect(body.errors).toBeUndefined();
    expect(body.data.updateCheckedListItem.text).toBe('Updated text');
  });

  test('rejects blank text', async ({ page }) => {
    const { token } = await registerUser(page, `cl_upd_item_blank_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'Item Blank', []);
    const item = await addItem(page, token, post.id, 'Valid text');

    const body = await gql(
      page,
      `mutation { updateCheckedListItem(input: { id: "${item.id}", text: "  " }) { id } }`,
      token,
    );
    expect(body.errors).toBeTruthy();
  });

  test('requires authentication', async ({ page }) => {
    const { token } = await registerUser(page, `cl_upd_item_auth_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'Auth Item', []);
    const item = await addItem(page, token, post.id, 'Protected');

    const body = await gql(
      page,
      `mutation { updateCheckedListItem(input: { id: "${item.id}", text: "Hacked" }) { id } }`,
    );
    expect(body.errors).toBeTruthy();
  });
});

// ── likePost / unlikePost on CheckedListPost ─────────────────────────────────

test.describe('CheckedList — likePost and unlikePost', () => {
  test('authenticated user can like a CheckedListPost and likeCount increments', async ({ page }) => {
    const suffix = Date.now();
    const { token: authorToken } = await registerUser(page, `cl_like_author_${suffix}`);
    const { token: likerToken } = await registerUser(page, `cl_liker_${suffix}`);
    const post = await createCheckedListPost(page, authorToken, 'Likable List', []);

    // Initial state: no likes
    const before = await gql(
      page,
      `{ node(id: "${post.id}") { ... on CheckedListPost { likeCount isLikedByMe } } }`,
      likerToken,
    );
    expect(before.data.node.likeCount).toBe(0);
    expect(before.data.node.isLikedByMe).toBe(false);

    // Like the post
    const likeBody = await gql(
      page,
      `mutation { likePost(postId: "${post.id}") { id } }`,
      likerToken,
    );
    expect(likeBody.errors).toBeUndefined();
    expect(likeBody.data.likePost.id).toBeTruthy();

    // After like: count=1, isLikedByMe=true
    const after = await gql(
      page,
      `{ node(id: "${post.id}") { ... on CheckedListPost { likeCount isLikedByMe } } }`,
      likerToken,
    );
    expect(after.data.node.likeCount).toBe(1);
    expect(after.data.node.isLikedByMe).toBe(true);
  });

  test('unlikePost decrements likeCount on a CheckedListPost', async ({ page }) => {
    const suffix = Date.now();
    const { token: authorToken } = await registerUser(page, `cl_unlike_author_${suffix}`);
    const { token: likerToken } = await registerUser(page, `cl_unlike_liker_${suffix}`);
    const post = await createCheckedListPost(page, authorToken, 'Unlike Test', []);

    await gql(page, `mutation { likePost(postId: "${post.id}") { id } }`, likerToken);
    await gql(page, `mutation { unlikePost(postId: "${post.id}") }`, likerToken);

    const after = await gql(
      page,
      `{ node(id: "${post.id}") { ... on CheckedListPost { likeCount isLikedByMe } } }`,
      likerToken,
    );
    expect(after.data.node.likeCount).toBe(0);
    expect(after.data.node.isLikedByMe).toBe(false);
  });

  test('likePost requires authentication', async ({ page }) => {
    const { token: authorToken } = await registerUser(page, `cl_like_auth_${Date.now()}`);
    const post = await createCheckedListPost(page, authorToken, 'Auth Like Test', []);

    const body = await gql(page, `mutation { likePost(postId: "${post.id}") { id } }`);
    expect(body.errors).toBeTruthy();
  });
});

// ── myCheckedListPosts query ──────────────────────────────────────────────────

test.describe('CheckedList — myCheckedListPosts query', () => {
  test('returns only the authenticated user\'s checklist posts', async ({ page }) => {
    const suffix = Date.now();
    const { token: user1Token } = await registerUser(page, `cl_my_u1_${suffix}`);
    const { token: user2Token } = await registerUser(page, `cl_my_u2_${suffix}`);

    const title = `My Personal Checklist ${suffix}`;
    await createCheckedListPost(page, user1Token, title, ['Task A']);
    await createCheckedListPost(page, user2Token, `Other User's List ${suffix}`, ['Task B']);

    const body = await gql(page, '{ myCheckedListPosts { id title } }', user1Token);
    expect(body.errors).toBeUndefined();
    const titles = body.data.myCheckedListPosts.map((p: { title: string }) => p.title);
    expect(titles).toContain(title);
    expect(titles.every((t: string) => !t.includes("Other User's"))).toBe(true);
  });

  test('requires authentication', async ({ page }) => {
    const body = await gql(page, '{ myCheckedListPosts { id } }');
    expect(body.errors).toBeTruthy();
  });
});

// ── My Posts page shows checklist posts (regression) ─────────────────────────

// Regression: myCheckedListPosts was missing — My Posts only showed BlogPosts.
test.describe('CheckedList — My Posts page', () => {
  test('checklist post appears on the My Posts page', async ({ page }) => {
    const suffix = Date.now();
    const creds = await registerAndLogin(page, `cl_myposts_${suffix}`);
    const title = `My Checklist ${suffix}`;
    await createCheckedListPost(page, creds.token, title, ['First task']);

    await page.goto('/my-posts');

    await expect(page.locator('.posts-list')).toContainText(title);
    await expect(page.locator('.post-card--checklist')).toBeVisible();
  });

  test('checklist post does not appear on another user\'s My Posts page', async ({ page }) => {
    const suffix = Date.now();
    const { token: authorToken } = await registerUser(page, `cl_myp_author_${suffix}`);
    const viewerCreds = await registerAndLogin(page, `cl_myp_viewer_${suffix}`);
    const title = `Author Checklist ${suffix}`;
    await createCheckedListPost(page, authorToken, title, ['Task']);

    await page.goto('/my-posts');

    await expect(page.locator('body')).not.toContainText(title);
  });
});

// ── analytics: viewCount & readTimeMinutes ────────────────────────────────────

test.describe('CheckedList — analytics (viewCount & readTimeMinutes)', () => {
  test('viewCount starts at 0 and increments after recordPostView', async ({ page }) => {
    const { token } = await registerUser(page, `cl_vc_${Date.now()}`);
    const post = await createCheckedListPost(page, token, 'Analytics Test', ['Item 1', 'Item 2']);

    // Before any views: viewCount should be 0
    const before = await gql(
      page,
      `{ checkedListPosts { id viewCount } }`,
    );
    const beforePost = before.data.checkedListPosts.find((p: { id: string }) => p.id === post.id);
    expect(beforePost?.viewCount).toBe(0);

    // Record a view (no auth required)
    await gql(page, `mutation { recordPostView(postId: "${post.id}") }`);

    // After recording: viewCount should be 1
    const after = await gql(page, `{ checkedListPosts { id viewCount } }`);
    const afterPost = after.data.checkedListPosts.find((p: { id: string }) => p.id === post.id);
    expect(afterPost?.viewCount).toBe(1);
  });

  test('readTimeMinutes returns a positive number', async ({ page }) => {
    const { token } = await registerUser(page, `cl_rt_${Date.now()}`);
    const post = await createCheckedListPost(
      page,
      token,
      'Read Time Test',
      ['First item with some words', 'Second item with more words'],
    );

    const body = await gql(page, `{ checkedListPosts { id readTimeMinutes } }`);
    const found = body.data.checkedListPosts.find((p: { id: string }) => p.id === post.id);
    expect(found).toBeTruthy();
    expect(typeof found.readTimeMinutes).toBe('number');
    expect(found.readTimeMinutes).toBeGreaterThan(0);
  });

  test('CheckedListPost appears in trending after receiving a view', async ({ page }) => {
    const { token } = await registerUser(page, `cl_trend_${Date.now()}`);
    const title = `Trending Checklist ${Date.now()}`;
    const post = await createCheckedListPost(page, token, title, ['Buy milk']);

    // Record multiple views so it ranks highly in trending
    await gql(page, `mutation { recordPostView(postId: "${post.id}") }`);
    await gql(page, `mutation { recordPostView(postId: "${post.id}") }`);
    await gql(page, `mutation { recordPostView(postId: "${post.id}") }`);

    const body = await gql(
      page,
      `{ trending(limit: 50) { id __typename ... on CheckedListPost { title } ... on BlogPost { title } } }`,
    );
    const found = body.data.trending.find((p: { id: string }) => p.id === post.id);
    expect(found).toBeTruthy();
    expect(found.__typename).toBe('CheckedListPost');
    expect(found.title).toBe(title);
  });
});

// ── postsConnection — PostEdge.node typed as Post interface ───────────────────

test.describe('CheckedList — postsConnection accepts Post interface fragments', () => {
  test('postsConnection accepts ... on CheckedListPost fragment without validation error', async ({ page }) => {
    // Before the fix, PostEdge.node was typed as BlogPost (concrete), so
    // "... on CheckedListPost" was rejected by GraphQL validation.
    // This test verifies the schema fix: node is now typed as Post (interface).
    const body = await gql(
      page,
      `{
        postsConnection {
          edges {
            node {
              __typename
              id
              title
              ... on BlogPost { content }
              ... on CheckedListPost { description }
            }
          }
        }
      }`,
    );
    expect(body.errors).toBeUndefined();
    expect(body.data.postsConnection).toBeTruthy();
  });
});
