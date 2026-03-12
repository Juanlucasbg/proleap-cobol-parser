# SaleADS Mi Negocio Full Workflow Test

This folder contains `saleads_mi_negocio_full_test`, a Playwright end-to-end test that:

1. Logs in with Google.
2. Validates the complete **Mi Negocio** sidebar and account workflow.
3. Validates **Términos y Condiciones** and **Política de Privacidad** pages (including popup/new-tab behavior).
4. Captures screenshots at required checkpoints.
5. Produces a final PASS/FAIL report for each requested section.

## Why this works across environments

- No hardcoded SaleADS domain is used.
- The test accepts `SALEADS_LOGIN_URL` (or `SALEADS_URL`) at runtime.
- If the browser session already starts on the login page, it can proceed without navigation.

## Setup

```bash
cd automation/saleads-mi-negocio
npx playwright install chromium
```

## Run

```bash
cd automation/saleads-mi-negocio
SALEADS_LOGIN_URL="https://<your-environment-login-url>" npm test
```

or headed mode:

```bash
cd automation/saleads-mi-negocio
SALEADS_LOGIN_URL="https://<your-environment-login-url>" npm run test:headed
```

## Outputs

- Screenshots and report artifacts are generated under Playwright `test-results`.
- Final report file: `saleads-mi-negocio-final-report.json`
- HTML report: `playwright-report/index.html`
