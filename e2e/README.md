# SaleADS Mi Negocio E2E

This folder contains a Playwright automation for the `saleads_mi_negocio_full_test` workflow.

## Goals covered

- Login with Google (starting from current environment login page already open in browser context).
- Continue through the complete **Mi Negocio** workflow.
- Validate required UI sections and labels.
- Validate legal links (`Términos y Condiciones`, `Política de Privacidad`) in same tab or new tab.
- Capture screenshots at key checkpoints.
- Produce a PASS/FAIL summary by requested report fields.

## Environment-agnostic behavior

- No fixed SaleADS domain is hardcoded.
- Selectors prioritize visible text and semantic roles.
- URLs for legal pages are captured dynamically at runtime.

## Setup

From repository root:

```bash
cd e2e
npm install
npm run install:browsers
```

## Run

Headless:

```bash
npm run test:mi-negocio
```

Headed:

```bash
npm run test:mi-negocio:headed
```

## Notes for Google login

The test expects to run where Google session/account chooser is available.  
If an account chooser appears, it attempts to click:

- `juanlucasbarbiergarzon@gmail.com`

If the account is already authenticated and the app redirects directly, the test continues.

## Output artifacts

- Screenshots and traces are written under:
  - `e2e/test-results/`
  - `e2e/playwright-report/`
- The final PASS/FAIL report is attached as `final-report.json` in Playwright test output.
