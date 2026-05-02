# SaleADS Mi Negocio E2E

Playwright end-to-end test for validating the **full Mi Negocio workflow** after Google login, including:

- Login and dashboard validation.
- Sidebar `Negocio -> Mi Negocio` menu expansion.
- `Agregar Negocio` modal validation.
- `Administrar Negocios` view validation.
- `Información General`, `Detalles de la Cuenta`, and `Tus Negocios` sections.
- Legal links (`Términos y Condiciones`, `Política de Privacidad`) with new-tab/same-tab handling.
- Checkpoint screenshots and final JSON report.

## Why this lives in `/e2e`

This repository is a Maven/Java project. The Playwright suite is isolated in `e2e/` to avoid affecting the existing Maven/JUnit pipeline.

## Prerequisites

- Node.js 20+ recommended.
- Browser installation for Playwright:

```bash
npx playwright install --with-deps chromium
```

## Install

```bash
cd e2e
npm install
```

## Run

### Option A: Provide login URL through env var (recommended)

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-page>" npm test
```

### Option B: Pre-navigate manually, then run

If you launch the browser/page yourself and are not at `about:blank`, the test can continue from current context.

## Optional env vars

- `SALEADS_LOGIN_URL`: Login page URL for current environment.
- `SALEADS_BASE_URL`: Alternate URL variable.
- `GOOGLE_ACCOUNT_EMAIL`: Defaults to `juanlucasbarbiergarzon@gmail.com`.
- `HEADED=true`: Run headed mode.

## Evidence and report

For each run, the suite writes:

- Screenshots in `e2e/evidence/saleads-mi-negocio-<timestamp>/`
- Final report JSON in `final-report.json`

Report fields:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
