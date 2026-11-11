# Product category images

Each merchant product category can have one optional image. Existing categories and JSON clients remain compatible.

`POST /v1/merchants/{merchantId}/product-categories` and `PUT /v1/merchants/{merchantId}/product-categories/{categoryId}` accept either the existing JSON body or `multipart/form-data`:

| Part | Content | Meaning |
| --- | --- | --- |
| `category` | `application/json` | The existing create/update category request, including name and optional estimated duration. Required. |
| `image` | File | Optional nonempty JPEG, PNG or WebP, at most 5 MiB. Replaces the previous image on update. |
| `removeImage` | Boolean | Update only; defaults to `false`. Set to `true` to remove the image. Cannot accompany `image`. |

Omitting image parts, including ordinary JSON updates, preserves the image. Category responses and branch catalog category responses include nullable `imageUrl`. URLs are signed, expire after the storage service's default download TTL (60 minutes), and must be refreshed by reloading categories; clients must not persist them as permanent public URLs.

Images use the existing private object storage, with unique keys under `merchants/{merchantId}/categories/{categoryId}/`. Replaced or removed objects are deleted after a successful database commit; new uploads are deleted on rollback. Category deletion also removes its image after commit. Failed cleanup is logged. No client-supplied storage key is accepted.

Mutations retain the existing authenticated category API access rules. Multipart files are capped at 25 MB at the servlet boundary (26 MB total request) to accommodate the existing voice API; category and product image policy still enforces 5 MiB. The Helm ingress allows 26 MiB requests so nginx does not reject valid image uploads at its default 1 MiB boundary.

Apply the Liquibase migration before deploying the new application. The admin dashboard should be deployed after the backend so its multipart requests are supported.
