# SaleADS Mi Negocio E2E

Playwright test suite for validating the full SaleADS `Mi Negocio` workflow, including:

- Google login (or already-authenticated session handling)
- Sidebar navigation validation
- `Agregar Negocio` modal validation
- `Administrar Negocios` sections validation
- Legal links (`Terminos y Condiciones`, `Politica de Privacidad`) with tab handling
- Screenshot checkpoints
- Final PASS/FAIL JSON report

## Setup

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
```

## Environment-agnostic execution

Do not hardcode domain URLs. Use environment variables:

- `SALEADS_BASE_URL` (recommended)
- `SALEADS_LOGIN_URL` (optional explicit login URL override)

Example:

```bash
SALEADS_BASE_URL="https://your-saleads-env.example.com" npm run test:saleads:mi-negocio
```

If your runner opens the login page before executing the test, the script also supports starting from the current page.

## Outputs

Playwright outputs include:

- HTML report
- Trace/video on failure
- Checkpoint screenshots
- `saleads-mi-negocio-report.json` with PASS/FAIL by required step
