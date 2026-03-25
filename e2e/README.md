## SaleADS E2E tests

This folder contains browser automation tests for SaleADS workflows.

### Prerequisites

- Node.js 18+ (the environment currently has Node 22)
- Install dependencies:

```bash
npm install
npx playwright install --with-deps chromium
```

### Run

```bash
npm run test:e2e
```

You can provide `SALEADS_BASE_URL` to navigate directly to a specific environment login page.  
If not provided, the test assumes the browser starts on the login page and uses `/` as default.

### Artifacts

- Screenshots and text evidence are saved in `e2e-artifacts/`
- HTML report is saved in `playwright-report/`
