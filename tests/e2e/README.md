# SaleADS E2E tests

This folder contains browser E2E tests built with Playwright.

## Mi Negocio full workflow test

Test file:

- `tests/e2e/saleads.mi-negocio.full.spec.js`

### Run

```bash
npx playwright install
SALEADS_LOGIN_URL="https://<your-saleads-login-url>" npm run test:e2e:saleads-mi-negocio
```

Notes:

- The test is environment-agnostic: it does not hardcode a specific SaleADS domain.
- If the browser starts on `about:blank`, provide `SALEADS_LOGIN_URL` (or `SALEADS_BASE_URL` / `BASE_URL`).
- Evidence is captured in Playwright output (`test-results`) including screenshots and `final-report.json`.
