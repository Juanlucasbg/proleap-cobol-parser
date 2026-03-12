# SaleADS E2E Automation

This repository now includes a Playwright workflow test:

- `e2e/saleads_mi_negocio_full_test.spec.js`

## Run

1. Install dependencies:

```bash
npm install
npx playwright install chromium
```

2. Provide an environment login URL (any SaleADS environment), or open the login page in your test harness before execution:

```bash
export SALEADS_LOGIN_URL="https://<your-saleads-environment>/login"
```

3. Execute:

```bash
npm run test:saleads:mi-negocio
```

## Notes

- The test does not hardcode a domain and is environment-agnostic.
- It prefers visible-text selectors.
- It captures checkpoint screenshots and a `final-report.json` attachment with PASS/FAIL per requested step and legal URLs.
