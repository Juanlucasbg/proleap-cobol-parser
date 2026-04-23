# SaleADS Mi Negocio E2E (Playwright)

This folder contains an end-to-end Playwright test that validates the **full Mi Negocio workflow** after Google login.

## Test file

- `tests/saleads_mi_negocio_full_test.spec.js`

## Environment variables

- `SALEADS_LOGIN_URL` (optional): Preferred explicit login URL for the current environment.
- `SALEADS_BASE_URL` (optional): Alternative URL variable if `SALEADS_LOGIN_URL` is not set.
- `BASE_URL` (optional): Generic fallback URL variable.
- `GOOGLE_ACCOUNT_EMAIL` (optional): Defaults to `juanlucasbarbiergarzon@gmail.com`.

Example:

```bash
SALEADS_LOGIN_URL="https://<current-environment>/login" npm test
```

## Key behavior

- Does not hardcode any specific SaleADS domain.
- Uses visible-text-first selectors.
- Waits after each click/navigation-sensitive action.
- Handles legal links that may open in same tab or new tab.
- Captures screenshots at requested checkpoints.
- Writes `artifacts/final-report.json` and attaches it to the Playwright test result.
