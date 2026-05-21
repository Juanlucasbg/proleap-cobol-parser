# SaleADS E2E - Mi Negocio workflow

This Playwright suite validates the full "Mi Negocio" workflow in a SaleADS.ai environment:

1. Login with Google.
2. Open sidebar "Negocio" > "Mi Negocio".
3. Validate "Agregar Negocio" modal.
4. Open and validate "Administrar Negocios".
5. Validate "Información General", "Detalles de la Cuenta", and "Tus Negocios".
6. Validate legal links:
   - Términos y Condiciones
   - Política de Privacidad
7. Generate a final PASS/FAIL report.

## Environment-agnostic execution

No hardcoded domain is used. Provide the login page URL through environment variables:

- `SALEADS_LOGIN_URL` (preferred), or
- `SALEADS_BASE_URL`

The test also supports cases where legal links open in a new tab.

## Run

```bash
cd e2e/saleads
npm install
npx playwright install chromium
SALEADS_LOGIN_URL="https://<current-env-domain>/login" npm test
```

## Artifacts

Output is generated under `e2e/saleads/test-results/`, including:

- checkpoint screenshots,
- traces/videos on failure,
- `mi-negocio-final-report.json` with PASS/FAIL per required section and legal URLs.
