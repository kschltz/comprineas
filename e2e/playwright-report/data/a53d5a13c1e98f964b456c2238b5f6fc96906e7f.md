# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: lists.spec.js >> PRD-0004: Join an existing list by code
- Location: tests/lists.spec.js:59:1

# Error details

```
TypeError: expect(received).toMatch(expected)

Matcher error: received value must be a string

Received has value: null
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
          - heading "Team List 1777317087501" [level=1] [ref=e15]
          - paragraph [ref=e16]: Created by you
        - generic [ref=e17]:
          - generic [ref=e18]: "Code:"
          - button "8AOQRZ" [ref=e19] [cursor=pointer]
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
          - code [ref=e46]: 8AOQRZ
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
  69  |   const pageContent = await page.content();
  70  |   console.log('Page content contains hx-post:', pageContent.includes('hx-post'));
  71  |   console.log('Page URL:', page.url());
  72  |   // Try copyCode pattern from the code display button
  73  |   let m = pageContent.match(/copyCode\('([a-z0-9]{6})'\)/);
  74  |   if (!m) m = pageContent.match(/complete\/([a-z0-9]{6})/);
  75  |   if (!m) m = pageContent.match(/\/list\/([a-z0-9]{6})/);
  76  |   if (!m) m = pageContent.match(/">([a-z0-9]{6})<\/button>/);
  77  |   const listCode = m ? m[1] : null;
  78  |   console.log('Extracted code:', listCode);
> 79  |   expect(listCode).toMatch(/^[a-z0-9]{6}$/);
      |                    ^ TypeError: expect(received).toMatch(expected)
  80  | 
  81  |   // Logout User A
  82  |   await logoutFromBrowser(page);
  83  | 
  84  |   // User B: register, join by code
  85  |   await registerAndLogin(page, emailB, 'testpass456', 'Bob');
  86  | 
  87  |   await page.fill('input[name="code"]', listCode);
  88  |   await page.click('button:has-text("Join")');
  89  |   // HTMX swaps body, URL stays /dashboard — wait for list content
  90  |   await expect(page.locator('#list-name')).toContainText(listName, { timeout: 10000 });
  91  | 
  92  |   await expect(page.locator('#list-name')).toContainText(listName);
  93  | });
  94  | 
  95  | // ──────────────────────────────────────────────────────────
  96  | // PRD-0003 + PRD-0006: Complete a list (archive to past lists)
  97  | // ──────────────────────────────────────────────────────────
  98  | 
  99  | test('PRD-0003/0006: Complete a list and see it in past lists', async ({ page }) => {
  100 |   const email = uniqueEmail('complete');
  101 |   const listName = `Trip to Store ${Date.now()}`;
  102 | 
  103 |   await registerAndLogin(page, email, 'testpass123', 'Charlie');
  104 |   await createListOnDashboard(page, listName);
  105 | 
  106 |   // Accept the hx-confirm dialog
  107 |   page.on('dialog', async (dialog) => { await dialog.accept(); });
  108 |   await page.click('button:has-text("Complete List")');
  109 |   // HTMX handles redirect, swaps body — wait for dashboard content
  110 |   await expect(page.locator('h1').first()).toContainText('My Lists', { timeout: 10000 });
  111 | 
  112 |   // Assert "Past Lists" heading is visible and list name appears
  113 |   await expect(page.locator('h2:has-text("Past Lists")')).toBeVisible();
  114 |   await expect(page.locator('text=Trip to Store').first()).toBeVisible();
  115 | });
  116 | 
  117 | // ──────────────────────────────────────────────────────────
  118 | // PRD-0004: Navigate to non-existent list shows error
  119 | // ──────────────────────────────────────────────────────────
  120 | 
  121 | test('PRD-0004: Navigate to a non-existent list shows error', async ({ page }) => {
  122 |   const email = uniqueEmail('notfound');
  123 |   await registerAndLogin(page, email, 'testpass123', 'Diana');
  124 | 
  125 |   await page.goto('/list/ZZZZZZ');
  126 |   await expect(page.locator('body')).toContainText(/List not found/i);
  127 | });
  128 | 
```