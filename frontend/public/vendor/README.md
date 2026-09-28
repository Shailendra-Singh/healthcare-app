# Vendored scripts and styles

| File | Source | Version | License |
|---|---|---|---|
| `pico-2.1.1.min.css` | npm `@picocss/pico`, `css/pico.min.css` | 2.1.1 (tarball integrity `sha512-kIDugA7Ps4U+2BHxiNHmvgPIQDWPDU4IeU6TNRdvXQM1uZX+FibqDQT2xUOnnO2yq/LUHcwnGlu1hvf4KfXnMg==`) | MIT, Copyright (c) 2019-2024 Pico |
| `alpine-3.17.4.min.js` | npm `alpinejs`, `dist/cdn.min.js` | 3.17.4 (tarball integrity `sha512-N2gsr+58XVUt7OAPKnUGiKPDtkd+xbSGTae8C8IJxeRdkkhBJPIautkJ+qcwb7lcjJ/QK3/CPVIbN20JPvtK7A==`) | MIT, Copyright (c) 2019-2021 Caleb Porzio and contributors |

Kept in the repository so the frontend has no runtime dependency on a CDN. To upgrade, download the new
file from the npm tarball (`npm pack <package>@<version>`), check the tarball's integrity against the registry
(`npm view <package>@<version> dist.integrity`), and update `index.html`.
