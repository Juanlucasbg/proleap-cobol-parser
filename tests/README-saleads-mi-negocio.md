## SaleADS Mi Negocio E2E workflow

This repository now includes a standalone Playwright test that automates:

1. Login with Google
2. Mi Negocio menu checks
3. Agregar Negocio modal validation
4. Administrar Negocios sections validation
5. Información General validation
6. Detalles de la Cuenta validation
7. Tus Negocios validation
8. Términos y Condiciones validation (same tab or new tab)
9. Política de Privacidad validation (same tab or new tab)
10. Final PASS/FAIL report generation

### Test file

- `tests/saleads-mi-negocio.spec.js`

### Environment-agnostic behavior

- The test does **not** hardcode a domain.
- If `SALEADS_BASE_URL` (or `BASE_URL`) is provided, the test navigates there.
- If no URL is provided, it uses the currently opened page context.

### Install

```bash
npm install
npx playwright install chromium
```

### Run

```bash
SALEADS_BASE_URL="https://<your-saleads-environment>" npm run test:saleads:mi-negocio
```

### Headed mode

```bash
HEADED=true SALEADS_BASE_URL="https://<your-saleads-environment>" npm run test:saleads:mi-negocio:headed
```

### Artifacts

Execution artifacts are stored in:

- `test-results/saleads-mi-negocio/<timestamp>/`

Generated evidence includes:

- checkpoint screenshots
- legal pages screenshots
- `final-report.json` with PASS/FAIL by step and final legal URLs
