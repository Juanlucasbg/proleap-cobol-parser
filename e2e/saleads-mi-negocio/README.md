# SaleADS Mi Negocio Full Workflow Test

This package contains the Playwright E2E test `saleads_mi_negocio_full_test`.

## What it validates

The test automates and validates:

1. Login with Google (including account chooser for `juanlucasbarbiergarzon@gmail.com`).
2. Sidebar navigation to `Negocio` > `Mi Negocio`.
3. `Agregar Negocio` modal fields and controls.
4. `Administrar Negocios` account view sections.
5. `Información General`.
6. `Detalles de la Cuenta`.
7. `Tus Negocios`.
8. `Términos y Condiciones` navigation/new-tab behavior.
9. `Política de Privacidad` navigation/new-tab behavior.
10. Final PASS/FAIL report payload.

The implementation does not hardcode any SaleADS domain. It relies on visible text selectors and supports any environment URL.

## Requirements

- Node.js 18+ (validated with Node 22 in CI agent).
- Browser binaries installed for Playwright.
- A valid SaleADS login URL for the target environment.

## Setup

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

```bash
SALEADS_LOGIN_URL="https://<current-environment>/login" npm test
```

For interactive runs:

```bash
SALEADS_LOGIN_URL="https://<current-environment>/login" npm run test:headed
```

## Evidence & report outputs

- Checkpoint screenshots are saved under Playwright test output in `checkpoints/`.
- Playwright HTML report is generated under `playwright-report/`.
- Final JSON summary is attached as `final-report` and saved as `final-report.json` inside the test output directory.
