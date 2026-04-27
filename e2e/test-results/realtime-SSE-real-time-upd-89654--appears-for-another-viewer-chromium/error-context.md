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
          - button "95W5XK" [ref=e19] [cursor=pointer]
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
          - code [ref=e52]: 95W5XK
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
  69  |       await pageB.waitForTimeout(1000);
  70  |       // Check SSE connection by looking at the sse-connect attribute
  71  |       const sseInfo = await pageB.evaluate(() => {
  72  |         const el = document.querySelector('[sse-connect]');
  73  |         const url = el ? el.getAttribute('sse-connect') : null;
  74  |         return { sseConnectUrl: url, origin: window.location.origin };
  75  |       });
  76  |       console.log('SSE info:', JSON.stringify(sseInfo));
  77  |     });
  78  | 
  79  |     await test.step('User A adds an item', async () => {
  80  |       await pageA.fill('input[name="name"]', 'Fresh Milk');
  81  |       await pageA.fill('input[name="quantity"]', '2 liters');
  82  |       await pageA.fill('input[name="observations"]', 'organic');
  83  |       await pageA.click('button:has-text("Add Item")');
  84  |     });
  85  | 
  86  |     await test.step('Assert User B sees the item within 5 seconds via SSE', async () => {
> 87  |       await expect(pageB.locator('text=Fresh Milk')).toBeVisible({ timeout: 5000 });
      |                                                      ^ Error: expect(locator).toBeVisible() failed
  88  |     });
  89  | 
  90  |     await ctxA.close();
  91  |     await ctxB.close();
  92  |   });
  93  | 
  94  |   test('2. Real-time item check sync', async ({ browser }) => {
  95  |     const ctxA = await browser.newContext();
  96  |     const ctxB = await browser.newContext();
  97  |     const pageA = await ctxA.newPage();
  98  |     const pageB = await ctxB.newPage();
  99  | 
  100 |     const emailA = uniqueEmail('rt-check-a');
  101 |     const emailB = uniqueEmail('rt-check-b');
  102 |     const password = 'TestPass123';
  103 | 
  104 |     await test.step('Register both users', async () => {
  105 |       await registerAndLogin(pageA, emailA, password, 'CheckA');
  106 |       await registerAndLogin(pageB, emailB, password, 'CheckB');
  107 |     });
  108 | 
  109 |     let code;
  110 |     await test.step('User A creates a list and adds an item', async () => {
  111 |       await createListAndWait(pageA, 'Check Sync Test');
  112 |       code = await extractListCode(pageA);
  113 |       await pageA.fill('input[name="name"]', 'Bananas');
  114 |       await pageA.fill('input[name="quantity"]', '1 bunch');
  115 |       await pageA.click('button:has-text("Add Item")');
  116 |       await expect(pageA.locator('text=Bananas')).toBeVisible({ timeout: 5000 });
  117 |     });
  118 | 
  119 |     await test.step('User B navigates to the same list and sees the item', async () => {
  120 |       await navigateToListViaUrl(pageB, code);
  121 |       await expect(pageB.locator('text=Bananas')).toBeVisible({ timeout: 5000 });
  122 |     });
  123 | 
  124 |     await test.step('User A checks the item', async () => {
  125 |       const checkbox = pageA.locator('#item-list input[type="checkbox"]');
  126 |       await checkbox.check();
  127 |       await expect(pageA.locator('div.opacity-50').filter({ hasText: 'Bananas' })).toBeVisible({ timeout: 5000 });
  128 |     });
  129 | 
  130 |     await test.step('Assert User B sees the item as checked within 5 seconds via SSE', async () => {
  131 |       const checkedItem = pageB.locator('div.opacity-50').filter({ hasText: 'Bananas' });
  132 |       await expect(checkedItem).toBeVisible({ timeout: 5000 });
  133 |       await expect(pageB.locator('#item-list input[type="checkbox"]')).toBeChecked();
  134 |     });
  135 | 
  136 |     await ctxA.close();
  137 |     await ctxB.close();
  138 |   });
  139 | 
  140 |   test('3. New list appears on dashboard via SSE', async ({ browser }) => {
  141 |     const ctxA = await browser.newContext();
  142 |     const ctxB = await browser.newContext();
  143 |     const pageA = await ctxA.newPage();
  144 |     const pageB = await ctxB.newPage();
  145 | 
  146 |     const emailA = uniqueEmail('rt-dash-a');
  147 |     const emailB = uniqueEmail('rt-dash-b');
  148 |     const password = 'TestPass123';
  149 | 
  150 |     await test.step('Register both users', async () => {
  151 |       await registerAndLogin(pageA, emailA, password, 'DashA');
  152 |       await registerAndLogin(pageB, emailB, password, 'DashB');
  153 |     });
  154 | 
  155 |     await test.step('Both users are on the dashboard with SSE connected', async () => {
  156 |       await pageA.goto('/dashboard');
  157 |       await pageB.goto('/dashboard');
  158 |       await pageA.waitForLoadState('networkidle');
  159 |       await pageB.waitForLoadState('networkidle');
  160 |     });
  161 | 
  162 |     await test.step('User A creates a new list', async () => {
  163 |       await pageA.fill('input[name="name"]', 'Dashboard SSE Alpha');
  164 |       await pageA.click('text=Create List');
  165 |       await expect(pageA.locator('#list-name')).toBeVisible({ timeout: 10000 });
  166 |     });
  167 | 
  168 |     await test.step('Assert User B sees the new list appear on dashboard within 5 seconds via SSE', async () => {
  169 |       await expect(pageB.locator('text=Dashboard SSE Alpha')).toBeVisible({ timeout: 5000 });
  170 |     });
  171 | 
  172 |     await ctxA.close();
  173 |     await ctxB.close();
  174 |   });
  175 | 
  176 | });
  177 | 
```