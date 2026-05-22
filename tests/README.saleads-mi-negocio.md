# SaleADS Mi Negocio full workflow test

This repository now includes an end-to-end Playwright test for the full **Mi Negocio** workflow:

- Google login
- Sidebar `Negocio` > `Mi Negocio`
- `Agregar Negocio` modal validation
- `Administrar Negocios` account view validation
- `Información General`, `Detalles de la Cuenta`, and `Tus Negocios` validations
- `Términos y Condiciones` and `Política de Privacidad` link validation (same tab or new tab)
- Screenshot evidence and final PASS/FAIL report

## Run

```bash
SALEADS_LOGIN_URL="https://<current-env-login-url>" \
GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" \
npm run test:saleads:mi-negocio
```

Optional:

- `HEADLESS=false` to run headed.
- `SALEADS_BASE_URL` can be used instead of `SALEADS_LOGIN_URL`.
- If your automation runner already opens the SaleADS login page before the test starts, `SALEADS_LOGIN_URL` is optional.

## Artifacts generated

- `artifacts/saleads_mi_negocio/*.png` screenshots at key checkpoints
- `artifacts/saleads_mi_negocio/final-report.json`
- `artifacts/saleads_mi_negocio/final-report.md`
