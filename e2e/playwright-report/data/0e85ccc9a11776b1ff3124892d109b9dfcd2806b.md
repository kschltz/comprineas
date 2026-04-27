# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: lists.spec.js >> PRD-0004: Join an existing list by code
- Location: tests/lists.spec.js:59:1

# Error details

```
Error: expect(locator).toContainText(expected) failed

Locator: locator('#list-name')
Expected substring: "Team List 1777317997724"
Timeout: 10000ms
Error: element(s) not found

Call log:
  - Expect "toContainText" with timeout 10000ms
  - waiting for locator('#list-name')

```

# Page snapshot

```yaml
- generic [ref=e1]:
  - navigation [ref=e2]:
    - generic [ref=e3]:
      - link "Comprineas" [ref=e4] [cursor=pointer]:
        - /url: /dashboard
      - generic [ref=e5]:
        - generic [ref=e6]: Hi, Bob
        - button "Logout" [ref=e8] [cursor=pointer]
  - generic [ref=e9]:
    - heading "My Lists" [level=1] [ref=e10]
    - generic [ref=e11]:
      - generic [ref=e12]:
        - heading "Create a New List" [level=2] [ref=e13]
        - generic [ref=e14]:
          - textbox "Shopping list name" [ref=e15]
          - button "Create List" [ref=e16] [cursor=pointer]
      - generic [ref=e17]:
        - heading "Join an Existing List" [level=2] [ref=e18]
        - generic [ref=e20]:
          - textbox "Enter share code" [ref=e21]: GLBMJ1
          - button "Join" [active] [ref=e22] [cursor=pointer]
    - generic [ref=e23]:
      - heading "Active Lists" [level=2] [ref=e24]
      - paragraph [ref=e26]: No active lists yet. Create one or join one above!
    - contentinfo [ref=e27]: Comprineas — shared grocery lists, made simple.
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
  31  |   // HTMX swaps body, no URL change — wait for list page content
  32  |   await expect(page.locator('#list-name')).toBeVisible({ timeout: 10000 });
  33  | }
  34  | 
  35  | async function logoutFromBrowser(page) {
  36  |   await page.click('button:has-text("Logout")');
  37  |   await page.waitForURL('**/login', { timeout: 10000 });
  38  | }
  39  | 
  40  | // ──────────────────────────────────────────────────────────
  41  | // PRD-0003: Create a new shared list
  42  | // ──────────────────────────────────────────────────────────
  43  | 
  44  | test('PRD-0003: Create a new shared list', async ({ page }) => {
  45  |   const email = uniqueEmail('create');
  46  |   const listName = `Weekly Groceries ${Date.now()}`;
  47  | 
  48  |   await registerAndLogin(page, email, 'testpass123', 'Alice');
  49  | 
  50  |   await createListOnDashboard(page, listName);
  51  | 
  52  |   await expect(page.locator('#list-name')).toContainText(listName);
  53  | });
  54  | 
  55  | // ──────────────────────────────────────────────────────────
  56  | // PRD-0004: Join an existing list by code
  57  | // ──────────────────────────────────────────────────────────
  58  | 
  59  | test('PRD-0004: Join an existing list by code', async ({ page }) => {
  60  |   const emailA = uniqueEmail('join-a');
  61  |   const emailB = uniqueEmail('join-b');
  62  |   const listName = `Team List ${Date.now()}`;
  63  | 
  64  |   // User A: register, create a list, extract the code from the page
  65  |   await registerAndLogin(page, emailA, 'testpass123', 'Alice');
  66  |   await createListOnDashboard(page, listName);
  67  |   
  68  |   // Extract the list code — try multiple patterns
  69  |   // Extract the list code from hx-post attribute (codes are uppercase)
  70  |   const pageContent = await page.content();
  71  |   const hxPostMatch = pageContent.match(/hx-post="\/list\/([A-Za-z0-9]{6})/);
  72  |   const listCode = hxPostMatch ? hxPostMatch[1] : null;
  73  |   expect(listCode).toMatch(/^[a-zA-Z0-9]{6}$/);
  74  | 
  75  | 
  76  |   // Logout User A
  77  |   await logoutFromBrowser(page);
  78  | 
  79  |   // User B: register, join by code
  80  |   await registerAndLogin(page, emailB, 'testpass456', 'Bob');
  81  | 
  82  |   await page.fill('input[name="code"]', listCode);
  83  |   await page.click('button:has-text("Join")');
  84  |   // HTMX swaps body, URL stays /dashboard — wait for list content
> 85  |   await expect(page.locator('#list-name')).toContainText(listName, { timeout: 10000 });
      |                                            ^ Error: expect(locator).toContainText(expected) failed
  86  | 
  87  |   await expect(page.locator('#list-name')).toContainText(listName);
  88  | });
  89  | 
  90  | // ──────────────────────────────────────────────────────────
  91  | // PRD-0003 + PRD-0006: Complete a list (archive to past lists)
  92  | // ──────────────────────────────────────────────────────────
  93  | 
  94  | test('PRD-0003/0006: Complete a list and see it in past lists', async ({ page }) => {
  95  |   const email = uniqueEmail('complete');
  96  |   const listName = `Trip to Store ${Date.now()}`;
  97  | 
  98  |   await registerAndLogin(page, email, 'testpass123', 'Charlie');
  99  |   await createListOnDashboard(page, listName);
  100 | 
  101 |   // Accept the hx-confirm dialog
  102 |   page.on('dialog', async (dialog) => { await dialog.accept(); });
  103 |   await page.click('button:has-text("Complete List")');
  104 |   // HTMX handles redirect, swaps body — wait for dashboard content
  105 |   await expect(page.locator('h1').first()).toContainText('My Lists', { timeout: 10000 });
  106 | 
  107 |   // Assert "Past Lists" heading is visible and list name appears
  108 |   await expect(page.locator('h2:has-text("Past Lists")')).toBeVisible();
  109 |   await expect(page.locator('text=Trip to Store').first()).toBeVisible();
  110 | });
  111 | 
  112 | // ──────────────────────────────────────────────────────────
  113 | // PRD-0004: Navigate to non-existent list shows error
  114 | // ──────────────────────────────────────────────────────────
  115 | 
  116 | test('PRD-0004: Navigate to a non-existent list shows error', async ({ page }) => {
  117 |   const email = uniqueEmail('notfound');
  118 |   await registerAndLogin(page, email, 'testpass123', 'Diana');
  119 | 
  120 |   await page.goto('/list/ZZZZZZ');
  121 |   await expect(page.locator('body')).toContainText(/List not found/i);
  122 | });
  123 | 
```