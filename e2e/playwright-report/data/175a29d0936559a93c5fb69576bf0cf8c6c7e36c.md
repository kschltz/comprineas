# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: realtime.spec.js >> SSE real-time updates (PRD-0003 FR-10, PRD-0005 FR-9/10/11) >> 3. New list appears on dashboard via SSE
- Location: tests/realtime.spec.js:127:3

# Error details

```
Test timeout of 30000ms exceeded.
```

# Page snapshot

```yaml
- generic [ref=e2]:
  - heading "Comprineas" [level=1] [ref=e3]
  - paragraph [ref=e4]: Create your account
  - generic [ref=e5]:
    - generic [ref=e6]:
      - generic [ref=e7]: Email address
      - textbox "Email address" [ref=e8]: rt-dash-a-1777315722786-1@e2e-test.com
    - generic [ref=e9]:
      - generic [ref=e10]: Password
      - textbox "Password" [active] [ref=e11]: TestPass123
      - paragraph [ref=e12]: At least 8 characters.
    - generic [ref=e13]:
      - generic [ref=e14]: Display name
      - textbox "Display name" [ref=e15]:
        - /placeholder: e.g., Alice
    - button "Register" [ref=e16] [cursor=pointer]
  - paragraph [ref=e17]:
    - text: Already have an account?
    - link "Log in" [ref=e18] [cursor=pointer]:
      - /url: /login
```