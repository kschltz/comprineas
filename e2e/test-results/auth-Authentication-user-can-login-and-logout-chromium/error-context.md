# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: auth.spec.js >> Authentication >> user can login and logout
- Location: tests/auth.spec.js:30:3

# Error details

```
TimeoutError: page.waitForURL: Timeout 10000ms exceeded.
=========================== logs ===========================
waiting for navigation to "**/dashboard" until "load"
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
  17 |   await page.click('button:has-text("Log in")');
> 18 |   await page.waitForURL('**/dashboard', { timeout: 10000 });
     |              ^ TimeoutError: page.waitForURL: Timeout 10000ms exceeded.
  19 | }
  20 | 
  21 | test.describe('Authentication', () => {
  22 | 
  23 |   test('user can register and login with password', async ({ page }) => {
  24 |     const email = uniqueEmail('reg');
  25 |     await createUser(page, email, 'password123', 'Reggie');
  26 |     await browserLogin(page, email, 'password123');
  27 |     await expect(page.locator('body')).toContainText('Hi, Reggie');
  28 |   });
  29 | 
  30 |   test('user can login and logout', async ({ page }) => {
  31 |     const email = uniqueEmail('logout');
  32 |     await createUser(page, email, 'password123', 'LogoutTest');
  33 |     await browserLogin(page, email, 'password123');
  34 |     await page.click('button:has-text("Logout")');
  35 |     await expect(page.locator('body')).toContainText(/log in|login/i, { timeout: 5000 });
  36 |   });
  37 | 
  38 |   test('forgot password page renders', async ({ page }) => {
  39 |     await page.goto('/forgot-password');
  40 |     await expect(page.locator('body')).toContainText('Forgot', { timeout: 5000 });
  41 |   });
  42 | 
  43 |   test('invalid login shows error', async ({ page }) => {
  44 |     const email = uniqueEmail('badlogin');
  45 |     await createUser(page, email, 'password123', 'BadLogin');
  46 |     await page.goto('/login');
  47 |     await page.fill('input[name="email"]', email);
  48 |     await page.fill('input[name="password"]', 'wrongpassword');
  49 |     await page.click('button:has-text("Log in")');
  50 |     await expect(page.locator('body')).toContainText(/invalid|wrong|error/i, { timeout: 5000 });
  51 |   });
  52 | 
  53 | });
  54 | 
```