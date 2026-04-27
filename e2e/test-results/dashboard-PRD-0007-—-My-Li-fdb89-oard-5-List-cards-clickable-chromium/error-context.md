# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: dashboard.spec.js >> PRD-0007 — My Lists Dashboard >> 5. List cards clickable
- Location: tests/dashboard.spec.js:65:3

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: page.waitForURL: Test timeout of 30000ms exceeded.
=========================== logs ===========================
waiting for navigation to "**/list/**" until "load"
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
        - generic [ref=e6]: Hi, ClickTest
        - button "Logout" [ref=e8] [cursor=pointer]
  - generic [ref=e10]:
    - generic [ref=e11]:
      - generic [ref=e13]:
        - generic [ref=e14]:
          - heading "ClickMe" [level=1] [ref=e15]
          - paragraph [ref=e16]: Created by you
        - generic [ref=e17]:
          - generic [ref=e18]: "Code:"
          - button "Y30VIQ" [ref=e19] [cursor=pointer]
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
          - code [ref=e46]: Y30VIQ
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
  1  | // @ts-check
  2  | const { test, expect } = require('@playwright/test');
  3  | 
  4  | function uniqueEmail(prefix) {
  5  |   return `${prefix}-${Date.now()}@example.com`;
  6  | }
  7  | 
  8  | async function registerAndLogin(page, email, password, displayName) {
  9  |   await page.goto('/register');
  10 |   await page.fill('input[name="email"]', email);
  11 |   await page.fill('input[name="password"]', password);
  12 |   await page.fill('input[name="display_name"]', displayName);
  13 |   await Promise.all([
  14 |     page.waitForURL('**/dashboard', { timeout: 10000 }),
  15 |     page.click('button[type="submit"]')
  16 |   ]);
  17 | }
  18 | 
  19 | async function createList(page, name) {
  20 |   await page.fill('input[name="name"]', name);
  21 |   await page.click('text=Create List');
> 22 |   await page.waitForURL('**/list/**');
     |              ^ Error: page.waitForURL: Test timeout of 30000ms exceeded.
  23 |   return page.url().split('/list/')[1];
  24 | }
  25 | 
  26 | test.describe('PRD-0007 — My Lists Dashboard', () => {
  27 | 
  28 |   test('1. Dashboard is landing page after login', async ({ page }) => {
  29 |     const email = uniqueEmail('dash1');
  30 |     await registerAndLogin(page, email, 'password123', 'UserOne');
  31 | 
  32 |     expect(page.url()).toMatch(/\/dashboard$/);
  33 |     await expect(page.locator('h1')).toContainText('My Lists');
  34 |   });
  35 | 
  36 |   test('2. Dashboard shows user greeting', async ({ page }) => {
  37 |     const email = uniqueEmail('dash2');
  38 |     await registerAndLogin(page, email, 'password123', 'DashTest');
  39 |     await expect(page.getByText('Hi, DashTest')).toBeVisible();
  40 |   });
  41 | 
  42 |   test('3. Empty state for new users', async ({ page }) => {
  43 |     const email = uniqueEmail('dash3');
  44 |     await registerAndLogin(page, email, 'password123', 'EmptyUser');
  45 |     await expect(page.getByText('No active lists yet')).toBeVisible();
  46 |   });
  47 | 
  48 |   test('4. Active and past lists separated', async ({ page }) => {
  49 |     const email = uniqueEmail('dash4');
  50 |     await registerAndLogin(page, email, 'password123', 'ListSep');
  51 | 
  52 |     await createList(page, 'ActiveOne');
  53 |     await page.goto('/dashboard');
  54 | 
  55 |     const code = await createList(page, 'ToBeDone');
  56 |     // Complete the list — accept the confirmation dialog
  57 |     page.on('dialog', dialog => dialog.accept());
  58 |     await page.click('text=Complete List');
  59 |     await page.waitForURL('**/dashboard');
  60 | 
  61 |     await expect(page.getByText('Active Lists')).toBeVisible();
  62 |     await expect(page.getByText('Past Lists')).toBeVisible();
  63 |   });
  64 | 
  65 |   test('5. List cards clickable', async ({ page }) => {
  66 |     const email = uniqueEmail('dash5');
  67 |     await registerAndLogin(page, email, 'password123', 'ClickTest');
  68 | 
  69 |     await createList(page, 'ClickMe');
  70 |     await page.goto('/dashboard');
  71 | 
  72 |     await page.click('text=ClickMe');
  73 |     expect(page.url()).toContain('/list/');
  74 |   });
  75 | 
  76 |   test('6. Logout from dashboard', async ({ page }) => {
  77 |     const email = uniqueEmail('dash6');
  78 |     await registerAndLogin(page, email, 'password123', 'LogoutTest');
  79 | 
  80 |     await page.click('button:has-text("Logout")');
  81 |     await page.waitForURL('**/login', { timeout: 10000 });
  82 |   });
  83 | 
  84 |   test('7. Create form with empty name shows validation', async ({ page }) => {
  85 |     const email = uniqueEmail('dash7');
  86 |     await registerAndLogin(page, email, 'password123', 'ValTest');
  87 | 
  88 |     // Submit the create form with empty name — browser validates required field
  89 |     await page.click('text=Create List');
  90 | 
  91 |     // Browser-native validation prevents submission; we stay on dashboard
  92 |     await expect(page).toHaveURL(/\/dashboard$/);
  93 |     await expect(page.locator('h1')).toContainText('My Lists');
  94 |   });
  95 | 
  96 | });
  97 | 
```