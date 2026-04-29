# SaleADS Mi Negocio full workflow test

This package contains the test:

- `io.proleap.saleads.SaleadsMiNegocioWorkflowTest`

## What it validates

The test automates the full workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google.
2. Open `Negocio` -> `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (same tab or new tab).
9. Validate `Política de Privacidad` (same tab or new tab).
10. Emit final PASS/FAIL report.

It captures screenshots at key checkpoints and writes a final report with step statuses and legal URLs.

## Runtime configuration

Use system properties to avoid hard-coding a specific environment URL:

- `saleads.startUrl` (optional): Login page URL for the current environment.
- `saleads.browser` (optional): `chrome` (default), `firefox`, or `edge`.
- `saleads.headless` (optional): `true` (default) or `false`.
- `saleads.timeout.seconds` (optional): explicit wait timeout, default `30`.

## Example command

```bash
mvn -Dtest=io.proleap.saleads.SaleadsMiNegocioWorkflowTest \
    -Dsaleads.startUrl="https://<current-env-login-url>" \
    -Dsaleads.browser=chrome \
    -Dsaleads.headless=false \
    test
```

## Artifacts

Artifacts are generated under:

- `target/saleads-e2e-artifacts/<timestamp>/`

including:

- checkpoint screenshots (`*.png`)
- `final-report.txt` with PASS/FAIL per required report field.
