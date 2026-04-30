# SaleADS Mi Negocio E2E

Playwright automation for validating the complete **Mi Negocio** workflow in any SaleADS.ai environment.

## What this test covers

This suite executes the full flow requested by `saleads_mi_negocio_full_test`:

1. Login with Google (continues after login)
2. Open **Negocio > Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate **Informacion General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Terminos y Condiciones** (same tab or popup/new tab)
9. Validate **Politica de Privacidad** (same tab or popup/new tab)
10. Generate final pass/fail report JSON

## Setup

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install chromium
```

Copy `.env.example` to `.env` and set values:

```bash
SALEADS_LOGIN_URL=https://<current-saleads-environment>/login
GOOGLE_ACCOUNT_EMAIL=juanlucasbarbiergarzon@gmail.com
```

## Run

```bash
npm test
```

Headed mode:

```bash
npm run test:headed
```

## Evidence output

Artifacts are written to:

- `artifacts/screenshots/` (checkpoints)
- `artifacts/final-report.json` (step-by-step PASS/FAIL)
- `playwright-report/` (HTML report)

## Environment agnostic behavior

- The test does not hardcode any SaleADS domain.
- It uses `SALEADS_LOGIN_URL` from environment.
- Element targeting is primarily text-based for resilience across environments.
