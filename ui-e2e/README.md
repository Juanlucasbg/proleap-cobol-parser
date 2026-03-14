# SaleADS Mi Negocio Full Workflow Test

This folder contains a Playwright E2E test that validates the complete `Mi Negocio` workflow requested in `saleads_mi_negocio_full_test`.

## What it validates

1. Login with Google (including optional account picker click for `juanlucasbarbiergarzon@gmail.com`)
2. `Mi Negocio` menu expansion
3. `Agregar Negocio` modal fields and actions
4. `Administrar Negocios` page sections
5. `Información General`
6. `Detalles de la Cuenta`
7. `Tus Negocios`
8. `Términos y Condiciones` content + final URL
9. `Política de Privacidad` content + final URL
10. Final PASS/FAIL report per step

The script captures screenshots at key checkpoints and attaches a JSON final report in Playwright artifacts.

## Run locally

```bash
cd ui-e2e
npm install
npm run install:browsers
```

### Option A: Start from already-open login page (default behavior)

```bash
npm run test:mi-negocio
```

### Option B: Provide an environment-specific start URL

No domain is hardcoded. If needed, provide the URL at runtime:

```bash
SALEADS_START_URL="https://your-current-environment.example/login" npm run test:mi-negocio
```

## Notes

- The test uses visible text selectors whenever possible.
- After each click, it waits for UI load states.
- For legal links, it supports either same-tab navigation or a newly opened tab and returns to the app afterward.
