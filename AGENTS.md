# AGENTS.md

## Purpose
This file defines collaboration and contribution conventions for agents working in this repository.

## Project Snapshot
- Stack: Java + Spring Boot + Gradle (Kotlin DSL)
- Build tool: `./gradlew`
- Main source path: `src/`

## Local Development
- Build: `./gradlew clean build`
- Run tests: `./gradlew test`
- Run app: `./gradlew bootRun`

## Contribution Rules
- Keep changes focused and minimal for the requested task.
- Prefer small, reviewable commits.
- Do not commit secrets, generated artifacts, or environment-specific files.
- Update docs when behavior or configuration changes.
- Treat `CONSTITUTION.md` as the governing repository policy for fixture-based testing and review expectations.

## Code Quality
- Follow existing style and structure.
- Add or update tests for behavior changes.
- Avoid unrelated refactors in the same change.
- After writing or modifying code, run the project's test suite before committing.
- If tests fail, fix the code until tests pass — do not commit failing tests.
- If no test runner is available, validate changes against contract/integration specs.

## Safety
- Never run destructive git commands unless explicitly requested.
- Do not overwrite user-authored changes without confirmation.
- Confirm risky operations before proceeding.

## CORS / Frontend Origin
`blitzpay.cors.allowed-origins` comes from `FRONTEND_ORIGIN`: set via `config.frontendOrigin` in `helm/blitz-pay/values-*.yaml` (k8s) and in `infra/lib/app-stack.ts` (AWS). Whenever the admin dashboard's domain changes, update these to match, or every browser call fails with a 403 on the CORS preflight. Full cross-repo checklist: `CLAUDE.md` in `blitzpay-admin-dashboard`, section "Changing Domains / URLs".

## Reference Documents

For coding conventions, architecture patterns, and technology-specific best practices, see the reference table in [`CONSTITUTION.md`](CONSTITUTION.md#coding-convention-references).

## Active Technologies
- Kotlin 2.3.20 on Java 25 + Spring Boot 4.0.4, Spring WebFlux Test, Spring Modulith, Jackson Kotlin module, Mockito Kotlin, JUnit 5, Testcontainers
- Test resource files under `src/test/resources/` and repository documentation in Markdown
- Kotlin 2.3.20 on Java 25 + Spring Boot 4.0.4, Spring WebFlux, Spring Modulith, Spring Data JPA/Hibernate, Liquibase, AWS SDK S3 v2, Jackson Kotlin module, Bean Validation (001-merchant-onboarding)
- PostgreSQL 16 for product metadata; S3-compatible object storage for private product images (`blitzpay.storage.*`, MinIO in local env) (001-merchant-onboarding)
- Kotlin 2.3.20 on Java 25 + Spring Boot 4.0.4, Spring WebFlux, Spring Modulith, Spring Data JPA/Hibernate, Liquibase, Jackson Kotlin module, Bean Validation (009-order-payment-tracking)
- PostgreSQL 16 in schema `blitzpay` (009-order-payment-tracking)
- Kotlin 2.3.20 on Java 25 + Spring Boot 4.0.4, Spring WebFlux, Spring Modulith, Spring Data JPA/Hibernate, Liquibase, Jackson Kotlin module, Bean Validation, Spring AI BOM-managed client libraries (010-voice-transcription-response)
- PostgreSQL 16 in schema `blitzpay` for payment-context metadata only; voice audio/transcripts are processed in-flight and not persisted (010-voice-transcription-response)

## Recent Changes
- 001-merchant-onboarding: Added Kotlin 2.3.20 on Java 25 + Spring Boot 4.0.4, Spring WebFlux, Spring Modulith, Spring Data JPA/Hibernate, Liquibase, AWS SDK S3 v2, Jackson Kotlin module, Bean Validation
- 010-voice-transcription-response: Added voice query processing scaffolding with provider-agnostic STT and TTS adapters, plus payment-context enrichment for spoken responses
