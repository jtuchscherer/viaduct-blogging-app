import { test, expect } from '@playwright/test';
import { registerAndLogin, API_URL } from './fixtures/auth';
import { createPostViaAPI } from './fixtures/posts';

// ── Helpers ───────────────────────────────────────────────────────────────────

// Use glob patterns so intercepts match regardless of whether the frontend
// uses `localhost` or `127.0.0.1` (VITE_API_URL vs API_URL differ in e2e.sh).

/** Intercept /health/ai and return a fake response so tests run without Ollama. */
async function mockAIHealthReachable(page: import('@playwright/test').Page) {
  await page.route('**/health/ai', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        ollamaReachable: true,
        chatModel: 'llama3.2',
        embeddingModel: 'nomic-embed-text',
      }),
    }),
  );
}

async function mockAIHealthUnreachable(page: import('@playwright/test').Page) {
  await page.route('**/health/ai', (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({
        ollamaReachable: false,
        chatModel: 'llama3.2',
        embeddingModel: 'nomic-embed-text',
      }),
    }),
  );
}

/** Intercept the rephraseContent GraphQL mutation and return a canned response. */
async function mockRephraseContent(page: import('@playwright/test').Page, rephrasedContent: string) {
  await page.route('**/graphql', async (route) => {
    const body = route.request().postDataJSON();
    if (typeof body?.query === 'string' && body.query.includes('rephraseContent')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: { rephraseContent: { rephrasedContent } },
        }),
      });
    } else {
      await route.continue();
    }
  });
}

// ── Tests ─────────────────────────────────────────────────────────────────────

test.describe('AI — rephrase on create post page', () => {
  test('rephrase button and tone selector are shown on create page when Ollama is reachable', async ({ page }) => {
    await mockAIHealthReachable(page);
    await registerAndLogin(page, `ai_create_show_${Date.now()}`);

    await page.goto('/create');

    await expect(page.getByRole('button', { name: /rephrase/i })).toBeVisible();
    await expect(page.locator('select').filter({ hasText: 'Professional' })).toBeVisible();
  });

  test('rephrase button is visible but disabled on create page when Ollama is unreachable', async ({ page }) => {
    await mockAIHealthUnreachable(page);
    await registerAndLogin(page, `ai_create_hide_${Date.now()}`);

    await page.goto('/create');

    await expect(page.getByRole('button', { name: /rephrase/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /rephrase/i })).toBeDisabled();
    await expect(page.locator('select').filter({ hasText: 'Professional' })).toBeVisible();
    await expect(page.locator('select').filter({ hasText: 'Professional' })).toBeDisabled();
    await expect(page.getByText('Ollama offline')).toBeVisible();
  });

  test('rephrase controls are absent on the create checklist page', async ({ page }) => {
    await mockAIHealthReachable(page);
    await registerAndLogin(page, `ai_create_cl_${Date.now()}`);

    await page.goto('/create');
    await page.locator('label').filter({ hasText: 'Checklist' }).click();

    await expect(page.getByRole('button', { name: /rephrase/i })).not.toBeVisible();
    await expect(page.locator('select').filter({ hasText: 'Professional' })).not.toBeVisible();
  });
});

test.describe('AI — rephrase blog post', () => {
  test('rephrase button and tone selector are shown when Ollama is reachable', async ({ page }) => {
    await mockAIHealthReachable(page);
    const creds = await registerAndLogin(page, `ai_show_${Date.now()}`);
    const post = await createPostViaAPI(page, creds.token, `AI Show Test ${Date.now()}`, 'Some content.');

    await page.goto(`/edit/${post.id}`);

    await expect(page.getByRole('button', { name: /rephrase/i })).toBeVisible();
    await expect(page.locator('select').filter({ hasText: 'Professional' })).toBeVisible();
  });

  test('rephrase button is visible but disabled when Ollama is unreachable', async ({ page }) => {
    await mockAIHealthUnreachable(page);
    const creds = await registerAndLogin(page, `ai_hide_${Date.now()}`);
    const post = await createPostViaAPI(page, creds.token, `AI Hide Test ${Date.now()}`, 'Some content.');

    await page.goto(`/edit/${post.id}`);

    await expect(page.getByRole('button', { name: /rephrase/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /rephrase/i })).toBeDisabled();
    await expect(page.locator('select').filter({ hasText: 'Professional' })).toBeVisible();
    await expect(page.locator('select').filter({ hasText: 'Professional' })).toBeDisabled();
    await expect(page.getByText('Ollama offline')).toBeVisible();
  });

  test('clicking rephrase replaces editor content with rephrased text', async ({ page }) => {
    await mockAIHealthReachable(page);
    const rephrasedText = 'This is the AI-rephrased version of the content.';
    await mockRephraseContent(page, rephrasedText);

    const creds = await registerAndLogin(page, `ai_rephrase_${Date.now()}`);
    const post = await createPostViaAPI(
      page,
      creds.token,
      `AI Rephrase Test ${Date.now()}`,
      'Original content that will be rephrased.',
    );

    await page.goto(`/edit/${post.id}`);

    // Wait for the rephrase button to be enabled (content loaded)
    const rephraseBtn = page.getByRole('button', { name: /rephrase/i });
    await expect(rephraseBtn).toBeEnabled();

    await rephraseBtn.click();

    // Editor should now show the rephrased content
    await expect(page.locator('[data-testid="rich-text-editor"]')).toContainText(rephrasedText);
  });

  test('tone selector sends selected tone to the mutation', async ({ page }) => {
    await mockAIHealthReachable(page);

    let capturedTone: string | null = null;
    await page.route('**/graphql', async (route) => {
      const body = route.request().postDataJSON();
      if (typeof body?.query === 'string' && body.query.includes('rephraseContent')) {
        // Capture the tone variable from the request
        capturedTone = body.variables?.tone ?? null;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: { rephraseContent: { rephrasedContent: 'Casual rephrased content.' } },
          }),
        });
      } else {
        await route.continue();
      }
    });

    const creds = await registerAndLogin(page, `ai_tone_${Date.now()}`);
    const post = await createPostViaAPI(
      page,
      creds.token,
      `AI Tone Test ${Date.now()}`,
      'Content for tone test.',
    );

    await page.goto(`/edit/${post.id}`);

    // Change tone to Casual
    await page.locator('select').filter({ hasText: 'Professional' }).selectOption('CASUAL');
    await page.getByRole('button', { name: /rephrase/i }).click();

    await expect.poll(() => capturedTone).toBe('CASUAL');
  });

  test('rephrase button is disabled while rephrasing is in progress', async ({ page }) => {
    await mockAIHealthReachable(page);

    // Slow mutation so we can assert the button is disabled mid-flight
    await page.route('**/graphql', async (route) => {
      const body = route.request().postDataJSON();
      if (typeof body?.query === 'string' && body.query.includes('rephraseContent')) {
        await new Promise((r) => setTimeout(r, 500));
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            data: { rephraseContent: { rephrasedContent: 'Done.' } },
          }),
        });
      } else {
        await route.continue();
      }
    });

    const creds = await registerAndLogin(page, `ai_loading_${Date.now()}`);
    const post = await createPostViaAPI(
      page,
      creds.token,
      `AI Loading Test ${Date.now()}`,
      'Content for loading test.',
    );

    await page.goto(`/edit/${post.id}`);

    const rephraseBtn = page.getByRole('button', { name: /rephrase/i });
    await expect(rephraseBtn).toBeEnabled();
    await rephraseBtn.click();

    // During the in-flight request the button shows "Rephrasing…" and is disabled
    await expect(page.getByRole('button', { name: /rephrasing/i })).toBeDisabled();
  });

  test('rephrase controls are absent on the checklist edit page', async ({ page }) => {
    await mockAIHealthReachable(page);
    const creds = await registerAndLogin(page, `ai_cl_${Date.now()}`);

    // Create a checklist post via API
    const response = await page.request.post(`${API_URL}/graphql`, {
      headers: { Authorization: `Bearer ${creds.token}` },
      data: {
        query: `mutation {
          createCheckedListPost(input: {
            title: "AI Checklist Test ${Date.now()}",
            description: "desc",
            items: ["item one", "item two"]
          }) { id }
        }`,
      },
    });
    const body = await response.json();
    const checklistId = body.data.createCheckedListPost.id;

    await page.goto(`/edit/${checklistId}`);

    await expect(page.getByRole('button', { name: /rephrase/i })).not.toBeVisible();
    await expect(page.locator('select').filter({ hasText: 'Professional' })).not.toBeVisible();
  });
});
