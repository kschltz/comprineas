---
status: accepted
date: 2026-04-25
decision-makers: user (kschltz), agent
consulted: {}
informed: {}
---

# Use Tailwind CSS for Styling

## Context and Problem Statement

The application needs visual styling for its server-rendered HTML templates. The UI must be functional, consistent, and easy to maintain without a dedicated designer. The styling solution must integrate cleanly with the kit-clj/Selmer templating pipeline and HTMX server-rendered fragments.

## Decision Drivers

- The user explicitly requested Tailwind CSS.
- Small team, no dedicated designer — utility-first reduces decision fatigue.
- Server-rendered HTML (Selmer templates) — CSS classes are applied inline in templates.
- HTMX swaps HTML fragments; Tailwind classes travel with the HTML.
- Need rapid prototyping and iteration without custom CSS files.
- Consistent design system (spacing, color, typography) is important.

## Considered Options

### 1. Tailwind CSS (Utility-First)

A utility-first CSS framework that provides low-level utility classes (e.g., `flex`, `p-4`, `bg-blue-500`).

- Build step required: PostCSS + Tailwind CLI runs in **all environments** (dev, staging, production) to guarantee dev/prod parity. The CDN is only for zero-setup prototyping, not for project development.
- Configuration via `tailwind.config.js` defines design tokens (colors, spacing, fonts). Version-pinned CDN (`https://cdn.tailwindcss.com@3.4`) may be used for quick experiments but is never the build target.
- Classes applied directly in HTML/Selmer templates; Selmer macros for repeated patterns (e.g., `{% button "primary" "Add Item" %}`) reduce verbosity.
- PostCSS plugin integrates with the build pipeline.

### 2. Bootstrap

A component-first CSS framework with pre-built components (buttons, cards, navbars).

- Mature, well-documented, large community.
- Opinionated design — less customization without overriding.
- Heavier CSS bundle (includes all components).
- Component model conflicts with utility-first Tailwind approach.

### 3. Bulma

A lightweight alternative to Bootstrap with a more modern, flexbox-based design.

- Lighter than Bootstrap.
- Still component-first, requiring class overrides for customization.
- Smaller community and ecosystem than Bootstrap or Tailwind.

### 4. Custom CSS / Sass

Writing all stylesheets from scratch.

- Full design control.
- Significant maintenance burden.
- Requires establishing and documenting a design system manually.
- Slower iteration for a small team.

### 5. DaisyUI (Component Layer on Tailwind)

A plugin for Tailwind CSS that adds semantic component classes (e.g., `btn`, `card`).

- Built on top of Tailwind — same utility-first foundation.
- Provides ready-to-use components while retaining Tailwind customization.
- Optional: can use utilities directly or DaisyUI component classes.

## Decision Outcome

Chosen option: **"Tailwind CSS utility-first"** (DaisyUI excluded), because:
- The user explicitly requested Tailwind CSS.
- Utility-first approach maps directly to Selmer template attributes — no separate CSS files for simple components.
- HTMX fragments carry their own styles via Tailwind classes.
- JIT compiler produces a very small CSS bundle.
- Rapid prototyping without design decisions for every component.
- **DaisyUI is rejected** at this stage: it introduces a second mental model (semantic `btn` vs utility classes), adds coupling to a third-party component library's class naming conventions, and the app's UI needs (buttons, inputs, cards, modals) are trivially expressed with 3-5 Tailwind utility classes each. If verbosity becomes unmanageable, Selmer macros (e.g., `{% button "primary" "Add Item" %}`) that expand to utility classes are the preferred escape hatch — keeping the vocabulary inside the project.

Bootstrap and Bulma are rejected because their component-first model is less flexible for a server-rendered app where each fragment needs fine-grained control. Custom CSS is rejected because the maintenance burden outweighs the benefit for a small team. DaisyUI alone is insufficient because it requires Tailwind as its foundation.

### Consequences

- Good, because rapid prototyping — apply classes directly in Selmer templates.
- Good, because consistent design system via `tailwind.config.js` (spacing scale, color palette, typography).
- Good, because JIT/Purge produces very small production bundles.
- Good, because works seamlessly with HTMX server-rendered HTML fragments.
- Good, because **dev/prod parity**: PostCSS build runs identically in all environments, preventing "works in dev, breaks in prod" CSS bugs.
- Bad, because HTML templates can become verbose with many utility classes (mitigated by **Selmer macros** for repeated patterns).
- Bad, because initial learning curve for utility-first approach.
- Bad, because Selmer macros require initial setup for repeated patterns (buttons, cards).

### Confirmation

The decision is confirmed by:
- Selmer templates rendering HTML elements with Tailwind classes (e.g., `<div class="flex flex-col p-4 bg-white rounded shadow">`).
- PostCSS build pipeline producing a CSS bundle < 50KB (confirmed by bundle size check).
- **Dev/prod parity**: identical CSS in development and production (same PostCSS build, no CDN divergence).
- Visual inspection confirming consistent spacing, color, and typography.

## Pros and Cons of the Options

### Tailwind CSS

- Good, because utility-first maps directly to template attributes.
- Good, because JIT/Purge produces small production bundles.
- Good, because design tokens enforce consistency.
- Good, because **PostCSS build in all environments guarantees dev/prod parity**.
- Bad, because verbose class strings in HTML.
- Bad, because learning curve for utility-first mental model.
- Bad, because **requires build step** (no CDN fallback for dev).

### Bootstrap

- Good, because mature ecosystem and pre-built components.
- Bad, because opinionated design — harder to customize without overrides.
- Bad, because heavier CSS bundle.
- Bad, because component-first less suited to fragment-level styling.

### Bulma

- Good, because lighter than Bootstrap.
- Bad, because smaller community.
- Bad, because same component-first limitations as Bootstrap.

### Custom CSS / Sass

- Good, because full design control.
- Bad, because high maintenance burden.
- Bad, because requires manual design system documentation.
- Bad, because slower iteration for small team.

### DaisyUI (on Tailwind)

- Not chosen at this stage. Trivial UI patterns (buttons, cards, inputs) are expressed with 3-5 Tailwind utility classes. Selmer macros are the preferred escape hatch if verbosity grows. DaisyUI may be reconsidered if the component library provides significant unique value.

## More Information

- Tailwind CSS documentation: https://tailwindcss.com/
- DaisyUI documentation: https://daisyui.com/
- Tailwind + HTMX integration guide: https://htmx.org/essays/htmx-and-tailwind/
- PurgeCSS / JIT mode: https://tailwindcss.com/docs/just-in-time-mode

