// @ts-check
const { test, expect } = require('@playwright/test');

//
// ─── Helpers ──────────────────────────────────────────────────────────────────
//

/**
 * Register a new user, then navigate to the dashboard.
 * The app redirects to "/" on success — we end up on /dashboard explicitly.
 */
async function registerAndLogin(page, email, password, displayName) {
  await page.goto('/register');
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.fill('input[name="display_name"]', displayName);
  await page.click('button[type="submit"]');
  // Wait for the POST to complete and redirect
  await page.waitForLoadState('networkidle');
  // Navigate to dashboard (the redirect target "/" is not a real route)
  await page.goto('/dashboard');
  await expect(page.locator('h1')).toContainText('My Lists');
}

/**
 * Create a shopping list via the dashboard form and wait for the list page.
 * The form uses hx-post="/lists" hx-target="body" so HTMX handles the
 * redirect from the server and loads /list/:code into the page.
 */
async function createList(page, name) {
  await page.fill('input[name="name"]', name);
  await page.click('text=Create List');
  await page.waitForURL('**/list/**');
}

/**
 * Add an item to the current list.
 * The form uses hx-post, target="#item-list" with swap "beforeend".
 */
async function addItem(page, itemName, quantity, observations) {
  await page.fill('input[name="name"]', itemName);
  if (quantity !== undefined) {
    await page.fill('input[name="quantity"]', quantity);
  }
  if (observations !== undefined) {
    await page.fill('input[name="observations"]', observations);
  }
  await page.click('button:has-text("Add Item")');
}

//
// ─── Tests ────────────────────────────────────────────────────────────────────
//

test.describe('PRD-0005: List Items', () => {

  //
  // Test 1 — Add an item with all fields
  //
  test('should add an item with all fields', async ({ page }) => {
    const email = `items-test-1-${Date.now()}@example.com`;
    const password = 'testpass123';
    const displayName = 'Test User 1';

    await registerAndLogin(page, email, password, displayName);
    await createList(page, 'Test List 1');

    // Add item with name, quantity, observations
    await addItem(page, 'Milk', '2 L', 'Organic whole milk');

    // Assert the new item appears in the list
    await expect(page.locator('#item-list')).toContainText('Milk');
    await expect(page.locator('#item-list')).toContainText('2 L');
    await expect(page.locator('#item-list')).toContainText('Organic whole milk');
  });

  //
  // Test 2 — Add item with only required fields (name)
  //
  test('should add an item with only the required name field', async ({ page }) => {
    const email = `items-test-2-${Date.now()}@example.com`;
    const password = 'testpass123';
    const displayName = 'Test User 2';

    await registerAndLogin(page, email, password, displayName);
    await createList(page, 'Test List 2');

    // Add item with only the name — leave quantity and observations empty
    await addItem(page, 'Bread');

    // Assert item appears
    await expect(page.locator('#item-list')).toContainText('Bread');
  });

  //
  // Test 3 — Check off an item
  //
  test('should visually mark an item as checked when toggled', async ({ page }) => {
    const email = `items-test-3-${Date.now()}@example.com`;
    const password = 'testpass123';
    const displayName = 'Test User 3';

    await registerAndLogin(page, email, password, displayName);
    await createList(page, 'Test List 3');

    // Add an item
    await addItem(page, 'Eggs', '12', 'Free range');

    // Locate the item row and the checkbox
    const itemRow = page.locator('#item-list > div').filter({ hasText: 'Eggs' });
    const checkbox = itemRow.locator('input[type="checkbox"]');

    // Before clicking, the row should NOT have the checked styles
    await expect(itemRow).not.toHaveClass(/opacity-50/);

    // Click the checkbox to check the item
    await checkbox.click();

    // After clicking, the list is re-rendered via hx-target="#item-list" innerHTML swap.
    // Re-query the item row (checked items are pushed to bottom).
    const checkedRow = page.locator('#item-list > div').filter({ hasText: 'Eggs' });

    // Assert the checked styling: opacity-50 and bg-gray-50 on the row
    await expect(checkedRow).toHaveClass(/opacity-50/);
    await expect(checkedRow).toHaveClass(/bg-gray-50/);

    // Assert the name has strikethrough (<s> element)
    await expect(checkedRow.locator('s')).toContainText('Eggs');
  });

  //
  // Test 4 — Delete an item
  //
  test('should remove an item from the list when deleted', async ({ page }) => {
    const email = `items-test-4-${Date.now()}@example.com`;
    const password = 'testpass123';
    const displayName = 'Test User 4';

    await registerAndLogin(page, email, password, displayName);
    await createList(page, 'Test List 4');

    // Add two items so we can confirm only the deleted one is removed
    await addItem(page, 'Butter', '250g', 'Salted');
    await addItem(page, 'Cheese', '200g', 'Cheddar');

    // Wait for both items to appear
    await expect(page.locator('#item-list')).toContainText('Butter');
    await expect(page.locator('#item-list')).toContainText('Cheese');

    // Click delete button on the Butter item
    const deleteButton = page.locator('#item-list > div')
      .filter({ hasText: 'Butter' })
      .locator('button[aria-label="Delete item"]');
    await deleteButton.click();

    // Assert Butter is removed from the list
    await expect(page.locator('#item-list')).not.toContainText('Butter');

    // Assert Cheese is still present
    await expect(page.locator('#item-list')).toContainText('Cheese');
  });

  //
  // Test 5 — Add-item validation with empty name
  //
  test('should show an error when trying to add an item with an empty name', async ({ page }) => {
    const email = `items-test-5-${Date.now()}@example.com`;
    const password = 'testpass123';
    const displayName = 'Test User 5';

    await registerAndLogin(page, email, password, displayName);
    await createList(page, 'Test List 5');

    // The name input has a `required` attribute which blocks HTMX submission.
    // Remove it so the request reaches the server for validation.
    await page.evaluate(() => {
      document.querySelector('input[name="name"]').removeAttribute('required');
    });

    // Submit the form with an empty name
    await page.fill('input[name="name"]', '');
    await page.fill('input[name="quantity"]', '1');
    await page.click('button:has-text("Add Item")');

    // Assert error message appears in the error div.
    // The server sends an OOB swap that sets innerHTML of #add-item-error,
    // though the hidden CSS class may still be present on the div itself.
    // Check text content regardless of visibility.
    await expect(page.locator('#add-item-error')).toContainText(/Name/);
  });

});
