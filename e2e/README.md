# SaleADS E2E Workflows

## `saleads_mi_negocio_full_test`

This workflow automates:

1. Login with Google.
2. Mi Negocio menu validation.
3. Agregar Negocio modal validation.
4. Administrar Negocios page validation.
5. Información General, Detalles de la Cuenta, and Tus Negocios checks.
6. Términos y Condiciones and Política de Privacidad legal link validation (popup or same-tab).
7. Evidence capture with screenshots and final PASS/FAIL report.

### Run

```bash
cd e2e
npm install
SALEADS_LOGIN_URL="https://your-current-saleads-login-page" npm run saleads:mi-negocio
```

### Environment Variables

- `SALEADS_LOGIN_URL` (required): Login page URL for the current SaleADS environment.
- `HEADLESS` (optional): Set `HEADLESS=false` to run headed.

### Artifacts

Each execution stores outputs at:

`e2e/artifacts/<timestamp>/`

- `screenshots/*.png`
- `final-report.json`
