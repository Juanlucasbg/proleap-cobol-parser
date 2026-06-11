# SaleADS Mi Negocio Full Test

This folder contains an end-to-end Playwright test named `saleads_mi_negocio_full_test` that validates the full Mi Negocio workflow:

1. Login with Google.
2. Open Mi Negocio menu and validate submenu options.
3. Validate Agregar Negocio modal.
4. Open Administrar Negocios and validate all sections.
5. Validate Informacion General.
6. Validate Detalles de la Cuenta.
7. Validate Tus Negocios.
8. Validate Terminos y Condiciones.
9. Validate Politica de Privacidad.
10. Produce a PASS/FAIL final report.

## Environment-agnostic configuration

No SaleADS domain is hardcoded. Set one of these variables:

- `SALEADS_LOGIN_URL`
- `SALEADS_BASE_URL`
- `BASE_URL`

Example:

```bash
cd qa/saleads
npm install
npx playwright install chromium
SALEADS_LOGIN_URL="https://<your-environment>/login" npm test
```

## Artifacts and evidence

On every run, the test writes outputs under:

`qa/saleads/artifacts/<timestamp>/`

Including:

- `screenshots/*.png` for important checkpoints.
- `final-report.json` with PASS/FAIL per required report field.
- `final-report.md` human-readable summary with evidence paths and legal URLs.

## Notes

- Selectors prioritize visible text in Spanish and tolerate accent/non-accent variants.
- The legal link flow supports both same-tab navigation and new-tab behavior.
- If Google account selection appears, the test tries to select:
  `juanlucasbarbiergarzon@gmail.com`.
