# SaleADS E2E tests

This folder contains Playwright E2E automation for SaleADS workflows.

## Test included

- `saleads-mi-negocio.full.spec.ts`: Full validation of Google login + Mi Negocio workflow.

## Why this is environment-agnostic

- No hardcoded SaleADS domain is used.
- The login page can be provided at runtime with environment variables.
- Selectors prioritize visible text and semantic roles.

## Prerequisites

- Node.js 20+ (recommended)
- Chromium dependencies (Playwright can install them)

## Install

```bash
cd e2e
npm install
npm run install:browsers
```

## Run

Use the current environment login URL via env var (no hardcoded domain):

```bash
cd e2e
SALEADS_LOGIN_URL="https://<current-env-login-url>" npm run test:mi-negocio
```

Optional:

- `SALEADS_HEADLESS=false` to run headed.

## Evidence generated

The test captures screenshots at required checkpoints and attaches a final text report with:

- PASS/FAIL by step
- Final URLs for legal pages
