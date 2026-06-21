## SaleADS Mi Negocio full workflow test

This repository now includes a Playwright E2E test for the workflow:

- Google login
- `Negocio` -> `Mi Negocio` expansion
- `Agregar Negocio` modal validation
- `Administrar Negocios` page validation
- Legal links (`Términos y Condiciones` and `Política de Privacidad`)
- Final PASS/FAIL JSON report

### Run

1. Install browser binaries:

```bash
npm run playwright:install
```

2. Run the test against the current environment login URL:

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm run test:e2e:saleads-mi-negocio
```

### Evidence generated

- Checkpoint screenshots are saved under Playwright test output.
- Final JSON report file: `saleads-mi-negocio-final-report.json`.
