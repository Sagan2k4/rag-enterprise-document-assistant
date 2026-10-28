# Engineering Onboarding Guide

Welcome to Northwind Engineering! This wiki page covers what you need in your first two weeks. Owner: Developer Experience team.

## Your First Week

On day one you will receive your laptop, GitHub access and an onboarding buddy. By the end of the first week you should have the platform running locally and have merged one small pull request (we tag starter issues with `good-first-issue`).

## Local Development Setup

1. Install Java 21, Node.js 22 and Docker Desktop.
2. Clone the `northwind/platform` monorepo.
3. Run `make bootstrap` to install dependencies and start Postgres, Redis and Kafka in Docker.
4. Run `make dev` to start all services with hot reload. The web app is served on http://localhost:3000.

If `make bootstrap` fails, ask in the #dev-help Slack channel.

## Code Review Standards

Every pull request needs two approvals, one of which must be from a code owner of the changed area. Pull requests should stay under 400 changed lines; split larger changes. All CI checks — unit tests, integration tests, lint and the dependency vulnerability scan — must pass before merge. Squash-merge is the only allowed merge strategy.

## Deployments

Production deploys run through the Spinnaker pipeline. The standard deploy window is Tuesday to Thursday, 10:00 to 16:00 local time. Friday and weekend deploys require approval from the engineering manager on duty. Every deploy first goes to a 5% canary for 30 minutes and is rolled back automatically if the error rate rises above 1%.

## On-Call Rotation

Engineers join the on-call rotation after 90 days. Rotations last one week, from Monday 10:00 to the following Monday 10:00, with a primary and a secondary engineer. On-call engineers receive an extra stipend of $500 per week.

## Incident Severity Levels

- **SEV1** — full outage or data loss. Acknowledge within 15 minutes; incident commander assigned immediately; status page updated every 30 minutes.
- **SEV2** — major feature degraded for many customers. Acknowledge within 30 minutes.
- **SEV3** — minor issue with a workaround. Handle during business hours.

A blameless post-mortem is required for every SEV1 and SEV2 within 5 business days.
