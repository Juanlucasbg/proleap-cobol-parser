## SaleADS Mi Negocio workflow test

This folder contains an environment-agnostic Playwright E2E test for:

- `saleads_mi_negocio_full_test`

The test validates the complete Mi Negocio workflow, not only login.

### What it covers

1. Login with Google.
2. Open **Negocio > Mi Negocio** menu and validate submenu entries.
3. Open and validate **Agregar Negocio** modal.
4. Open **Administrar Negocios** and validate page sections.
5. Validate **Informacion General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Terminos y Condiciones** (same tab or new tab).
9. Validate **Politica de Privacidad** (same tab or new tab).
10. Emit final PASS/FAIL report.

Screenshots are captured at key checkpoints and attached to Playwright results.

## Requirements

- Node.js 18+
- A reachable SaleADS environment login URL
- Browser session capable of completing Google authentication

## Setup

```bash
cd saleads-e2e
npm install
npx playwright install --with-deps chromium
```

## Run

If the runner already opens the SaleADS login page, just run the test directly.
If not, pass the current environment login URL at runtime (still environment-agnostic):

```bash
cd saleads-e2e
SALEADS_BASE_URL="https://<current-environment-login-url>" npx playwright test tests/saleads-mi-negocio-full.spec.js --project=chromium
```

Optional headed execution:

```bash
cd saleads-e2e
SALEADS_BASE_URL="https://<current-environment-login-url>" npx playwright test tests/saleads-mi-negocio-full.spec.js --headed --project=chromium
```

## Evidence artifacts

- Checkpoint screenshots attached in test output.
- HTML report under `playwright-report/`.
- Final JSON attachment in the test named `final-report.json`.
