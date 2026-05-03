# SaleADS Mi Negocio E2E

Playwright test suite to validate the full **Mi Negocio** workflow:

1. Login with Google
2. Open Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open Administrar Negocios
5. Validate Información General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Términos y Condiciones
9. Validate Política de Privacidad
10. Generate PASS/FAIL final report

## Environment-agnostic behavior

- The test does **not** hardcode any SaleADS domain.
- It starts from the current page context and uses visible text selectors.
- If legal links open in a new tab, it validates content, captures evidence, closes the tab, and returns to the app.

## Install

```bash
cd e2e
npm install
npm run install:browsers
```

## Run

```bash
cd e2e
npm test
```

Headed mode:

```bash
cd e2e
npm run test:headed
```

## Evidence output

- Checkpoint screenshots: `e2e/test-results/checkpoints/`
- Final report JSON: `e2e/test-results/mi-negocio-final-report.json`
