# SaleADS Mi Negocio Full Workflow Test

This Playwright runner validates the full `Mi Negocio` workflow after Google login, including:

- Login with Google
- Sidebar and menu expansion checks
- `Agregar Negocio` modal validation
- `Administrar Negocios` page validation
- `Informacion General`, `Detalles de la Cuenta`, and `Tus Negocios` checks
- Legal links (`Terminos y Condiciones`, `Politica de Privacidad`) with new-tab/same-tab handling
- Evidence capture (screenshots + legal page final URLs)
- Final PASS/FAIL report for each required section

## Run

1. Install dependencies (already handled if `npm install` ran at repo root):

```bash
npm install
```

2. Install Playwright browser binaries:

```bash
npm run saleads:install-browsers
```

3. Run the test against any SaleADS environment (no hardcoded domain):

```bash
SALEADS_URL="https://<your-saleads-environment>/login" npm run saleads:mi-negocio:test
```

Optional variables:

- `HEADLESS=false` to run headed
- `GOOGLE_ACCOUNT_EMAIL=juanlucasbarbiergarzon@gmail.com` (defaults to this email)

Example headed run:

```bash
SALEADS_URL="https://<your-saleads-environment>/login" npm run saleads:mi-negocio:test:headed
```

## Artifacts

Each run writes artifacts under:

`artifacts/saleads_mi_negocio_full_test/<timestamp>/`

Includes:

- checkpoint screenshots
- failure screenshots (when applicable)
- `final-report.json` with PASS/FAIL per required report field
