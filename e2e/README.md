# SaleADS E2E Tests

This directory contains environment-agnostic Playwright tests for SaleADS workflows.

## Test included

- `saleads_mi_negocio_full_test.spec.ts`: validates the full **Mi Negocio** workflow:
  - Google login handoff
  - Sidebar/menu checks
  - Agregar Negocio modal checks
  - Administrar Negocios and account sections
  - Legal links (`Términos y Condiciones` / `Política de Privacidad`)
  - Evidence capture via screenshots and final JSON report attachment

## Setup

```bash
cd e2e
npm install
npx playwright install --with-deps
```

## Run

If your environment requires navigation from a known URL, provide it through an environment variable (no hardcoded domain in test code):

```bash
cd e2e
SALEADS_BASE_URL="https://your-saleads-environment" npm test
```

If the browser is already positioned at the login page by your runner, `SALEADS_BASE_URL` can be omitted.

## Output artifacts

- HTML report: `e2e/playwright-report/`
- Test artifacts (screenshots, trace, videos): `e2e/test-results/`
