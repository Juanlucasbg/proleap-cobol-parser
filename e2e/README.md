## SaleADS Mi Negocio E2E

Environment-agnostic Playwright workflow for:

- Logging in with Google
- Validating the full **Mi Negocio** module flow
- Capturing required screenshots and legal-page URLs
- Producing a final PASS/FAIL JSON report

### Prerequisites

- Node.js 18+ (Node 22 recommended)
- Browser already opened on the SaleADS login page **or** set `SALEADS_URL`
- Access to Google account selector entry `juanlucasbarbiergarzon@gmail.com`

### Install

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
```

### Run

If your runner already starts on the login page:

```bash
npm run test:saleads-mi-negocio
```

If you need the test to open the environment URL first:

```bash
SALEADS_URL="https://your-current-saleads-environment/login" npm run test:saleads-mi-negocio
```

### Outputs

- Screenshots: `e2e/artifacts/screenshots/*.png`
- Final report: `e2e/artifacts/saleads-mi-negocio-report.json`
- Playwright HTML report: `e2e/artifacts/playwright-report/index.html`
