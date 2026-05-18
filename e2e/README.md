# SaleADS Mi Negocio full workflow test

This Playwright test automates the workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google
2. Open **Negocio > Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate all required account sections
6. Validate **Términos y Condiciones** and **Política de Privacidad**
7. Produce a final PASS/FAIL report

## Run

```bash
SALEADS_START_URL="https://<current-environment-login-page>" npm run test:e2e:saleads-mi-negocio
```

For headed mode:

```bash
SALEADS_START_URL="https://<current-environment-login-page>" npm run test:e2e:saleads-mi-negocio:headed
```

## Output

- Checkpoint screenshots: `artifacts/screenshots/`
- Final step-by-step report: `artifacts/saleads-mi-negocio-report.json`
- Playwright HTML report: `playwright-report/`

## Notes

- The test does **not** hardcode any SaleADS domain.
- It uses visible text-first selectors and waits after each click.
- If legal links open in a new tab, the test validates content, captures evidence, then returns to the app tab.
