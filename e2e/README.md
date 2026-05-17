# SaleADS Mi Negocio E2E

This folder contains an end-to-end Playwright test for the full **Mi Negocio** workflow:

- Google login (including optional account selector)
- Sidebar navigation validation
- `Mi Negocio` menu expansion
- `Agregar Negocio` modal validation
- `Administrar Negocios` page and section checks
- Legal links validation (`Términos y Condiciones`, `Política de Privacidad`)
- Screenshot checkpoints and JSON PASS/FAIL report

## Prerequisites

1. Node.js 18+.
2. Install dependencies:

```bash
cd e2e
npm install
```

3. Install Playwright browser binaries (first run only):

```bash
npx playwright install --with-deps chromium
```

## Run

Provide the login page URL for the current environment via env var (keeps the test environment-agnostic and avoids hardcoded domains):

```bash
cd e2e
SALEADS_LOGIN_URL="https://<current-environment>/login" npm run test:saleads:mi-negocio
```

Optional headed run:

```bash
cd e2e
SALEADS_LOGIN_URL="https://<current-environment>/login" npm run test:saleads:headed
```

## Artifacts

Generated under `e2e/artifacts/`:

- `saleads_mi_negocio_full_report.json` (final PASS/FAIL report by section)
- `screenshots/*.png` (checkpoint evidence)
- `playwright-results.json`
- `playwright-report/` (HTML report)
