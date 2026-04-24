# SaleADS E2E - Mi Negocio Full Workflow

This folder contains the automated Playwright test:

- `e2e/tests/saleads_mi_negocio_full_test.spec.js`

The test covers:

1. Google login flow.
2. "Mi Negocio" menu expansion.
3. "Agregar Negocio" modal validation.
4. "Administrar Negocios" page validation.
5. "Informacion General", "Detalles de la Cuenta", and "Tus Negocios" sections.
6. Legal links: "Terminos y Condiciones" and "Politica de Privacidad", including:
   - validation of heading and legal content,
   - screenshot capture,
   - final URL capture,
   - return to the application tab.
7. Final PASS/FAIL report by step.

## Environment configuration

This test is environment-agnostic and does not hardcode a domain.

Set one of the following variables:

- `SALEADS_LOGIN_URL` (recommended)
- `SALEADS_BASE_URL` (fallback)

Example:

```bash
export SALEADS_LOGIN_URL="https://<your-saleads-environment>/login"
```

## Install browsers

```bash
npm run e2e:saleads:install-browsers
```

## Run test

Headless:

```bash
npm run e2e:saleads:mi-negocio
```

Headed:

```bash
npm run e2e:saleads:mi-negocio:headed
```

## Artifacts

Execution artifacts are written to:

- `e2e/artifacts/<timestamp>/screenshots/*.png`
- `e2e/artifacts/<timestamp>/saleads_mi_negocio_final_report.json`
