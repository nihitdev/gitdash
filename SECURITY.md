# Security Policy

## Supported versions

The latest published GitDash release and the current `main` branch receive security fixes.

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting for this repository: open the repository's **Security** tab, choose **Advisories**, then **Report a vulnerability**. Do not include credentials, tokens, private repository URLs, or exploit details in a public issue.

Include the affected GitDash version, operating system, Java version, reproduction steps, impact, and any suggested mitigation. You should receive an acknowledgement within seven days. Please allow time for investigation and a coordinated fix before public disclosure.

## Security model

GitDash delegates Git operations to the installed `git` executable using argument-safe process invocation. Inspection commands do not fetch or mutate working-tree files. Remote URLs are sanitized before display. Installers retrieve release archives over HTTPS and verify published SHA-256 checksums before extraction.
