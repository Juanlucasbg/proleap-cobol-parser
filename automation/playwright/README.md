# SaleADS Mi Negocio Full Workflow Test

This folder contains an environment-agnostic Playwright test for the full
`Mi Negocio` workflow:

- Google login flow
- sidebar + `Mi Negocio` menu expansion
- `Agregar Negocio` modal validation
- `Administrar Negocios` page section checks
- legal links (`Terminos y Condiciones` and `Politica de Privacidad`)
- screenshots at key checkpoints
- PASS/FAIL final report in JSON

## Test file

- `tests/saleads_mi_negocio_full_test.spec.js`

## Runtime requirements

1. Node.js + npm installed
2. Playwright test package available (`@playwright/test`)
3. Browser context authenticated or ready for Google login

## Environment variables

- `SALEADS_LOGIN_URL` (optional):
  - If set and the page is blank, the test opens this URL.
  - Keep this value environment-specific (dev/staging/prod) outside the test
    code.

## Example execution

```bash
SALEADS_LOGIN_URL="https://<current-saleads-login>" npx playwright test automation/playwright/tests/saleads_mi_negocio_full_test.spec.js
```

## Artifacts

The test writes outputs to:

- `automation/playwright/artifacts/saleads_mi_negocio_full_test_<timestamp>/`

Inside that folder:

- checkpoint screenshots (`*.png`)
- `final_report.json` with PASS/FAIL per requested validation block
