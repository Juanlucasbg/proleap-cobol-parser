# SaleADS.ai - Mi Negocio workflow E2E

This folder contains a Playwright test that validates the full **Mi Negocio** workflow after Google login, including legal links and evidence capture.

## Prerequisites

- Node.js 18+ (recommended)
- A reachable SaleADS environment URL (dev, staging, or production)
- Access to the Google account:
  - `juanlucasbarbiergarzon@gmail.com`

## Install

```bash
cd ui-e2e
npm install
npm run install:browsers
```

## Run

Set the environment URL at runtime (no hardcoded domain is used):

```bash
cd ui-e2e
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm test
```

Or:

```bash
cd ui-e2e
SALEADS_BASE_URL="https://<current-environment-login-url>" npm test
```

## What it validates

- Login with Google and sidebar visibility
- `Negocio` -> `Mi Negocio` expansion
- `Agregar Negocio` modal fields and controls
- `Administrar Negocios` view sections
- `Información General`, `Detalles de la Cuenta`, `Tus Negocios`
- `Términos y Condiciones` and `Política de Privacidad` content + final URL

## Artifacts

Playwright outputs screenshots and a JSON final report under `playwright-report` and test output directories after execution.
