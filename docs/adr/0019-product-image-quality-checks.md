# ADR-0019 — Product image quality: technical gate now, semantic review later

## Status
Accepted (technical gate implemented; semantic review deferred)

## Context

MCP clients (AI agents) import merchant catalogues and attach product images via `imageUrl` or
`imageBase64`. They pick images from merchant websites, marketplaces and chat attachments, and
often choose thumbnails, blurry photos, placeholders, logos or unrelated pictures. Our tool
descriptions ask for "clear, high-quality images directly related to the product", but nothing
enforced it, so poor images reached the shopper app.

Image quality has two independent aspects:

1. **Technical:** resolution, sharpness, exposure, blank or banner-shaped images. Measurable
   deterministically from the pixels.
2. **Semantic:** does the image show *this* product; is it a real product photo rather than a
   logo, screenshot, collage or watermarked stock image; is it appropriate. This needs a vision
   model.

## Decision

### 1. Technical gate, synchronous, in plain Java (implemented)

`ProductImageProcessor` runs `ProductImageQualityInspector` on every MCP product image and
rejects failures inside the tool call. The error lists each failed check with its measured
value, so the agent can pick a better image.

| Check | Metric | Default |
|---|---|---|
| Resolution | Shorter side, in source pixels after crop | ≥ 400 px |
| Aspect ratio | Longer side / shorter side | ≤ 4:1 |
| Sharpness | 90th-percentile tile variance of the Laplacian at 1024 px | ≥ 40 |
| Exposure | Mean luminance (0–255) | 20 – 250 |
| Blank | Luminance standard deviation | ≥ 10 |

- The percentile-of-tiles sharpness metric keeps a sharp product on a plain white background
  (the e-commerce norm) from scoring as blurry.
- WebP cannot be decoded by ImageIO, so only resolution and aspect ratio (from the header) are
  checked for WebP.
- Thresholds live in `blitzpay.product-image.quality.*` (env-overridable) and are deliberately
  lenient. Every inspection logs its scores so they can be calibrated on real merchant images.
- No native dependency (OpenCV/JavaCV) was added; the checks are a few hundred lines of JDK code.
- Scope: MCP tools only. REST uploads from the dashboard and merchant app are made by humans
  looking at the image and are not gated.

### 2. Semantic review, asynchronous, with a vision LLM (deferred)

When implemented, semantic review will **not** block the MCP call:

- After an image is stored, publish a domain event (Spring Modulith). A listener sends the
  image (resized to about 1024 px) together with the product name, description and category to a
  small vision model, asking for a structured verdict: `matchesProduct`, `isProductPhoto`,
  `hasWatermarkOrHeavyText`, `isAppropriate`, plus a reason.
- The result sets a product image status: `PENDING_REVIEW` → `APPROVED` | `NEEDS_REVIEW` |
  `REJECTED`. This needs a new column via Liquibase. Shopper-facing APIs show only `APPROVED`
  images. Doubtful verdicts go to `NEEDS_REVIEW` for a human in the admin dashboard rather than
  automatic rejection.
- The MCP submitter is never trusted to judge its own images.

Reasons for deferring: it needs a model provider decision and API key handling, a schema
change, an admin review UI, and a policy for what shoppers see while review is pending. The
technical gate removes most bad images at no running cost.

## Consequences

- Agents get immediate, actionable feedback for technically poor images; some legitimate
  images may be rejected until thresholds are calibrated. Operators can relax or disable the
  gate with environment variables without a release.
- All JPEG/PNG images are now decoded (subsampled, bounded by the existing decode semaphore)
  even when stored unchanged, which adds CPU time per MCP image call.
- Wrong-product, watermarked or inappropriate images still pass until the semantic review
  lands. It is tracked as a GitHub issue.
