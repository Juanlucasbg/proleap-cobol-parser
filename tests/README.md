# SaleADS E2E Workflow Test

This folder contains an end-to-end Playwright test for the **Mi Negocio** workflow:

- `saleads-mi-negocio.spec.js`

## Run

1. Install browsers (one-time):

```bash
npm run playwright:install
```

2. Execute the test:

```bash
SALEADS_START_URL="https://<current-environment-login-page>" npm run test:e2e:saleads-mi-negocio
```

## Environment variables

- `SALEADS_START_URL` (preferred): login page for the environment under test.
- `SALEADS_URL` or `BASE_URL`: accepted fallbacks.
- `PW_HEADLESS=false`: run headed mode.

The test does not hardcode any SaleADS domain and relies on visible-text selectors.
