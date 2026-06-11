# SaleADS Mi Negocio E2E workflow

This folder contains a Playwright test that validates the full **Mi Negocio** workflow:

- Google login
- Sidebar navigation to **Mi Negocio**
- **Agregar Negocio** modal validation
- **Administrar Negocios** sections
- Legal links for **Términos y Condiciones** and **Política de Privacidad**
- Screenshot checkpoints and final PASS/FAIL report

## Run

1. Install browser binaries once:

```bash
npx playwright install
```

2. Run the workflow against any environment login URL:

```bash
SALEADS_URL="https://<current-environment-login-url>" npm run test:saleads-mi-negocio
```

Optional:

- `HEADLESS=false` to run with a visible browser.
- `npm run test:saleads-mi-negocio:headed` for interactive observation.

## Artifacts

- Final step report: `artifacts/saleads-mi-negocio/final-report.json`
- Screenshots: `artifacts/saleads-mi-negocio/screenshots/`
- Playwright HTML report: `playwright-report/`
