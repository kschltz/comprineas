const { test, expect } = require('@playwright/test');

function uniqueEmail(prefix) {
  return `${prefix}-${Date.now()}@e2e-test.com`;
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

async function createListAndWait(page, name) {
  await page.fill('input[name="name"]', name);
  await page.click('text=Create List');
  // HTMX swaps body — wait for list content to appear
  await expect(page.locator('#list-name')).toBeVisible({ timeout: 10000 });
  // Also wait for the complete button to be rendered
  await expect(page.locator('button:has-text("Complete List")')).toBeVisible({ timeout: 5000 });
}

async function extractListCode(page) {
  const body = await page.content();
  // Try hx-post attribute (codes are uppercase)
  let m = body.match(/hx-post="\/list\/([A-Za-z0-9]{6})/);
  if (m) return m[1];
  // Try copyCode function call
  m = body.match(/copyCode\('([A-Za-z0-9]{6})'\)/);
  if (m) return m[1];
  return null;
}

async function navigateToListViaUrl(page, code) {
  await page.goto('/list/' + code);
  await expect(page.locator('#list-name')).toBeVisible({ timeout: 10000 });
}

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

    let code;
    await test.step('User A creates a list and gets share code', async () => {
      await createListAndWait(pageA, 'Real-Time Item Sync');
      code = await extractListCode(pageA);
      expect(code).toMatch(/^[a-zA-Z0-9]{6}$/);
    });

    await test.step('User B navigates to the same list', async () => {
      await navigateToListViaUrl(pageB, code);
      await pageB.waitForTimeout(1000);
      // Check SSE connection by looking at the sse-connect attribute
      const sseInfo = await pageB.evaluate(() => {
        const el = document.querySelector('[sse-connect]');
        const url = el ? el.getAttribute('sse-connect') : null;
        return { sseConnectUrl: url, origin: window.location.origin };
      });
      console.log('SSE info:', JSON.stringify(sseInfo));
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
      await createListAndWait(pageA, 'Check Sync Test');
      code = await extractListCode(pageA);
      await pageA.fill('input[name="name"]', 'Bananas');
      await pageA.fill('input[name="quantity"]', '1 bunch');
      await pageA.click('button:has-text("Add Item")');
      await expect(pageA.locator('text=Bananas')).toBeVisible({ timeout: 5000 });
    });

    await test.step('User B navigates to the same list and sees the item', async () => {
      await navigateToListViaUrl(pageB, code);
      await expect(pageB.locator('text=Bananas')).toBeVisible({ timeout: 5000 });
    });

    await test.step('User A checks the item', async () => {
      const checkbox = pageA.locator('#item-list input[type="checkbox"]');
      await checkbox.check();
      await expect(pageA.locator('div.opacity-50').filter({ hasText: 'Bananas' })).toBeVisible({ timeout: 5000 });
    });

    await test.step('Assert User B sees the item as checked within 5 seconds via SSE', async () => {
      const checkedItem = pageB.locator('div.opacity-50').filter({ hasText: 'Bananas' });
      await expect(checkedItem).toBeVisible({ timeout: 5000 });
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
      await expect(pageA.locator('#list-name')).toBeVisible({ timeout: 10000 });
    });

    await test.step('Assert User B sees the new list appear on dashboard within 5 seconds via SSE', async () => {
      await expect(pageB.locator('text=Dashboard SSE Alpha')).toBeVisible({ timeout: 5000 });
    });

    await ctxA.close();
    await ctxB.close();
  });

});
