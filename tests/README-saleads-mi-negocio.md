# SaleADS Mi Negocio Full Workflow E2E

This Playwright test automates the full `saleads_mi_negocio_full_test` flow:

1. Login with Google.
2. Open and validate `Mi Negocio` menu.
3. Validate `Agregar Negocio` modal.
4. Open and validate `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (same tab or new tab).
9. Validate `Política de Privacidad` (same tab or new tab).
10. Build a final PASS/FAIL report.

## Environment-agnostic configuration

No domain is hardcoded in the spec.

- `SALEADS_START_URL` (recommended): full login URL for the current environment.
- `SALEADS_BASE_URL` (optional): base URL if you prefer navigating from a root domain.

If both are set, `SALEADS_START_URL` is used.

## Run locally

```bash
npm install
npx playwright install --with-deps
SALEADS_START_URL="https://<current-env>/login" npm run test:saleads-mi-negocio
```

For headed mode:

```bash
SALEADS_START_URL="https://<current-env>/login" npm run test:saleads-mi-negocio:headed
```

## Evidence generated

The test stores evidence in Playwright artifacts:

- Dashboard screenshot after successful login
- Expanded menu screenshot
- Modal screenshot
- Full account page screenshot
- Terms and Privacy screenshots
- JSON final report attachment with PASS/FAIL statuses and final legal URLs
