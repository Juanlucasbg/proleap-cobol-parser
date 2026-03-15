# SaleADS Mi Negocio Full Test

This folder contains the automation script `saleads_mi_negocio_full_test` that validates the full **Mi Negocio** workflow after Google login, including legal links and evidence capture.

## What it validates

The script executes and reports PASS/FAIL for:

1. Login
2. Mi Negocio menu
3. Agregar Negocio modal
4. Administrar Negocios view
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones
9. Política de Privacidad

It always captures screenshots at key checkpoints and writes a `final_report.json` artifact.

## Usage

From this folder:

```bash
npm install
npm run install:browsers
npm run test:saleads-mi-negocio -- --url "https://<your-current-saleads-environment-login-page>"
```

## Runtime options

- `--url "<login-url>"`: login page URL for the current environment.
- `--headless`: run without a visible browser.
- `--headed`: force headed mode.

## Environment variables

- `SALEADS_LOGIN_URL`: default URL if `--url` is omitted.
- `SALEADS_GOOGLE_ACCOUNT`: defaults to `juanlucasbarbiergarzon@gmail.com`.
- `HEADLESS=true`: run headless.
- `SALEADS_SLOW_MO`: milliseconds delay between browser actions (default `120`).

## Artifacts

Artifacts are stored in:

`artifacts/saleads_mi_negocio_full_test/<timestamp>/`

Including:

- checkpoint screenshots (`*.png`)
- `final_report.json`
