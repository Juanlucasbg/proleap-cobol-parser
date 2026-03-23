# SaleADS Mi Negocio Full Workflow Test

This Playwright test implements the full workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google.
2. Open and validate the **Mi Negocio** menu.
3. Validate the **Agregar Negocio** modal.
4. Open **Administrar Negocios** and validate account sections.
5. Validate legal links (**Terminos y Condiciones** and **Politica de Privacidad**), including new-tab handling.
6. Generate a final PASS/FAIL report as JSON attachment.

## Why this works across environments

- The test never hardcodes a SaleADS domain.
- It uses text-based selectors and structural fallbacks.
- You provide the environment entry URL at runtime.

## Prerequisites

- Node.js 18+.
- Playwright browsers installed:

```bash
npx playwright install
```

## Run

From this folder:

```bash
cd e2e/saleads-mi-negocio
```

Headless:

```bash
SALEADS_START_URL="https://<your-environment-login-url>" npm run test:mi-negocio
```

Headed (recommended for Google auth flows):

```bash
SALEADS_START_URL="https://<your-environment-login-url>" npm run test:mi-negocio -- --headed
```

## Evidence and report output

- Checkpoint screenshots are saved in Playwright test output.
- The final JSON report is attached as:
  - `saleads-mi-negocio-final-report.json`
- Report fields:
  - `Login`
  - `Mi Negocio menu`
  - `Agregar Negocio modal`
  - `Administrar Negocios view`
  - `Informacion General`
  - `Detalles de la Cuenta`
  - `Tus Negocios`
  - `Terminos y Condiciones`
  - `Politica de Privacidad`
