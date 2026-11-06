# Preparation station configuration (issue #199)

GET and PUT `/v1/merchants/{merchantId}/branches/{branchId}/preparation-configuration`
require a Platform Admin or a Merchant Manager, using the same setup authorization
as Branch Tables. The branch must belong to the merchant in the path.

The body contains `stations` (stable UUID and unique name), `categoryDefaults`
(category UUID to station UUID), and `productOverrides` (product UUID to station UUID).
Defaults are branch-specific. Categories must belong to the merchant; products must
belong to the selected branch. All referenced stations must appear in the configuration.
An empty map entry is removed by omitting its key. Station names are trimmed.

Configuration is persisted in `merchant_preparation_configuration` through Liquibase.
A branch without configuration returns empty lists/maps and retains its existing queue.
Removing a station also requires removing any assignments to it in the submitted body.

Submitted order items snapshot their resolved station ID/name and maintain independent preparation
status. Product overrides take precedence over category defaults. Missing routing uses the reserved
zero UUID, shown as Unassigned / General kitchen. Existing work is migrated to that fallback,
retaining its previous preparation status; it is never rerouted from today's catalog.

Kitchen endpoints under `/v1/merchant/branches/{branchId}/kitchen`:

- GET `/stations`: configured stations, snapshots still needed by active work, and the fallback.
- GET `/stations/{stationId}/orders`: this station's open tickets and cancellations.
- GET `/stations/{stationId}/stream`: filtered SSE events, including empty READY tombstones when
  a station's last item is removed. Empty tombstones use order.changed, even for a cancellation,
  so they never create an empty cancellation card.
- POST `/stations/{stationId}/orders/{orderId}/preparation-status`: advance only this ticket.

All four require the existing branch group authorization. Station list/stream selection must
belong to the branch configuration or a retained active snapshot. Completion is checked against
the branch and the submitted ticket itself, allowing safe retries after a station is removed.
The legacy branch-wide completion endpoint rejects routed orders to prevent older clients from
completing other stations. Branches without configured stations retain their existing behavior.

Station completion locks prepared items against edits, serializes preparation and item mutations
on the customer Order row, and computes aggregate readiness across all remaining items.
Repeating the current PREPARING or READY status is a no-op. Cancelled orders reject completion.
Deleting the last pending item makes the remaining completed order ready. Shopper readiness
notifications are emitted only on the aggregate transition to READY. Dynamic SQL updates keep
commercial/payment field updates separate from preparation field updates.

The admin Branch edit page opens a fresh-data preparation settings dialog. Closing or
navigating away with unsaved changes requires confirmation. Product/category labels are
loaded with an explicit language; this form does not edit catalog text.
