# SaleADS Mi Negocio Full Workflow Test

This folder contains an environment-agnostic Playwright automation script for the full **SaleADS.ai Mi Negocio** workflow:

- Google login
- Sidebar/Mi Negocio expansion
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Información General / Detalles de la Cuenta / Tus Negocios checks
- Términos y Condiciones + Política de Privacidad checks (including new-tab support)
- Evidence capture (screenshots + final legal URLs)
- Final PASS/FAIL report per requested section

## Why it is environment-agnostic

- No domain is hardcoded.
- The start URL is passed at runtime (`--start-url` or environment variable).
- Element targeting prioritizes **visible text** and role-based selectors.

## Prerequisites

- Node.js 18+ (recommended)
- Dependencies installed:

```bash
npm install
```

- Playwright browser binaries installed (first run only):

```bash
npx playwright install chromium
```

## Run

From this folder:

```bash
npm run saleads:mi-negocio -- --start-url "https://<your-saleads-environment>/login"
```

Alternative using env vars:

```bash
SALEADS_START_URL="https://<your-saleads-environment>/login" npm test
```

Run in headed mode (useful for interactive Google login):

```bash
npm test -- --start-url "https://<your-saleads-environment>/login" --headed
```

## Outputs

By default, outputs are written to:

`artifacts/saleads-mi-negocio/<timestamp>/`

Including:

- `screenshots/` checkpoint images
- `report.json` with step-by-step PASS/FAIL:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad

