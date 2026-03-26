# SaleADS Mi Negocio E2E

This folder contains an environment-agnostic Playwright test for the full "Mi Negocio" workflow:

- Login with Google
- Navigate to `Negocio` -> `Mi Negocio`
- Validate `Agregar Negocio` modal
- Open and validate `Administrar Negocios` sections
- Validate legal links (`Términos y Condiciones`, `Política de Privacidad`) including new-tab handling
- Capture screenshots at important checkpoints
- Attach a final PASS/FAIL JSON report

## Prerequisites

- Node.js 20+ (recommended)
- npm

## Install

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
```

## Run

The test supports any environment and does not hardcode a domain.

Option A: Browser is already on SaleADS login page (manual setup)

```bash
cd e2e
npm run test:mi-negocio
```

Option B: Provide an environment URL via variable

```bash
cd e2e
SALEADS_BASE_URL="https://<current-saleads-environment>" npm run test:mi-negocio
```

## Outputs

- Checkpoint screenshots: `e2e/artifacts/screenshots/`
- Playwright artifacts (on failures): `e2e/test-results/`
- HTML report: `e2e/playwright-report/`
- Final structured report attachment: `mi-negocio-final-report.json`
