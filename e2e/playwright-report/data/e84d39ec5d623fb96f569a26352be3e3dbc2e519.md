# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: auth.spec.js >> Authentication >> register + login works
- Location: tests/auth.spec.js:33:3

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
  7  | async function createUser(page, email, password, displayName) {
  8  |   // Create user in DB via API (no session needed from this)
  9  |   await page.request.post('/register', {
  10 |     form: { email, password, password_confirm: password, display_name: displayName },
  11 |   });
  12 | }
  13 | 
  14 | async function browserLogin(page, email, password) {
  15 |   await page.goto('/login');
  16 |   await page.fill('input[name="email"]', email);
  17 |   await page.fill('input[name="password"]', password);
  18 |   // Submit and catch any navigation errors
  19 |   try {
  20 |     await Promise.all([
  21 |       page.waitForURL('**/dashboard', { timeout: 8000 }),
  22 |       page.click('button:has-text("Log in")')
  23 |     ]);
  24 |   } catch (e) {
  25 |     // If redirect fails, try navigating manually
  26 |     console.log('Login redirect failed, trying direct navigation. Error:', e.message.substring(0, 100));
  27 |     await page.goto('/dashboard');
  28 |   }
  29 | }
  30 | 
  31 | test.describe('Authentication', () => {
  32 | 
  33 |   test('register + login works', async ({ page }) => {
  34 |     const email = uniqueEmail('reg');
  35 |     await createUser(page, email, 'password123', 'Reggie');
  36 |     await page.goto('/login');
  37 |     await page.fill('input[name="email"]', email);
  38 |     await page.fill('input[name="password"]', 'password123');
  39 |     await page.click('button:has-text("Log in")');
  40 |     // Should redirect to dashboard
> 41 |     await page.waitForURL('**/dashboard', { timeout: 10000 });
     |                ^ TimeoutError: page.waitForURL: Timeout 10000ms exceeded.
  42 |     await expect(page.locator('body')).toContainText('Hi, Reggie');
  43 |   });
  44 | 
  45 |   test('user can login with password', async ({ page }) => {
  46 |     const email = uniqueEmail('login');
  47 |     await createUser(page, email, 'password123', 'LoginTest');
  48 |     await browserLogin(page, email, 'password123');
  49 |     await expect(page.locator('body')).toContainText('Hi, LoginTest');
  50 |   });
  51 | 
  52 |   test('user can logout', async ({ page }) => {
  53 |     const email = uniqueEmail('logout');
  54 |     await createUser(page, email, 'password123', 'LogoutTest');
  55 |     await browserLogin(page, email, 'password123');
  56 |     await page.click('button:has-text("Logout")');
  57 |     await expect(page.locator('body')).toContainText(/log in|login/i, { timeout: 5000 });
  58 |   });
  59 | 
  60 |   test('forgot password page renders', async ({ page }) => {
  61 |     await page.goto('/forgot-password');
  62 |     // Page should contain the heading
  63 |     await expect(page.locator('body')).toContainText('Forgot', { timeout: 5000 });
  64 |   });
  65 | 
  66 |   test('invalid login shows error', async ({ page }) => {
  67 |     const email = uniqueEmail('badlogin');
  68 |     await createUser(page, email, 'password123', 'BadLogin');
  69 |     await page.goto('/login');
  70 |     await page.fill('input[name="email"]', email);
  71 |     await page.fill('input[name="password"]', 'wrongpassword');
  72 |     await page.click('button:has-text("Log in")');
  73 |     await expect(page.locator('body')).toContainText(/invalid|wrong|error/i, { timeout: 5000 });
  74 |   });
  75 | 
  76 | });
  77 | 
```