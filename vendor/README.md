# Vendored Source Layout

Use this directory only for source trees that are intentionally imported from an
upstream project.

## Layout

```text
vendor/
  upstream/<component>/<version>/
  patches/<component>/<version>/
  adapters/<component>/<version>/
```

## Rules

- `upstream/` keeps pristine source snapshots from tagged upstream releases.
- `patches/` stores diffs, patch files, or change logs against the upstream tag.
- `adapters/` stores local wrappers and integration glue that should not be
  mixed into the upstream snapshot.
- Every imported source tree must be listed in
  `third_party/reuse-manifest.json`.
- Every imported source tree must retain upstream `LICENSE`, `NOTICE`, and
  copyright headers.
- Import only `Apache-2.0` or `MIT` source trees unless legal approval says
  otherwise.
