# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: realtime.spec.js >> SSE real-time updates (PRD-0003 FR-10, PRD-0005 FR-9/10/11) >> 1. Real-time item appears for another viewer
- Location: tests/realtime.spec.js:45:3

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: locator('text=Fresh Milk')
Expected: visible
Timeout: 5000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 5000ms
  - waiting for locator('text=Fresh Milk')

```

# Page snapshot

```yaml
- generic [ref=e1]:
  - navigation [ref=e2]:
    - generic [ref=e3]:
      - link "Comprineas" [ref=e4] [cursor=pointer]:
        - /url: /dashboard
      - generic [ref=e5]:
        - generic [ref=e6]: Hi, RealTimeA
        - button "Logout" [ref=e8] [cursor=pointer]
  - generic [ref=e10]:
    - generic [ref=e11]:
      - generic [ref=e13]:
        - generic [ref=e14]:
          - heading "Real-Time Item Sync" [level=1] [ref=e15]
          - paragraph [ref=e16]: Created by you
        - generic [ref=e17]:
          - generic [ref=e18]: "Code:"
          - button "8FBEKX" [ref=e19] [cursor=pointer]
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
          - button "Add Item" [active] [ref=e33] [cursor=pointer]
      - generic [ref=e34]:
        - heading "Items (0)" [level=2] [ref=e36]
        - generic [ref=e38]:
          - checkbox [ref=e39] [cursor=pointer]
          - generic [ref=e40]:
            - text: Fresh Milk (2 liters)
            - paragraph [ref=e41]: organic
          - button "Delete item" [ref=e42] [cursor=pointer]:
            - img [ref=e43]
      - button "✓ Complete List" [ref=e46] [cursor=pointer]
    - generic [ref=e47]:
      - generic [ref=e48]:
        - heading "Share List" [level=3] [ref=e49]
        - paragraph [ref=e50]: "Share this code with anyone you want to collaborate with:"
        - generic [ref=e51]:
          - code [ref=e52]: 8FBEKX
          - button "📋" [ref=e53] [cursor=pointer]
      - generic [ref=e54]:
        - heading "Participants" [level=3] [ref=e55]
        - paragraph [ref=e56]: "1"
        - paragraph [ref=e57]: people have joined this list
      - link "← Back to My Lists" [ref=e59] [cursor=pointer]:
        - /url: /dashboard
```

# Test source

```ts
  1   | const { test, expect } = require('@playwright/test');
  2   | 
  3   | function uniqueEmail(prefix) {
  4   |   return `${prefix}-${Date.now()}@e2e-test.com`;
  5   | }
  6   | 
  7   | async function registerAndLogin(page, email, password, displayName) {
  8   |   await page.goto('/register');
  9   |   await page.fill('input[name="email"]', email);
  10  |   await page.fill('input[name="password"]', password);
  11  |   await page.fill('input[name="display_name"]', displayName);
  12  |   await Promise.all([
  13  |     page.waitForURL('**/dashboard', { timeout: 10000 }),
  14  |     page.click('button[type="submit"]')
  15  |   ]);
  16  | }
  17  | 
  18  | async function createListAndWait(page, name) {
  19  |   await page.fill('input[name="name"]', name);
  20  |   await page.click('text=Create List');
  21  |   // HTMX swaps body — wait for list content to appear
  22  |   await expect(page.locator('#list-name')).toBeVisible({ timeout: 10000 });
  23  |   // Also wait for the complete button to be rendered
  24  |   await expect(page.locator('button:has-text("Complete List")')).toBeVisible({ timeout: 5000 });
  25  | }
  26  | 
  27  | async function extractListCode(page) {
  28  |   const body = await page.content();
  29  |   // Try hx-post attribute (codes are uppercase)
  30  |   let m = body.match(/hx-post="\/list\/([A-Za-z0-9]{6})/);
  31  |   if (m) return m[1];
  32  |   // Try copyCode function call
  33  |   m = body.match(/copyCode\('([A-Za-z0-9]{6})'\)/);
  34  |   if (m) return m[1];
  35  |   return null;
  36  | }
  37  | 
  38  | async function navigateToListViaUrl(page, code) {
  39  |   await page.goto('/list/' + code);
  40  |   await expect(page.locator('#list-name')).toBeVisible({ timeout: 10000 });
  41  | }
  42  | 
  43  | test.describe('SSE real-time updates (PRD-0003 FR-10, PRD-0005 FR-9/10/11)', () => {
  44  | 
  45  |   test('1. Real-time item appears for another viewer', async ({ browser }) => {
  46  |     const ctxA = await browser.newContext();
  47  |     const ctxB = await browser.newContext();
  48  |     const pageA = await ctxA.newPage();
  49  |     const pageB = await ctxB.newPage();
  50  | 
  51  |     const emailA = uniqueEmail('rt-item-a');
  52  |     const emailB = uniqueEmail('rt-item-b');
  53  |     const password = 'TestPass123';
  54  | 
  55  |     await test.step('Register both users', async () => {
  56  |       await registerAndLogin(pageA, emailA, password, 'RealTimeA');
  57  |       await registerAndLogin(pageB, emailB, password, 'RealTimeB');
  58  |     });
  59  | 
  60  |     let code;
  61  |     await test.step('User A creates a list and gets share code', async () => {
  62  |       await createListAndWait(pageA, 'Real-Time Item Sync');
  63  |       code = await extractListCode(pageA);
  64  |       expect(code).toMatch(/^[a-zA-Z0-9]{6}$/);
  65  |     });
  66  | 
  67  |     await test.step('User B navigates to the same list', async () => {
  68  |       await navigateToListViaUrl(pageB, code);
  69  |       await pageB.waitForTimeout(1500);
  70  |     });
  71  | 
  72  |     await test.step('User A adds an item', async () => {
  73  |       await pageA.fill('input[name="name"]', 'Fresh Milk');
  74  |       await pageA.fill('input[name="quantity"]', '2 liters');
  75  |       await pageA.fill('input[name="observations"]', 'organic');
  76  |       await pageA.click('button:has-text("Add Item")');
  77  |     });
  78  | 
  79  |     await test.step('Assert User B sees the item within 5 seconds via SSE', async () => {
  80  |       const events = await pageB.evaluate(() => window.__sseEvents || []);
  81  |       console.log('SSE events received by User B:', JSON.stringify(events, null, 2));
> 82  |       await expect(pageB.locator('text=Fresh Milk')).toBeVisible({ timeout: 5000 });
      |                                                      ^ Error: expect(locator).toBeVisible() failed
  83  |     });
  84  | 
  85  |     await ctxA.close();
  86  |     await ctxB.close();
  87  |   });
  88  | 
  89  |   test('2. Real-time item check sync', async ({ browser }) => {
  90  |     const ctxA = await browser.newContext();
  91  |     const ctxB = await browser.newContext();
  92  |     const pageA = await ctxA.newPage();
  93  |     const pageB = await ctxB.newPage();
  94  | 
  95  |     const emailA = uniqueEmail('rt-check-a');
  96  |     const emailB = uniqueEmail('rt-check-b');
  97  |     const password = 'TestPass123';
  98  | 
  99  |     await test.step('Register both users', async () => {
  100 |       await registerAndLogin(pageA, emailA, password, 'CheckA');
  101 |       await registerAndLogin(pageB, emailB, password, 'CheckB');
  102 |     });
  103 | 
  104 |     let code;
  105 |     await test.step('User A creates a list and adds an item', async () => {
  106 |       await createListAndWait(pageA, 'Check Sync Test');
  107 |       code = await extractListCode(pageA);
  108 |       await pageA.fill('input[name="name"]', 'Bananas');
  109 |       await pageA.fill('input[name="quantity"]', '1 bunch');
  110 |       await pageA.click('button:has-text("Add Item")');
  111 |       await expect(pageA.locator('text=Bananas')).toBeVisible({ timeout: 5000 });
  112 |     });
  113 | 
  114 |     await test.step('User B navigates to the same list and sees the item', async () => {
  115 |       await navigateToListViaUrl(pageB, code);
  116 |       await expect(pageB.locator('text=Bananas')).toBeVisible({ timeout: 5000 });
  117 |     });
  118 | 
  119 |     await test.step('User A checks the item', async () => {
  120 |       const checkbox = pageA.locator('#item-list input[type="checkbox"]');
  121 |       await checkbox.check();
  122 |       await expect(pageA.locator('div.opacity-50').filter({ hasText: 'Bananas' })).toBeVisible({ timeout: 5000 });
  123 |     });
  124 | 
  125 |     await test.step('Assert User B sees the item as checked within 5 seconds via SSE', async () => {
  126 |       const checkedItem = pageB.locator('div.opacity-50').filter({ hasText: 'Bananas' });
  127 |       await expect(checkedItem).toBeVisible({ timeout: 5000 });
  128 |       await expect(pageB.locator('#item-list input[type="checkbox"]')).toBeChecked();
  129 |     });
  130 | 
  131 |     await ctxA.close();
  132 |     await ctxB.close();
  133 |   });
  134 | 
  135 |   test('3. New list appears on dashboard via SSE', async ({ browser }) => {
  136 |     const ctxA = await browser.newContext();
  137 |     const ctxB = await browser.newContext();
  138 |     const pageA = await ctxA.newPage();
  139 |     const pageB = await ctxB.newPage();
  140 | 
  141 |     const emailA = uniqueEmail('rt-dash-a');
  142 |     const emailB = uniqueEmail('rt-dash-b');
  143 |     const password = 'TestPass123';
  144 | 
  145 |     await test.step('Register both users', async () => {
  146 |       await registerAndLogin(pageA, emailA, password, 'DashA');
  147 |       await registerAndLogin(pageB, emailB, password, 'DashB');
  148 |     });
  149 | 
  150 |     await test.step('Both users are on the dashboard with SSE connected', async () => {
  151 |       await pageA.goto('/dashboard');
  152 |       await pageB.goto('/dashboard');
  153 |       await pageA.waitForLoadState('networkidle');
  154 |       await pageB.waitForLoadState('networkidle');
  155 |     });
  156 | 
  157 |     await test.step('User A creates a new list', async () => {
  158 |       await pageA.fill('input[name="name"]', 'Dashboard SSE Alpha');
  159 |       await pageA.click('text=Create List');
  160 |       await expect(pageA.locator('#list-name')).toBeVisible({ timeout: 10000 });
  161 |     });
  162 | 
  163 |     await test.step('Assert User B sees the new list appear on dashboard within 5 seconds via SSE', async () => {
  164 |       await expect(pageB.locator('text=Dashboard SSE Alpha')).toBeVisible({ timeout: 5000 });
  165 |     });
  166 | 
  167 |     await ctxA.close();
  168 |     await ctxB.close();
  169 |   });
  170 | 
  171 | });
  172 | 
```