// @ts-check
const { test, expect } = require('@playwright/test');

// ──────────────────────────────────────────────────────────
// Helpers (inlined from task requirements)
// ──────────────────────────────────────────────────────────

async function registerAndLogin(page, email, password, displayName) {
  await page.goto('/register');
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.fill('input[name="password_confirm"]', password);
  await page.fill('input[name="display_name"]', displayName);
  await page.click('button[type="submit"]');
  await page.waitForURL('**/dashboard');
}

async function createList(page, name) {
  await page.fill('input[name="name"]', name);
  await page.click('text=Create List');
  await page.waitForURL('**/list/**');
  return page.url().split('/list/')[1];
}

// ──────────────────────────────────────────────────────────
// Unique email counter (resets per file but fine for test isolation)
// ──────────────────────────────────────────────────────────
let uid = 1;
function uniqueEmail() {
  return `dash-test-${uid++}@example.com`;
}

// ──────────────────────────────────────────────────────────
// Tests
// ──────────────────────────────────────────────────────────

test.describe('PRD-0007 — My Lists Dashboard', () => {

  test('1. Dashboard is landing page after login', async ({ page }) => {
    const email = uniqueEmail();
    await registerAndLogin(page, email, 'password123', 'UserOne');

    // Assert URL ends with /dashboard
    expect(page.url()).toMatch(/\/dashboard$/);

    // Assert "My Lists" heading visible
    await expect(page.locator('h1')).toContainText('My Lists');
  });

  test('2. Dashboard shows user greeting', async ({ page }) => {
    const email = uniqueEmail();
    await registerAndLogin(page, email, 'password123', 'DashTest');

    // Assert greeting includes display name
    await expect(page.getByText('Hi, DashTest')).toBeVisible();
  });

  test('3. Empty state for new users', async ({ page }) => {
    const email = uniqueEmail();
    await registerAndLogin(page, email, 'password123', 'EmptyUser');

    // Assert the empty-state message is visible
    await expect(page.getByText('No active lists yet')).toBeVisible();
  });

  test('4. Active and past lists separated', async ({ page }) => {
    const email = uniqueEmail();
    await registerAndLogin(page, email, 'password123', 'ListSep');

    // Create first list "ActiveOne"
    await createList(page, 'ActiveOne');

    // Navigate back to dashboard
    await page.goto('/dashboard');
    await page.waitForURL('**/dashboard');

    // Create second list "ToBeDone"
    const code = await createList(page, 'ToBeDone');

    // Complete "ToBeDone" — handle the hx-confirm browser dialog
    page.on('dialog', dialog => dialog.accept());
    await page.click('text=Complete List');
    await page.waitForURL('**/dashboard');

    // Assert both section headings visible
    await expect(page.getByText('Active Lists')).toBeVisible();
    await expect(page.getByText('Past Lists')).toBeVisible();
  });

  test('5. List cards clickable', async ({ page }) => {
    const email = uniqueEmail();
    await registerAndLogin(page, email, 'password123', 'ClickTest');

    // Create list and go back to dashboard
    await createList(page, 'ClickMe');
    await page.goto('/dashboard');
    await page.waitForURL('**/dashboard');

    // Click the list card showing the list name
    await page.click('text=ClickMe');

    // Assert navigated to list page
    expect(page.url()).toContain('/list/');
  });

  test('6. Logout from dashboard', async ({ page }) => {
    const email = uniqueEmail();
    await registerAndLogin(page, email, 'password123', 'LogoutTest');

    // Click the logout button
    await page.click('text=Logout');

    // Assert redirected to /login
    await page.waitForURL('**/login');
  });

  test('7. Create form with empty name shows validation', async ({ page }) => {
    const email = uniqueEmail();
    await registerAndLogin(page, email, 'password123', 'ValTest');

    // Try submitting the create form without entering a name
    await page.click('text=Create List');

    // With browser-native validation (required attribute), the form won't submit
    // and we remain on the dashboard. Assert still on /dashboard.
    await expect(page).toHaveURL(/\/dashboard$/);

    // Also check that "My Lists" heading is still visible (page didn't navigate)
    await expect(page.locator('h1')).toContainText('My Lists');
  });

});
