const { test, expect } = require('@playwright/test');

function uniqueEmail(prefix) {
  return `${prefix}.${Date.now()}@example.com`;
}

async function createUser(page, email, password, displayName) {
  // Create user in DB via API (no session needed from this)
  await page.request.post('/register', {
    form: { email, password, password_confirm: password, display_name: displayName },
  });
}

async function browserLogin(page, email, password) {
  // Login via browser form — this sets the session cookie in the browser context
  await page.goto('/login');
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.click('button:has-text("Log in")');
  await page.waitForURL('**/dashboard', { timeout: 10000 });
}

test.describe('Authentication', () => {

  test('user can register and see dashboard', async ({ page }) => {
    const email = uniqueEmail('reg');
    await createUser(page, email, 'password123', 'Reggie');
    await browserLogin(page, email, 'password123');
    await expect(page.locator('body')).toContainText('Hi, Reggie');
  });

  test('user can login with password', async ({ page }) => {
    const email = uniqueEmail('login');
    await createUser(page, email, 'password123', 'LoginTest');
    await browserLogin(page, email, 'password123');
    await expect(page.locator('body')).toContainText('Hi, LoginTest');
  });

  test('user can logout', async ({ page }) => {
    const email = uniqueEmail('logout');
    await createUser(page, email, 'password123', 'LogoutTest');
    await browserLogin(page, email, 'password123');
    await page.click('button:has-text("Logout")');
    await expect(page.locator('body')).toContainText(/log in|login/i, { timeout: 5000 });
  });

  test('forgot password page renders', async ({ page }) => {
    await page.goto('/forgot-password');
    // Page should contain the heading
    await expect(page.locator('body')).toContainText('Forgot', { timeout: 5000 });
  });

  test('invalid login shows error', async ({ page }) => {
    const email = uniqueEmail('badlogin');
    await createUser(page, email, 'password123', 'BadLogin');
    await page.goto('/login');
    await page.fill('input[name="email"]', email);
    await page.fill('input[name="password"]', 'wrongpassword');
    await page.click('button:has-text("Log in")');
    await expect(page.locator('body')).toContainText(/invalid|wrong|error/i, { timeout: 5000 });
  });

});
