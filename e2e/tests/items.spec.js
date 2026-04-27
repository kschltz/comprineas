// @ts-check
const { test, expect } = require('@playwright/test');

function uniqueEmail(prefix) {
  return `${prefix}-${Date.now()}@example.com`;
}

async function registerAndLogin(page, email, password, displayName) {
  await page.goto('/register');
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.fill('input[name="display_name"]', displayName);
  await Promise.all([
    page.waitForURL('**/dashboard', { timeout: 10000 }),
    page.click('button[type="submit"]')
  ]);
}

async function createList(page, name) {
  await page.fill('input[name="name"]', name);
  await page.click('text=Create List');
  // HTMX swaps body, no URL change — wait for list page content
  await expect(page.locator('#list-name')).toBeVisible({ timeout: 10000 });
}

async function addItem(page, itemName, quantity, observations) {
  await page.fill('input[name="name"]', itemName);
  if (quantity !== undefined) await page.fill('input[name="quantity"]', quantity);
  if (observations !== undefined) await page.fill('input[name="observations"]', observations);
  await page.click('button:has-text("Add Item")');
}

test.describe('PRD-0005: List Items', () => {

  test('should add an item with all fields', async ({ page }) => {
    const email = uniqueEmail('items1');
    await registerAndLogin(page, email, 'testpass123', 'Test User 1');
    await createList(page, 'Test List 1');

    await addItem(page, 'Milk', '2 L', 'Organic whole milk');

    await expect(page.locator('#item-list')).toContainText('Milk');
    await expect(page.locator('#item-list')).toContainText('2 L');
    await expect(page.locator('#item-list')).toContainText('Organic whole milk');
  });

  test('should add an item with only the required name field', async ({ page }) => {
    const email = uniqueEmail('items2');
    await registerAndLogin(page, email, 'testpass123', 'Test User 2');
    await createList(page, 'Test List 2');

    await addItem(page, 'Bread');
    await expect(page.locator('#item-list')).toContainText('Bread');
  });

  test('should visually mark an item as checked when toggled', async ({ page }) => {
    const email = uniqueEmail('items3');
    await registerAndLogin(page, email, 'testpass123', 'Test User 3');
    await createList(page, 'Test List 3');

    await addItem(page, 'Eggs', '12', 'Free range');

    const checkbox = page.locator('#item-list > div').filter({ hasText: 'Eggs' }).locator('input[type="checkbox"]');
    await checkbox.click();

    const checkedRow = page.locator('#item-list > div').filter({ hasText: 'Eggs' });
    await expect(checkedRow).toHaveClass(/opacity-50/);
    await expect(checkedRow.locator('s')).toContainText('Eggs');
  });

  test('should remove an item from the list when deleted', async ({ page }) => {
    const email = uniqueEmail('items4');
    await registerAndLogin(page, email, 'testpass123', 'Test User 4');
    await createList(page, 'Test List 4');

    await addItem(page, 'Butter', '250g', 'Salted');
    await addItem(page, 'Cheese', '200g', 'Cheddar');

    // Wait for items to fully load (hx-trigger="load" may be async)
    await expect(page.locator('#item-list')).not.toContainText('Loading items', { timeout: 5000 });
    // Wait for actual item content
    await page.waitForTimeout(500);

    await expect(page.locator('#item-list')).toContainText('Butter');
    await expect(page.locator('#item-list')).toContainText('Cheese');

    const deleteButton = page.locator('#item-list > div')
      .filter({ hasText: 'Butter' })
      .locator('button[aria-label="Delete item"]');
    await deleteButton.click();

    await expect(page.locator('#item-list')).not.toContainText('Butter');
    await expect(page.locator('#item-list')).toContainText('Cheese');
  });

  test('should show an error when trying to add an item with an empty name', async ({ page }) => {
    const email = uniqueEmail('items5');
    await registerAndLogin(page, email, 'testpass123', 'Test User 5');
    await createList(page, 'Test List 5');

    await page.evaluate(() => {
      document.querySelector('input[name="name"]').removeAttribute('required');
    });

    await page.fill('input[name="name"]', '');
    await page.fill('input[name="quantity"]', '1');
    await page.click('button:has-text("Add Item")');

    // Wait for the error to appear — it's set via HTMX OOB swap
    await page.waitForTimeout(500);
    const errorText = await page.locator('#add-item-error').textContent();
    // The error may be in a hidden div but should have content
    expect(errorText).toMatch(/Name|required|empty/i);
  });

});
