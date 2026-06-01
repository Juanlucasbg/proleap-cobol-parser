# SaleADS - Mi Negocio Full Workflow E2E

This folder contains an end-to-end Selenium test that validates the complete **Mi Negocio** workflow:

1. Login with Google.
2. Expand **Mi Negocio** menu.
3. Validate **Agregar Negocio** modal.
4. Open and validate **Administrar Negocios** view.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** (new tab or same tab).
9. Validate **Política de Privacidad** (new tab or same tab).
10. Generate final PASS/FAIL report.

## Why this is environment-agnostic

- It does **not** hardcode any SaleADS domain.
- The login page URL is provided at runtime.
- Selectors prioritize **visible text**.

## Runtime configuration

Required:

- `saleads.e2e.enabled=true`
- `saleads.login.url=<current environment login URL>`

Optional:

- `saleads.google.account=juanlucasbarbiergarzon@gmail.com` (default already set)
- `saleads.e2e.headed=true|false` (default `false`, headless)

Example:

```bash
mvn -Dtest=io.proleap.saleads.e2e.SaleadsMiNegocioWorkflowTest \
    -Dsaleads.e2e.enabled=true \
    -Dsaleads.login.url="https://<your-saleads-environment>/login" \
    test
```

## Evidence and report output

After execution:

- Screenshots: `target/saleads-e2e/screenshots/`
- Final report: `target/saleads-e2e/report.txt`

The final report includes:

- PASS/FAIL per requested validation field
- Final URL for **Términos y Condiciones**
- Final URL for **Política de Privacidad**
