#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${SALEADS_BASE_URL:-}" ]]; then
  echo "ERROR: SALEADS_BASE_URL is required."
  echo "Example:"
  echo "  SALEADS_BASE_URL=https://staging.saleads.ai ./scripts/run-saleads-mi-negocio.sh"
  exit 1
fi

npx playwright test tests/saleads_mi_negocio_full_test.spec.ts "$@"
