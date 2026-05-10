# SaleADS Mi Negocio workflow test

This script validates the full `Mi Negocio` workflow after Google login, including:

1. Login with Google
2. `Mi Negocio` menu expansion
3. `Agregar Negocio` modal validation
4. `Administrar Negocios` page validation
5. `Informacion General` validation
6. `Detalles de la Cuenta` validation
7. `Tus Negocios` validation
8. `Terminos y Condiciones` link validation
9. `Politica de Privacidad` link validation
10. Final PASS/FAIL report

## Requirements

- Node.js 18+
- Playwright Chromium browser:

```bash
npx playwright install chromium
```

## Run

Set the environment-specific login URL (no hardcoded domain in code):

```bash
export SALEADS_LOGIN_URL="https://<your-saleads-environment>/login"
```

Optional environment variables:

- `SALEADS_GOOGLE_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS` (`true` by default, set `false` for headed mode)
- `SLOW_MO` (default: `150`)

Run test:

```bash
npm test
```

Run in headed mode:

```bash
npm run test:headed
```

## Evidence output

Artifacts are generated under:

`e2e/saleads/artifacts/<timestamp>/`

- checkpoint screenshots
- `final-report.json` with PASS/FAIL per required report field
