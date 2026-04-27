# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: auth.spec.js >> Authentication >> user can login with password
- Location: tests/auth.spec.js:37:3

# Error details

```
TimeoutError: page.waitForURL: Timeout 10000ms exceeded.
=========================== logs ===========================
waiting for navigation to "**/dashboard" until "load"
  navigated to "chrome-error://chromewebdata/"
============================================================
```

# Page snapshot

```yaml
- generic [ref=e6]:
  - heading "This site can’t be reached" [level=1] [ref=e7]
  - paragraph [ref=e8]:
    - text: The webpage at
    - strong [ref=e9]: http://localhost:3001/
    - text: might be temporarily down or it may have moved permanently to a new web address.
  - generic [ref=e10]: ERR_INVALID_RESPONSE
```

# Test source

```ts
  1  | const { test, expect } = require('@playwright/test');
  2  | 
  3  | function uniqueEmail(prefix) {
  4  |   return `${prefix}.${Date.now()}@example.com`;
  5  | }
  6  | 
  7  | async function registerViaApi(page, email, password, displayName) {
  8  |   // Direct POST to register endpoint — browser will follow redirects and store session cookie
  9  |   await page.goto('/register');
  10 |   await page.fill('input[name="email"]', email);
  11 |   await page.fill('input[name="password"]', password);
  12 |   await page.fill('input[name="display_name"]', displayName);
  13 |   await page.click('button[type="submit"]');
  14 |   // Wait for redirect chain to settle
  15 |   await page.waitForLoadState('networkidle');
  16 | }
  17 | 
  18 | async function login(page, email, password) {
  19 |   await page.goto('/login');
  20 |   // Target the password form specifically (there are 2 email inputs on the page)
  21 |   await page.fill('#form-password input[name="email"]', email);
  22 |   await page.fill('#form-password input[name="password"]', password);
  23 |   await page.click('#form-password button[type="submit"]');
> 24 |   await page.waitForURL('**/dashboard', { timeout: 10000 });
     |              ^ TimeoutError: page.waitForURL: Timeout 10000ms exceeded.
  25 | }
  26 | 
  27 | test.describe('Authentication', () => {
  28 | 
  29 |   test('user can register and see dashboard', async ({ page }) => {
  30 |     const email = uniqueEmail('reg');
  31 |     // Register, then log in (simplest reliable path for browser-based auth)
  32 |     await registerViaApi(page, email, 'password123', 'Reggie');
  33 |     await login(page, email, 'password123');
  34 |     await expect(page.locator('body')).toContainText('Hi, Reggie', { timeout: 5000 });
  35 |   });
  36 | 
  37 |   test('user can login with password', async ({ page }) => {
  38 |     const email = uniqueEmail('login');
  39 |     await registerViaApi(page, email, 'password123', 'LoginTest');
  40 |     await login(page, email, 'password123');
  41 |     await expect(page.locator('body')).toContainText('Hi, LoginTest');
  42 |   });
  43 | 
  44 |   test('user can logout', async ({ page }) => {
  45 |     const email = uniqueEmail('logout');
  46 |     await registerViaApi(page, email, 'password123', 'LogoutTest');
  47 |     await login(page, email, 'password123');
  48 |     // Click logout
  49 |     await page.click('button:has-text("Logout")');
  50 |     // Should redirect to login page
  51 |     await expect(page.locator('body')).toContainText(/log in|login/i);
  52 |   });
  53 | 
  54 |   test('forgot password page renders', async ({ page }) => {
  55 |     await page.goto('/forgot-password');
  56 |     // Page should contain the heading
  57 |     await expect(page.locator('body')).toContainText('Forgot', { timeout: 5000 });
  58 |   });
  59 | 
  60 |   test('invalid login shows error', async ({ page }) => {
  61 |     const email = uniqueEmail('badlogin');
  62 |     await registerViaApi(page, email, 'password123', 'BadLogin');
  63 |     // Try wrong password
  64 |     await page.goto('/login');
  65 |     await page.fill('input[name="email"]', email);
  66 |     await page.fill('input[name="password"]', 'wrongpassword');
  67 |     await page.click('button[type="submit"]');
  68 |     // Should show error — either inline or stay on login page with message
  69 |     await expect(page.locator('body')).toContainText(/invalid|wrong|error/i);
  70 |   });
  71 | 
  72 | });
  73 | 
```