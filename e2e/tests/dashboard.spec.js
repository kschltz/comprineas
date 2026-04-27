// @ts-check
const { test, expect } = require('@playwright/test');

function uniqueEmail(prefix) {
  return `${prefix}-${Date.now()}@example.com`;
}

async function registerAndLogin(page, email, password, displayName) {
  await page.goto('/register');
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.fill('input[name="display_name"]', displayName);
  await Promise.all([
    page.waitForURL('**/dashboard', { timeout: 10000 }),
    page.click('button[type="submit"]')
  ]);
}

async function createList(page, name) {
  await page.fill('input[name="name"]', name);
  await page.click('text=Create List');
  // HTMX swaps body, no URL change — wait for list page content
  await expect(page.locator('#list-name')).toBeVisible({ timeout: 10000 });
}

test.describe('PRD-0007 — My Lists Dashboard', () => {

  test('1. Dashboard is landing page after login', async ({ page }) => {
    const email = uniqueEmail('dash1');
    await registerAndLogin(page, email, 'password123', 'UserOne');

    expect(page.url()).toMatch(/\/dashboard$/);
    await expect(page.locator('h1')).toContainText('My Lists');
  });

  test('2. Dashboard shows user greeting', async ({ page }) => {
    const email = uniqueEmail('dash2');
    await registerAndLogin(page, email, 'password123', 'DashTest');
    await expect(page.getByText('Hi, DashTest')).toBeVisible();
  });

  test('3. Empty state for new users', async ({ page }) => {
    const email = uniqueEmail('dash3');
    await registerAndLogin(page, email, 'password123', 'EmptyUser');
    // The empty state shows "No active lists yet" or a similar message
    const bodyText = await page.textContent('body');
    console.log('Body text:', bodyText.substring(0, 500));
    await expect(page.locator('body')).toContainText('No active lists');
  });

  test('4. Active and past lists separated', async ({ page }) => {
    const email = uniqueEmail('dash4');
    await registerAndLogin(page, email, 'password123', 'ListSep');

    await createList(page, 'ActiveOne');
    await page.goto('/dashboard');

    const code = await createList(page, 'ToBeDone');
    // Complete the list — accept the confirmation dialog
    page.on('dialog', dialog => dialog.accept());
    await page.click('text=Complete List');
    // HTMX handles redirect, swaps body — wait for dashboard content
    await expect(page.locator('h1')).toContainText('My Lists', { timeout: 10000 });

    await expect(page.getByText('Active Lists')).toBeVisible();
    await expect(page.getByText('Past Lists')).toBeVisible();
  });

  test('5. List cards clickable', async ({ page }) => {
    const email = uniqueEmail('dash5');
    await registerAndLogin(page, email, 'password123', 'ClickTest');

    await createList(page, 'ClickMe');
    await page.goto('/dashboard');

    await page.click('text=ClickMe');
    expect(page.url()).toContain('/list/');
  });

  test('6. Logout from dashboard', async ({ page }) => {
    const email = uniqueEmail('dash6');
    await registerAndLogin(page, email, 'password123', 'LogoutTest');

    await page.click('button:has-text("Logout")');
    await page.waitForURL('**/login', { timeout: 10000 });
  });

  test('7. Create form with empty name shows validation', async ({ page }) => {
    const email = uniqueEmail('dash7');
    await registerAndLogin(page, email, 'password123', 'ValTest');

    // Submit the create form with empty name — browser validates required field
    await page.click('text=Create List');

    // Browser-native validation prevents submission; we stay on dashboard
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.locator('h1')).toContainText('My Lists');
  });

});
