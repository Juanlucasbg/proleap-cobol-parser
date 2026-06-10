# SaleADS E2E Workflow Tests

This folder contains environment-agnostic Playwright tests for SaleADS.ai flows.

## Implemented workflow

- `tests/saleads-mi-negocio.spec.ts`
  - Login with Google
  - Validate the full **Mi Negocio** workflow
  - Capture screenshots at important checkpoints
  - Validate legal links (same tab or new tab)
  - Emit a PASS/FAIL final report per validation area

## Prerequisites

- Node.js 18+
- Browsers installed for Playwright

## Install

```bash
cd e2e
npm install
npx playwright install chromium
```

## Run

Set an environment-specific login URL at runtime (no hardcoded domain):

```bash
cd e2e
SALEADS_LOGIN_URL="https://your-saleads-env.example.com/login" npm run test:saleads:mi-negocio
```

You can also use `SALEADS_BASE_URL` if preferred:

```bash
cd e2e
SALEADS_BASE_URL="https://your-saleads-env.example.com/login" npm run test:saleads:mi-negocio
```

## Artifacts

- Screenshot checkpoints are generated in Playwright's test output directory.
- HTML report:

```bash
cd e2e
npm run report
```
