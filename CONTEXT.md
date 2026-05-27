# Merchant Operations

This context models the operational side of a merchant on the BlitzPay platform: the business entity, its branches, and the commercial capabilities offered to customers. It explicitly excludes onboarding, compliance review, and application-processing workflow.

## Language

**Merchant**:
The long-lived business entity that owns branches, operational addresses, products, orders, payment options, and customer-facing capabilities.
_Avoid_: MerchantApplication, application, onboarding record

**Merchant Code**:
A stable system-generated business identifier for a Merchant used in operational and integration workflows.
_Avoid_: applicationReference, UUID as business identifier

**Merchant Name**:
An editable display name for a Merchant, separate from its stable Merchant Code.
_Avoid_: using code as display label

**Merchant Status**:
The operational lifecycle state of a Merchant, using a simple status model such as Active or Inactive.
_Avoid_: onboarding workflow states

**Merchant Contact Info**:
The merchant-level public contact surface, including website, email, and phone number.
_Avoid_: branch-only contact identity

**Branch**:
An operational location of a Merchant that can have its own address, contact details, payment setup, and customer-facing capabilities.
_Avoid_: Outlet, store, shop

**Branch Code**:
A stable system-generated business identifier for a Branch that is unique within its parent Merchant.
_Avoid_: branch name, UUID as business identifier

**Branch Name**:
An editable display name for a Branch, separate from its stable Branch Code.
_Avoid_: using code as display label

**Branch Status**:
The operational lifecycle state of a Branch, independent from the Merchant's status.
_Avoid_: deriving all branch availability from merchant status alone

**Branch Contact Override**:
Branch-specific website, email, and phone details that replace the merchant's public contact info for that branch when provided.
_Avoid_: requiring every branch to duplicate merchant contact info

**Branch Address**:
A structured postal address for a Branch and is mandatory because a Branch is an operational location. For a `MOBILE` branch this is the depot or registered business address, not the operational serving location.
_Avoid_: optional branch address, free-text branch address

**Branch Location**:
Geospatial data for a Branch, including coordinates and geofence radius, and is mandatory because a Branch is an operational location. For a `MOBILE` branch this is the depot location and is never used for proximity or Walk-In Ordering checks while an Appearance is active.
_Avoid_: optional branch coordinates

**Merchant Mobility**:
The operational mode of a Merchant, either `MOBILE` or `BRICK_AND_MORTAR`. `MOBILE` merchants operate from scheduled Appearances at variable locations; `BRICK_AND_MORTAR` merchants operate from fixed Branch Locations.
_Avoid_: merchant type, mobile flag, fixed flag

**Appearance**:
A scheduled commitment by a `MOBILE` branch to be present at a specific location during a defined time window. Carries a Declared Appearance Location set at creation and an optional Live Position updated during operation. An Appearance with an optional name.
_Avoid_: event, session, slot, deployment, engagement

**Declared Appearance Location**:
The lat/lng coordinates set when an Appearance is created. Used for customer discovery of upcoming and active Appearances. Serves as the fallback location when no Live Position has been pushed.
_Avoid_: planned location, scheduled location

**Live Position**:
The current GPS coordinates pushed by the merchant's device during an active Appearance. Overrides the Declared Appearance Location for proximity and geofence checks while present. Never required; the Declared Appearance Location is always the fallback.
_Avoid_: real-time location, GPS broadcast, current coordinates

**Active Appearance**:
An Appearance whose time window contains the current moment (`startTime <= now <= endTime`). The effective location of an Active Appearance is the Live Position when present, otherwise the Declared Appearance Location.
_Avoid_: live event, open slot, current appearance

**Currently Happening**:
The read-side query that returns all Active Appearances, optionally filtered by proximity to the caller's lat/lng and radius.
_Avoid_: live feed, active events, ongoing appearances

**Merchant Address**:
A structured postal address for a Merchant using the same field shape as a Branch address, but all fields are optional.
_Avoid_: primaryBusinessAddress, free-text address

**Merchant Location**:
Optional geospatial and place-enrichment data for a Merchant using the same field shape as Branch location data.
_Avoid_: mandatory merchant coordinates

**Place Enrichment**:
Optional third-party place metadata attached to a Merchant or Branch, such as a Google Place identifier and formatted place details.
_Avoid_: mandatory place provider dependency

**Merchant Offering**:
A platform-managed customer-facing offering enabled for a Merchant, such as pre-ordering, walk-in ordering, deferred payment, or appointment booking, and applies uniformly to all of its branches.
_Avoid_: Capability, service, mode, branch feature, vertical feature

**Pre-Order**:
The ability for a customer to place an order while away from the branch and collect or receive it later.
_Avoid_: Ordering, walk-in order, in-place order

**Walk-In Ordering**:
The ability for a customer to place an order only while physically present at the branch.
_Avoid_: Ordering, walking order

**Deferred Payment**:
The ability for a customer to pay after an order is created rather than immediately at order time, for either Pre-Order or Walk-In Ordering.
_Avoid_: standalone payment type

**Appointment Booking**:
The ability for a customer to reserve a time slot with the merchant.
_Avoid_: visit, booking slot only

**Service Category**:
A merchant-defined classification for services that can be ordered or scheduled, such as haircut, shave, or color treatment.
_Avoid_: product category, service type, offering type

**Estimated Service Duration**:
The expected duration in minutes for completing a service within a Service Category, used for appointment scheduling and capacity planning.
_Avoid_: appointment duration, slot duration, time required

**Order**:
A customer order always placed against a specific Branch of a Merchant.
_Avoid_: merchant-only order

**Order Type**:
The explicit business classification of an Order, distinguishing Pre-Order from Walk-In Ordering.
_Avoid_: inferring order kind from channel, actor, or payment state

## Relationships

- A **Merchant** owns one or more **Branch**es
- A **Merchant** may temporarily exist without any **Branch**
- A **Merchant** has exactly one **Merchant Code**
- A **Merchant** has exactly one editable **Merchant Name**
- A **Merchant** has exactly one **Merchant Status**
- A **Merchant** may have zero or one **Merchant Contact Info**
- A **Branch** belongs to exactly one **Merchant**
- A **Branch** has exactly one **Branch Code**, unique within its parent **Merchant**
- A **Branch** has exactly one editable **Branch Name**
- A **Branch** has exactly one **Branch Status**
- A **Branch** may have zero or one **Branch Contact Override**
- A **Branch** must have exactly one **Branch Address**
- A **Branch** must have exactly one **Branch Location**
- An **Order** belongs to exactly one **Branch**
- An **Order** has exactly one **Order Type**
- A **Merchant** may have zero or one **Merchant Address**
- A **Merchant** may have zero or one **Merchant Location**
- A **Merchant** has exactly one **Merchant Mobility** (`MOBILE` or `BRICK_AND_MORTAR`)
- All **Branch**es of a `MOBILE` **Merchant** are implicitly mobile; a `MOBILE` **Merchant** cannot have `BRICK_AND_MORTAR` branches and vice versa
- A `MOBILE` **Branch** may have zero or more **Appearance**s
- A `BRICK_AND_MORTAR` **Branch** does not have **Appearance**s
- An **Appearance** belongs to exactly one **Branch**
- An **Appearance** has exactly one **Declared Appearance Location**
- An **Appearance** may have zero or one **Live Position**
- An **Appearance** may have zero or one name
- An **Active Appearance**'s effective location is its **Live Position** when present, otherwise its **Declared Appearance Location**
- Walk-In Ordering for a `MOBILE` branch uses the **Active Appearance**'s effective location for geofence checks, not the **Branch Location**
- The **Branch Location** of a `MOBILE` branch is the depot location and is never used for proximity checks while an **Appearance** is active
- Any authenticated user of the owning **Merchant** may push a **Live Position** update to an **Appearance**
- A `MOBILE` **Branch** may not have two **Appearance**s whose time windows overlap; the system rejects the creation of an overlapping **Appearance** at write time
- A **Merchant** enables zero or more **Merchant Offering** entries
- All **Branch**es of the same **Merchant** inherit the same **Merchant Offering** set
- **Appointment Booking** is independent from **Pre-Order** and **Walk-In Ordering**
- **Merchant Offering** values come from a fixed platform-managed catalogue
- A **Merchant** enables offerings by selecting from the platform-managed catalogue, not by storing hard-coded boolean flags
- A **Merchant** may exist before any offering is enabled
- **Deferred Payment** may be enabled only when **Pre-Order** or **Walk-In Ordering** is enabled
- **Merchant Offering** is configured at the **Merchant** level only; branch-level overrides are out of scope in this context
- Customer actions such as order creation and appointment booking are allowed only when the corresponding **Merchant Offering** is enabled
- **Merchant Status** acts as a global gate; an inactive merchant disables branch-facing flows even if a branch itself is active
- A **Merchant** owns zero or more **Service Category** entries
- A **Service Category** belongs to exactly one **Merchant**
- A **Service Category** may have zero or one **Estimated Service Duration**
- **Estimated Service Duration** is stored in minutes and must be positive when present
- **Estimated Service Duration** is capped at 480 minutes (8 hours) for operational sanity
- **Estimated Service Duration** may only be written (on create or update) when the **Merchant** has **Appointment Booking** enabled; writing it without that offering is rejected
- Disabling **Appointment Booking** does not clear existing **Estimated Service Duration** values; they are inert and preserved for if the offering is re-enabled
- **Walk-In Ordering** is decided by the backend using the customer's geolocation relative to the branch
- **Pre-Order** may be used regardless of the customer's proximity to the branch
- The customer requests an **Order Type**, and the backend validates it against merchant offerings and geolocation rules
- **Walk-In Ordering** requires customer geolocation and is rejected when geolocation is absent
- **Walk-In Ordering** requires geolocation that is recent and sufficiently accurate
- **Deferred Payment** being enabled on the merchant means an order may choose it, not that every order must use it
- **Appointment Booking** remains a merchant offering in this context, but its workflow and persistence model are out of scope for this refactor

## Example dialogue

> **Dev:** "When a **Merchant** is approved during onboarding, do we update the same record?"
> **Domain expert:** "No. Onboarding is outside this context. This context starts with the operational **Merchant** that owns **Branch**es."
>
> **Dev:** "Can one branch offer appointments while another branch of the same merchant does not?"
> **Domain expert:** "No. Capabilities are configured on the **Merchant** and apply to all of its **Branch**es."
>
> **Dev:** "Does the **Merchant** use the same address structure as a **Branch**?"
> **Domain expert:** "Yes, but every merchant address and location field is optional."
>
> **Dev:** "Can a **Branch** exist without address or location details?"
> **Domain expert:** "No. A **Branch** is an operational location, so address and location are mandatory."
>
> **Dev:** "Is a postal address enough for a **Branch**, or do we also require coordinates?"
> **Domain expert:** "Both are required. A **Branch** must be addressable and geolocatable."
>
> **Dev:** "Must every **Branch** have a Google Place ID too?"
> **Domain expert:** "No. Place enrichment is optional; the branch itself is still valid without it."
>
> **Dev:** "What is the difference between **Pre-Order** and **Walk-In Ordering**?"
> **Domain expert:** "**Pre-Order** is placed while the customer is away from the branch. **Walk-In Ordering** is only allowed while the customer is physically at the branch."
>
> **Dev:** "Does **Appointment Booking** require an order flow?"
> **Domain expert:** "No. **Appointment Booking** is an independent capability."
>
> **Dev:** "Can merchants invent their own offering names?"
> **Domain expert:** "No. Offerings come from a fixed platform-managed catalogue."
>
> **Dev:** "Why not just store four boolean fields on the merchant?"
> **Domain expert:** "Because offerings are a controlled catalogue that may grow over time; merchants enable entries from that catalogue rather than changing the schema for every new offering."
>
> **Dev:** "Can a **Merchant** exist with no offerings enabled yet?"
> **Domain expert:** "Yes. A **Merchant** may exist before any offering is enabled."
>
> **Dev:** "Can **Deferred Payment** be enabled on its own?"
> **Domain expert:** "No. **Deferred Payment** requires **Pre-Order** or **Walk-In Ordering**."
>
> **Dev:** "Can one branch of a merchant override the merchant's offering set?"
> **Domain expert:** "No. Offerings are merchant-wide in this context; branch-level overrides are out of scope."
>
> **Dev:** "Must a **Merchant** have a **Branch** immediately when it is created?"
> **Domain expert:** "No. A **Merchant** may exist before any **Branch** is created."
>
> **Dev:** "Is a branch name enough to identify a **Branch** in business workflows?"
> **Domain expert:** "No. A **Branch** needs a stable **Branch Code** that is unique within its **Merchant**."
>
> **Dev:** "Should the operational **Merchant** still be identified by an application reference?"
> **Domain expert:** "No. The operational **Merchant** needs its own stable **Merchant Code**."
>
> **Dev:** "Are merchant and branch codes assigned manually?"
> **Domain expert:** "No. **Merchant Code** and **Branch Code** are system-generated."
>
> **Dev:** "Can merchant and branch names change later?"
> **Domain expert:** "Yes. **Merchant Name** and **Branch Name** are editable display labels and are separate from stable codes."
>
> **Dev:** "Where do website, email, and phone live?"
> **Domain expert:** "A **Merchant** can have merchant-level public contact info, and a **Branch** can override it with branch-specific contact details."
>
> **Dev:** "Can a branch override only phone and email, or website too?"
> **Domain expert:** "A branch may override website, email, and phone."
>
> **Dev:** "Must every **Merchant** provide website, email, and phone?"
> **Domain expert:** "No. **Merchant Contact Info** is optional."
>
> **Dev:** "Can an **Order** belong only to the merchant with no branch selected?"
> **Domain expert:** "No. Every **Order** belongs to a specific **Branch**."
>
> **Dev:** "If a merchant has not enabled an offering, can customers still use that flow?"
> **Domain expert:** "No. Offerings are enforced as runtime rules for order and appointment flows."
>
> **Dev:** "Should the system infer whether an order is pre-order or walk-in?"
> **Domain expert:** "No. Every **Order** carries an explicit **Order Type**."
>
> **Dev:** "Who decides whether **Walk-In Ordering** is allowed?"
> **Domain expert:** "The backend decides it based on the customer's geolocation relative to the branch."
>
> **Dev:** "If the customer is already at the branch, is **Pre-Order** blocked?"
> **Domain expert:** "No. **Pre-Order** may still be used regardless of proximity."
>
> **Dev:** "Does the backend derive the order type automatically from geolocation?"
> **Domain expert:** "No. The customer requests the intended **Order Type**, and the backend validates whether that choice is allowed."
>
> **Dev:** "What if the customer requests **Walk-In Ordering** but does not share geolocation?"
> **Domain expert:** "The request is rejected. **Walk-In Ordering** requires customer geolocation."
>
> **Dev:** "Is any geolocation sample enough for **Walk-In Ordering**?"
> **Domain expert:** "No. The backend requires geolocation that is recent and sufficiently accurate."
>
> **Dev:** "If a merchant offers **Deferred Payment**, does every order have to use it?"
> **Domain expert:** "No. The merchant offers the option, and each order may choose whether to use it."
>
> **Dev:** "Are we designing appointment workflow in this refactor?"
> **Domain expert:** "No. **Appointment Booking** stays in the offering catalogue, but its detailed workflow is out of scope for now."
>
> **Dev:** "Does the operational **Merchant** keep onboarding statuses like submitted or verification?"
> **Domain expert:** "No. The operational **Merchant** uses a simpler lifecycle such as Active and Inactive."
>
> **Dev:** "Does branch availability come only from merchant status?"
> **Domain expert:** "No. A **Branch** has its own operational status independent from the **Merchant**."
>
> **Dev:** "If a merchant is inactive but a branch is active, can that branch still serve traffic?"
> **Domain expert:** "No. An inactive **Merchant** disables branch-facing flows globally."
>
> **Dev:** "What is a **Service Category**?"
> **Domain expert:** "A merchant-defined classification for services like haircut, shave, or color treatment. They belong to the **Merchant**, not individual branches."
>
> **Dev:** "Does every **Service Category** need an **Estimated Service Duration**?"
> **Domain expert:** "No. Duration is optional, but when provided it must be positive and capped at 8 hours for sanity."
>
> **Dev:** "Is duration the appointment slot size or the service time?"
> **Domain expert:** "It's the expected service time. Appointment slot sizing and staff availability are separate concerns."
>
> **Dev:** "Can a merchant that doesn't offer **Appointment Booking** still set an **Estimated Service Duration** on a category?"
> **Domain expert:** "No. Duration is only meaningful for appointment scheduling, so writing it without **Appointment Booking** enabled is rejected. Storing it silently would create invisible, unusable data."
>
> **Dev:** "If a merchant disables **Appointment Booking**, do we clear all the durations they've set?"
> **Domain expert:** "No. We leave them in place — they're inert without the offering, and clearing them on toggle would be a destructive, irreversible side-effect. If the offering is re-enabled, the durations are still there."

**Connected Account**:
The Stripe Express account representing a Merchant's financial identity on the BlitzPay platform. Created when a Merchant enables Stripe as a payment channel.
_Avoid_: Stripe account, integration account, intermediate account

**Connected Account ID**:
The Stripe-issued identifier (`acct_XXXX`) for a Merchant's Connected Account. Stored at the Merchant level. Used when creating direct charges on behalf of that Merchant.
_Avoid_: Stripe user ID, stripe_id, integration ID

**Connect Onboarding**:
The Stripe-hosted process a Merchant completes to create and verify their Connected Account. Triggered by the admin dashboard when Stripe is enabled for a Merchant. Considered complete only when Stripe confirms `charges_enabled: true` via webhook.
_Avoid_: Stripe onboarding, account setup, payment onboarding

**Connect Onboarding URL**:
The one-time Stripe-generated URL returned to the merchant app, directing the Merchant to Stripe's hosted identity and banking verification form. Expires if unused; a dedicated refresh endpoint generates a replacement.
_Avoid_: Stripe link, onboarding link, redirect URL

**Account Holder**:
The Adyen Balance Platform entity representing a Merchant's legal and financial identity on the BlitzPay platform. Created when a Merchant enables Adyen as a payment provider. Owns one or more Balance Accounts. The Adyen equivalent of a Connected Account.
_Avoid_: Adyen account, sub-merchant, marketplace account

**Account Holder ID**:
The Adyen-assigned identifier (`legalEntityId`) for a Merchant's Account Holder. Stored at the Merchant level alongside the Balance Account ID. Together they are the Adyen equivalent of a single Connected Account ID.
_Avoid_: Adyen legal entity, legalEntity, adyen_id

**Balance Account ID**:
The Adyen-assigned identifier (`balanceAccountId`) for the account where a Merchant's funds land after a split-routed payment. A child of the Account Holder. Must be stored alongside the Account Holder ID — neither is sufficient alone for payment routing.
_Avoid_: Adyen wallet, sub-account ID, funds account

**Adyen Onboarding**:
The Adyen-hosted process a Merchant completes to verify their Account Holder. Triggered when the Merchant enables Adyen as a payment provider in the admin dashboard. Considered complete only when Adyen confirms the Receive Payments Capability is `allowed` via webhook. The Adyen equivalent of Connect Onboarding.
_Avoid_: Adyen KYC, account setup, payment onboarding

**Adyen Onboarding URL**:
The one-time Adyen-generated URL returned to the admin dashboard, directing the Merchant to Adyen's hosted identity and banking verification form. Generated via `POST /legalEntities/{id}/onboardingLinks`. Expires if unused; a dedicated refresh endpoint generates a replacement. The Adyen equivalent of Connect Onboarding URL.
_Avoid_: Adyen link, onboarding link, redirect URL

**Receive Payments Capability**:
The Adyen Balance Platform signal that a Merchant's Account Holder is verified and permitted to accept live payments. Confirmed via the `balanceAccount.updated` webhook when the `receivePayments` capability status becomes `allowed`. The Adyen equivalent of Stripe's `charges_enabled: true`.
_Avoid_: Adyen verified, charges enabled, KYC complete

## Flagged ambiguities

- Overlapping **Appearance**s on the same branch could have been allowed with a last-write-wins rule. Resolved: overlaps are rejected at write time; the Walk-In Ordering geofence fallback requires an unambiguous declared location.
- **Live Position** push could have been restricted to a specific branch-operator role. Resolved: any authenticated user of the owning **Merchant** may push a **Live Position**; role-based restriction is out of scope for now.
- Mobile merchant location could have been modelled as a real-time broadcast only, with no time window. Resolved: an **Appearance** carries a mandatory time window; the **Live Position** is an optional enrichment on top of it, not the primary concept.
- The `MOBILE`/`BRICK_AND_MORTAR` distinction could have lived on the Branch. Resolved: it lives on the **Merchant**; all branches of a **Merchant** share the same **Merchant Mobility**. A business with a mixed fleet must register as separate merchants.
- An **Appearance** could have stored only a time window with no pre-declared location. Resolved: the **Declared Appearance Location** is mandatory at creation time so customers can discover and plan around future **Appearance**s.
- Walk-In Ordering geofence for `MOBILE` branches could have used the depot **Branch Location**. Resolved: it uses the **Active Appearance**'s effective location (Live Position falling back to Declared Appearance Location).
- "event" was used informally to describe time-windowed mobile presence. Resolved: use **Appearance** to avoid collision with domain integration events published via Spring `ApplicationEventPublisher`.
- "merchant" was previously used to mean both the operational business and the onboarding/application record. Resolved: this context uses **Merchant** only for the operational business; onboarding is out of scope here.
- "OrderOnly", "OrderAndPayLater", and "Appointment" could have been modelled at merchant or branch scope. Resolved: they are **Merchant Offering** values configured once per **Merchant** and inherited by all **Branch**es.
- "OrderOnly", "OrderAndPayLater", and "Appointment" could have been modelled as exclusive modes. Resolved: they are combinable **Merchant Offering** values.
- "ORDERING" and "WALKING_ORDER" overlapped. Resolved: **Pre-Order** means order while away from the branch; **Walk-In Ordering** means order while physically present at the branch.
- "Deferred payment" could have been tied to only one ordering path. Resolved: **Deferred Payment** can apply to both **Pre-Order** and **Walk-In Ordering**.
- "Deferred payment" could have been enabled without any ordering flow. Resolved: **Deferred Payment** requires **Pre-Order** or **Walk-In Ordering**.
- "Appointment" could have been tied to ordering. Resolved: **Appointment Booking** is independent from both **Pre-Order** and **Walk-In Ordering**.
- Merchant-wide offerings could have implied future per-branch overrides. Resolved: **Merchant Offering** is merchant-level only in this context; branch-level overrides are out of scope.
- A merchant could have been forced to create a branch immediately. Resolved: a **Merchant** may temporarily exist with zero **Branch**es.
- A branch name could have been used as the operational identifier. Resolved: each **Branch** has a merchant-scoped **Branch Code** as its stable business identifier.
- The operational merchant could have kept using onboarding-style `applicationReference`. Resolved: the operational **Merchant** uses its own stable **Merchant Code**.
- Merchant and branch identifiers could have been manually assigned. Resolved: **Merchant Code** and **Branch Code** are system-generated.
- Merchant and branch names could have been used as stable identifiers. Resolved: names are editable display labels, while codes are the stable business identifiers.
- Contact details could have lived only on merchant or only on branch. Resolved: **Merchant** owns primary public contact info, and **Branch** may provide contact overrides.
- Branch contact override could have been limited to email and phone. Resolved: **Branch Contact Override** may replace website, email, and phone.
- Merchant contact info could have been mandatory. Resolved: **Merchant Contact Info** is optional.
- Orders could have been attached to merchant without a branch. Resolved: every **Order** belongs to a specific **Branch**.
- Merchant offerings could have been descriptive only. Resolved: offerings are enforced as runtime validation rules for customer flows.
- Order kind could have been inferred from context. Resolved: every **Order** stores an explicit **Order Type**.
- Walk-in eligibility could have been left to the client. Resolved: **Walk-In Ordering** is enforced by the backend using customer geolocation and branch location rules.
- Pre-order could have been blocked when the customer is already at the branch. Resolved: **Pre-Order** is allowed regardless of customer proximity.
- Order type could have been derived automatically from geolocation. Resolved: the customer requests the **Order Type**, and the backend validates it.
- Walk-in ordering could have been allowed without customer location. Resolved: **Walk-In Ordering** is rejected when customer geolocation is absent.
- Walk-in ordering could have accepted any location sample. Resolved: **Walk-In Ordering** requires geolocation that is recent and sufficiently accurate.
- Deferred payment could have been automatically applied to every eligible order. Resolved: the merchant enables the option, and each order decides whether to use it.
- Appointment booking could have implied immediate workflow design in this refactor. Resolved: **Appointment Booking** remains in the offering catalogue, but its domain workflow is deferred.
- The operational merchant could have retained onboarding statuses. Resolved: **Merchant Status** uses a simple operational lifecycle rather than onboarding workflow states.
- Branch availability could have been derived only from merchant state. Resolved: **Branch** has its own independent **Branch Status**.
- Merchant and branch statuses could have conflicted with no precedence rule. Resolved: **Merchant Status** is the global gate and overrides active branches for customer-facing flows.
- Merchant capabilities could have been free-form merchant-defined values. Resolved: **Merchant Offering** values come from a fixed platform-managed catalogue.
- Merchant capabilities could have been stored as fixed boolean fields. Resolved: use a platform-managed offering catalogue plus merchant-to-offering assignment instead of hard-coded merchant flags.
- A merchant could have been forced to enable a capability at creation time. Resolved: a **Merchant** may temporarily exist with zero enabled offerings.
- Merchant address could have remained a single free-text field while branches used structured fields. Resolved: **Merchant** and **Branch** share the same structured address shape, but merchant address and location are entirely optional.
- Merchant and branch could have shared the same optionality rules. Resolved: **Merchant** address/location are optional, while **Branch** address/location are mandatory.
- Branch location could have meant only postal address. Resolved: every **Branch** requires both a structured postal address and geospatial location data.
- Branch validity could have depended on an external place provider. Resolved: `googlePlaceId` and other **Place Enrichment** data are optional for both **Merchant** and **Branch**.
- "Product category" could have been used for inventory, services, or menu items. Resolved: use **Service Category** for merchant-defined service classifications relevant to ordering and appointments.
- Estimated duration could have been mandatory for all service categories. Resolved: **Estimated Service Duration** is optional; services without duration can exist but cannot be scheduled.
- Service duration could have been conflated with appointment slot size or staff availability. Resolved: **Estimated Service Duration** models only the expected service time; slot sizing and staff schedules are separate concerns.
- Service categories could have been branch-specific. Resolved: **Service Category** belongs to **Merchant** and applies uniformly across all branches.
- Estimated service duration could have been accepted for any merchant regardless of their offerings. Resolved: **Estimated Service Duration** may only be written when **Appointment Booking** is enabled; the write is rejected otherwise.
- Disabling **Appointment Booking** could have triggered a cascade-clear of all stored durations. Resolved: existing durations are left in place and treated as inert; clearing on toggle is destructive and non-reversible.
- Duration update could have had its own dedicated endpoint (`PATCH /product-categories/{id}/duration`). Resolved: duration is maintained through the standard category update endpoint (`PUT /product-categories/{id}`) alongside name; a dedicated endpoint was a smell that duplicated the create path's optionality.
- Stripe Connect and Adyen for Platforms could have been enabled simultaneously per Merchant. Resolved: a Merchant operates on exactly one platform PSP at a time; the active PSP is selected in the admin dashboard. Enabling one while the other is inactive is the only supported state.
- A single PSP-agnostic term (e.g. "Platform Sub-Account") could have covered both Stripe Connected Accounts and Adyen Account Holders. Resolved: PSP-specific terms are used side by side (**Connected Account** for Stripe, **Account Holder** for Adyen) because the two models differ structurally — Stripe uses one identifier (`acct_XXXX`) while Adyen uses two (**Account Holder ID** + **Balance Account ID**).
