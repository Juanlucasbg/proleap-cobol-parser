# SaleADS Mi Negocio Full Workflow Test

This folder contains an end-to-end Playwright test named:

- `saleads_mi_negocio_full_test`

The test validates the complete **Mi Negocio** workflow requested by automation:

1. Login with Google (and continue after login)
2. Expand `Mi Negocio` menu
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Información General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Términos y Condiciones` (including new-tab handling)
9. Validate `Política de Privacidad` (including new-tab handling)
10. Produce final PASS/FAIL report

## Environment-agnostic behavior

- The test **does not hardcode a domain**.
- Provide the environment login URL at runtime with `SALEADS_URL` (or `BASE_URL`).
- If the browser is already on the login page, you can omit the URL.

## Setup

```bash
cd e2e
npm install
npm run install:browsers
```

## Run

```bash
cd e2e
SALEADS_URL="https://<your-saleads-env-login-url>" npm run test:mi-negocio
```

Optional env vars:

- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS=false` for headed execution

## Output artifacts

For each run, artifacts are generated under:

- `e2e/artifacts/saleads_mi_negocio_full_test/<timestamp>/`

Includes:

- `final-report.json` (step-by-step PASS/FAIL)
- `final-report.md` (human-readable summary)
- `screenshots/*.png` (checkpoint evidence)

