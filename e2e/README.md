# SaleADS E2E - Mi Negocio Workflow

This repository now includes a Playwright E2E test that validates the full **Mi Negocio** workflow:

- Google login
- Sidebar / Mi Negocio menu behavior
- "Agregar Negocio" modal checks
- "Administrar Negocios" sections
- Legal links (Terms and Privacy) with same-tab/new-tab handling
- Checkpoint screenshots and final PASS/FAIL JSON report

## Test file

- `e2e/tests/saleads-mi-negocio-full.spec.js`

## Required environment

Set one of the following environment variables to the current environment login URL:

- `SALEADS_LOGIN_URL` (preferred)
- `SALEADS_BASE_URL`
- `BASE_URL`
- `APP_URL`

No hardcoded domain is used in the test.

## Install

```bash
npm run e2e:install:browsers
```

## Run

```bash
npm run e2e:saleads:mi-negocio
```

Headed mode:

```bash
npm run e2e:saleads:mi-negocio:headed
```

## Artifacts

- Playwright JSON report: `e2e/artifacts/playwright-report.json`
- HTML report: `playwright-report/`
- Test output (screenshots, trace, video, final report): `test-results/`
