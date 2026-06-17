# SaleADS Mi Negocio Full Workflow E2E

This folder contains an opt-in Playwright-for-Java test:

- `SaleadsMiNegocioFullWorkflowTest`

## Purpose

Validate the complete `Mi Negocio` flow:

1. Login with Google
2. Open `Mi Negocio` menu
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Información General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Términos y Condiciones` (+ URL capture)
9. Validate `Política de Privacidad` (+ URL capture)
10. Emit final PASS/FAIL report

## Environment-agnostic execution

No domain is hardcoded. Provide the login URL of the active environment through env vars.

```bash
export SALEADS_E2E_ENABLED=true
export SALEADS_START_URL="https://<current-env-login-url>"
export SALEADS_HEADLESS=false
```

Then run:

```bash
mvn -Dtest=SaleadsMiNegocioFullWorkflowTest test
```

## Evidence output

Evidence is written to:

- `target/saleads-evidence/<timestamp>/`

Including:

- Checkpoint screenshots (`*.png`)
- Final report (`final-report.txt`) with required report fields and legal URLs
