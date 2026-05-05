import { test, expect } from '@playwright/test';
import { registerAndLogin, GRAPHQL_URL } from './fixtures/auth';
import { createPostViaAPI } from './fixtures/posts';

test.describe('Input validation', () => {
  test.describe('Registration', () => {
    test('rejects username exceeding 100 characters', async ({ page }) => {
      await page.goto('/register');
      await page.fill('input#username', 'a'.repeat(101));
      await page.fill('input#email', 'toolong@test.com');
      await page.fill('input#name', 'Test User');
      await page.fill('input#password', 'password123');
      await page.click('button[type="submit"]');

      await expect(page.locator('.error-message')).toBeVisible();
      await expect(page).not.toHaveURL('/');
    });
  });

  test.describe('Create post', () => {
    test('rejects title exceeding 500 characters', async ({ page }) => {
      await registerAndLogin(page, `valtitle_${Date.now()}`);
      await page.goto('/create');

      await page.fill('input#title', 'a'.repeat(501));
      await page.click('[data-testid="rich-text-editor"]');
      await page.keyboard.type('Some content.');
      await page.click('button[type="submit"]');

      await expect(page.locator('.error-message')).toBeVisible();
      await expect(page).toHaveURL('/create');
    });

    test('rejects post content exceeding 100,000 characters via API', async ({ page }) => {
      const creds = await registerAndLogin(page, `valcontent_${Date.now()}`);

      const response = await page.request.post(GRAPHQL_URL, {
        headers: { Authorization: `Bearer ${creds.token}` },
        data: {
          query: `
            mutation {
              createPost(input: { title: "Valid title", content: ${JSON.stringify('a'.repeat(100_001))} }) {
                id
              }
            }
          `,
        },
      });

      const body = await response.json();
      expect(body.errors).toBeDefined();
      expect(body.errors[0].message).toMatch(/100,000/);
    });
  });

  test.describe('Comments', () => {
    test('rejects comment content exceeding 10,000 characters', async ({ page }) => {
      const creds = await registerAndLogin(page, `valcomment_${Date.now()}`);
      const post = await createPostViaAPI(page, creds.token, 'Comment Validation Post');

      await page.goto(`/post/${post.id}`);
      await page.fill('.comment-form textarea', 'a'.repeat(10_001));
      await page.click('.comment-form button[type="submit"]');

      await expect(page.locator('.comment-form .error-message')).toBeVisible();
      await expect(page.locator('.comments-section h2')).toContainText('Comments (0)');
    });
  });
});
