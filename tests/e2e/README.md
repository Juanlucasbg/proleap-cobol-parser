# SaleADS Mi Negocio E2E Workflow

This suite implements `saleads_mi_negocio_full_test` and validates:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal
4. Administrar Negocios page and sections
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones
9. Política de Privacidad

## Environment-agnostic execution

No domain is hardcoded. Provide one of these variables at runtime:

- `SALEADS_LOGIN_URL`
- `SALEADS_APP_URL`
- `SALEADS_BASE_URL`

Example:

```bash
SALEADS_LOGIN_URL="https://<current-env-domain>/login" npm run e2e:mi-negocio
```

Optional:

- `HEADLESS=false` to run with browser UI

## Install browsers

```bash
npm run e2e:install
```

## Run the workflow test

```bash
npm run e2e:mi-negocio
```

## View final report

```bash
npm run e2e:report
```

Artifacts:

- JSON report: `tests/e2e/artifacts/mi-negocio-latest-report.json`
- Screenshots: `tests/e2e/artifacts/*.png`
- Playwright HTML report: `playwright-report/`
