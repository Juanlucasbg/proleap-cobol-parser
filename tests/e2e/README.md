# SaleADS E2E Automation

This folder contains the `saleads_mi_negocio_full_test` Playwright test.

## What it validates

- Google login flow (and account selection when shown).
- Left navigation and **Mi Negocio** submenu behavior.
- **Agregar Negocio** modal content and buttons.
- **Administrar Negocios** sections:
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Sección Legal
- Legal links:
  - Términos y Condiciones
  - Política de Privacidad
- A final PASS/FAIL report in JSON format.

## Environment agnostic execution

No domain is hardcoded in the test.

Set the login URL for the environment you want to validate:

```bash
export SALEADS_URL="https://<your-current-saleads-environment>/login"
```

## Run

```bash
npm run test:e2e
```

Headed mode:

```bash
npm run test:e2e:headed
```

## Evidence generated

- Checkpoint screenshots are saved in Playwright test output.
- `final-report.json` is attached to the test run output and contains:
  - PASS/FAIL per requested validation block
  - Captured final URLs for legal pages
  - Any collected errors
