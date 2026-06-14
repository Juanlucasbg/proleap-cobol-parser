# SaleADS Mi Negocio E2E

This suite validates the full workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google
2. Open `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate account sections
6. Validate legal links (`Términos y Condiciones`, `Política de Privacidad`)
7. Emit a final PASS/FAIL report

## Environment-agnostic execution

The test does **not** hardcode any SaleADS domain. Provide the target login page URL at runtime:

```bash
SALEADS_BASE_URL="https://<current-environment>/login" npm run test:e2e:saleads
```

## Output artifacts

Execution artifacts are generated under:

- `artifacts/saleads-mi-negocio/` (screenshots + `final-report.json`)
- `playwright-report/` (Playwright HTML report)

You can override artifact output location with:

```bash
SALEADS_ARTIFACTS_DIR="/tmp/saleads-artifacts" npm run test:e2e:saleads
```
