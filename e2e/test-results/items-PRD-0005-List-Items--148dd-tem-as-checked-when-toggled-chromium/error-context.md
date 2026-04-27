# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: items.spec.js >> PRD-0005: List Items >> should visually mark an item as checked when toggled
- Location: tests/items.spec.js:55:3

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: page.waitForURL: Test timeout of 30000ms exceeded.
=========================== logs ===========================
waiting for navigation to "**/list/**" until "load"
============================================================
```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
  - navigation [ref=e2]:
    - generic [ref=e3]:
      - link "Comprineas" [ref=e4] [cursor=pointer]:
        - /url: /dashboard
      - generic [ref=e5]:
        - generic [ref=e6]: Hi, Test User 3
        - button "Logout" [ref=e8] [cursor=pointer]
  - generic [ref=e10]:
    - generic [ref=e11]:
      - generic [ref=e13]:
        - generic [ref=e14]:
          - heading "Test List 3" [level=1] [ref=e15]
          - paragraph [ref=e16]: Created by you
        - generic [ref=e17]:
          - generic [ref=e18]: "Code:"
          - button "52PPJ9" [ref=e19] [cursor=pointer]
      - generic [ref=e20]:
        - heading "Add an Item" [level=2] [ref=e21]
        - generic [ref=e22]:
          - generic [ref=e23]:
            - generic [ref=e24]:
              - generic [ref=e25]: Name *
              - textbox "Item name" [ref=e26]
            - generic [ref=e27]:
              - generic [ref=e28]: Quantity
              - textbox "1 kg" [ref=e29]
            - generic [ref=e30]:
              - generic [ref=e31]: Observations
              - textbox "notes" [ref=e32]
          - button "Add Item" [ref=e33] [cursor=pointer]
      - generic [ref=e34]:
        - heading "Items (0)" [level=2] [ref=e36]
        - paragraph [ref=e38]: Loading items…
      - button "✓ Complete List" [ref=e40] [cursor=pointer]
    - generic [ref=e41]:
      - generic [ref=e42]:
        - heading "Share List" [level=3] [ref=e43]
        - paragraph [ref=e44]: "Share this code with anyone you want to collaborate with:"
        - generic [ref=e45]:
          - code [ref=e46]: 52PPJ9
          - button "📋" [ref=e47] [cursor=pointer]
      - generic [ref=e48]:
        - heading "Participants" [level=3] [ref=e49]
        - paragraph [ref=e50]: "1"
        - paragraph [ref=e51]: people have joined this list
      - link "← Back to My Lists" [ref=e53] [cursor=pointer]:
        - /url: /dashboard
```

# Test source

```ts
  1   | // @ts-check
  2   | const { test, expect } = require('@playwright/test');
  3   | 
  4   | function uniqueEmail(prefix) {
  5   |   return `${prefix}-${Date.now()}@example.com`;
  6   | }
  7   | 
  8   | async function registerAndLogin(page, email, password, displayName) {
  9   |   await page.goto('/register');
  10  |   await page.fill('input[name="email"]', email);
  11  |   await page.fill('input[name="password"]', password);
  12  |   await page.fill('input[name="display_name"]', displayName);
  13  |   await Promise.all([
  14  |     page.waitForURL('**/dashboard', { timeout: 10000 }),
  15  |     page.click('button[type="submit"]')
  16  |   ]);
  17  | }
  18  | 
  19  | async function createList(page, name) {
  20  |   await page.fill('input[name="name"]', name);
  21  |   await page.click('text=Create List');
> 22  |   await page.waitForURL('**/list/**');
      |              ^ Error: page.waitForURL: Test timeout of 30000ms exceeded.
  23  | }
  24  | 
  25  | async function addItem(page, itemName, quantity, observations) {
  26  |   await page.fill('input[name="name"]', itemName);
  27  |   if (quantity !== undefined) await page.fill('input[name="quantity"]', quantity);
  28  |   if (observations !== undefined) await page.fill('input[name="observations"]', observations);
  29  |   await page.click('button:has-text("Add Item")');
  30  | }
  31  | 
  32  | test.describe('PRD-0005: List Items', () => {
  33  | 
  34  |   test('should add an item with all fields', async ({ page }) => {
  35  |     const email = uniqueEmail('items1');
  36  |     await registerAndLogin(page, email, 'testpass123', 'Test User 1');
  37  |     await createList(page, 'Test List 1');
  38  | 
  39  |     await addItem(page, 'Milk', '2 L', 'Organic whole milk');
  40  | 
  41  |     await expect(page.locator('#item-list')).toContainText('Milk');
  42  |     await expect(page.locator('#item-list')).toContainText('2 L');
  43  |     await expect(page.locator('#item-list')).toContainText('Organic whole milk');
  44  |   });
  45  | 
  46  |   test('should add an item with only the required name field', async ({ page }) => {
  47  |     const email = uniqueEmail('items2');
  48  |     await registerAndLogin(page, email, 'testpass123', 'Test User 2');
  49  |     await createList(page, 'Test List 2');
  50  | 
  51  |     await addItem(page, 'Bread');
  52  |     await expect(page.locator('#item-list')).toContainText('Bread');
  53  |   });
  54  | 
  55  |   test('should visually mark an item as checked when toggled', async ({ page }) => {
  56  |     const email = uniqueEmail('items3');
  57  |     await registerAndLogin(page, email, 'testpass123', 'Test User 3');
  58  |     await createList(page, 'Test List 3');
  59  | 
  60  |     await addItem(page, 'Eggs', '12', 'Free range');
  61  | 
  62  |     const checkbox = page.locator('#item-list > div').filter({ hasText: 'Eggs' }).locator('input[type="checkbox"]');
  63  |     await checkbox.click();
  64  | 
  65  |     const checkedRow = page.locator('#item-list > div').filter({ hasText: 'Eggs' });
  66  |     await expect(checkedRow).toHaveClass(/opacity-50/);
  67  |     await expect(checkedRow.locator('s')).toContainText('Eggs');
  68  |   });
  69  | 
  70  |   test('should remove an item from the list when deleted', async ({ page }) => {
  71  |     const email = uniqueEmail('items4');
  72  |     await registerAndLogin(page, email, 'testpass123', 'Test User 4');
  73  |     await createList(page, 'Test List 4');
  74  | 
  75  |     await addItem(page, 'Butter', '250g', 'Salted');
  76  |     await addItem(page, 'Cheese', '200g', 'Cheddar');
  77  | 
  78  |     await expect(page.locator('#item-list')).toContainText('Butter');
  79  |     await expect(page.locator('#item-list')).toContainText('Cheese');
  80  | 
  81  |     const deleteButton = page.locator('#item-list > div')
  82  |       .filter({ hasText: 'Butter' })
  83  |       .locator('button[aria-label="Delete item"]');
  84  |     await deleteButton.click();
  85  | 
  86  |     await expect(page.locator('#item-list')).not.toContainText('Butter');
  87  |     await expect(page.locator('#item-list')).toContainText('Cheese');
  88  |   });
  89  | 
  90  |   test('should show an error when trying to add an item with an empty name', async ({ page }) => {
  91  |     const email = uniqueEmail('items5');
  92  |     await registerAndLogin(page, email, 'testpass123', 'Test User 5');
  93  |     await createList(page, 'Test List 5');
  94  | 
  95  |     await page.evaluate(() => {
  96  |       document.querySelector('input[name="name"]').removeAttribute('required');
  97  |     });
  98  | 
  99  |     await page.fill('input[name="name"]', '');
  100 |     await page.fill('input[name="quantity"]', '1');
  101 |     await page.click('button:has-text("Add Item")');
  102 | 
  103 |     await expect(page.locator('#add-item-error')).toContainText(/Name/);
  104 |   });
  105 | 
  106 | });
  107 | 
```