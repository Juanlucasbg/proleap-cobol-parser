# SaleADS Mi Negocio E2E

This repository now includes a Playwright E2E test that validates the complete **Mi Negocio** workflow:

- Google login
- Sidebar + Mi Negocio menu
- "Agregar Negocio" modal
- "Administrar Negocios" account sections
- Legal links ("Términos y Condiciones", "Política de Privacidad")
- Checkpoint screenshots and final PASS/FAIL report attachment

## Setup

```bash
npm install
npx playwright install
```

## Run

Set the environment URL dynamically (works for dev/staging/prod):

```bash
SALEADS_LOGIN_URL="https://your-environment-login-url" npm run test:e2e
```

Optional:

- `GOOGLE_ACCOUNT_EMAIL` (defaults to `juanlucasbarbiergarzon@gmail.com`)

## Output

- Checkpoint screenshots and test artifacts are saved in Playwright output directories.
- A JSON report attachment (`mi-negocio-final-report.json`) includes:
  - PASS/FAIL by requested validation field
  - Final URLs for legal pages
  - Any failure details
