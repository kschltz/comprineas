const { test, expect } = require('@playwright/test');

function uniqueEmail(prefix) {
  return `${prefix}.${Date.now()}@example.com`;
}

async function register(page, email, password, displayName) {
  await page.goto('/register');
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.fill('input[name="display_name"]', displayName);
  await page.click('button[type="submit"]');
  // post-register redirects to / (no route), then browser lands on 404.
  // Wait for redirects to settle, then navigate to dashboard.
  await page.waitForURL('**/', { timeout: 5000 }).catch(() => {});
}

async function login(page, email, password) {
  await page.goto('/login');
  // Target the password form specifically (there are 2 email inputs on the page)
  await page.fill('#form-password input[name="email"]', email);
  await page.fill('#form-password input[name="password"]', password);
  await page.click('#form-password button[type="submit"]');
  await page.waitForURL('**/dashboard', { timeout: 10000 });
}

test.describe('Authentication', () => {

  test('user can register', async ({ page }) => {
    const email = uniqueEmail('reg');
    await register(page, email, 'password123', 'Reggie');
    // After registration, user has session cookie set via redirect.
    // Navigate to dashboard — should be authenticated.
    await page.goto('/dashboard');
    // If still on login, the session wasn't set — log cookies for debugging
    const cookies = await page.context().cookies();
    console.log('Cookies:', JSON.stringify(cookies.map(c => c.name)));
    await expect(page.locator('body')).toContainText('Hi, Reggie', { timeout: 5000 });
  });

  test('user can login with password', async ({ page }) => {
    const email = uniqueEmail('login');
    // Register first
    await register(page, email, 'password123', 'LoginTest');
    // Now logout if needed, then login
    await page.goto('/login');
    await page.fill('input[name="email"]', email);
    await page.fill('input[name="password"]', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForURL('**/dashboard');
    await expect(page.locator('body')).toContainText('Hi, LoginTest');
  });

  test('user can logout', async ({ page }) => {
    const email = uniqueEmail('logout');
    await register(page, email, 'password123', 'LogoutTest');
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
    await register(page, email, 'password123', 'BadLogin');
    // Try wrong password
    await page.goto('/login');
    await page.fill('input[name="email"]', email);
    await page.fill('input[name="password"]', 'wrongpassword');
    await page.click('button[type="submit"]');
    // Should show error — either inline or stay on login page with message
    await expect(page.locator('body')).toContainText(/invalid|wrong|error/i);
  });

});
