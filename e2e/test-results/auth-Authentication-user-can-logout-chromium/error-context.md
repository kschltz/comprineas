# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: auth.spec.js >> Authentication >> user can logout
- Location: tests/auth.spec.js:41:3

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: page.click: Test timeout of 30000ms exceeded.
Call log:
  - waiting for locator('button:has-text("Logout")')

```

# Page snapshot

```yaml
- generic [ref=e2]:
  - heading "Comprineas" [level=1] [ref=e3]
  - paragraph [ref=e4]: Create your account
  - generic [ref=e5]:
    - generic [ref=e6]:
      - generic [ref=e7]: Email address
      - textbox "Email address" [active] [ref=e8]: "'"
    - generic [ref=e9]:
      - generic [ref=e10]: Password
      - textbox "Password" [ref=e11]
      - paragraph [ref=e12]: At least 8 characters.
    - generic [ref=e13]:
      - generic [ref=e14]: Display name
      - textbox "Display name" [ref=e15]:
        - /placeholder: e.g., Alice
    - button "Register" [ref=e16] [cursor=pointer]
  - paragraph [ref=e17]:
    - text: Already have an account?
    - link "Log in" [ref=e18] [cursor=pointer]:
      - /url: /login
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
  8  |   // Use direct POST to register — the response sets the session cookie
  9  |   const resp = await page.request.post('/register', {
  10 |     form: { email, password, password_confirm: password, display_name: displayName },
  11 |   });
  12 |   // Save cookies for subsequent requests
  13 |   await page.context().storageState({ path: undefined });
  14 | }
  15 | 
  16 | async function loginViaApi(page, email, password) {
  17 |   const resp = await page.request.post('/login/password', {
  18 |     form: { email, password },
  19 |   });
  20 |   // Session cookie should be set by the 303 response
  21 | }
  22 | 
  23 | test.describe('Authentication', () => {
  24 | 
  25 |   test('user can register and see dashboard', async ({ page }) => {
  26 |     const email = uniqueEmail('reg');
  27 |     await registerViaApi(page, email, 'password123', 'Reggie');
  28 |     await loginViaApi(page, email, 'password123');
  29 |     await page.goto('/dashboard');
  30 |     await expect(page.locator('body')).toContainText('Hi, Reggie', { timeout: 5000 });
  31 |   });
  32 | 
  33 |   test('user can login with password', async ({ page }) => {
  34 |     const email = uniqueEmail('login');
  35 |     await registerViaApi(page, email, 'password123', 'LoginTest');
  36 |     await loginViaApi(page, email, 'password123');
  37 |     await page.goto('/dashboard');
  38 |     await expect(page.locator('body')).toContainText('Hi, LoginTest');
  39 |   });
  40 | 
  41 |   test('user can logout', async ({ page }) => {
  42 |     const email = uniqueEmail('logout');
  43 |     await registerViaApi(page, email, 'password123', 'LogoutTest');
  44 |     await loginViaApi(page, email, 'password123');
  45 |     await page.goto('/dashboard');
> 46 |     await page.click('button:has-text("Logout")');
     |                ^ Error: page.click: Test timeout of 30000ms exceeded.
  47 |     await expect(page.locator('body')).toContainText(/log in|login/i, { timeout: 5000 });
  48 |   });
  49 | 
  50 |   test('forgot password page renders', async ({ page }) => {
  51 |     await page.goto('/forgot-password');
  52 |     // Page should contain the heading
  53 |     await expect(page.locator('body')).toContainText('Forgot', { timeout: 5000 });
  54 |   });
  55 | 
  56 |   test('invalid login shows error', async ({ page }) => {
  57 |     const email = uniqueEmail('badlogin');
  58 |     await registerViaApi(page, email, 'password123', 'BadLogin');
  59 |     // Navigate to login and try wrong password
  60 |     await page.goto('/login');
  61 |     await page.fill('input[name="email"]', email);
  62 |     await page.fill('input[name="password"]', 'wrongpassword');
  63 |     await page.click('button:has-text("Log in")');
  64 |     await expect(page.locator('body')).toContainText(/invalid|wrong|error/i, { timeout: 5000 });
  65 |   });
  66 | 
  67 | });
  68 | 
```