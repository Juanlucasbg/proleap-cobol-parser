# SaleADS Mi Negocio workflow E2E

This Playwright test automates the full `saleads_mi_negocio_full_test` workflow:

1. Login with Google.
2. Open and validate the **Mi Negocio** menu.
3. Validate the **Agregar Negocio** modal.
4. Open and validate **Administrar Negocios**.
5. Validate:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Terminos y Condiciones
   - Politica de Privacidad
7. Generate a final PASS/FAIL JSON report.

## Run

Install browser binaries once:

```bash
npm run playwright:install
```

Run the test:

```bash
SALEADS_LOGIN_URL="https://<current-saleads-env-login>" npm run test:saleads:mi-negocio
```

## Notes

- The test does not hardcode a specific SaleADS domain.
- Provide the environment login URL through `SALEADS_LOGIN_URL` (or `SALEADS_BASE_URL`).
- Screenshots and report artifacts are written to `test-results/`.
