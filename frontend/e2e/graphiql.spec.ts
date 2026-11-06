import { test, expect } from '@playwright/test';
import { API_URL } from './fixtures/auth';

/**
 * One deliberately narrow smoke test.
 *
 * The GraphiQL React app is loaded unpinned from esm.sh by Viaduct's graphiQLHtml template, so
 * anything it renders — button names, CSS class names, placeholder text — can change without a
 * commit in this repo. Earlier versions of this file asserted on exactly those things and broke
 * when upstream renamed the execute button.
 *
 * So this asserts only what this app controls or what the pinned template guarantees: our route
 * serves the configured page, the bundle boots far enough to introspect our endpoint, the app
 * mounts, and nothing throws. Whether the default query is still valid against the schema is
 * checked in GraphiQLDefaultQueryTest — server-side, no browser, no CDN.
 */
test('GraphiQL boots and introspects the blogging endpoint', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));

  const introspectionResponse = page.waitForResponse(response =>
    response.url() === `${API_URL}/graphql`
    && response.request().postData()?.includes('IntrospectionQuery') === true,
  );

  await page.goto(`${API_URL}/graphiql?path=/graphql`);

  // Server-rendered by our route from GraphiQLHtmlConfig, not by the CDN bundle.
  await expect(page).toHaveTitle('Blogging App');

  // Only the mounted app issues this, so a clean introspection proves the bundle ran, reached
  // our endpoint, and got a usable schema back.
  const introspection = await (await introspectionResponse).json();
  expect(introspection.errors).toBeUndefined();
  expect(introspection.data.__schema.queryType.name).toBe('Query');

  // The pinned template renders `<div class="loading">Loading…</div>` inside #graphiql; React
  // replaces it on mount. Its absence means the app rendered, whatever this version looks like.
  await expect(page.locator('#graphiql .loading')).toHaveCount(0);

  expect(errors).toEqual([]);
});
