# SaleADS Mi Negocio Full Workflow Test

This package contains an environment-agnostic Playwright script for validating the full **Mi Negocio** workflow in SaleADS.ai, including:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal checks
4. Administrar Negocios page checks
5. Información General checks
6. Detalles de la Cuenta checks
7. Tus Negocios checks
8. Términos y Condiciones validation (+ URL capture)
9. Política de Privacidad validation (+ URL capture)

## Install

```bash
npm install
npx playwright install chromium
```

## Run

```bash
SALEADS_LOGIN_URL="https://<current-environment-login>" npm run test:saleads-mi-negocio
```

Optional environment variables:

- `HEADLESS=false` to watch browser execution
- `PW_SLOW_MO=200` to slow interactions
- `SALEADS_GOOGLE_ACCOUNT=<email>` to override default Google account selection

## Output

- Screenshots and report are written to:
  - `artifacts/saleads_mi_negocio_full_test-<timestamp>/`
- Final validation matrix is printed as JSON and saved as `report.json`
- Exit code is:
  - `0` if all required validations pass
  - `1` if any validation fails
