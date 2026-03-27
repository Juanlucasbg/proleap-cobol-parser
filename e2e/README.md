SaleADS Mi Negocio E2E
======================

This folder contains a Playwright test that validates the full "Mi Negocio" workflow.

Test included
-------------

- `tests/saleads-mi-negocio-full.spec.ts`

What it validates
-----------------

1. Login with Google and dashboard/sidebar visibility.
2. "Mi Negocio" menu expansion and submenu visibility.
3. "Agregar Negocio" modal fields/buttons/content.
4. "Administrar Negocios" account view sections.
5. "Informacion General" section.
6. "Detalles de la Cuenta" section.
7. "Tus Negocios" section.
8. "Terminos y Condiciones" legal page (same tab or new tab).
9. "Politica de Privacidad" legal page (same tab or new tab).
10. Final PASS/FAIL report attachment.

Environment compatibility
-------------------------

- The test does not depend on a fixed domain.
- If the browser is already at the SaleADS login page, run directly.
- If you want the test to navigate first, set `SALEADS_BASE_URL`.

Setup
-----

```bash
cd /workspace/e2e
npm install
npx playwright install --with-deps chromium
```

Run
---

```bash
# Headless
npm run test:mi-negocio

# Headed
npm run test:mi-negocio:headed
```

With optional base URL:

```bash
SALEADS_BASE_URL="https://your-saleads-environment" npm run test:mi-negocio
```

Evidence generated
------------------

- Screenshots for key checkpoints (dashboard, expanded menu, modal, account view, legal pages).
- Captured URLs for legal pages.
- `final-report.json` attachment with PASS/FAIL per required section.
