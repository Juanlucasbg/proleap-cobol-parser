## SaleADS E2E: Mi Negocio Full Workflow

This repository now includes a Playwright test named:

- `tests/saleads_mi_negocio_full_test.spec.js`

### What it validates

The script automates the full workflow requested for the Mi Negocio module:

1. Login with Google (including optional account picker selection).
2. Open and validate the Mi Negocio menu.
3. Open and validate the Agregar Negocio modal.
4. Open Administrar Negocios and validate account sections.
5. Validate Informacion General content.
6. Validate Detalles de la Cuenta.
7. Validate Tus Negocios.
8. Validate Terminos y Condiciones (same tab or new tab).
9. Validate Politica de Privacidad (same tab or new tab).
10. Generate a PASS/FAIL final report.

### Evidence generated

The test captures screenshots at important checkpoints and attaches a final JSON report with:

- PASS/FAIL by workflow area
- Captured legal URLs
- Validation errors (if any)

### Run

1. Install dependencies:

```bash
npm install
```

2. Run the test (set environment URL dynamically):

```bash
SALEADS_LOGIN_URL="https://your-current-saleads-login-url" npm run test:e2e:saleads-mi-negocio
```

> The test does not hardcode any specific SaleADS domain and is designed to run against dev, staging, or production by changing `SALEADS_LOGIN_URL`.
