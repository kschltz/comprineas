# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: items.spec.js >> PRD-0005: List Items >> should show an error when trying to add an item with an empty name
- Location: tests/items.spec.js:96:3

# Error details

```
Error: expect(received).toMatch(expected)

Expected pattern: /Name|required|empty/i
Received string:  ""
```

# Page snapshot

```yaml
- generic [ref=e1]:
  - navigation [ref=e2]:
    - generic [ref=e3]:
      - link "Comprineas" [ref=e4] [cursor=pointer]:
        - /url: /dashboard
      - generic [ref=e5]:
        - generic [ref=e6]: Hi, Test User 5
        - button "Logout" [ref=e8] [cursor=pointer]
  - generic [ref=e10]:
    - generic [ref=e11]:
      - generic [ref=e13]:
        - generic [ref=e14]:
          - heading "Test List 5" [level=1] [ref=e15]
          - paragraph [ref=e16]: Created by you
        - generic [ref=e17]:
          - generic [ref=e18]: "Code:"
          - button "KLXEW5" [ref=e19] [cursor=pointer]
      - generic [ref=e20]:
        - heading "Add an Item" [level=2] [ref=e21]
        - generic [ref=e22]:
          - generic [ref=e23]:
            - generic [ref=e24]:
              - generic [ref=e25]: Name *
              - textbox "Item name" [ref=e26]
            - generic [ref=e27]:
              - generic [ref=e28]: Quantity
              - textbox "1 kg" [ref=e29]: "1"
            - generic [ref=e30]:
              - generic [ref=e31]: Observations
              - textbox "notes" [ref=e32]
          - button "Add Item" [active] [ref=e33] [cursor=pointer]
      - generic [ref=e34]:
        - heading "Items (0)" [level=2] [ref=e36]
        - paragraph [ref=e38]: Loading items…
      - button "✓ Complete List" [ref=e40] [cursor=pointer]
    - generic [ref=e41]:
      - generic [ref=e42]:
        - heading "Share List" [level=3] [ref=e43]
        - paragraph [ref=e44]: "Share this code with anyone you want to collaborate with:"
        - generic [ref=e45]:
          - code [ref=e46]: KLXEW5
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
  13  |   await Promise.all([
  14  |     page.waitForURL('**/dashboard', { timeout: 10000 }),
  15  |     page.click('button[type="submit"]')
  16  |   ]);
  17  | }
  18  | 
  19  | async function createList(page, name) {
  20  |   await page.fill('input[name="name"]', name);
  21  |   await page.click('text=Create List');
  22  |   // HTMX swaps body, no URL change — wait for list page content
  23  |   await expect(page.locator('#list-name')).toBeVisible({ timeout: 10000 });
  24  | }
  25  | 
  26  | async function addItem(page, itemName, quantity, observations) {
  27  |   await page.fill('input[name="name"]', itemName);
  28  |   if (quantity !== undefined) await page.fill('input[name="quantity"]', quantity);
  29  |   if (observations !== undefined) await page.fill('input[name="observations"]', observations);
  30  |   await page.click('button:has-text("Add Item")');
  31  | }
  32  | 
  33  | test.describe('PRD-0005: List Items', () => {
  34  | 
  35  |   test('should add an item with all fields', async ({ page }) => {
  36  |     const email = uniqueEmail('items1');
  37  |     await registerAndLogin(page, email, 'testpass123', 'Test User 1');
  38  |     await createList(page, 'Test List 1');
  39  | 
  40  |     await addItem(page, 'Milk', '2 L', 'Organic whole milk');
  41  | 
  42  |     await expect(page.locator('#item-list')).toContainText('Milk');
  43  |     await expect(page.locator('#item-list')).toContainText('2 L');
  44  |     await expect(page.locator('#item-list')).toContainText('Organic whole milk');
  45  |   });
  46  | 
  47  |   test('should add an item with only the required name field', async ({ page }) => {
  48  |     const email = uniqueEmail('items2');
  49  |     await registerAndLogin(page, email, 'testpass123', 'Test User 2');
  50  |     await createList(page, 'Test List 2');
  51  | 
  52  |     await addItem(page, 'Bread');
  53  |     await expect(page.locator('#item-list')).toContainText('Bread');
  54  |   });
  55  | 
  56  |   test('should visually mark an item as checked when toggled', async ({ page }) => {
  57  |     const email = uniqueEmail('items3');
  58  |     await registerAndLogin(page, email, 'testpass123', 'Test User 3');
  59  |     await createList(page, 'Test List 3');
  60  | 
  61  |     await addItem(page, 'Eggs', '12', 'Free range');
  62  | 
  63  |     const checkbox = page.locator('#item-list > div').filter({ hasText: 'Eggs' }).locator('input[type="checkbox"]');
  64  |     await checkbox.click();
  65  | 
  66  |     const checkedRow = page.locator('#item-list > div').filter({ hasText: 'Eggs' });
  67  |     await expect(checkedRow).toHaveClass(/opacity-50/);
  68  |     await expect(checkedRow.locator('s')).toContainText('Eggs');
  69  |   });
  70  | 
  71  |   test('should remove an item from the list when deleted', async ({ page }) => {
  72  |     const email = uniqueEmail('items4');
  73  |     await registerAndLogin(page, email, 'testpass123', 'Test User 4');
  74  |     await createList(page, 'Test List 4');
  75  | 
  76  |     await addItem(page, 'Butter', '250g', 'Salted');
  77  |     await addItem(page, 'Cheese', '200g', 'Cheddar');
  78  | 
  79  |     // Wait for items to fully load (hx-trigger="load" may be async)
  80  |     await expect(page.locator('#item-list')).not.toContainText('Loading items', { timeout: 5000 });
  81  |     // Wait for actual item content
  82  |     await page.waitForTimeout(500);
  83  | 
  84  |     await expect(page.locator('#item-list')).toContainText('Butter');
  85  |     await expect(page.locator('#item-list')).toContainText('Cheese');
  86  | 
  87  |     const deleteButton = page.locator('#item-list > div')
  88  |       .filter({ hasText: 'Butter' })
  89  |       .locator('button[aria-label="Delete item"]');
  90  |     await deleteButton.click();
  91  | 
  92  |     await expect(page.locator('#item-list')).not.toContainText('Butter');
  93  |     await expect(page.locator('#item-list')).toContainText('Cheese');
  94  |   });
  95  | 
  96  |   test('should show an error when trying to add an item with an empty name', async ({ page }) => {
  97  |     const email = uniqueEmail('items5');
  98  |     await registerAndLogin(page, email, 'testpass123', 'Test User 5');
  99  |     await createList(page, 'Test List 5');
  100 | 
  101 |     await page.evaluate(() => {
  102 |       document.querySelector('input[name="name"]').removeAttribute('required');
  103 |     });
  104 | 
  105 |     await page.fill('input[name="name"]', '');
  106 |     await page.fill('input[name="quantity"]', '1');
  107 |     await page.click('button:has-text("Add Item")');
  108 | 
  109 |     // Wait for the error to appear — it's set via HTMX OOB swap
  110 |     await page.waitForTimeout(500);
  111 |     const errorText = await page.locator('#add-item-error').textContent();
  112 |     // The error may be in a hidden div but should have content
> 113 |     expect(errorText).toMatch(/Name|required|empty/i);
      |                       ^ Error: expect(received).toMatch(expected)
  114 |   });
  115 | 
  116 | });
  117 | 
```