# SaleADS - Mi Negocio full workflow test

This Playwright test covers the workflow named `saleads_mi_negocio_full_test`:

1. Login with Google
2. Open **Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate **Informacion General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Terminos y Condiciones** (same tab or popup)
9. Validate **Politica de Privacidad** (same tab or popup)
10. Emit a final PASS/FAIL report (`test-results/saleads-mi-negocio/final-report.json`)

## Environment-agnostic behavior

- No SaleADS domain is hardcoded.
- If `SALEADS_LOGIN_URL` is provided, the test navigates there.
- If `SALEADS_LOGIN_URL` is omitted, run it in a context where the browser is already at the SaleADS login page.

## Run

```bash
npm run test:saleads-mi-negocio
```

Optional headed mode:

```bash
npm run test:saleads-mi-negocio:headed
```

## Evidence generated

- Checkpoint screenshots are attached to Playwright output.
- Final report JSON includes PASS/FAIL by validation section.
- Legal page URLs are captured in the final report payload.
