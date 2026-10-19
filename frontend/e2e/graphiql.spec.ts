import { test, expect } from '@playwright/test';
import { API_URL } from './fixtures/auth';

test('GraphiQL loads the schema and executes the default blogging query', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));

  const introspectionResponse = page.waitForResponse(response =>
    response.url() === `${API_URL}/graphql`
    && response.request().postData()?.includes('IntrospectionQuery') === true,
  );
  await page.goto(`${API_URL}/graphiql?path=/graphql`);
  await expect(page).toHaveTitle('Blogging App');
  const introspection = await (await introspectionResponse).json();
  expect(introspection.errors).toBeUndefined();
  expect(introspection.data.__schema.queryType.name).toBe('Query');

  const execute = page.getByRole('button', { name: /execute query/i });
  await expect(execute).toBeEnabled();
  const queryResponse = page.waitForResponse(response =>
    response.url() === `${API_URL}/graphql`
    && response.request().postData()?.includes('likeCount') === true,
  );
  await execute.click();
  const result = await (await queryResponse).json();
  expect(result.errors).toBeUndefined();
  expect(Array.isArray(result.data.posts)).toBe(true);
  await expect(page.locator('.graphiql-response')).toContainText('"posts"');
  await expect(page.getByText('Loading…', { exact: true })).not.toBeVisible();
  expect(errors).toEqual([]);
});

test('GraphiQL Global ID utility decodes and encodes a blogging node ID', async ({ page }) => {
  await page.goto(`${API_URL}/graphiql?path=/graphql`);
  await page.getByRole('button', { name: 'Show Global ID Utils', exact: true }).click();
  const encodedId = Buffer.from('BlogPost:post-1').toString('base64');
  await page.getByPlaceholder('e.g., Q2hhcmFjdGVyOjE=').fill(encodedId);
  await expect(page.locator('.global-id-plugin textarea')).toHaveValue('Type: BlogPost\nInternal ID: post-1');

  await page.getByRole('button', { name: 'Switch to Encode', exact: true }).click();
  await expect(page.getByPlaceholder('e.g., Character:1')).toHaveValue('BlogPost:post-1');
  await expect(page.locator('.global-id-plugin textarea')).toHaveValue(encodedId);
});
