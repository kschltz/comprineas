const { test, expect } = require('@playwright/test');

// ──────────────────────────────────────────────────────────
// Inline login/logout helpers
// ──────────────────────────────────────────────────────────

async function login(page, email, password) {
  await page.goto('/login');
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.click('button[type="submit"]');
  await page.waitForURL('**/dashboard');
}

async function registerAndLogin(page, email, password, displayName) {
  await page.goto('/register');
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.fill('input[name="display_name"]', displayName);
  await page.click('button[type="submit"]');
  await page.waitForURL('**/dashboard');
}

async function logout(page) {
  // POST to /logout (GET is not accepted — POST only per route config)
  await page.request.post('/logout');
  // Navigate to login to confirm session is cleared
  await page.goto('/login');
  await page.waitForURL('**/login');
}

// ──────────────────────────────────────────────────────────
// PRD-0003: Create a new shared list
// ──────────────────────────────────────────────────────────

test('PRD-0003: Create a new shared list', async ({ page }) => {
  const id = Date.now();
  const email    = `create-list-${id}@test.com`;
  const listName = `Weekly Groceries ${id}`;

  await registerAndLogin(page, email, 'testpass123', 'Alice');

  // Fill "Create a New List" form and submit
  await page.fill('input[name="name"]', listName);
  await page.click('button:has-text("Create List")');

  // Assert redirect to /list/SOMECODE (6-char alphanumeric code)
  await page.waitForURL(/\/list\/[a-z0-9]{6}/);

  // Assert list name is visible on the list page
  await expect(page.locator('#list-name')).toContainText(listName);
});

// ──────────────────────────────────────────────────────────
// PRD-0004: Join an existing list by code
// ──────────────────────────────────────────────────────────

test('PRD-0004: Join an existing list by code', async ({ page }) => {
  const id = Date.now();
  const emailA   = `join-user-a-${id}@test.com`;
  const emailB   = `join-user-b-${id}@test.com`;
  const listName = `Team List ${id}`;

  // ── User A: register, create a list, extract the code ──
  await registerAndLogin(page, emailA, 'testpass123', 'Alice');

  await page.fill('input[name="name"]', listName);
  await page.click('button:has-text("Create List")');
  await page.waitForURL(/\/list\/([a-z0-9]{6})/);

  const url = page.url();
  const listCode = url.match(/\/list\/([a-z0-9]{6})/)[1];
  expect(listCode).toMatch(/^[a-z0-9]{6}$/);

  // ── Logout User A ──
  await logout(page);

  // ── User B: register, join by code ──
  await registerAndLogin(page, emailB, 'testpass456', 'Bob');

  // Fill the join form on the dashboard
  await page.fill('input[name="code"]', listCode);
  await page.click('button:has-text("Join")');

  // Assert redirected to the shared list page
  await page.waitForURL(`/list/${listCode}`);

  // Assert list name matches
  await expect(page.locator('#list-name')).toContainText(listName);
});

// ──────────────────────────────────────────────────────────
// PRD-0003 + PRD-0006: Complete a list (archive to past lists)
// ──────────────────────────────────────────────────────────

test('PRD-0003/0006: Complete a list and see it in past lists', async ({ page }) => {
  const id = Date.now();
  const email    = `complete-list-${id}@test.com`;
  const listName = `Trip to Store ${id}`;

  await registerAndLogin(page, email, 'testpass123', 'Charlie');

  // Create a list first
  await page.fill('input[name="name"]', listName);
  await page.click('button:has-text("Create List")');
  await page.waitForURL(/\/list\/[a-z0-9]{6}/);

  // Accept the hx-confirm dialog that fires on "Complete List" click
  page.on('dialog', async (dialog) => {
    await dialog.accept();
  });

  await page.click('button:has-text("Complete List")');

  // Assert redirect to /dashboard
  await page.waitForURL('**/dashboard');

  // Assert "Past Lists" heading is visible
  await expect(page.locator('h2:has-text("Past Lists")')).toBeVisible();

  // Assert the completed list name appears in the past-lists section
  await expect(page.locator('text=Trip to Store').first()).toBeVisible();
});

// ──────────────────────────────────────────────────────────
// PRD-0004: Navigate to non-existent list shows error
// ──────────────────────────────────────────────────────────

test('PRD-0004: Navigate to a non-existent list shows error', async ({ page }) => {
  // Must be logged in (otherwise the app redirects to /login)
  const id = Date.now();
  const email = `notfound-${id}@test.com`;

  await registerAndLogin(page, email, 'testpass123', 'Diana');

  // Navigate to a non-existent list code (6 uppercase chars)
  await page.goto('/list/ZZZZZZ');

  // Assert "List not found" is visible somewhere on the page
  await expect(page.locator('body')).toContainText(/List not found/i);
});
