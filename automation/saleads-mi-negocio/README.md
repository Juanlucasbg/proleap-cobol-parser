# SaleADS Mi Negocio Full Workflow Test

This isolated Playwright package validates the complete **Mi Negocio** workflow:

1. Login with Google
2. Open and validate the **Mi Negocio** menu
3. Validate the **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate account sections and legal links
6. Produce a final PASS/FAIL JSON report per requested field

## Environment-agnostic behavior

- No domain is hardcoded.
- If `SALEADS_LOGIN_URL` is provided, the test navigates there.
- If `SALEADS_LOGIN_URL` is not provided, the test runs from the current page/session.

## Setup

```bash
cd automation/saleads-mi-negocio
npm install
npx playwright install
```

## Run

```bash
cd automation/saleads-mi-negocio
SALEADS_LOGIN_URL="https://<current-environment-login-page>" npm test
```

Optional:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)

## Evidence generated

- Checkpoint screenshots are captured during the flow.
- A final JSON report is attached by Playwright as `mi-negocio-final-report.json`.
- Legal document URLs are captured in the report for:
  - `Términos y Condiciones`
  - `Política de Privacidad`
