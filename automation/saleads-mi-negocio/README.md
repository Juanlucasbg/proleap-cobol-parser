# SaleADS Mi Negocio Full Workflow Test

This folder contains an environment-agnostic Playwright automation for:

- Login with Google
- Mi Negocio menu checks
- Agregar Negocio modal checks
- Administrar Negocios view checks
- Información General / Detalles de la Cuenta / Tus Negocios checks
- Términos y Condiciones + Política de Privacidad checks (same-tab or new-tab)
- Screenshot capture at key checkpoints
- Final PASS/FAIL report output

## Prerequisites

- Node.js 18+ (recommended)
- Internet access for SaleADS and Google auth flows

## Install

```bash
cd automation/saleads-mi-negocio
npm install
```

## Run

Use a runtime URL so this works in any SaleADS environment:

```bash
SALEADS_LOGIN_URL="https://<your-env-login-url>" npm run test:mi-negocio
```

Or pass URL as argument:

```bash
npm run test:mi-negocio -- --url="https://<your-env-login-url>"
```

Optional:

- `HEADLESS=false` to run with visible browser

## Output

Artifacts are written to:

`automation/saleads-mi-negocio/artifacts/<run-id>/`

- `final-report.json`
- `final-report.md`
- checkpoint screenshots (`*.png`)
