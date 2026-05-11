# SaleADS Mi Negocio E2E

Playwright suite that validates the complete **Mi Negocio** workflow:

1. Login with Google
2. Open Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open Administrar Negocios
5. Validate Información General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Términos y Condiciones
9. Validate Política de Privacidad
10. Emit final PASS/FAIL report

## Environment-agnostic usage

Do not hardcode domains. Pass the current environment login URL at runtime:

```bash
SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:mi-negocio
```

Optional alternatives supported by the test:

- `SALEADS_BASE_URL`
- `BASE_URL`

## Setup

```bash
npm install
npx playwright install chromium
```

## Run

```bash
npm run test:mi-negocio
```

## Evidence generated

The test captures screenshots at key checkpoints and also writes a structured JSON final report with:

- PASS/FAIL per required section
- error details for failed validations
- captured legal-page final URLs
- checkpoint screenshot paths
