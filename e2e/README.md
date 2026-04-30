## SaleADS Mi Negocio workflow E2E

This Playwright suite validates the full `saleads_mi_negocio_full_test` workflow:

1. Login with Google
2. Open and validate `Mi Negocio` menu
3. Validate `Agregar Negocio` modal
4. Open and validate `Administrar Negocios`
5. Validate `Informacion General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Terminos y Condiciones`
9. Validate `Politica de Privacidad`
10. Emit final PASS/FAIL report per required field

### Environment-agnostic behavior

- The test does not hardcode a SaleADS domain.
- If `SALEADS_BASE_URL` (or `BASE_URL`) is defined, it navigates there.
- If no URL is configured, it assumes the browser session is already positioned on the login page as required.

### Run

```bash
cd e2e
npm install
npx playwright install --with-deps
SALEADS_BASE_URL="https://<current-environment-host>" npm run test:mi-negocio
```

### Evidence and report

- Important checkpoints are captured as screenshots and attached to the Playwright report.
- Legal link steps capture final URLs in the JSON report details.
- Final per-step statuses are written to:
  - Playwright console output
  - `saleads-mi-negocio-final-report.json` test artifact
