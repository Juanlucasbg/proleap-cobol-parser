# SaleADS Mi Negocio E2E

This folder contains the Playwright end-to-end test:

- `saleads-mi-negocio-full.spec.ts`

## Goal

Validate the full **SaleADS.ai Mi Negocio workflow**:

1. Login with Google
2. Open `Negocio` -> `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate:
   - Información General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Términos y Condiciones
   - Política de Privacidad

The test captures screenshots at key checkpoints and emits a final PASS/FAIL report artifact.

## Environment-agnostic execution

The test does **not** hardcode any SaleADS domain.

Provide the login page URL dynamically:

```bash
SALEADS_URL="https://<your-saleads-environment>/login" npm run test:e2e
```

Alternative env var names supported:

- `SALEADS_LOGIN_URL`
- `PLAYWRIGHT_BASE_URL`

If none are provided, the test expects the browser to already be on the SaleADS login page.

## Notes

- The script prefers text-based selectors (visible labels and button/link text).
- If legal links open a new tab, the test validates content there and returns to the app tab.
- Google account selector handling targets:
  - `juanlucasbarbiergarzon@gmail.com`
