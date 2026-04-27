# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: items.spec.js >> PRD-0005: List Items >> should remove an item from the list when deleted
- Location: tests/items.spec.js:72:3

# Error details

```
Error: expect(locator).not.toContainText(expected) failed

Locator: locator('#item-list')
Expected substring: not "Butter"
Received string: "
                        Loading items…
                    
  
  
    
      Butter
    
    
    (250g)
    
    
    Salted
    
  
  
    
      
    
  


  
  
    
      Cheese
    
    
    (200g)
    
    
    Cheddar
    
  
  
    
      
    
  

"
Timeout: 10000ms

Call log:
  - Expect "not toContainText" with timeout 10000ms
  - waiting for locator('#item-list')
    14 × locator resolved to <div id="item-list" hx-trigger="load" class="px-6 pb-6" hx-get="/list/GD6YIK/items-list">…</div>
       - unexpected value "
                        Loading items…
                    
  
  
    
      Butter
    
    
    (250g)
    
    
    Salted
    
  
  
    
      
    
  


  
  
    
      Cheese
    
    
    (200g)
    
    
    Cheddar
    
  
  
    
      
    
  

"

```

# Page snapshot

```yaml
- generic [ref=e1]:
  - navigation [ref=e2]:
    - generic [ref=e3]:
      - link "Comprineas" [ref=e4] [cursor=pointer]:
        - /url: /dashboard
      - generic [ref=e5]:
        - generic [ref=e6]: Hi, Test User 4
        - button "Logout" [ref=e8] [cursor=pointer]
  - generic [ref=e10]:
    - generic [ref=e11]:
      - generic [ref=e13]:
        - generic [ref=e14]:
          - heading "Test List 4" [level=1] [ref=e15]
          - paragraph [ref=e16]: Created by you
        - generic [ref=e17]:
          - generic [ref=e18]: "Code:"
          - button "GD6YIK" [ref=e19] [cursor=pointer]
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
        - generic [ref=e37]:
          - paragraph [ref=e38]: Loading items…
          - generic [ref=e39]:
            - checkbox [ref=e40] [cursor=pointer]
            - generic [ref=e41]:
              - text: Butter (250g)
              - paragraph [ref=e42]: Salted
            - button "Delete item" [active] [ref=e43] [cursor=pointer]:
              - img [ref=e44]
          - generic [ref=e46]:
            - checkbox [ref=e47] [cursor=pointer]
            - generic [ref=e48]:
              - text: Cheese (200g)
              - paragraph [ref=e49]: Cheddar
            - button "Delete item" [ref=e50] [cursor=pointer]:
              - img [ref=e51]
      - button "✓ Complete List" [ref=e54] [cursor=pointer]
    - generic [ref=e55]:
      - generic [ref=e56]:
        - heading "Share List" [level=3] [ref=e57]
        - paragraph [ref=e58]: "Share this code with anyone you want to collaborate with:"
        - generic [ref=e59]:
          - code [ref=e60]: GD6YIK
          - button "📋" [ref=e61] [cursor=pointer]
      - generic [ref=e62]:
        - heading "Participants" [level=3] [ref=e63]
        - paragraph [ref=e64]: "1"
        - paragraph [ref=e65]: people have joined this list
      - link "← Back to My Lists" [ref=e67] [cursor=pointer]:
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
  22  |   // HTMX swaps body, no URL change — wait for list page content
  23  |   await expect(page.locator('#list-name')).toBeVisible({ timeout: 10000 });
  24  | }
  25  | 
  26  | async function addItem(page, itemName, quantity, observations) {
  27  |   await page.fill('input[name="name"]', itemName);
  28  |   if (quantity !== undefined) await page.fill('input[name="quantity"]', quantity);
  29  |   if (observations !== undefined) await page.fill('input[name="observations"]', observations);
  30  |   await page.click('button:has-text("Add Item")');
  31  |   await page.waitForTimeout(300);
  32  | }
  33  | 
  34  | test.describe('PRD-0005: List Items', () => {
  35  | 
  36  |   test('should add an item with all fields', async ({ page }) => {
  37  |     const email = uniqueEmail('items1');
  38  |     await registerAndLogin(page, email, 'testpass123', 'Test User 1');
  39  |     await createList(page, 'Test List 1');
  40  | 
  41  |     await addItem(page, 'Milk', '2 L', 'Organic whole milk');
  42  | 
  43  |     await expect(page.locator('#item-list')).toContainText('Milk');
  44  |     await expect(page.locator('#item-list')).toContainText('2 L');
  45  |     await expect(page.locator('#item-list')).toContainText('Organic whole milk');
  46  |   });
  47  | 
  48  |   test('should add an item with only the required name field', async ({ page }) => {
  49  |     const email = uniqueEmail('items2');
  50  |     await registerAndLogin(page, email, 'testpass123', 'Test User 2');
  51  |     await createList(page, 'Test List 2');
  52  | 
  53  |     await addItem(page, 'Bread');
  54  |     await expect(page.locator('#item-list')).toContainText('Bread');
  55  |   });
  56  | 
  57  |   test('should visually mark an item as checked when toggled', async ({ page }) => {
  58  |     const email = uniqueEmail('items3');
  59  |     await registerAndLogin(page, email, 'testpass123', 'Test User 3');
  60  |     await createList(page, 'Test List 3');
  61  | 
  62  |     await addItem(page, 'Eggs', '12', 'Free range');
  63  | 
  64  |     const checkbox = page.locator('#item-list > div').filter({ hasText: 'Eggs' }).locator('input[type="checkbox"]');
  65  |     await checkbox.click();
  66  | 
  67  |     const checkedRow = page.locator('#item-list > div').filter({ hasText: 'Eggs' });
  68  |     await expect(checkedRow).toHaveClass(/opacity-50/);
  69  |     await expect(checkedRow.locator('s')).toContainText('Eggs');
  70  |   });
  71  | 
  72  |   test('should remove an item from the list when deleted', async ({ page }) => {
  73  |     const email = uniqueEmail('items4');
  74  |     await registerAndLogin(page, email, 'testpass123', 'Test User 4');
  75  |     await createList(page, 'Test List 4');
  76  | 
  77  |     await addItem(page, 'Butter', '250g', 'Salted');
  78  |     await addItem(page, 'Cheese', '200g', 'Cheddar');
  79  | 
  80  |     // Give HTMX time to process the load trigger and items-list response
  81  |     await page.waitForTimeout(2000);
  82  |     // Verify items are visible
  83  |     await expect(page.locator('#item-list')).toContainText('Butter');
  84  |     await expect(page.locator('#item-list')).toContainText('Cheese');
  85  | 
  86  |     // Click delete on the Butter item — find by aria-label on the first matching button
  87  |     const deleteBtn = page.locator('#item-list button[aria-label="Delete item"]').first();
  88  |     await deleteBtn.click();
  89  | 
  90  |     // Give HTMX time to process the swap
  91  |     await page.waitForTimeout(500);
  92  | 
> 93  |     await expect(page.locator('#item-list')).not.toContainText('Butter');
      |                                                  ^ Error: expect(locator).not.toContainText(expected) failed
  94  |     await expect(page.locator('#item-list')).toContainText('Cheese');
  95  |   });
  96  | 
  97  |   test('should show an error when trying to add an item with an empty name', async ({ page }) => {
  98  |     const email = uniqueEmail('items5');
  99  |     await registerAndLogin(page, email, 'testpass123', 'Test User 5');
  100 |     await createList(page, 'Test List 5');
  101 | 
  102 |     await page.evaluate(() => {
  103 |       document.querySelector('input[name="name"]').removeAttribute('required');
  104 |     });
  105 | 
  106 |     await page.fill('input[name="name"]', '');
  107 |     await page.fill('input[name="quantity"]', '1');
  108 |     await page.click('button:has-text("Add Item")');
  109 | 
  110 |     // HTMX OOB swap may take a tick — wait briefly
  111 |     await page.waitForTimeout(1000);
  112 |     const errorText = await page.locator('#add-item-error').textContent();
  113 |     // The error div should have content after submission
  114 |     if (errorText && errorText.trim()) {
  115 |       expect(errorText).toMatch(/Name|required|empty/i);
  116 |     } else {
  117 |       // Fallback: check if response HTML contains error anywhere
  118 |       const bodyText = await page.textContent('body');
  119 |       expect(bodyText).toMatch(/Name|required|empty/i);
  120 |     }
  121 |   });
  122 | 
  123 | });
  124 | 
```