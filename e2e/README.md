# SaleADS Mi Negocio full workflow test

This folder contains the Playwright end-to-end automation for:

- `saleads_mi_negocio_full_test`

## Run

1. Install Playwright browsers:

```bash
npx playwright install --with-deps
```

2. Run the workflow test (headless):

```bash
SALEADS_LOGIN_URL="<current-environment-login-url>" npm run e2e:saleads:mi-negocio
```

3. Optional headed run:

```bash
SALEADS_LOGIN_URL="<current-environment-login-url>" npm run e2e:saleads:mi-negocio:headed
```

## Notes

- The script does not hardcode domains or URLs.
- It uses visible-text-first selectors whenever possible.
- It captures screenshots at all important checkpoints and attaches a final JSON report.
- If legal pages open in a new tab, it validates them, captures evidence, and returns to the app tab.
