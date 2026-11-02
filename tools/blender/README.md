# Active model production

```sh
python3 tools/build_assets.py --device xiaomi-18-fold --all-states
python3 tools/build_assets.py --device redmi-k90-pro-max --all-states
python3 tools/validate_assets.py --all
```

Xiaomi18Fold now has a dedicated two-state generator `build_xiaomi18_fold.py`, verified official dimensions and views; no wide-fold alias. `build_device.py` only retains shared original mesh primitives. MIX Fold4 and generic wide production geometry/runtime assets were removed; historical source/evidence remain in Git commits and prior deliverables. New builds output models/checks under `deliverables/xiaomi18-refresh/`, without overwriting earlier evidence.

## Isolated research/profile pipeline

For non-destructive replay and new-phone research, see
[the reusable Xiaomi pipeline](../../docs/XIAOMI_DEVICE_PIPELINE.md).
Use `tools/build_assets.py --profile ... --staging-root ...` to build into a new
isolated directory and run the existing asset validator there. The legacy
`--device` commands above still write production runtime assets.
