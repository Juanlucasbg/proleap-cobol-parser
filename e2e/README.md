# SaleADS Mi Negocio full workflow test

This folder contains an environment-agnostic Playwright script that validates the full `Mi Negocio` workflow, including:

- Login with Google.
- Sidebar navigation and `Mi Negocio` submenu.
- `Agregar Negocio` modal validations.
- `Administrar Negocios` account view validations.
- Legal links (`Terminos y Condiciones` and `Politica de Privacidad`) with popup/tab handling.
- Evidence capture (screenshots + JSON/Markdown report with PASS/FAIL per checkpoint).

## Run

```bash
npm install
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm run test:saleads:mi-negocio
```

### Optional environment variables

- `HEADED=true` to run headed browser mode.
- `SLOW_MO_MS=250` to slow down interactions.

## Outputs

Artifacts are written to:

`evidence/saleads-mi-negocio/<timestamp>/`

Including:

- Checkpoint screenshots.
- `final-report.json`
- `final-report.md`
