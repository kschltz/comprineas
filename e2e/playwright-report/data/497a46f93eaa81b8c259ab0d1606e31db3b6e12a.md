# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: dashboard.spec.js >> PRD-0007 — My Lists Dashboard >> 3. Empty state for new users
- Location: tests/dashboard.spec.js:42:3

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: getByText('No active lists yet')
Expected: visible
Timeout: 10000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 10000ms
  - waiting for getByText('No active lists yet')

```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
  - navigation [ref=e2]:
    - generic [ref=e3]:
      - link "Comprineas" [ref=e4] [cursor=pointer]:
        - /url: /dashboard
      - generic [ref=e5]:
        - generic [ref=e6]: Hi, EmptyUser
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
          - textbox "Enter share code" [ref=e21]
          - button "Join" [ref=e22] [cursor=pointer]
    - heading "Active Lists" [level=2] [ref=e24]
    - heading "Past Lists" [level=2] [ref=e26]
    - contentinfo [ref=e27]: Comprineas — shared grocery lists, made simple.
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
  22 |   await page.waitForURL('**/list/**');
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
> 45 |     await expect(page.getByText('No active lists yet')).toBeVisible();
     |                                                         ^ Error: expect(locator).toBeVisible() failed
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