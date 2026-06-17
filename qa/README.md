# SaleADS Mi Negocio Full Test

This folder contains a Playwright automation script for validating the full **Mi Negocio** workflow in any SaleADS.ai environment.

## What it covers

`saleads_mi_negocio_full_test.js` validates:

1. Login with Google (and optional account selection for `juanlucasbarbiergarzon@gmail.com`)
2. Sidebar > Negocio > Mi Negocio navigation
3. "Agregar Negocio" modal content
4. "Administrar Negocios" page sections
5. "Información General" section
6. "Detalles de la Cuenta" section
7. "Tus Negocios" section
8. "Términos y Condiciones" link behavior (same tab or new tab)
9. "Política de Privacidad" link behavior (same tab or new tab)
10. Final PASS/FAIL status report

It captures screenshots at key checkpoints and stores a JSON report artifact.

## Why it is environment-agnostic

- No hardcoded SaleADS domain is used.
- It can start from the current login page (default behavior).
- Optionally, you can provide any environment URL using `SALEADS_BASE_URL`.

## Setup

From this folder:

```bash
npm install
npm run install:browsers
```

## Run

If your flow starts from the currently open login page:

```bash
npm run test:saleads-mi-negocio
```

If you want to target a specific environment URL:

```bash
SALEADS_BASE_URL="https://your-saleads-environment.example" npm run test:saleads-mi-negocio
```

Optional environment variables:

- `HEADLESS=false` to run with visible browser.
- `SALEADS_GOOGLE_ACCOUNT=juanlucasbarbiergarzon@gmail.com` to customize account chooser text.

## Artifacts

After each run, artifacts are generated in:

`qa/artifacts/<timestamp>/`

Including:

- `screenshots/*.png`
- `final-report.json`
