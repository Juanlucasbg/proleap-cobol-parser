# SaleADS Mi Negocio E2E Workflow

This folder contains an environment-agnostic Playwright test for:

- Google login on the current SaleADS environment
- Mi Negocio menu navigation
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Legal links (Terminos y Condiciones / Politica de Privacidad), including new-tab handling
- Screenshot checkpoints and final PASS/FAIL report output

## Prerequisites

- Node.js 18+
- A reachable SaleADS login page URL for the target environment (dev/staging/prod)

## Install

```bash
cd saleads-e2e
npm install
npm run install:browsers
```

## Run

```bash
cd saleads-e2e
SALEADS_URL="https://your-current-saleads-environment/login" npm run test:mi-negocio
```

Notes:

- The test does not hardcode any domain.
- If already authenticated, it continues directly with workflow validation.
- If Google account selection appears, it attempts to select `juanlucasbarbiergarzon@gmail.com`.

## Evidence output

Artifacts are generated under:

```text
saleads-e2e/test-results/saleads-mi-negocio/<timestamp>/
```

This includes:

- checkpoint screenshots
- `final-report.json` containing PASS/FAIL per requested workflow section and captured legal URLs
