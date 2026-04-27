# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: auth.spec.js >> Authentication >> forgot password page renders
- Location: tests/auth.spec.js:59:3

# Error details

```
Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:3001/forgot-password
Call log:
  - navigating to "http://localhost:3001/forgot-password", waiting until "load"

```

# Page snapshot

```yaml
- generic [ref=e3]:
  - generic [ref=e6]:
    - heading "This site can’t be reached" [level=1] [ref=e7]
    - paragraph [ref=e8]:
      - strong [ref=e9]: localhost
      - text: refused to connect.
    - generic [ref=e10]:
      - paragraph [ref=e11]: "Try:"
      - list [ref=e12]:
        - listitem [ref=e13]: Checking the connection
        - listitem [ref=e14]:
          - link "Checking the proxy and the firewall" [ref=e15] [cursor=pointer]:
            - /url: "#buttons"
    - generic [ref=e16]: ERR_CONNECTION_REFUSED
  - generic [ref=e17]:
    - button "Reload" [ref=e19] [cursor=pointer]
    - button "Details" [ref=e20] [cursor=pointer]
```

# Test source

```ts
  1  | const { test, expect } = require('@playwright/test');
  2  | 
  3  | function uniqueEmail(prefix) {
  4  |   return `${prefix}.${Date.now()}@example.com`;
  5  | }
  6  | 
  7  | async function register(page, email, password, displayName) {
  8  |   await page.goto('/register');
  9  |   await page.fill('input[name="email"]', email);
  10 |   await page.fill('input[name="password"]', password);
  11 |   await page.fill('input[name="display_name"]', displayName);
  12 |   await page.click('button[type="submit"]');
  13 |   // post-register redirects to /, which has no route;
  14 |   // wait for navigation to settle then check we ended up somewhere valid
  15 |   await page.waitForTimeout(500);
  16 | }
  17 | 
  18 | async function login(page, email, password) {
  19 |   await page.goto('/login');
  20 |   await page.fill('input[name="email"]', email);
  21 |   await page.fill('input[name="password"]', password);
  22 |   await page.click('button[type="submit"]');
  23 |   await page.waitForURL('**/dashboard');
  24 | }
  25 | 
  26 | test.describe('Authentication', () => {
  27 | 
  28 |   test('user can register', async ({ page }) => {
  29 |     const email = uniqueEmail('reg');
  30 |     await register(page, email, 'password123', 'Reggie');
  31 |     // After registration, app should land on dashboard or redirect there
  32 |     await page.goto('/dashboard');
  33 |     await expect(page.locator('body')).toContainText('Hi, Reggie');
  34 |   });
  35 | 
  36 |   test('user can login with password', async ({ page }) => {
  37 |     const email = uniqueEmail('login');
  38 |     // Register first
  39 |     await register(page, email, 'password123', 'LoginTest');
  40 |     // Now logout if needed, then login
  41 |     await page.goto('/login');
  42 |     await page.fill('input[name="email"]', email);
  43 |     await page.fill('input[name="password"]', 'password123');
  44 |     await page.click('button[type="submit"]');
  45 |     await page.waitForURL('**/dashboard');
  46 |     await expect(page.locator('body')).toContainText('Hi, LoginTest');
  47 |   });
  48 | 
  49 |   test('user can logout', async ({ page }) => {
  50 |     const email = uniqueEmail('logout');
  51 |     await register(page, email, 'password123', 'LogoutTest');
  52 |     await login(page, email, 'password123');
  53 |     // Click logout
  54 |     await page.click('button:has-text("Logout")');
  55 |     // Should redirect to login page
  56 |     await expect(page.locator('body')).toContainText(/log in|login/i);
  57 |   });
  58 | 
  59 |   test('forgot password page renders', async ({ page }) => {
> 60 |     await page.goto('/forgot-password');
     |                ^ Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:3001/forgot-password
  61 |     await expect(page.locator('input[name="email"]')).toBeVisible();
  62 |     await expect(page.locator('button[type="submit"]')).toBeVisible();
  63 |   });
  64 | 
  65 |   test('invalid login shows error', async ({ page }) => {
  66 |     const email = uniqueEmail('badlogin');
  67 |     await register(page, email, 'password123', 'BadLogin');
  68 |     // Try wrong password
  69 |     await page.goto('/login');
  70 |     await page.fill('input[name="email"]', email);
  71 |     await page.fill('input[name="password"]', 'wrongpassword');
  72 |     await page.click('button[type="submit"]');
  73 |     // Should show error — either inline or stay on login page with message
  74 |     await expect(page.locator('body')).toContainText(/invalid|wrong|error/i);
  75 |   });
  76 | 
  77 | });
  78 | 
```