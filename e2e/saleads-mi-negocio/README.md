# SaleADS Mi Negocio full workflow test

This Playwright suite automates the complete workflow requested for:

- Google login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios sections
- Legal links (Términos y Condiciones / Política de Privacidad)

## Why this is environment-agnostic

- No hardcoded SaleADS domain is used.
- Target login URL is provided at runtime.
- All interactions prefer visible text selectors.

## Setup

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
```

## Run

Set one of the following:

- `SALEADS_LOGIN_URL`
- `SALEADS_BASE_URL`

Then run:

```bash
SALEADS_LOGIN_URL="https://<current-saleads-environment>/login" npm test
```

Optional headed mode:

```bash
HEADLESS=false SALEADS_LOGIN_URL="https://<current-saleads-environment>/login" npm run test:headed
```

## Evidence and report output

For each run, artifacts are saved under:

```text
artifacts/<timestamp>/
```

Including:

- Checkpoint screenshots
- `final-report.json` with PASS/FAIL for each requested validation field
- Final URLs captured for legal pages
