# SaleADS Mi Negocio Full Workflow Test

This Playwright script automates the complete `Mi Negocio` workflow validation requested in `saleads_mi_negocio_full_test`.

## What it validates

1. Login with Google and app shell visibility.
2. Expansion of `Mi Negocio` menu and submenu options.
3. `Agregar Negocio` modal fields/buttons/text.
4. `Administrar Negocios` account page sections.
5. `Informacion General` content checks.
6. `Detalles de la Cuenta` content checks.
7. `Tus Negocios` content checks.
8. `Terminos y Condiciones` link behavior/content/URL.
9. `Politica de Privacidad` link behavior/content/URL.
10. Final PASS/FAIL report per section.

The test is cross-environment by design: it does not hardcode any SaleADS domain and accepts the target URL via environment variable or CLI arg.

## Install

```bash
npm install
```

## Run

```bash
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm test
```

You can also pass the URL as the first argument:

```bash
npm test -- "https://<your-saleads-env>/login"
```

Run headed browser mode:

```bash
HEADLESS=false SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm run test:headed
```

## Optional environment variables

- `SALEADS_LOGIN_URL` (preferred start URL)
- `SALEADS_APP_URL` (fallback URL)
- `BASE_URL` (fallback URL)
- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `GOOGLE_ACCOUNT_NAME` (optional explicit user name assertion)
- `SALEADS_ARTIFACTS_DIR` (default: `./artifacts/<timestamp>`)
- `HEADLESS` (`true` by default)

## Evidence artifacts

The run stores:

- checkpoint screenshots
- legal destination URLs
- final structured report JSON

at:

`artifacts/<timestamp>/saleads_mi_negocio_report.json`
