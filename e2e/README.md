# SaleADS E2E - Mi Negocio Full Workflow

This folder contains the Playwright test:

- `tests/saleads-mi-negocio.spec.ts`

It automates the full workflow requested by `saleads_mi_negocio_full_test`:

1. Login with Google.
2. Expand `Negocio` -> `Mi Negocio`.
3. Validate the `Agregar Negocio` modal.
4. Open and validate `Administrar Negocios`.
5. Validate account sections.
6. Validate legal links, including new-tab handling.
7. Produce a final per-step PASS/FAIL report.

## Environment-agnostic setup

No SaleADS domain is hardcoded. Provide the login page URL for the target environment using:

- `SALEADS_LOGIN_URL`

Example:

```bash
cd /workspace/e2e
SALEADS_LOGIN_URL="https://<current-env>/login" npm test
```

## Notes

- The script prefers text-based and role-based selectors.
- The script waits for UI load after click actions.
- Screenshots are captured at key checkpoints and on failures.
- The final JSON report is written to the Playwright test output directory as `final-report.json`.
