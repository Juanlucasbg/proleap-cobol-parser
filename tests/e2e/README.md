# SaleADS Mi Negocio E2E

This Playwright test validates the full **Mi Negocio** workflow, including:

- Google login flow
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios page sections
- Legal links (Términos y Condiciones, Política de Privacidad)
- Screenshot evidence and final PASS/FAIL JSON report

## Environment-agnostic execution

The test does **not** hardcode any SaleADS domain.

Set the start URL for the current environment at runtime:

```bash
SALEADS_START_URL="https://<current-saleads-environment>/login" npm run test:e2e:headed
```

You can also use `BASE_URL` instead of `SALEADS_START_URL`.

## Notes

- If Google account chooser is displayed, the test selects:
  `juanlucasbarbiergarzon@gmail.com`
- Evidence is saved in Playwright outputs (`test-results` / report attachments).
- The final report file is generated as:
  `saleads-mi-negocio-report.json` (inside the test output folder).
