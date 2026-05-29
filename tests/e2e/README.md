# SaleADS Mi Negocio E2E

This suite implements the `saleads_mi_negocio_full_test` workflow with:

- Google login flow handling (same tab or popup)
- Mi Negocio menu and modal validation
- Administrar Negocios account view validation
- Legal links validation (same tab or new tab)
- Checkpoint screenshots and a final JSON report attachment

## Run

```bash
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<current-env-login-url>" npm run test:e2e
```

### Notes

- The test is environment-agnostic: no hardcoded domain is used.
- If your runner already opens the login page, set that in your runner and keep the URL env var unset.
- The account picker prefers `juanlucasbarbiergarzon@gmail.com` if Google account selection appears.
