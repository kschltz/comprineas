# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: lists.spec.js >> PRD-0003: Create a new shared list
- Location: tests/lists.spec.js:43:1

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: page.waitForURL: Test timeout of 30000ms exceeded.
=========================== logs ===========================
waiting for navigation until "load"
============================================================
```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
  - navigation [ref=e2]:
    - generic [ref=e3]:
      - link "Comprineas" [ref=e4] [cursor=pointer]:
        - /url: /dashboard
      - generic [ref=e5]:
        - generic [ref=e6]: Hi, Alice
        - button "Logout" [ref=e8] [cursor=pointer]
  - generic [ref=e10]:
    - generic [ref=e11]:
      - generic [ref=e13]:
        - generic [ref=e14]:
          - heading "Weekly Groceries 1777315672766" [level=1] [ref=e15]
          - paragraph [ref=e16]: Created by you
        - generic [ref=e17]:
          - generic [ref=e18]: "Code:"
          - button "TCUYWS" [ref=e19] [cursor=pointer]
      - generic [ref=e20]:
        - heading "Add an Item" [level=2] [ref=e21]
        - generic [ref=e22]:
          - generic [ref=e23]:
            - generic [ref=e24]:
              - generic [ref=e25]: Name *
              - textbox "Item name" [ref=e26]
            - generic [ref=e27]:
              - generic [ref=e28]: Quantity
              - textbox "1 kg" [ref=e29]
            - generic [ref=e30]:
              - generic [ref=e31]: Observations
              - textbox "notes" [ref=e32]
          - button "Add Item" [ref=e33] [cursor=pointer]
      - generic [ref=e34]:
        - heading "Items (0)" [level=2] [ref=e36]
        - paragraph [ref=e38]: Loading items…
      - button "✓ Complete List" [ref=e40] [cursor=pointer]
    - generic [ref=e41]:
      - generic [ref=e42]:
        - heading "Share List" [level=3] [ref=e43]
        - paragraph [ref=e44]: "Share this code with anyone you want to collaborate with:"
        - generic [ref=e45]:
          - code [ref=e46]: TCUYWS
          - button "📋" [ref=e47] [cursor=pointer]
      - generic [ref=e48]:
        - heading "Participants" [level=3] [ref=e49]
        - paragraph [ref=e50]: "1"
        - paragraph [ref=e51]: people have joined this list
      - link "← Back to My Lists" [ref=e53] [cursor=pointer]:
        - /url: /dashboard
```

# Test source

```ts
  1   | const { test, expect } = require('@playwright/test');
  2   | 
  3   | function uniqueEmail(prefix) {
  4   |   return `${prefix}-${Date.now()}@example.com`;
  5   | }
  6   | 
  7   | async function login(page, email, password) {
  8   |   await page.goto('/login');
  9   |   await page.fill('input[name="email"]', email);
  10  |   await page.fill('input[name="password"]', password);
  11  |   await Promise.all([
  12  |     page.waitForURL('**/dashboard', { timeout: 10000 }),
  13  |     page.click('button:has-text("Log in")')
  14  |   ]);
  15  | }
  16  | 
  17  | async function registerAndLogin(page, email, password, displayName) {
  18  |   await page.goto('/register');
  19  |   await page.fill('input[name="email"]', email);
  20  |   await page.fill('input[name="password"]', password);
  21  |   await page.fill('input[name="display_name"]', displayName);
  22  |   await Promise.all([
  23  |     page.waitForURL('**/dashboard', { timeout: 10000 }),
  24  |     page.click('button[type="submit"]')
  25  |   ]);
  26  | }
  27  | 
  28  | async function createListOnDashboard(page, name) {
  29  |   await page.fill('input[name="name"]', name);
  30  |   await page.click('button:has-text("Create List")');
> 31  |   await page.waitForURL(/\/list\/[a-z0-9]{6}/);
      |              ^ Error: page.waitForURL: Test timeout of 30000ms exceeded.
  32  | }
  33  | 
  34  | async function logoutFromBrowser(page) {
  35  |   await page.click('button:has-text("Logout")');
  36  |   await page.waitForURL('**/login', { timeout: 10000 });
  37  | }
  38  | 
  39  | // ──────────────────────────────────────────────────────────
  40  | // PRD-0003: Create a new shared list
  41  | // ──────────────────────────────────────────────────────────
  42  | 
  43  | test('PRD-0003: Create a new shared list', async ({ page }) => {
  44  |   const email = uniqueEmail('create');
  45  |   const listName = `Weekly Groceries ${Date.now()}`;
  46  | 
  47  |   await registerAndLogin(page, email, 'testpass123', 'Alice');
  48  | 
  49  |   await createListOnDashboard(page, listName);
  50  | 
  51  |   await expect(page.locator('#list-name')).toContainText(listName);
  52  | });
  53  | 
  54  | // ──────────────────────────────────────────────────────────
  55  | // PRD-0004: Join an existing list by code
  56  | // ──────────────────────────────────────────────────────────
  57  | 
  58  | test('PRD-0004: Join an existing list by code', async ({ page }) => {
  59  |   const emailA = uniqueEmail('join-a');
  60  |   const emailB = uniqueEmail('join-b');
  61  |   const listName = `Team List ${Date.now()}`;
  62  | 
  63  |   // User A: register, create a list, extract the code
  64  |   await registerAndLogin(page, emailA, 'testpass123', 'Alice');
  65  |   await createListOnDashboard(page, listName);
  66  | 
  67  |   const url = page.url();
  68  |   const listCode = url.match(/\/list\/([a-z0-9]{6})/)[1];
  69  |   expect(listCode).toMatch(/^[a-z0-9]{6}$/);
  70  | 
  71  |   // Logout User A
  72  |   await logoutFromBrowser(page);
  73  | 
  74  |   // User B: register, join by code
  75  |   await registerAndLogin(page, emailB, 'testpass456', 'Bob');
  76  | 
  77  |   await page.fill('input[name="code"]', listCode);
  78  |   await page.click('button:has-text("Join")');
  79  |   await page.waitForURL(`/list/${listCode}`);
  80  | 
  81  |   await expect(page.locator('#list-name')).toContainText(listName);
  82  | });
  83  | 
  84  | // ──────────────────────────────────────────────────────────
  85  | // PRD-0003 + PRD-0006: Complete a list (archive to past lists)
  86  | // ──────────────────────────────────────────────────────────
  87  | 
  88  | test('PRD-0003/0006: Complete a list and see it in past lists', async ({ page }) => {
  89  |   const email = uniqueEmail('complete');
  90  |   const listName = `Trip to Store ${Date.now()}`;
  91  | 
  92  |   await registerAndLogin(page, email, 'testpass123', 'Charlie');
  93  |   await createListOnDashboard(page, listName);
  94  | 
  95  |   // Accept the hx-confirm dialog
  96  |   page.on('dialog', async (dialog) => { await dialog.accept(); });
  97  |   await page.click('button:has-text("Complete List")');
  98  |   await page.waitForURL('**/dashboard');
  99  | 
  100 |   // Assert "Past Lists" heading is visible and list name appears
  101 |   await expect(page.locator('h2:has-text("Past Lists")')).toBeVisible();
  102 |   await expect(page.locator('text=Trip to Store').first()).toBeVisible();
  103 | });
  104 | 
  105 | // ──────────────────────────────────────────────────────────
  106 | // PRD-0004: Navigate to non-existent list shows error
  107 | // ──────────────────────────────────────────────────────────
  108 | 
  109 | test('PRD-0004: Navigate to a non-existent list shows error', async ({ page }) => {
  110 |   const email = uniqueEmail('notfound');
  111 |   await registerAndLogin(page, email, 'testpass123', 'Diana');
  112 | 
  113 |   await page.goto('/list/ZZZZZZ');
  114 |   await expect(page.locator('body')).toContainText(/List not found/i);
  115 | });
  116 | 
```