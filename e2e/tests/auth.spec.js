const { test, expect } = require('@playwright/test');

function uniqueEmail(prefix) {
  return `${prefix}.${Date.now()}@example.com`;
}

async function registerViaApi(page, email, password, displayName) {
  // Direct POST to register endpoint — browser will follow redirects and store session cookie
  await page.goto('/register');
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.fill('input[name="display_name"]', displayName);
  await page.click('button[type="submit"]');
  // Wait for redirect chain to settle
  await page.waitForLoadState('networkidle');
}

async function login(page, email, password) {
  await page.goto('/login');
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.click('button:has-text("Log in")');
  await page.waitForURL('**/dashboard', { timeout: 10000 });
}

test.describe('Authentication', () => {

  test('user can register and see dashboard', async ({ page }) => {
    const email = uniqueEmail('reg');
    // Register, then log in (simplest reliable path for browser-based auth)
    await registerViaApi(page, email, 'password123', 'Reggie');
    await login(page, email, 'password123');
    await expect(page.locator('body')).toContainText('Hi, Reggie', { timeout: 5000 });
  });

  test('user can login with password', async ({ page }) => {
    const email = uniqueEmail('login');
    await registerViaApi(page, email, 'password123', 'LoginTest');
    await login(page, email, 'password123');
    await expect(page.locator('body')).toContainText('Hi, LoginTest');
  });

  test('user can logout', async ({ page }) => {
    const email = uniqueEmail('logout');
    await registerViaApi(page, email, 'password123', 'LogoutTest');
    await login(page, email, 'password123');
    // Click logout
    await page.click('button:has-text("Logout")');
    // Should redirect to login page
    await expect(page.locator('body')).toContainText(/log in|login/i);
  });

  test('forgot password page renders', async ({ page }) => {
    await page.goto('/forgot-password');
    // Page should contain the heading
    await expect(page.locator('body')).toContainText('Forgot', { timeout: 5000 });
  });

  test('invalid login shows error', async ({ page }) => {
    const email = uniqueEmail('badlogin');
    await registerViaApi(page, email, 'password123', 'BadLogin');
    // Try wrong password
    await page.goto('/login');
    await page.fill('input[name="email"]', email);
    await page.fill('input[name="password"]', 'wrongpassword');
    await page.click('button:has-text("Log in")');
    // Should show error — either inline or stay on login page with message
    await expect(page.locator('body')).toContainText(/invalid|wrong|error/i);
  });

});
