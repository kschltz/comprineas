# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: items.spec.js >> PRD-0005: List Items >> should remove an item from the list when deleted
- Location: tests/items.spec.js:76:3

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
    14 × locator resolved to <div id="item-list" hx-trigger="load" class="px-6 pb-6" hx-get="/list/WL3AH9/items-list">…</div>
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
          - button "WL3AH9" [ref=e19] [cursor=pointer]
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
          - code [ref=e60]: WL3AH9
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
  27  |   console.log(`addItem: ${itemName}`);
  28  |   await page.fill('input[name="name"]', itemName);
  29  |   if (quantity !== undefined) await page.fill('input[name="quantity"]', quantity);
  30  |   if (observations !== undefined) await page.fill('input[name="observations"]', observations);
  31  |   await page.click('button:has-text("Add Item")');
  32  |   console.log(`  clicked Add Item for ${itemName}`);
  33  |   // Wait for the HTMX swap to complete
  34  |   await page.waitForTimeout(300);
  35  |   console.log(`  done with ${itemName}`);
  36  | }
  37  | 
  38  | test.describe('PRD-0005: List Items', () => {
  39  | 
  40  |   test('should add an item with all fields', async ({ page }) => {
  41  |     const email = uniqueEmail('items1');
  42  |     await registerAndLogin(page, email, 'testpass123', 'Test User 1');
  43  |     await createList(page, 'Test List 1');
  44  | 
  45  |     await addItem(page, 'Milk', '2 L', 'Organic whole milk');
  46  | 
  47  |     await expect(page.locator('#item-list')).toContainText('Milk');
  48  |     await expect(page.locator('#item-list')).toContainText('2 L');
  49  |     await expect(page.locator('#item-list')).toContainText('Organic whole milk');
  50  |   });
  51  | 
  52  |   test('should add an item with only the required name field', async ({ page }) => {
  53  |     const email = uniqueEmail('items2');
  54  |     await registerAndLogin(page, email, 'testpass123', 'Test User 2');
  55  |     await createList(page, 'Test List 2');
  56  | 
  57  |     await addItem(page, 'Bread');
  58  |     await expect(page.locator('#item-list')).toContainText('Bread');
  59  |   });
  60  | 
  61  |   test('should visually mark an item as checked when toggled', async ({ page }) => {
  62  |     const email = uniqueEmail('items3');
  63  |     await registerAndLogin(page, email, 'testpass123', 'Test User 3');
  64  |     await createList(page, 'Test List 3');
  65  | 
  66  |     await addItem(page, 'Eggs', '12', 'Free range');
  67  | 
  68  |     const checkbox = page.locator('#item-list > div').filter({ hasText: 'Eggs' }).locator('input[type="checkbox"]');
  69  |     await checkbox.click();
  70  | 
  71  |     const checkedRow = page.locator('#item-list > div').filter({ hasText: 'Eggs' });
  72  |     await expect(checkedRow).toHaveClass(/opacity-50/);
  73  |     await expect(checkedRow.locator('s')).toContainText('Eggs');
  74  |   });
  75  | 
  76  |   test('should remove an item from the list when deleted', async ({ page }) => {
  77  |     const email = uniqueEmail('items4');
  78  |     await registerAndLogin(page, email, 'testpass123', 'Test User 4');
  79  |     await createList(page, 'Test List 4');
  80  | 
  81  |     await addItem(page, 'Butter', '250g', 'Salted');
  82  |     await addItem(page, 'Cheese', '200g', 'Cheddar');
  83  | 
  84  |     // Give HTMX time to process the load trigger and items-list response
  85  |     await page.waitForTimeout(2000);
  86  |     // Verify items are visible
  87  |     const itemListText = await page.textContent('#item-list');
  88  |     console.log('item-list content:', itemListText);
  89  |     await expect(page.locator('#item-list')).toContainText('Butter');
  90  |     await expect(page.locator('#item-list')).toContainText('Cheese');
  91  | 
  92  |     // Click delete on the Butter item — find by aria-label on the first matching button
  93  |     const deleteBtn = page.locator('#item-list button[aria-label="Delete item"]').first();
  94  |     // Click delete — catch the response
  95  |     const [deleteResp] = await Promise.all([
  96  |       page.waitForResponse(r => r.request().method() === 'DELETE' && r.url().includes('/items/'), { timeout: 5000 }),
  97  |       deleteBtn.click()
  98  |     ]);
  99  |     console.log('Delete status:', deleteResp.status());
  100 |     console.log('Delete body:', await deleteResp.text());
  101 | 
  102 |     // Give HTMX time to process the swap
  103 |     await page.waitForTimeout(500);
  104 | 
> 105 |     await expect(page.locator('#item-list')).not.toContainText('Butter');
      |                                                  ^ Error: expect(locator).not.toContainText(expected) failed
  106 |     await expect(page.locator('#item-list')).toContainText('Cheese');
  107 |   });
  108 | 
  109 |   test('should show an error when trying to add an item with an empty name', async ({ page }) => {
  110 |     const email = uniqueEmail('items5');
  111 |     await registerAndLogin(page, email, 'testpass123', 'Test User 5');
  112 |     await createList(page, 'Test List 5');
  113 | 
  114 |     await page.evaluate(() => {
  115 |       document.querySelector('input[name="name"]').removeAttribute('required');
  116 |     });
  117 | 
  118 |     await page.fill('input[name="name"]', '');
  119 |     await page.fill('input[name="quantity"]', '1');
  120 |     await page.click('button:has-text("Add Item")');
  121 | 
  122 |     // HTMX OOB swap may take a tick — wait briefly
  123 |     await page.waitForTimeout(1000);
  124 |     const errorText = await page.locator('#add-item-error').textContent();
  125 |     // The error div should have content after submission
  126 |     if (errorText && errorText.trim()) {
  127 |       expect(errorText).toMatch(/Name|required|empty/i);
  128 |     } else {
  129 |       // Fallback: check if response HTML contains error anywhere
  130 |       const bodyText = await page.textContent('body');
  131 |       expect(bodyText).toMatch(/Name|required|empty/i);
  132 |     }
  133 |   });
  134 | 
  135 | });
  136 | 
```