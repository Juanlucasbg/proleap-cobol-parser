# SaleADS E2E - Mi Negocio workflow

This Playwright suite validates the full **Mi Negocio** workflow described in the automation task:

- Google login flow (and optional account selection),
- sidebar navigation to **Negocio -> Mi Negocio**,
- **Agregar Negocio** modal validations,
- **Administrar Negocios** account sections,
- legal links (**Términos y Condiciones** and **Política de Privacidad**),
- screenshot evidence at each checkpoint,
- final PASS/FAIL report per required field.

## Prerequisites

- Node.js 18+ (tested with Node 22)
- Access to a SaleADS.ai environment login page

## Install

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
```

## Run

Provide the current environment login URL via env var (domain-agnostic):

```bash
cd e2e
SALEADS_LOGIN_URL="https://your-current-env/login" npm run test:saleads-mi-negocio
```

Optional strict username validation:

```bash
SALEADS_LOGIN_URL="https://your-current-env/login" \
SALEADS_EXPECTED_USER_NAME="Nombre Apellido" \
npm run test:saleads-mi-negocio
```

## Output

- HTML report: `e2e/playwright-report/`
- Screenshots + JSON result: `e2e/artifacts/saleads-mi-negocio/<run-id>/`
- Final validation matrix: `final-report.json` in the run folder
