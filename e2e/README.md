# SaleADS Mi Negocio full workflow test

This script validates the full "Mi Negocio" workflow using Playwright, including:

- Login with Google (or validating existing authenticated session),
- Sidebar and menu validations,
- "Agregar Negocio" modal validations,
- "Administrar Negocios" account page section validations,
- Legal links ("Términos y Condiciones" and "Política de Privacidad"),
- Screenshots at important checkpoints,
- Final PASS/FAIL report in JSON.

## Run

### Option A: Launch browser and navigate with environment URL

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-page>" npm run saleads:mi-negocio:test
```

### Option B: Reuse an already opened browser/session

If you have an existing browser available over CDP and already positioned on the login page:

```bash
PW_CDP_ENDPOINT="http://127.0.0.1:9222" npm run saleads:mi-negocio:test
```

## Optional environment variables

- `SALEADS_GOOGLE_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS=true|false` (default: `false`, only applies when launching browser directly)

## Artifacts

Output is saved under:

```text
artifacts/saleads-mi-negocio/<timestamp>/
```

Including:

- checkpoint screenshots (`*.png`)
- `final-report.json` with PASS/FAIL per validation area and captured legal URLs.
