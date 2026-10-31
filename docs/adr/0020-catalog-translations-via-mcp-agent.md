# ADR-0020 — Catalog translations are produced by an external LLM agent over MCP

## Status
Accepted

## Context

Issue #157 asks for Product and Product Category text in several languages (the International
Menu offering). A catalogue is authored once in the Merchant's Original Menu Language, usually
when an AI agent imports the merchant's menu through our MCP server. Someone has to produce the
text for each Supported Menu Language.

## Decision

The backend never calls an LLM to translate. It exposes MCP tools that list Pending Translations
(missing or stale) and accept bulk translation upserts. The same external agent that imports the
menu does the translating. Merchants can correct any translation in the admin dashboard. Those
`MERCHANT`-origin translations are never overwritten by a machine translation while they are
current.

Each translation stores a hash of the original name/description it was made from. A translation
is stale when that hash no longer matches. A stale translation is still served to shoppers until
it is replaced. Products and categories have separate translation tables, not one polymorphic
table, so each translation has a real `ON DELETE RESTRICT` foreign key. The category service deletes
a category's translations before the category; products are only soft-deleted. One shared component
holds the fallback, staleness and origin rules.

## Considered Options

- **Backend calls an LLM (`spring-ai-openai` is already a dependency), triggered by events.**
  Rejected for now: it adds per-merchant AI cost, prompt ownership and failure handling to the
  backend. The import agent already understands the menu's context and terminology.
- **Manual translation only.** Rejected: a 200-item menu × several languages is impractical to type.
- **Timestamp-based staleness (`translation.updatedAt < product.updatedAt`).** Rejected: price,
  image and status edits would trigger unnecessary re-translation.

## Consequences

- Translations only appear after an agent session runs. Nothing translates automatically in the
  background. Shoppers see the Original Menu Language until then.
- Moving translation into the backend later only adds a new producer of `MACHINE` translations.
  The storage, staleness and serving rules stay unchanged.
- Order items, appointment snapshots, invoices and push notifications stay in the Original Menu
  Language (out of scope for #157).
