# Quickstart: Merchant Product Image Upload API

**Feature Branch**: `001-merchant-onboarding`  
**Date**: 2026-04-21

## Prerequisites

- Java 25 toolchain available to Gradle
- Local PostgreSQL configured for the app
- Local MinIO or S3-compatible endpoint
- Bucket named by `STORAGE_BUCKET` exists, default `blitzpay`

Default local storage configuration is already present in `src/main/resources/application.yml`:

```yaml
blitzpay:
  storage:
    endpoint: ${STORAGE_ENDPOINT:http://localhost:9000}
    region: ${STORAGE_REGION:us-east-1}
    access-key: ${STORAGE_ACCESS_KEY:minioadmin}
    secret-key: ${STORAGE_SECRET_KEY:minioadmin}
    bucket: ${STORAGE_BUCKET:blitzpay}
    path-style-access: ${STORAGE_PATH_STYLE:true}
```

## Start Local Object Storage

```bash
docker run --rm --name blitzpay-minio \
  -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  quay.io/minio/minio server /data --console-address ":9001"
```

Create the bucket:

```bash
docker run --rm --network host \
  -e MC_HOST_local=http://minioadmin:minioadmin@localhost:9000 \
  minio/mc mb --ignore-existing local/blitzpay
```

## Run Tests

```bash
./gradlew test
./gradlew contractTest
```

Expected coverage for this feature:

- Multipart create with valid JPEG/PNG/WebP stores a product and object key.
- Multipart create/update accepts optional Markdown description up to 2,000 characters.
- Multipart update with valid image replaces the stored image key.
- MCP product ingestion with `imageBase64` and `imageContentType` stores a product image through the same object-key storage path.
- MCP product ingestion with crop coordinates stores the cropped image, not the uncropped source image.
- MCP-created branches and products are persisted as inactive by default and can be updated by later MCP calls while inactive.
- Description longer than 2,000 characters returns HTTP 400 and performs no DB/object write.
- Unsupported MIME type returns HTTP 400 and performs no DB/object write.
- Image larger than 5 MB returns HTTP 400 and performs no DB/object write.
- Storage upload failure rejects create/update and leaves no partial product record.
- Product list/get returns a signed `imageUrl` for products with a stored key.
- Product list/get returns `imageUrl: null` when no key exists or signing cannot resolve the object.

## Run App

```bash
./gradlew bootRun
```

## Manual API Check

Create a product with an image:

```bash
curl -X POST "http://localhost:8080/v1/merchants/${MERCHANT_ID}/products" \
  -H "Accept: application/json" \
  -F "name=Artisan Coffee Blend 250g" \
  -F "description=**Single-origin** medium roast with cocoa notes." \
  -F "unitPrice=12.50" \
  -F "image=@./coffee.webp;type=image/webp"
```

Expected response:

- HTTP `201 Created`
- `imageUrl` is a signed MinIO/S3 GET URL
- Database stores an object key, not the signed URL

List products:

```bash
curl "http://localhost:8080/v1/merchants/${MERCHANT_ID}/products" \
  -H "Accept: application/json"
```

Expected response:

- HTTP `200 OK`
- Only active products for `${MERCHANT_ID}`
- Each product with an image contains a fresh signed `imageUrl`

Reject unsupported image type:

```bash
curl -X POST "http://localhost:8080/v1/merchants/${MERCHANT_ID}/products" \
  -H "Accept: application/json" \
  -F "name=Invalid Image Product" \
  -F "unitPrice=1.00" \
  -F "image=@./notes.txt;type=text/plain"
```

Expected response:

- HTTP `400 Bad Request`
- Structured validation error
- No product record persisted
- No object uploaded

## MCP Ingestion Check

MCP catalog ingestion uses the same `MerchantProductService` storage path as REST, but it is optimized for agent workflows that extract data from menu photos or external sources.

Expected MCP behavior:

- `branch_id_by_name_or_create` creates a missing branch with `active = false`.
- `branch_id_by_name_or_create` finds and updates existing inactive branches by merchant/name.
- `product_id_by_name_or_create` creates a missing product with `active = false`.
- `product_id_by_name_or_create` finds and updates existing inactive products by merchant/branch/name.
- `product_id_by_name_or_create` and `merchant_product_update` accept `imageBase64` and `imageContentType`.
- Product images should be clear, high-quality, directly related to the product, JPEG/PNG/WebP, and 5 MB or smaller.
- Optional `cropX`, `cropY`, `cropWidth`, and `cropHeight` crop JPEG/PNG inputs before upload.

Example MCP product update payload shape:

```json
{
  "merchantId": "${MERCHANT_ID}",
  "branchId": "${BRANCH_ID}",
  "productName": "Erdbeer Becher",
  "unitPrice": "8.00",
  "description": "Vanilleeis, frische Erdbeeren, Sahne und selbst gemachte Erdbeersoße.",
  "imageBase64": "<base64-encoded JPEG bytes>",
  "imageContentType": "image/jpeg"
}
```

When using an uncropped source image:

```json
{
  "merchantId": "${MERCHANT_ID}",
  "branchId": "${BRANCH_ID}",
  "productName": "Erdbeer Becher",
  "unitPrice": "8.00",
  "description": "Vanilleeis, frische Erdbeeren, Sahne und selbst gemachte Erdbeersoße.",
  "imageBase64": "<base64-encoded JPEG bytes>",
  "imageContentType": "image/jpeg",
  "cropX": 120,
  "cropY": 2500,
  "cropWidth": 900,
  "cropHeight": 900
}
```
