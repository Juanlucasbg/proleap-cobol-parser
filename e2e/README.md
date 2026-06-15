# SaleADS Mi Negocio E2E

This folder contains the cross-environment Playwright test:

- `saleads-mi-negocio.full.spec.js`

## Run

1. Install dependencies:

```bash
npm install
npx playwright install --with-deps chromium
```

2. Provide the environment entry URL (no hardcoded domain in test):

```bash
export SALEADS_LOGIN_URL="https://<your-saleads-environment>/login"
```

3. Execute:

```bash
npm run test:e2e:saleads-mi-negocio
```

## Notes

- The test uses visible-text selectors whenever possible.
- It validates both in-tab navigation and new-tab behavior for legal links.
- Checkpoints are captured as screenshots and URLs are attached as test artifacts.
