# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: realtime.spec.js >> SSE real-time updates (PRD-0003 FR-10, PRD-0005 FR-9/10/11) >> 3. New list appears on dashboard via SSE
- Location: tests/realtime.spec.js:132:3

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
          - button "USVKSK" [ref=e19] [cursor=pointer]
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
          - code [ref=e45]: USVKSK
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
  61  |     await test.step('User A creates a list and gets share code', async () => {
  62  |       await createListAndWait(pageA, 'Real-Time Item Sync');
  63  |       code = await extractListCode(pageA);
  64  |       expect(code).toMatch(/^[a-zA-Z0-9]{6}$/);
  65  |     });
  66  | 
  67  |     await test.step('User B navigates to the same list', async () => {
  68  |       await navigateToListViaUrl(pageB, code);
  69  |     });
  70  | 
  71  |     await test.step('User A adds an item', async () => {
  72  |       await pageA.fill('input[name="name"]', 'Fresh Milk');
  73  |       await pageA.fill('input[name="quantity"]', '2 liters');
  74  |       await pageA.fill('input[name="observations"]', 'organic');
  75  |       await pageA.click('button:has-text("Add Item")');
  76  |     });
  77  | 
  78  |     await test.step('Assert User B sees the item within 5 seconds via SSE', async () => {
  79  |       await expect(pageB.locator('text=Fresh Milk')).toBeVisible({ timeout: 5000 });
  80  |     });
  81  | 
  82  |     await ctxA.close();
  83  |     await ctxB.close();
  84  |   });
  85  | 
  86  |   test('2. Real-time item check sync', async ({ browser }) => {
  87  |     const ctxA = await browser.newContext();
  88  |     const ctxB = await browser.newContext();
  89  |     const pageA = await ctxA.newPage();
  90  |     const pageB = await ctxB.newPage();
  91  | 
  92  |     const emailA = uniqueEmail('rt-check-a');
  93  |     const emailB = uniqueEmail('rt-check-b');
  94  |     const password = 'TestPass123';
  95  | 
  96  |     await test.step('Register both users', async () => {
  97  |       await registerAndLogin(pageA, emailA, password, 'CheckA');
  98  |       await registerAndLogin(pageB, emailB, password, 'CheckB');
  99  |     });
  100 | 
  101 |     let code;
  102 |     await test.step('User A creates a list and adds an item', async () => {
  103 |       await createListAndWait(pageA, 'Check Sync Test');
  104 |       code = await extractListCode(pageA);
  105 |       await pageA.fill('input[name="name"]', 'Bananas');
  106 |       await pageA.fill('input[name="quantity"]', '1 bunch');
  107 |       await pageA.click('button:has-text("Add Item")');
  108 |       await expect(pageA.locator('text=Bananas')).toBeVisible({ timeout: 5000 });
  109 |     });
  110 | 
  111 |     await test.step('User B navigates to the same list and sees the item', async () => {
  112 |       await navigateToListViaUrl(pageB, code);
  113 |       await expect(pageB.locator('text=Bananas')).toBeVisible({ timeout: 5000 });
  114 |     });
  115 | 
  116 |     await test.step('User A checks the item', async () => {
  117 |       const checkbox = pageA.locator('#item-list input[type="checkbox"]');
  118 |       await checkbox.check();
  119 |       await expect(pageA.locator('div.opacity-50').filter({ hasText: 'Bananas' })).toBeVisible({ timeout: 5000 });
  120 |     });
  121 | 
  122 |     await test.step('Assert User B sees the item as checked within 5 seconds via SSE', async () => {
  123 |       const checkedItem = pageB.locator('div.opacity-50').filter({ hasText: 'Bananas' });
  124 |       await expect(checkedItem).toBeVisible({ timeout: 5000 });
  125 |       await expect(pageB.locator('#item-list input[type="checkbox"]')).toBeChecked();
  126 |     });
  127 | 
  128 |     await ctxA.close();
  129 |     await ctxB.close();
  130 |   });
  131 | 
  132 |   test('3. New list appears on dashboard via SSE', async ({ browser }) => {
  133 |     const ctxA = await browser.newContext();
  134 |     const ctxB = await browser.newContext();
  135 |     const pageA = await ctxA.newPage();
  136 |     const pageB = await ctxB.newPage();
  137 | 
  138 |     const emailA = uniqueEmail('rt-dash-a');
  139 |     const emailB = uniqueEmail('rt-dash-b');
  140 |     const password = 'TestPass123';
  141 | 
  142 |     await test.step('Register both users', async () => {
  143 |       await registerAndLogin(pageA, emailA, password, 'DashA');
  144 |       await registerAndLogin(pageB, emailB, password, 'DashB');
  145 |     });
  146 | 
  147 |     await test.step('Both users are on the dashboard with SSE connected', async () => {
  148 |       await pageA.goto('/dashboard');
  149 |       await pageB.goto('/dashboard');
  150 |       await pageA.waitForLoadState('networkidle');
  151 |       await pageB.waitForLoadState('networkidle');
  152 |     });
  153 | 
  154 |     await test.step('User A creates a new list', async () => {
  155 |       await pageA.fill('input[name="name"]', 'Dashboard SSE Alpha');
  156 |       await pageA.click('text=Create List');
  157 |       await expect(pageA.locator('#list-name')).toBeVisible({ timeout: 10000 });
  158 |     });
  159 | 
  160 |     await test.step('Assert User B sees the new list appear on dashboard within 5 seconds via SSE', async () => {
> 161 |       await expect(pageB.locator('text=Dashboard SSE Alpha')).toBeVisible({ timeout: 5000 });
      |                                                               ^ Error: expect(locator).toBeVisible() failed
  162 |     });
  163 | 
  164 |     await ctxA.close();
  165 |     await ctxB.close();
  166 |   });
  167 | 
  168 | });
  169 | 
```