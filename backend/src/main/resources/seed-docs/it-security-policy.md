# IT Security Policy

Northwind Labs — Policy ID SEC-001 — Owner: Information Security Office — Applies to all employees and contractors.

## Passwords and Authentication

All accounts must use a password of at least 14 characters. Passwords must not be reused across company and personal services. A company-approved password manager (1Password) is provided to every employee and must be used to generate and store work credentials.

Multi-factor authentication (MFA) is mandatory for email, VPN, the HR portal and all production systems. Hardware security keys (YubiKey) are required for anyone with production or finance-system access. SMS codes are not an accepted second factor.

## Device Security

Company laptops must have full-disk encryption enabled (FileVault on macOS, BitLocker on Windows). The screen must lock automatically after 5 minutes of inactivity. Operating-system security updates must be installed within 7 days of release.

Personal devices may access email and chat only through the managed mobile app. Company source code and customer data must never be stored on personal devices.

## Data Classification

Northwind classifies information into four levels:

- **Public** — approved for external release, such as marketing pages.
- **Internal** — default for company information; may be shared with any employee.
- **Confidential** — customer data, financial results before publication, compensation data. Share only with people who need it for their role.
- **Restricted** — credentials, encryption keys, and personal health data. Access is logged and reviewed quarterly.

Confidential and Restricted data must never be pasted into public AI tools. Use the internal Document Assistant instead, which respects document permissions.

## Reporting Security Incidents

Report suspected phishing, lost devices or any security incident to security@northwind.example or the #security-help channel within 1 hour of discovery. Lost or stolen laptops must also be reported to IT so the device can be remotely wiped. Employees are never penalised for reporting a mistake promptly.

## Acceptable Use

Company systems may be used for limited personal purposes that do not interfere with work. Installing unapproved browser extensions, running crypto-mining software, or disabling endpoint protection (CrowdStrike) is prohibited. Violations may lead to disciplinary action up to and including termination.
