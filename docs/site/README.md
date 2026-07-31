# Silence website (GitHub Pages)

This folder is the public landing page.

## Enable Pages

1. Repo **Settings → Pages**
2. Source: **Deploy from a branch**
3. Branch: `main` → folder `/docs/site`  

Or use the GitHub CLI:

```bash
gh api -X POST repos/mvinodk2082-glitch/Silence/pages \
  -f build_type=legacy \
  -f source[branch]=main \
  -f source[path]=/docs/site
```

Site URL: https://mvinodk2082-glitch.github.io/Silence/

Note: if GitHub only allows `/docs` (not `/docs/site`), copy `index.html` to `docs/index.html` or set Pages root to `/docs` and keep `site/index.html` linked — currently Pages path options are `/` or `/docs`. For `/docs`, place `index.html` at `docs/index.html`.
