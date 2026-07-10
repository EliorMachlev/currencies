# Security Policy

## Supported Versions

Only the latest release is actively supported with security fixes.

| Version | Supported |
|---------|-----------|
| Latest  | Yes       |
| Older   | No        |

## Reporting a Vulnerability

Please **do not** open a public GitHub Issue for security vulnerabilities.

Report vulnerabilities privately via [GitHub's private vulnerability reporting](https://github.com/EliorMachlev/currencies/security/advisories/new).

Include:
- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Android version and app version (`Settings → About`)

You will receive a response within 7 days. If the vulnerability is confirmed, a fix will be released as soon as possible, and you will be credited in the release notes unless you prefer otherwise.

## Scope

This app:
- Requests only the `INTERNET` permission
- Stores no personal data
- Contains no authentication or payment flows
- Communicates only with the exchange rate provider selected by the user

Issues related to third-party exchange rate APIs should be reported directly to those providers.
