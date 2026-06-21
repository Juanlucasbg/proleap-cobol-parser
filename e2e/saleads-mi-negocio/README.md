# SaleADS Mi Negocio E2E (Playwright)

Environment-agnostic Playwright test for validating the full **Mi Negocio** workflow after Google login.

## What this test covers

- Login using **Sign in with Google**.
- Continue through the full **Mi Negocio** workflow (does not stop after login).
- Validate:
  - `Mi Negocio` menu expansion.
  - `Agregar Negocio` modal content.
  - `Administrar Negocios` page sections.
  - `Información General`, `Detalles de la Cuenta`, and `Tus Negocios`.
  - Legal links for `Términos y Condiciones` and `Política de Privacidad`.
- Capture screenshots at required checkpoints.
- Generate a final PASS/FAIL JSON report for each requested validation area.

## Requirements

- Node.js 18+ (or newer).
- A reachable SaleADS environment URL from the execution environment.

## Setup

```bash
cd e2e/saleads-mi-negocio
npm install
npm run install:browsers
```

## Run

```bash
SALEADS_BASE_URL="https://your-saleads-environment-url" npm test
```

Optional headed execution:

```bash
SALEADS_BASE_URL="https://your-saleads-environment-url" npm run test:headed
```

## Outputs

- Screenshots and report files are stored in Playwright `test-results/` output.
- Final per-step report file: `final-report.json` (attached by Playwright and written in the test output folder).

## Notes

- The test intentionally avoids hardcoded domains and only uses runtime URL configuration.
- It prefers text-based selectors and waits for UI load after interactions.
