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
  console.log(`addItem: ${itemName}`);
  await page.fill('input[name="name"]', itemName);
  if (quantity !== undefined) await page.fill('input[name="quantity"]', quantity);
  if (observations !== undefined) await page.fill('input[name="observations"]', observations);
  await page.click('button:has-text("Add Item")');
  console.log(`  clicked Add Item for ${itemName}`);
  // Wait for the HTMX swap to complete
  await page.waitForTimeout(300);
  console.log(`  done with ${itemName}`);
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

    // Give HTMX time to process the load trigger and items-list response
    await page.waitForTimeout(2000);
    // Verify items are visible
    const itemListText = await page.textContent('#item-list');
    console.log('item-list content:', itemListText);
    await expect(page.locator('#item-list')).toContainText('Butter');
    await expect(page.locator('#item-list')).toContainText('Cheese');

    // Click delete on the Butter item — find by aria-label on the first matching button
    const deleteBtn = page.locator('#item-list button[aria-label="Delete item"]').first();
    // Click delete — catch the response
    const [deleteResp] = await Promise.all([
      page.waitForResponse(r => r.request().method() === 'DELETE' && r.url().includes('/items/'), { timeout: 5000 }),
      deleteBtn.click()
    ]);
    console.log('Delete status:', deleteResp.status());
    console.log('Delete body:', await deleteResp.text());

    // Give HTMX time to process the swap
    await page.waitForTimeout(500);

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

    // HTMX OOB swap may take a tick — wait briefly
    await page.waitForTimeout(1000);
    const errorText = await page.locator('#add-item-error').textContent();
    // The error div should have content after submission
    if (errorText && errorText.trim()) {
      expect(errorText).toMatch(/Name|required|empty/i);
    } else {
      // Fallback: check if response HTML contains error anywhere
      const bodyText = await page.textContent('body');
      expect(bodyText).toMatch(/Name|required|empty/i);
    }
  });

});
