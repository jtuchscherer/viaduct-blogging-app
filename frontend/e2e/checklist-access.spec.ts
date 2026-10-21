import { test, expect } from '@playwright/test';
import { GRAPHQL_URL, registerUser, registerAndLogin } from './fixtures/auth';

for (const viewer of ['anonymous', 'another user'] as const) {
  test(`${viewer} cannot open a draft checklist by URL`, async ({ page }) => {
    const { token } = await registerUser(page, `private_checklist_owner_${Date.now()}`);
    const title = `Private checklist ${Date.now()}`;
    const response = await fetch(GRAPHQL_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({
        query: 'mutation($input: CreateCheckedListPostInput!) { createCheckedListPost(input:$input) { id } }',
        variables: { input: { title, description: 'Private description', items: ['Private item'], status: 'DRAFT' } },
      }),
    });
    const result = await response.json();
    expect(result.errors).toBeUndefined();
    if (viewer === 'another user') await registerAndLogin(page, `private_checklist_viewer_${Date.now()}`);
    await page.goto(`/post/${result.data.createCheckedListPost.id}`);
    await expect(page.getByText('Post not found', { exact: true })).toBeVisible();
    await expect(page.locator('main')).not.toContainText(title);
    await expect(page.locator('main')).not.toContainText('Private description');
    await expect(page.locator('main')).not.toContainText('Private item');
  });
}
