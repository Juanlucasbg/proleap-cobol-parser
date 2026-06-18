# SaleADS UI workflow automation

This folder contains an environment-agnostic Playwright script for validating
the **SaleADS "Mi Negocio" full workflow**.

## Test implemented

- `saleads/mi-negocio-full-test.js`

It validates:

1. Login with Google (including optional account selector)
2. Sidebar > Negocio > Mi Negocio expansion
3. "Agregar Negocio" modal fields and actions
4. "Administrar Negocios" account page sections
5. "Información General"
6. "Detalles de la Cuenta"
7. "Tus Negocios"
8. "Términos y Condiciones" (same tab or popup)
9. "Política de Privacidad" (same tab or popup)

The script captures screenshots on important checkpoints and writes a structured
final report JSON with PASS/FAIL and legal URLs.

## Usage

From this `qa` directory:

```bash
npm install
npx playwright install
SALEADS_LOGIN_URL="https://your-current-saleads-environment/login" npm run saleads:mi-negocio:test
```

Notes:

- Do not hardcode a specific environment URL in code.
- The script uses `SALEADS_LOGIN_URL` only as runtime input.
- If the login flow shows a Google account selector, it will try to pick:
  `juanlucasbarbiergarzon@gmail.com`.
- Set `HEADLESS=false` to run with UI.

## Outputs

Each run writes evidence to:

- `saleads/evidence/<timestamp>/`

Including:

- Step screenshots (`*.png`)
- `final-report.json` with per-section PASS/FAIL and captured legal URLs
