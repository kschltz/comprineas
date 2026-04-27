const { test, expect } = require('@playwright/test');

function uniqueEmail(prefix) {
  return `${prefix}-${Date.now()}@example.com`;
}

async function login(page, email, password) {
  await page.goto('/login');
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await Promise.all([
    page.waitForURL('**/dashboard', { timeout: 10000 }),
    page.click('button:has-text("Log in")')
  ]);
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

async function createListOnDashboard(page, name) {
  await page.fill('input[name="name"]', name);
  console.log('Filled name, clicking Create List...');
  await page.click('button:has-text("Create List")');
  // HTMX swaps body, no URL change — wait for list page content
  console.log('Waiting for #list-name...');
  await expect(page.locator('#list-name')).toBeVisible({ timeout: 10000 });
  console.log('createListOnDashboard done, URL:', page.url());
}

async function logoutFromBrowser(page) {
  await page.click('button:has-text("Logout")');
  await page.waitForURL('**/login', { timeout: 10000 });
}

// ──────────────────────────────────────────────────────────
// PRD-0003: Create a new shared list
// ──────────────────────────────────────────────────────────

test('PRD-0003: Create a new shared list', async ({ page }) => {
  const email = uniqueEmail('create');
  const listName = `Weekly Groceries ${Date.now()}`;

  await registerAndLogin(page, email, 'testpass123', 'Alice');

  await createListOnDashboard(page, listName);

  await expect(page.locator('#list-name')).toContainText(listName);
});

// ──────────────────────────────────────────────────────────
// PRD-0004: Join an existing list by code
// ──────────────────────────────────────────────────────────

test('PRD-0004: Join an existing list by code', async ({ page }) => {
  const emailA = uniqueEmail('join-a');
  const emailB = uniqueEmail('join-b');
  const listName = `Team List ${Date.now()}`;

  // User A: register, create a list, extract the code from the page
  await registerAndLogin(page, emailA, 'testpass123', 'Alice');
  await createListOnDashboard(page, listName);
  
  // Extract the list code — try multiple patterns
  const pageContent = await page.content();
  // Find the hx-post attribute values
  const hxPosts = pageContent.match(/hx-post="[^"]*"/g);
  console.log('hx-post values:', hxPosts);
  // Also find any 6-char alphanumeric code
  const codes = pageContent.match(/[a-z0-9]{6}/g);
  console.log('All 6-char codes:', [...new Set(codes || [])].slice(0, 10));


  // Logout User A
  await logoutFromBrowser(page);

  // User B: register, join by code
  await registerAndLogin(page, emailB, 'testpass456', 'Bob');

  await page.fill('input[name="code"]', listCode);
  await page.click('button:has-text("Join")');
  // HTMX swaps body, URL stays /dashboard — wait for list content
  await expect(page.locator('#list-name')).toContainText(listName, { timeout: 10000 });

  await expect(page.locator('#list-name')).toContainText(listName);
});

// ──────────────────────────────────────────────────────────
// PRD-0003 + PRD-0006: Complete a list (archive to past lists)
// ──────────────────────────────────────────────────────────

test('PRD-0003/0006: Complete a list and see it in past lists', async ({ page }) => {
  const email = uniqueEmail('complete');
  const listName = `Trip to Store ${Date.now()}`;

  await registerAndLogin(page, email, 'testpass123', 'Charlie');
  await createListOnDashboard(page, listName);

  // Accept the hx-confirm dialog
  page.on('dialog', async (dialog) => { await dialog.accept(); });
  await page.click('button:has-text("Complete List")');
  // HTMX handles redirect, swaps body — wait for dashboard content
  await expect(page.locator('h1').first()).toContainText('My Lists', { timeout: 10000 });

  // Assert "Past Lists" heading is visible and list name appears
  await expect(page.locator('h2:has-text("Past Lists")')).toBeVisible();
  await expect(page.locator('text=Trip to Store').first()).toBeVisible();
});

// ──────────────────────────────────────────────────────────
// PRD-0004: Navigate to non-existent list shows error
// ──────────────────────────────────────────────────────────

test('PRD-0004: Navigate to a non-existent list shows error', async ({ page }) => {
  const email = uniqueEmail('notfound');
  await registerAndLogin(page, email, 'testpass123', 'Diana');

  await page.goto('/list/ZZZZZZ');
  await expect(page.locator('body')).toContainText(/List not found/i);
});
