# SaleADS Mi Negocio E2E

This Playwright suite validates the full **Mi Negocio** workflow requested in automation:

1. Login with Google (and continue)
2. Open Mi Negocio sidebar menu
3. Validate "Agregar Negocio" modal
4. Open "Administrar Negocios"
5. Validate "Informacion General"
6. Validate "Detalles de la Cuenta"
7. Validate "Tus Negocios"
8. Validate "Terminos y Condiciones" (new tab or same tab)
9. Validate "Politica de Privacidad" (new tab or same tab)
10. Emit PASS/FAIL style checkpoints through assertions

## Why this works across environments

- No hardcoded SaleADS domain is used.
- The test starts on whatever page/context the runner opens.
- Navigation/actions are done by visible text and semantic roles.
- If links open a new tab, the test validates the new tab and returns.

## Prerequisites

- Node.js 18+
- Browser is already on the SaleADS login page before running (as requested).
- A valid Google session can be completed by the operator/test environment.

## Install

```bash
cd /workspace/e2e
npm install
npx playwright install chromium
```

## Run

```bash
cd /workspace/e2e
npm test
```

Headed mode:

```bash
npm run test:headed
```

## Artifacts

- Screenshots are stored under `e2e/artifacts/`
- Playwright HTML report is under `e2e/artifacts/html-report/`
