# SaleADS Mi Negocio workflow automation

This script executes the full `saleads_mi_negocio_full_test` workflow using Selenium and writes evidence artifacts (screenshots + reports) under `target/saleads-mi-negocio/<timestamp>/`.

## What it validates

1. Login with Google and dashboard/sidebar visibility.
2. `Negocio > Mi Negocio` menu expansion.
3. `Agregar Negocio` modal content (`Crear Nuevo Negocio`, `Nombre del Negocio`, quota text, action buttons).
4. `Administrar Negocios` account page sections.
5. `Información General` section fields.
6. `Detalles de la Cuenta` section fields.
7. `Tus Negocios` section fields.
8. `Términos y Condiciones` navigation, legal content, final URL, and tab return.
9. `Política de Privacidad` navigation, legal content, final URL, and tab return.

## Install

```bash
python3 -m pip install -r scripts/requirements-saleads-e2e.txt
```

## Run

The test is environment-agnostic and requires an explicit login URL for the target SaleADS environment:

```bash
python3 scripts/saleads_mi_negocio_full_test.py \
  --login-url "https://<current-saleads-environment>/login" \
  --headless
```

Optional arguments:

- `--email` (default: `juanlucasbarbiergarzon@gmail.com`)
- `--timeout-seconds` (default: `20`)
- `--artifacts-dir` (default: `target/saleads-mi-negocio`)

## Outputs

- `report.json`: structured PASS/FAIL data for each required step.
- `report.md`: human-readable summary table with evidence paths.
- `*.png`: checkpoint and failure screenshots.

Exit code:

- `0` when all workflow validations pass.
- `1` when one or more steps fail.
