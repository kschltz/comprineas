# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: realtime.spec.js >> SSE real-time updates (PRD-0003 FR-10, PRD-0005 FR-9/10/11) >> 3. New list appears on dashboard via SSE
- Location: tests/realtime.spec.js:149:3

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: locator('text=Dashboard SSE Alpha')
Expected: visible
Timeout: 5000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 5000ms
  - waiting for locator('text=Dashboard SSE Alpha')

```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
  - navigation [ref=e2]:
    - generic [ref=e3]:
      - link "Comprineas" [ref=e4] [cursor=pointer]:
        - /url: /dashboard
      - generic [ref=e5]:
        - generic [ref=e6]: Hi, DashA
        - button "Logout" [ref=e8] [cursor=pointer]
  - generic [ref=e10]:
    - generic [ref=e11]:
      - generic [ref=e13]:
        - generic [ref=e14]:
          - heading "Dashboard SSE Alpha" [level=1] [ref=e15]
          - paragraph [ref=e16]: Created by you
        - generic [ref=e17]:
          - generic [ref=e18]: "Code:"
          - button "MYLRG9" [ref=e19] [cursor=pointer]
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
      - heading "Items (0)" [level=2] [ref=e36]
      - button "✓ Complete List" [ref=e39] [cursor=pointer]
    - generic [ref=e40]:
      - generic [ref=e41]:
        - heading "Share List" [level=3] [ref=e42]
        - paragraph [ref=e43]: "Share this code with anyone you want to collaborate with:"
        - generic [ref=e44]:
          - code [ref=e45]: MYLRG9
          - button "📋" [ref=e46] [cursor=pointer]
      - generic [ref=e47]:
        - heading "Participants" [level=3] [ref=e48]
        - paragraph [ref=e49]: "1"
        - paragraph [ref=e50]: people have joined this list
      - link "← Back to My Lists" [ref=e52] [cursor=pointer]:
        - /url: /dashboard
```

# Test source

```ts
  78  |           htmxLoaded: hasHtmx,
  79  |           htmxExtSse: hasExt,
  80  |           sseAttr: sseEl ? sseEl.getAttribute('hx-sse') : null,
  81  |           scripts: scripts,
  82  |           eventSources: performance.getEntriesByType('resource').filter(e => e.name.includes('/events')).map(e => ({name: e.name, status: e.responseStatus}))
  83  |         };
  84  |       });
  85  |       console.log('User B SSE info:', JSON.stringify(sseInfo, null, 2));
  86  |     });
  87  | 
  88  |     await test.step('User A adds an item', async () => {
  89  |       await pageA.fill('input[name="name"]', 'Fresh Milk');
  90  |       await pageA.fill('input[name="quantity"]', '2 liters');
  91  |       await pageA.fill('input[name="observations"]', 'organic');
  92  |       await pageA.click('button:has-text("Add Item")');
  93  |     });
  94  | 
  95  |     await test.step('Assert User B sees the item within 5 seconds via SSE', async () => {
  96  |       await expect(pageB.locator('text=Fresh Milk')).toBeVisible({ timeout: 5000 });
  97  |     });
  98  | 
  99  |     await ctxA.close();
  100 |     await ctxB.close();
  101 |   });
  102 | 
  103 |   test('2. Real-time item check sync', async ({ browser }) => {
  104 |     const ctxA = await browser.newContext();
  105 |     const ctxB = await browser.newContext();
  106 |     const pageA = await ctxA.newPage();
  107 |     const pageB = await ctxB.newPage();
  108 | 
  109 |     const emailA = uniqueEmail('rt-check-a');
  110 |     const emailB = uniqueEmail('rt-check-b');
  111 |     const password = 'TestPass123';
  112 | 
  113 |     await test.step('Register both users', async () => {
  114 |       await registerAndLogin(pageA, emailA, password, 'CheckA');
  115 |       await registerAndLogin(pageB, emailB, password, 'CheckB');
  116 |     });
  117 | 
  118 |     let code;
  119 |     await test.step('User A creates a list and adds an item', async () => {
  120 |       await createListAndWait(pageA, 'Check Sync Test');
  121 |       code = await extractListCode(pageA);
  122 |       await pageA.fill('input[name="name"]', 'Bananas');
  123 |       await pageA.fill('input[name="quantity"]', '1 bunch');
  124 |       await pageA.click('button:has-text("Add Item")');
  125 |       await expect(pageA.locator('text=Bananas')).toBeVisible({ timeout: 5000 });
  126 |     });
  127 | 
  128 |     await test.step('User B navigates to the same list and sees the item', async () => {
  129 |       await navigateToListViaUrl(pageB, code);
  130 |       await expect(pageB.locator('text=Bananas')).toBeVisible({ timeout: 5000 });
  131 |     });
  132 | 
  133 |     await test.step('User A checks the item', async () => {
  134 |       const checkbox = pageA.locator('#item-list input[type="checkbox"]');
  135 |       await checkbox.check();
  136 |       await expect(pageA.locator('div.opacity-50').filter({ hasText: 'Bananas' })).toBeVisible({ timeout: 5000 });
  137 |     });
  138 | 
  139 |     await test.step('Assert User B sees the item as checked within 5 seconds via SSE', async () => {
  140 |       const checkedItem = pageB.locator('div.opacity-50').filter({ hasText: 'Bananas' });
  141 |       await expect(checkedItem).toBeVisible({ timeout: 5000 });
  142 |       await expect(pageB.locator('#item-list input[type="checkbox"]')).toBeChecked();
  143 |     });
  144 | 
  145 |     await ctxA.close();
  146 |     await ctxB.close();
  147 |   });
  148 | 
  149 |   test('3. New list appears on dashboard via SSE', async ({ browser }) => {
  150 |     const ctxA = await browser.newContext();
  151 |     const ctxB = await browser.newContext();
  152 |     const pageA = await ctxA.newPage();
  153 |     const pageB = await ctxB.newPage();
  154 | 
  155 |     const emailA = uniqueEmail('rt-dash-a');
  156 |     const emailB = uniqueEmail('rt-dash-b');
  157 |     const password = 'TestPass123';
  158 | 
  159 |     await test.step('Register both users', async () => {
  160 |       await registerAndLogin(pageA, emailA, password, 'DashA');
  161 |       await registerAndLogin(pageB, emailB, password, 'DashB');
  162 |     });
  163 | 
  164 |     await test.step('Both users are on the dashboard with SSE connected', async () => {
  165 |       await pageA.goto('/dashboard');
  166 |       await pageB.goto('/dashboard');
  167 |       await pageA.waitForLoadState('networkidle');
  168 |       await pageB.waitForLoadState('networkidle');
  169 |     });
  170 | 
  171 |     await test.step('User A creates a new list', async () => {
  172 |       await pageA.fill('input[name="name"]', 'Dashboard SSE Alpha');
  173 |       await pageA.click('text=Create List');
  174 |       await expect(pageA.locator('#list-name')).toBeVisible({ timeout: 10000 });
  175 |     });
  176 | 
  177 |     await test.step('Assert User B sees the new list appear on dashboard within 5 seconds via SSE', async () => {
> 178 |       await expect(pageB.locator('text=Dashboard SSE Alpha')).toBeVisible({ timeout: 5000 });
      |                                                               ^ Error: expect(locator).toBeVisible() failed
  179 |     });
  180 | 
  181 |     await ctxA.close();
  182 |     await ctxB.close();
  183 |   });
  184 | 
  185 | });
  186 | 
```