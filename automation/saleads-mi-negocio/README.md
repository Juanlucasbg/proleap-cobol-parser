# SaleADS Mi Negocio full workflow test

This folder contains a Playwright-based workflow runner for validating the full **Mi Negocio** module flow after Google login.

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

It also:

- waits for UI load after each click,
- prefers visible text selectors,
- handles legal links opening in same tab or new tab,
- captures screenshots at required checkpoints,
- stores legal final URLs in the output report.

## Setup

```bash
npm install
npx playwright install chromium
```

## Run

```bash
SALEADS_LOGIN_URL="https://your-current-saleads-environment/login" npm run test:saleads-mi-negocio
```

Optional environment variables:

- `HEADLESS=false` (default is `true`)
- `SALEADS_ARTIFACTS_DIR=/custom/path/for/reports`

## Output

After each run, artifacts are generated under:

`artifacts/saleads-mi-negocio-<timestamp>/`

Including:

- `report.json` with step-level PASS/FAIL and details
- `summary.md` with a quick table summary
- `screenshots/*.png` for all required checkpoints
