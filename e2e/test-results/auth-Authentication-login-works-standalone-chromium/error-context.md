# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: auth.spec.js >> Authentication >> login works standalone
- Location: tests/auth.spec.js:26:3

# Error details

```
TimeoutError: page.waitForURL: Timeout 10000ms exceeded.
=========================== logs ===========================
waiting for navigation to "**/dashboard" until "load"
  navigated to "http://localhost:3001/login"
============================================================
```

# Page snapshot

```yaml
- generic [ref=e2]:
  - heading "Comprineas" [level=1] [ref=e3]
  - paragraph [ref=e4]: Log in to your account
  - generic [ref=e5]:
    - button "Password" [ref=e6] [cursor=pointer]
    - button "Magic Link" [ref=e7] [cursor=pointer]
  - generic [ref=e8]:
    - generic [ref=e9]:
      - generic [ref=e10]: Email address
      - textbox "Email address" [ref=e11]: "''"
    - generic [ref=e12]:
      - generic [ref=e13]: Password
      - textbox "Password" [ref=e14]
    - button "Log in" [ref=e15] [cursor=pointer]
    - paragraph [ref=e16]:
      - link "Forgot password?" [ref=e17] [cursor=pointer]:
        - /url: /forgot-password
  - paragraph [ref=e18]:
    - text: Don't have an account?
    - link "Create account" [ref=e19] [cursor=pointer]:
      - /url: /register
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
  8  |   await page.request.post('/register', {
  9  |     form: { email, password, password_confirm: password, display_name: displayName },
  10 |   });
  11 | }
  12 | 
  13 | async function browserLogin(page, email, password) {
  14 |   await page.goto('/login');
  15 |   await page.fill('input[name="email"]', email);
  16 |   await page.fill('input[name="password"]', password);
  17 |   // Submit the form; catch the navigation event that follows redirects to /dashboard
  18 |   await Promise.all([
  19 |     page.waitForURL('**/dashboard', { timeout: 10000 }),
  20 |     page.click('button:has-text("Log in")')
  21 |   ]);
  22 | }
  23 | 
  24 | test.describe('Authentication', () => {
  25 | 
  26 |   test('login works standalone', async ({ page }) => {
  27 |     const email = uniqueEmail('solo');
  28 |     const password = 'password123';
  29 |     // Create user via API (follows redirects, returns 200 from /dashboard)
  30 |     await page.request.post('/register', {
  31 |       form: { email, password, password_confirm: password, display_name: 'Solo' },
  32 |     });
  33 |     // Login via browser form
  34 |     await page.goto('/login');
  35 |     await page.fill('input[name="email"]', email);
  36 |     await page.fill('input[name="password"]', password);
  37 |     
  38 |     await Promise.all([
> 39 |       page.waitForURL('**/dashboard', { timeout: 10000 }),
     |            ^ TimeoutError: page.waitForURL: Timeout 10000ms exceeded.
  40 |       page.click('button:has-text("Log in")')
  41 |     ]);
  42 |     
  43 |     await expect(page.locator('body')).toContainText('Hi, Solo');
  44 |     console.log('SUCCESS — dashboard loaded');
  45 |   });
  46 | 
  47 |   test('user can login and logout', async ({ page }) => {
  48 |     const email = uniqueEmail('logout');
  49 |     await createUser(page, email, 'password123', 'LogoutTest');
  50 |     await browserLogin(page, email, 'password123');
  51 |     await page.click('button:has-text("Logout")');
  52 |     await expect(page.locator('body')).toContainText(/log in|login/i, { timeout: 5000 });
  53 |   });
  54 | 
  55 |   test('forgot password page renders', async ({ page }) => {
  56 |     await page.goto('/forgot-password');
  57 |     await expect(page.locator('body')).toContainText('Forgot', { timeout: 5000 });
  58 |   });
  59 | 
  60 |   test('invalid login shows error', async ({ page }) => {
  61 |     const email = uniqueEmail('badlogin');
  62 |     await createUser(page, email, 'password123', 'BadLogin');
  63 |     await page.goto('/login');
  64 |     await page.fill('input[name="email"]', email);
  65 |     await page.fill('input[name="password"]', 'wrongpassword');
  66 |     await page.click('button:has-text("Log in")');
  67 |     await expect(page.locator('body')).toContainText(/invalid|wrong|error/i, { timeout: 5000 });
  68 |   });
  69 | 
  70 | });
  71 | 
```