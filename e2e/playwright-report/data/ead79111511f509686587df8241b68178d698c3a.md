# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: auth.spec.js >> Authentication >> login redirects to dashboard
- Location: tests/auth.spec.js:33:3

# Error details

```
TimeoutError: page.waitForResponse: Timeout 5000ms exceeded while waiting for event "response"
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
  33 |   test('login redirects to dashboard', async ({ page }) => {
  34 |     const email = uniqueEmail('redir');
  35 |     await createUser(page, email, 'password123', 'Redirect');
  36 |     await page.goto('/login');
  37 |     await page.fill('input[name="email"]', email);
  38 |     await page.fill('input[name="password"]', 'password123');
  39 |     
  40 |     // Watch for the redirect response
  41 |     const [response] = await Promise.all([
> 42 |       page.waitForResponse(resp => resp.url().includes('/dashboard') && resp.status() === 200, { timeout: 5000 }),
     |            ^ TimeoutError: page.waitForResponse: Timeout 5000ms exceeded while waiting for event "response"
  43 |       page.click('button:has-text("Log in")')
  44 |     ]);
  45 |     // If we get here, the dashboard loaded successfully
  46 |     expect(response.status()).toBe(200);
  47 |   });
  48 | 
  49 |   test('user can login with password', async ({ page }) => {
  50 |     const email = uniqueEmail('login');
  51 |     await createUser(page, email, 'password123', 'LoginTest');
  52 |     await browserLogin(page, email, 'password123');
  53 |     await expect(page.locator('body')).toContainText('Hi, LoginTest');
  54 |   });
  55 | 
  56 |   test('user can logout', async ({ page }) => {
  57 |     const email = uniqueEmail('logout');
  58 |     await createUser(page, email, 'password123', 'LogoutTest');
  59 |     await browserLogin(page, email, 'password123');
  60 |     await page.click('button:has-text("Logout")');
  61 |     await expect(page.locator('body')).toContainText(/log in|login/i, { timeout: 5000 });
  62 |   });
  63 | 
  64 |   test('forgot password page renders', async ({ page }) => {
  65 |     await page.goto('/forgot-password');
  66 |     // Page should contain the heading
  67 |     await expect(page.locator('body')).toContainText('Forgot', { timeout: 5000 });
  68 |   });
  69 | 
  70 |   test('invalid login shows error', async ({ page }) => {
  71 |     const email = uniqueEmail('badlogin');
  72 |     await createUser(page, email, 'password123', 'BadLogin');
  73 |     await page.goto('/login');
  74 |     await page.fill('input[name="email"]', email);
  75 |     await page.fill('input[name="password"]', 'wrongpassword');
  76 |     await page.click('button:has-text("Log in")');
  77 |     await expect(page.locator('body')).toContainText(/invalid|wrong|error/i, { timeout: 5000 });
  78 |   });
  79 | 
  80 | });
  81 | 
```