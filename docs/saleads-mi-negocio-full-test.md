# SaleADS - Mi Negocio Full Workflow Test

This repository now includes an environment-agnostic browser automation script:

- Test name: `saleads_mi_negocio_full_test`
- Script: `scripts/saleads_mi_negocio_full_test.mjs`

## What it validates

The script automates the full workflow requested for SaleADS:

1. Login with Google (including optional account picker selection for `juanlucasbarbiergarzon@gmail.com`).
2. Open **Negocio > Mi Negocio** and validate submenu expansion.
3. Open and validate **Agregar Negocio** modal.
4. Open **Administrar Negocios** and validate all required account sections.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** (including new tab handling and URL capture).
9. Validate **Política de Privacidad** (including new tab handling and URL capture).
10. Produce final PASS/FAIL report by step.

The test captures screenshots at all required checkpoints and writes a JSON final report.

## Run in any SaleADS environment

No domain is hardcoded. Pass the login URL for the target environment through an environment variable:

```bash
SALEADS_LOGIN_URL="https://<your-saleads-environment>/login" npm run saleads:mi-negocio:test
```

Optional configuration:

- `HEADLESS=false` to run with visible browser.
- `SALEADS_EVIDENCE_DIR=/absolute/path` to customize evidence output directory.

## One-time browser installation

```bash
npm run saleads:mi-negocio:install-browsers
```

## Evidence and report

By default, evidence is stored under:

`artifacts/saleads_mi_negocio_full_test/<timestamp>/`

Including:

- checkpoint screenshots (`*.png`)
- `final-report.json` with:
  - PASS/FAIL status for each required step
  - captured final URL for Terms and Privacy pages
