# SaleADS Mi Negocio Full Workflow Test

This folder contains `saleads_mi_negocio_full_test.py`, a browser E2E test that validates:

1. Google login entry point.
2. Mi Negocio menu expansion.
3. Agregar Negocio modal.
4. Administrar Negocios account page.
5. Informacion General fields.
6. Detalles de la Cuenta fields.
7. Tus Negocios fields.
8. Terminos y Condiciones legal page.
9. Politica de Privacidad legal page.
10. Final PASS/FAIL report per validation area.

## Key behavior

- No domain is hardcoded.
- Selectors are text-first.
- The test waits for UI load after each click.
- New-tab legal links are validated and the script returns to the app context.
- Screenshots are captured at required checkpoints and on failures.
- Final output includes legal URLs and a per-step status report.

## Install

```bash
python3 -m pip install -r automation/tests/requirements.txt
python3 -m playwright install chromium
```

## Run options

Use one of these modes:

1. Start from a URL (any environment):

```bash
python3 automation/tests/saleads_mi_negocio_full_test.py \
  --start-url "https://<current-saleads-env>/login"
```

2. Attach to an existing browser session that is already on login page:

```bash
python3 automation/tests/saleads_mi_negocio_full_test.py \
  --cdp-endpoint "http://127.0.0.1:9222"
```

Artifacts are written under:

`artifacts/saleads_mi_negocio_full_test/<timestamp>/`
