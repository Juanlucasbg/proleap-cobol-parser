# SaleADS Mi Negocio Full Workflow Test

This automation implements the `saleads_mi_negocio_full_test` end-to-end flow:

1. Login with Google.
2. Open **Negocio -> Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate **Informacion General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Terminos y Condiciones** (including new-tab handling).
9. Validate **Politica de Privacidad** (including new-tab handling).
10. Produce PASS/FAIL report per requested field.

The script does not hardcode any SaleADS domain. Provide the login URL for the current environment at runtime.

## Run

```bash
npm run saleads:mi-negocio -- --url "https://<current-environment-login-url>"
```

Optional:

```bash
npm run saleads:mi-negocio -- --url "https://<url>" --headed
```

Or with environment variable:

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm run saleads:mi-negocio
```

## Artifacts

Each run writes artifacts under:

`automation/artifacts/saleads_mi_negocio_full_test-<timestamp>/`

Including:

- Screenshots at important checkpoints.
- `final-report.json` with:
  - PASS/FAIL status for each required report field.
  - Validation details.
  - Final URLs captured for legal pages.
