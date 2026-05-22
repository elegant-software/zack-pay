# MCP Server Best Practices

Authoritative reference for documenting and designing Model Context Protocol (MCP) servers in this project.
Use this alongside `CONSTITUTION.md` when adding or changing MCP-facing capabilities.

---

## 1. Treat MCP Like a Published Contract

MCP servers do not have a single Swagger-equivalent standard document, but they do expose a standard protocol contract through:

- server capabilities
- tool schemas
- resource definitions
- prompt definitions

Any MCP tool, resource, or prompt exposed by this repository is a public integration surface and must be documented with the same discipline as an HTTP API.

---

## 2. Minimum Documentation Required

For every MCP server or MCP-facing module, document:

- purpose of the server
- transport and runtime assumptions
- authentication and authorization model
- exposed tools with input and output examples
- exposed resources and URI patterns
- exposed prompts and required arguments
- error behavior and validation rules
- rate limits, size limits, and timeout expectations

If a capability is intended for internal use only, state that explicitly.

---

## 3. Tool Documentation Format

Each tool should describe:

- tool name
- one-sentence purpose
- required arguments
- optional arguments
- input constraints and validation rules
- output shape
- failure modes
- example request and example response

Prefer stable, explicit field names. Avoid overloaded parameters or ambiguous free-form inputs when a structured schema is practical.

---

## 4. Resource Documentation Format

For each resource or resource template, document:

- URI or URI template
- meaning of each template parameter
- MIME type
- whether content is text or binary
- intended audience
- refresh or caching expectations
- authorization requirements

Use predictable URI schemes and naming. Resource identifiers should remain stable over time.

---

## 5. Prompt Documentation Format

For each prompt, document:

- prompt name
- purpose
- required and optional arguments
- expected user workflow
- any embedded resources used
- safety or approval constraints

Prompts should be discoverable and narrowly scoped. Do not hide critical behavior in vague prompt names.

### Prompt vs Tool Guidance

- Use an MCP **guide tool** as the primary contract for workflow routing, tool selection, operation order, idempotency notes, and bulk-vs-single decision rules.
- Use MCP **prompts** only as an optional client-side UX layer for reusable workflow scaffolds, argument collection, and form-like onboarding flows.
- Do **not** move critical routing or execution guidance from the guide tool into prompts only. Many MCP clients have stronger tool support than prompt support, and some clients may ignore prompts entirely.
- If both exist, the guide tool is the canonical source of truth and prompts must defer to it explicitly.
- Prompt completions should be reserved for constrained prompt arguments, such as codes or enumerated values, not as a replacement for tool discovery.

---

## 6. Schema and Compatibility Rules

- Prefer machine-readable schemas wherever MCP tooling supports them.
- Backward-incompatible changes to tool arguments or output fields must be treated as contract changes.
- Additive changes are preferred over breaking renames or semantic shifts.
- If behavior changes materially, update both docs and examples in the same change.

Do not rely on clients inferring meaning from implementation details.

---

## 7. Operational Best Practices

- Validate all inputs at the server boundary.
- Keep MCP tool classes thin. They should delegate business logic and persistence orchestration to application services.
- Do not inject or call JPA repositories directly from MCP tools. Repository access belongs in the service layer so authorization, validation, transactions, and audit behavior stay centralized.
- Return clear, structured errors for invalid params, missing arguments, and internal failures.
- Log tool invocations and failures without leaking secrets or personal data.
- Define timeouts for network and database access.
- Keep long-running or side-effecting operations explicit in tool descriptions.

If a tool mutates data, the documentation must say so plainly.

---

## 8. Security Requirements

- Never expose secrets, credentials, or raw private configuration through tools or resources.
- Enforce least privilege for each capability.
- Document which capabilities are read-only and which can mutate state.
- Sanitize any user-controlled content that can flow into prompts, logs, or downstream systems.

For high-impact tools, document approval expectations and audit considerations.

---

## 9. Testing Expectations

Every MCP-facing capability should have tests for:

- valid request handling
- invalid input handling
- authorization or access checks where applicable
- stable output shape
- contract-sensitive edge cases

Where possible, keep example payloads and fixtures static and reviewable.

---

## 10. Recommended Repo Layout

When the repository grows MCP functionality, prefer keeping:

- implementation under the owning module
- contract tests near other integration tests
- human-readable MCP docs under `reference/` or feature-specific docs
- example payloads under `src/test/resources/`

Do not scatter MCP contract details across unrelated README files.

---

## 11. Practical Standard to Follow

The practical standard for MCP documentation is:

- official MCP protocol semantics for capabilities and message shapes
- machine-readable schemas for tools and resources
- repository-local human documentation for behavior, examples, and operational constraints

In other words: use MCP schema for discovery and validation, and use repository docs the way REST projects use OpenAPI plus handbook-style guidance.

---

## 12. LLM-Steering Description Conventions

MCP tool descriptions are not just documentation — they are runtime instructions that the LLM reads to decide which tool to call. Write them accordingly.

### 12.1 Guide Tool Pattern

Every MCP tool group that exposes multiple tools for related operations **must** include a dedicated guide tool.

**Rules:**

- Name it `<domain>_api_guide` (e.g., `merchant_api_guide`).
- Its `description` field must tell the LLM to call it first: `"Call this before starting any <domain> workflow. Returns tool-selection rules and the recommended operation order."`
- Its return value is plain text with three fixed sections (see 12.3).
- The guide is the single source of truth for tool selection within its domain. Do not duplicate routing logic across individual tool descriptions.

**Implementation sketch:**

```kotlin
@McpTool(
    name = "merchant_api_guide",
    description = "Call this before starting any merchant, category, or product workflow. " +
        "Returns tool-selection rules and the recommended operation order."
)
fun merchantApiGuide(): String = """
    TOOL SELECTION
    ...
    RECOMMENDED WORKFLOW ORDER
    ...
    IDEMPOTENCY & RESPONSES
    ...
""".trimIndent()
```

### 12.2 Individual Tool Descriptions

Each tool description must answer three questions for the LLM:

1. **What does this tool do?** — one sentence, action-first (e.g., "Create or update a merchant by name.").
2. **When should I use this tool instead of an alternative?** — explicit count or condition (e.g., "PREFERRED tool when working with 2 or more branches").
3. **What should I never do?** — the anti-pattern to avoid (e.g., "do NOT call this tool in a loop").

When a single-item tool and a bulk tool cover the same entity:

- The **single-item tool** description must contain: `"WARNING: if you need to create or update 2 or more <items>, call <bulk_tool> instead — do NOT call this tool in a loop."`
- The **bulk tool** description must open with: `"PREFERRED tool when working with 2 or more <items> — use this instead of calling <single_tool> in a loop."`

**Example — single-item tool:**

```kotlin
@McpTool(
    name = "product_id_by_name_or_create",
    description = "Get or create a SINGLE product by name. " +
        "WARNING: if you need to create or update 2 or more products, " +
        "call products_bulk_upsert instead — do NOT call this tool in a loop."
)
```

**Example — bulk tool:**

```kotlin
@McpTool(
    name = "products_bulk_upsert",
    description = "PREFERRED tool when working with 2 or more products — " +
        "use this instead of calling product_id_by_name_or_create in a loop. ..."
)
```

### 12.3 Guide Tool Output Format

The guide tool's return value must use this exact three-section structure, using box-drawing separators for visual clarity:

```
TOOL SELECTION
══════════════
<EntityType>
  <condition>  → <tool_name>  (<brief note>)
  <condition>  → <tool_name>  (<brief note>)

RECOMMENDED WORKFLOW ORDER
══════════════════════════
1. <tool_name>  — <one-line purpose>
2. <tool_name>  — <one-line purpose>
...

IDEMPOTENCY & RESPONSES
═══════════════════════
• <tool_name> is idempotent by <key> — <safe re-run statement>.
• <Additional idempotency or response shape notes>.
```

**Count-based routing table rules:**

- Use `1 <entity>  →` for the single-item tool.
- Use `2+ <entities> →` for the bulk tool, followed by `(never loop the single-item tool)`.
- List entity groups in dependency order (merchant before branch before product).

**Example:**

```
TOOL SELECTION
══════════════
Merchant
  always upsert  → merchant_upsert  (creates or updates, including logo)

Branches
  1 branch   → branch_id_by_name_or_create
  2+ branches → branches_bulk_upsert  (never loop the single-branch tool; supports all attributes)

Products
  1 product  → product_id_by_name_or_create
  2+ products → products_bulk_upsert  (never loop the single-product tool)

RECOMMENDED WORKFLOW ORDER
══════════════════════════
1. merchant_upsert             — create or update the merchant (returns applicationId)
2. branches_bulk_upsert        — create or update all branches with full attributes
3. categories_bulk_create      — (optional) pre-create all categories in one call
4. products_bulk_upsert        — create all products, referencing categoryName

IDEMPOTENCY & RESPONSES
═══════════════════════
• merchant_upsert is idempotent by merchantName — re-running updates the merchant safely.
• branches_bulk_upsert is idempotent by branchName — re-running updates branches safely.
• productCode is the idempotency key for products — re-running the same payload is safe.
• Bulk responses include IDs in all buckets (created / updated / skipped / failed).
• categoryName is resolved or created automatically; no separate category lookup required.
```

### 12.4 Idempotency Statements

Every tool that creates or mutates state must declare its idempotency key in both:

- its own `description` field (one sentence: `"Re-running the same payload is safe — existing <items> are updated, not duplicated."`)
- the IDEMPOTENCY & RESPONSES section of the domain guide tool

If a tool is **not** idempotent, state that explicitly so the LLM does not retry blindly.

---

## 13. Merchant Module Tool Index

The merchant MCP module exposes:

| Tool | Purpose | Idempotency key |
|------|---------|-----------------|
| `merchant_api_guide` | Call-first guide; returns tool selection rules | — |
| `merchant_upsert` | Create or update merchant + optional logo | `merchantName` |
| `merchant_id_by_name` | Look up existing merchant ID | — (read-only) |
| `branch_id_by_name_or_create` | Upsert a single branch | `branchName` |
| `branch_id_by_name` | Look up existing branch ID | — (read-only) |
| `branches_bulk_upsert` | Upsert 2–200 branches with all attributes | `branchName` |
| `category_id_by_name_or_create` | Get or create a single category | `categoryName` |
| `category_id_by_name` | Look up existing category ID | — (read-only) |
| `categories_bulk_create` | Create 2–200 categories | `categoryName` |
| `merchant_list_product_categories` | List all categories for a merchant | — (read-only) |
| `product_id_by_name_or_create` | Get or create a single product | `productName` |
| `product_id_by_name` | Look up existing product ID | — (read-only) |
| `products_bulk_upsert` | Upsert 2–200 products | `productCode` (falls back to `productName`) |
| `merchant_product_update` | Update a product's fields and image | — (by explicit ID) |

Bulk tool contracts, validation rules, and example payloads live in
`specs/015-mcp-bulk-operations/contracts/mcp-tools.md`.

---

## 14. Kotlin Implementation Conventions

### 14.1 One Class Per Domain (SRP)

Split MCP tool methods into one `@Component` class per domain entity. Each class should inject only the services it needs.

```
merchant/mcp/
├── MerchantMcpTools.kt   — merchant_api_guide, merchant_id_by_name, merchant_upsert
├── BranchMcpTools.kt     — branch_id_by_name, branch_id_by_name_or_create, branches_bulk_upsert
├── CategoryMcpTools.kt   — category_id_by_name*, categories_bulk_create, list
└── ProductMcpTools.kt    — product_id_by_name*, products_bulk_upsert, merchant_product_update
```

Do not add a new domain's tools to an existing class. Create a new file.

### 14.2 Suppress "Unused" IDE Warnings

MCP tool classes follow the same rule as REST controllers and event listeners — see **§8** of [`reference/spring-boot-best-practices.md`](spring-boot-best-practices.md) for the full pattern and rationale.
