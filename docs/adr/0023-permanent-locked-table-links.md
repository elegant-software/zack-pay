# ADR-0023 — Table Links are permanent and NFC tags are locked; geolocation still proves presence

## Status
Accepted

## Context

Shoppers check in to a Table by scanning a QR code or tapping an NFC tag (zack-pay#195). Both carry the
same Table Link. A plain NTAG sticker can be rewritten by anyone with a phone, so an unlocked tag could be
pointed at a phishing page. A static link can also be photographed and opened from anywhere, so it cannot
prove the shopper is at the Branch.

## Decision

- A Table Link is `https://<storefront host>/t/{tableCode}`. The Table Code is opaque, system-generated and
  unique across the platform; it never changes. The editable Table Name is never part of the link.
- The merchant app writes the link as a single NDEF URI record and **locks the tag read-only** right after.
- A Table check-in does **not** prove presence. Walk-In Ordering at a Table still requires recent, accurate
  geolocation, exactly like Walk-In Ordering without a Table.
- A deactivated Table keeps its code; its link falls back to the Branch storefront without a Table.

## Consequences

- Stickers last forever and can't be tampered with. Changing a link means replacing the sticker.
- Customers who deny location can't order at a Table (same as Walk-In today).
- A copied link is harmless: ordering from elsewhere fails the geolocation check.
- Opaque codes keep QR codes small and fit NTAG213, and renaming or renumbering Tables costs nothing.

## Considered Options

- **Rotating links / signed short-lived tokens as proof of presence:** tags could not be locked, so they'd
  need password-protected rewrites or powered/dynamic tags, and printed QR codes would stop working.
- **Readable links (`/m/{merchant}/b/{branch}/t/5`):** renumbering tables breaks locked stickers.
- **Unlocked tags:** anyone could repoint a table to a malicious URL.
