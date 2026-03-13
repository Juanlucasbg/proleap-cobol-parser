# SaleADS Mi Negocio Full Workflow Test

This repository includes a Playwright end-to-end test for the complete **Mi Negocio** workflow:

- Google login
- Sidebar and **Mi Negocio** menu expansion
- **Agregar Negocio** modal validation
- **Administrar Negocios** sections validation
- Legal links validation (**Términos y Condiciones** and **Política de Privacidad**), including new-tab handling
- Checkpoint screenshots and a final PASS/FAIL report JSON

## Test file

- `tests/saleads_mi_negocio_full_test.spec.js`

## Install

```bash
npm install
npx playwright install --with-deps chromium
```

## Run

### Option A: Provide environment URL at runtime

```bash
SALEADS_URL="https://<your-environment-login-page>" npm run test:saleads-mi-negocio
```

### Option B: Browser already on login page

If your execution platform opens the login page automatically, run without `SALEADS_URL`.

```bash
npm run test:saleads-mi-negocio
```

## Optional environment variables

- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS=false` to run headed mode

## Artifacts

Playwright stores run artifacts under `test-results/`, including:

- Screenshots for major checkpoints
- `final-report.json` containing PASS/FAIL by requested validation field and legal-page URLs
