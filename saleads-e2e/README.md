# SaleADS Mi Negocio - E2E Workflow

This Playwright test automates the full **SaleADS Mi Negocio** workflow, including:

1. Login with Google.
2. Expanding the **Mi Negocio** menu.
3. Validating the **Agregar Negocio** modal.
4. Opening **Administrar Negocios** and validating all account sections.
5. Validating **Términos y Condiciones** and **Política de Privacidad** pages.
6. Producing a final PASS/FAIL report by required validation block.

## Why this is environment-agnostic

- The test does **not hardcode any domain**.
- You provide the login page URL for the current environment through an environment variable.

## Requirements

- Node.js 18+
- A reachable SaleADS login page for the target environment

## Install

```bash
cd saleads-e2e
npm install
npx playwright install chromium
```

## Run

```bash
cd saleads-e2e
SALEADS_LOGIN_URL="https://<your-environment-login-url>" \
SALEADS_GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" \
npm run test:mi-negocio
```

## Output and evidence

- Screenshots are captured at major checkpoints.
- A JSON report (`final-report.json` attachment in Playwright output) includes:
  - PASS/FAIL for each required report field
  - captured final URLs for legal pages
  - failure details when any validation fails

## Notes

- The flow prefers selecting elements by visible text and roles.
- If legal links open a new tab, the test validates that tab and returns to the application.
