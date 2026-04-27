# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: auth.spec.js >> Authentication >> forgot password page renders
- Location: tests/auth.spec.js:82:3

# Error details

```
Error: expect(locator).toContainText(expected) failed

Locator: locator('body')
Expected substring: "Forgot"
Received string:    "OK"
Timeout: 5000ms

Call log:
  - Expect "toContainText" with timeout 5000ms
  - waiting for locator('body')
    9 × locator resolved to <body>…</body>
      - unexpected value "OK"

```

# Page snapshot

```yaml
- generic [ref=e2]: OK
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
  33 |   test('dashboard accessible', async ({ page }) => {
  34 |     const email = uniqueEmail('simp');
  35 |     // Create user, then login via browser form
  36 |     await createUser(page, email, 'testpass', 'Simple');
  37 |     
  38 |     // Try login via request context instead (which will set cookies properly)
  39 |     const loginResp = await page.request.post('/login/password', {
  40 |       form: { email, password: 'testpass' },
  41 |     });
  42 |     console.log('Login status:', loginResp.status());
  43 |     
  44 |     // Extract cookies from response
  45 |     const setCookie = loginResp.headers()['set-cookie'];
  46 |     console.log('Set-Cookie:', setCookie);
  47 |     
  48 |     // Now set the cookie manually on the browser context and navigate
  49 |     if (setCookie) {
  50 |       const cookieStr = setCookie.split(';')[0];
  51 |       const [name, value] = cookieStr.split('=');
  52 |       await page.context().addCookies([{
  53 |         name,
  54 |         value,
  55 |         domain: 'localhost',
  56 |         path: '/',
  57 |         httpOnly: true,
  58 |         sameSite: 'Strict',
  59 |       }]);
  60 |     }
  61 |     
  62 |     await page.goto('/dashboard');
  63 |     console.log('Final URL:', page.url());
  64 |     await expect(page.locator('body')).toContainText('Simple', { timeout: 5000 });
  65 |   });
  66 | 
  67 |   test('user can login with password', async ({ page }) => {
  68 |     const email = uniqueEmail('login');
  69 |     await createUser(page, email, 'password123', 'LoginTest');
  70 |     await browserLogin(page, email, 'password123');
  71 |     await expect(page.locator('body')).toContainText('Hi, LoginTest');
  72 |   });
  73 | 
  74 |   test('user can logout', async ({ page }) => {
  75 |     const email = uniqueEmail('logout');
  76 |     await createUser(page, email, 'password123', 'LogoutTest');
  77 |     await browserLogin(page, email, 'password123');
  78 |     await page.click('button:has-text("Logout")');
  79 |     await expect(page.locator('body')).toContainText(/log in|login/i, { timeout: 5000 });
  80 |   });
  81 | 
  82 |   test('forgot password page renders', async ({ page }) => {
  83 |     await page.goto('/forgot-password');
  84 |     // Page should contain the heading
> 85 |     await expect(page.locator('body')).toContainText('Forgot', { timeout: 5000 });
     |                                        ^ Error: expect(locator).toContainText(expected) failed
  86 |   });
  87 | 
  88 |   test('invalid login shows error', async ({ page }) => {
  89 |     const email = uniqueEmail('badlogin');
  90 |     await createUser(page, email, 'password123', 'BadLogin');
  91 |     await page.goto('/login');
  92 |     await page.fill('input[name="email"]', email);
  93 |     await page.fill('input[name="password"]', 'wrongpassword');
  94 |     await page.click('button:has-text("Log in")');
  95 |     await expect(page.locator('body')).toContainText(/invalid|wrong|error/i, { timeout: 5000 });
  96 |   });
  97 | 
  98 | });
  99 | 
```