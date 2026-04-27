const { test, expect } = require('@playwright/test');

function uniqueEmail(prefix) {
  return `${prefix}.${Date.now()}@example.com`;
}

async function registerViaApi(page, email, password, displayName) {
  // Use direct POST to register — the response sets the session cookie
  const resp = await page.request.post('/register', {
    form: { email, password, password_confirm: password, display_name: displayName },
  });
  // Save cookies for subsequent requests
  await page.context().storageState({ path: undefined });
}

async function loginViaApi(page, email, password) {
  const resp = await page.request.post('/login/password', {
    form: { email, password },
  });
  // Session cookie should be set by the 303 response
}

test.describe('Authentication', () => {

  test('user can register and see dashboard', async ({ page }) => {
    const email = uniqueEmail('reg');
    await registerViaApi(page, email, 'password123', 'Reggie');
    await loginViaApi(page, email, 'password123');
    await page.goto('/dashboard');
    await expect(page.locator('body')).toContainText('Hi, Reggie', { timeout: 5000 });
  });

  test('user can login with password', async ({ page }) => {
    const email = uniqueEmail('login');
    await registerViaApi(page, email, 'password123', 'LoginTest');
    await loginViaApi(page, email, 'password123');
    await page.goto('/dashboard');
    await expect(page.locator('body')).toContainText('Hi, LoginTest');
  });

  test('user can logout', async ({ page }) => {
    const email = uniqueEmail('logout');
    await registerViaApi(page, email, 'password123', 'LogoutTest');
    await loginViaApi(page, email, 'password123');
    await page.goto('/dashboard');
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
    await registerViaApi(page, email, 'password123', 'BadLogin');
    // Navigate to login and try wrong password
    await page.goto('/login');
    await page.fill('input[name="email"]', email);
    await page.fill('input[name="password"]', 'wrongpassword');
    await page.click('button:has-text("Log in")');
    await expect(page.locator('body')).toContainText(/invalid|wrong|error/i, { timeout: 5000 });
  });

});
