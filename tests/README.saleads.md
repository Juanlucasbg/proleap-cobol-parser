# SaleADS Mi Negocio E2E

This Playwright spec validates the full `Mi Negocio` workflow:

- Google login
- Sidebar and `Mi Negocio` expansion
- `Agregar Negocio` modal validations
- `Administrar Negocios` page validations
- Legal links (`Terminos y Condiciones`, `Politica de Privacidad`) including new-tab handling
- Screenshot evidence in key checkpoints
- Final PASS/FAIL JSON report attached by Playwright test info

## Run

```bash
SALEADS_URL="https://your-current-saleads-environment/login" npm run test:saleads-mi-negocio
```

Optional headed mode:

```bash
SALEADS_URL="https://your-current-saleads-environment/login" npm run test:saleads-mi-negocio:headed
```

## Notes

- The spec does not hardcode any SaleADS domain.
- Target URL is provided by `SALEADS_URL` (or `BASE_URL`).
- Google account selector is handled for:
  - `juanlucasbarbiergarzon@gmail.com`
