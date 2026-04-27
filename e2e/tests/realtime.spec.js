const { test, expect } = require('@playwright/test');

// ── Helpers ──────────────────────────────────────────────────

async function registerAndLogin(page, email, password, displayName) {
  await page.goto('/register');
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.fill('input[name="password_confirm"]', password);
  await page.fill('input[name="display_name"]', displayName);
  await page.click('button[type="submit"]');
  await page.waitForURL('**/dashboard');
}

async function createListAndGetCode(page, name) {
  await page.fill('input[name="name"]', name);
  await page.click('text=Create List');
  await page.waitForURL('**/list/**');
  return page.url().split('/list/')[1];
}

async function navigateToList(page, code) {
  await page.goto('/list/' + code);
  await page.waitForLoadState('networkidle');
}

// ── Unique email counter per worker ─────────────────────────

let emailSeq = 0;
function uniqueEmail(prefix) {
  emailSeq++;
  // Timestamp + sequence ensures uniqueness across parallel workers
  return `${prefix}-${Date.now()}-${emailSeq}@e2e-test.com`;
}

// ── Tests ────────────────────────────────────────────────────

test.describe('SSE real-time updates (PRD-0003 FR-10, PRD-0005 FR-9/10/11)', () => {

  test('1. Real-time item appears for another viewer', async ({ browser }) => {
    const ctxA = await browser.newContext();
    const ctxB = await browser.newContext();
    const pageA = await ctxA.newPage();
    const pageB = await ctxB.newPage();

    const emailA = uniqueEmail('rt-item-a');
    const emailB = uniqueEmail('rt-item-b');
    const password = 'TestPass123';

    await test.step('Register both users', async () => {
      await registerAndLogin(pageA, emailA, password, 'RealTimeA');
      await registerAndLogin(pageB, emailB, password, 'RealTimeB');
    });

    await test.step('User A creates a list and gets share code', async () => {
      const code = await createListAndGetCode(pageA, 'Real-Time Item Sync');
      expect(code).toMatch(/^[a-z0-9]{6}$/);
    });

    await test.step('User B navigates to the same list', async () => {
      await navigateToList(pageB, code);
    });

    await test.step('User A adds an item', async () => {
      await pageA.fill('input[name="name"]', 'Fresh Milk');
      await pageA.fill('input[name="quantity"]', '2 liters');
      await pageA.fill('input[name="observations"]', 'organic');
      await pageA.click('button:has-text("Add Item")');
    });

    await test.step('Assert User B sees the item within 5 seconds via SSE', async () => {
      await expect(pageB.locator('text=Fresh Milk')).toBeVisible({ timeout: 5000 });
    });

    await ctxA.close();
    await ctxB.close();
  });

  test('2. Real-time item check sync', async ({ browser }) => {
    const ctxA = await browser.newContext();
    const ctxB = await browser.newContext();
    const pageA = await ctxA.newPage();
    const pageB = await ctxB.newPage();

    const emailA = uniqueEmail('rt-check-a');
    const emailB = uniqueEmail('rt-check-b');
    const password = 'TestPass123';

    await test.step('Register both users', async () => {
      await registerAndLogin(pageA, emailA, password, 'CheckA');
      await registerAndLogin(pageB, emailB, password, 'CheckB');
    });

    let code;
    await test.step('User A creates a list and adds an item', async () => {
      code = await createListAndGetCode(pageA, 'Check Sync Test');
      await pageA.fill('input[name="name"]', 'Bananas');
      await pageA.fill('input[name="quantity"]', '1 bunch');
      await pageA.click('button:has-text("Add Item")');
      await expect(pageA.locator('text=Bananas')).toBeVisible({ timeout: 5000 });
    });

    await test.step('User B navigates to the same list and sees the item', async () => {
      await navigateToList(pageB, code);
      await expect(pageB.locator('text=Bananas')).toBeVisible({ timeout: 5000 });
    });

    await test.step('User A checks the item', async () => {
      const checkbox = pageA.locator('#item-list input[type="checkbox"]');
      await checkbox.check();
      // Assert the item appears checked on User A's page
      await expect(pageA.locator('div.opacity-50:has(text=Bananas)')).toBeVisible({ timeout: 5000 });
    });

    await test.step('Assert User B sees the item as checked within 5 seconds via SSE', async () => {
      // Checked items get opacity-50 + bg-gray-50, text is wrapped in <s> tag
      const checkedItem = pageB.locator('div.opacity-50:has(text=Bananas)');
      await expect(checkedItem).toBeVisible({ timeout: 5000 });
      // Verify the checkbox is also checked
      await expect(pageB.locator('#item-list input[type="checkbox"]')).toBeChecked();
    });

    await ctxA.close();
    await ctxB.close();
  });

  test('3. New list appears on dashboard via SSE', async ({ browser }) => {
    const ctxA = await browser.newContext();
    const ctxB = await browser.newContext();
    const pageA = await ctxA.newPage();
    const pageB = await ctxB.newPage();

    const emailA = uniqueEmail('rt-dash-a');
    const emailB = uniqueEmail('rt-dash-b');
    const password = 'TestPass123';

    await test.step('Register both users', async () => {
      await registerAndLogin(pageA, emailA, password, 'DashA');
      await registerAndLogin(pageB, emailB, password, 'DashB');
    });

    await test.step('Both users are on the dashboard with SSE connected', async () => {
      await pageA.goto('/dashboard');
      await pageB.goto('/dashboard');
      await pageA.waitForLoadState('networkidle');
      await pageB.waitForLoadState('networkidle');
    });

    await test.step('User A creates a new list', async () => {
      await pageA.fill('input[name="name"]', 'Dashboard SSE Alpha');
      await pageA.click('text=Create List');
      await pageA.waitForURL('**/list/**');
    });

    await test.step('Assert User B sees the new list appear on dashboard within 5 seconds via SSE', async () => {
      // The dashboard auto-reloads via hx-trigger="sse:list-created" hx-get="/dashboard"
      await expect(pageB.locator('text=Dashboard SSE Alpha')).toBeVisible({ timeout: 5000 });
    });

    await ctxA.close();
    await ctxB.close();
  });

});
