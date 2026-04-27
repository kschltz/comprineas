const { test, expect } = require('@playwright/test');

function uniqueEmail(prefix) {
  return `${prefix}.${Date.now()}@example.com`;
}

async function createUser(page, email, password, displayName) {
  await page.request.post('/register', {
    form: { email, password, password_confirm: password, display_name: displayName },
  });
}

async function browserLogin(page, email, password) {
  await page.goto('/login');
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  // Submit the form; catch the navigation event that follows redirects to /dashboard
  await Promise.all([
    page.waitForURL('**/dashboard', { timeout: 10000 }),
    page.click('button:has-text("Log in")')
  ]);
}

test.describe('Authentication', () => {

  test('register & login via browser only', async ({ page }) => {
    const email = uniqueEmail('browser');
    const password = 'password123';
    
    // Register via browser form
    await page.goto('/register');
    await page.fill('input[name="email"]', email);
    await page.fill('input[name="password"]', password);
    await page.fill('input[name="display_name"]', 'Browser');
    
    await Promise.all([
      page.waitForURL('**/dashboard', { timeout: 10000 }),
      page.click('button:has-text("Register")')
    ]);
    
    await expect(page.locator('body')).toContainText('Hi, Browser');
    console.log('Registration + dashboard works!');
  });

  test('user can login and logout', async ({ page }) => {
    const email = uniqueEmail('logout');
    await createUser(page, email, 'password123', 'LogoutTest');
    await browserLogin(page, email, 'password123');
    await page.click('button:has-text("Logout")');
    await expect(page.locator('body')).toContainText(/log in|login/i, { timeout: 5000 });
  });

  test('forgot password page renders', async ({ page }) => {
    await page.goto('/forgot-password');
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
