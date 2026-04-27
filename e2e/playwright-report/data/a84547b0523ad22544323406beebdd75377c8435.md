# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: auth.spec.js >> Authentication >> debug login
- Location: tests/auth.spec.js:26:3

# Error details

```
Error: response.text: Response body is unavailable for redirect responses
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
  26 |   test('debug login', async ({ page }) => {
  27 |     const email = uniqueEmail('debug');
  28 |     const password = 'password123';
  29 |     // Create user directly
  30 |     const regResp = await page.request.post('/register', {
  31 |       form: { email, password, password_confirm: password, display_name: 'Debug' },
  32 |     });
  33 |     console.log('Register status:', regResp.status());
  34 |     console.log('Register location:', regResp.headers()['location']);
  35 |     
  36 |     // Now try login
  37 |     await page.goto('/login');
  38 |     await page.fill('input[name="email"]', email);
  39 |     await page.fill('input[name="password"]', password);
  40 |     
  41 |     const [loginResp] = await Promise.all([
  42 |       page.waitForResponse(r => r.url().includes('/login/password'), { timeout: 5000 }),
  43 |       page.click('button:has-text("Log in")')
  44 |     ]);
  45 |     console.log('Login status:', loginResp.status());
  46 |     console.log('Login location:', loginResp.headers()['location']);
> 47 |     console.log('Login body start:', (await loginResp.text()).substring(0, 300));
     |                                                       ^ Error: response.text: Response body is unavailable for redirect responses
  48 |   });
  49 | 
  50 |   test('user can login and logout', async ({ page }) => {
  51 |     const email = uniqueEmail('logout');
  52 |     await createUser(page, email, 'password123', 'LogoutTest');
  53 |     await browserLogin(page, email, 'password123');
  54 |     await page.click('button:has-text("Logout")');
  55 |     await expect(page.locator('body')).toContainText(/log in|login/i, { timeout: 5000 });
  56 |   });
  57 | 
  58 |   test('forgot password page renders', async ({ page }) => {
  59 |     await page.goto('/forgot-password');
  60 |     await expect(page.locator('body')).toContainText('Forgot', { timeout: 5000 });
  61 |   });
  62 | 
  63 |   test('invalid login shows error', async ({ page }) => {
  64 |     const email = uniqueEmail('badlogin');
  65 |     await createUser(page, email, 'password123', 'BadLogin');
  66 |     await page.goto('/login');
  67 |     await page.fill('input[name="email"]', email);
  68 |     await page.fill('input[name="password"]', 'wrongpassword');
  69 |     await page.click('button:has-text("Log in")');
  70 |     await expect(page.locator('body')).toContainText(/invalid|wrong|error/i, { timeout: 5000 });
  71 |   });
  72 | 
  73 | });
  74 | 
```