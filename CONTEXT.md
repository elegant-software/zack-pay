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

**Branch Opening Hours**:
The recurring weekly time windows when a Branch is open for customer-facing activity.
_Avoid_: employee availability, branch status, appointment slots

**Branch Opening Exception**:
A date-specific change to Branch Opening Hours, such as a holiday closure or one-off extra opening window.
_Avoid_: employee time off, branch status change, temporary employee availability

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

**Employee**:
A person who works for a Merchant and may be assigned to appointments.
_Avoid_: staff, barber, chef, worker

**Employee Status**:
The operational lifecycle state of an Employee, such as Active or Inactive.
_Avoid_: deleted employee, employment record state

**Employee Selection**:
The shopper's choice of either a specific Employee or any available Employee when requesting an appointment.
_Avoid_: staff preference, barber choice, automatic assignment only

**Any-Employee Assignment**:
The deterministic assignment of one qualified and available Employee when the shopper chooses any available Employee.
_Avoid_: random staff assignment, unassigned appointment, shopper-hidden manual assignment

**Appointment Search**:
The shopper query for Bookable Time after selecting a Branch and Product Category.
_Avoid_: branch browsing, catalogue screen, slot list only

**Selectable Employee**:
An Employee shown to shoppers for a selected Product Category and booking window because they can perform the service and have Bookable Time.
_Avoid_: all employees, unavailable employee, unqualified employee

**Employee Branch Assignment**:
The relationship that says an Employee can serve appointments at a Branch.
_Avoid_: branch employee duplicate, employee location copy

**Employee Availability**:
The recurring weekly time windows when an Employee can be assigned to appointments at a Branch.
_Avoid_: opening hours, calendar event, appointment slot

**Availability Exception**:
A date-specific change to Employee Availability, such as time off, a holiday, or a one-off extra working window.
_Avoid_: schedule edit, temporary opening hours, ad hoc slot

**Employee Service Assignment**:
The relationship that says an Employee can perform a Product Category.
_Avoid_: skill tag, service permission, employee product assignment

**Bookable Time**:
A generated appointment start time calculated from Branch Opening Hours, Employee Availability, Availability Exceptions, existing appointments, and the selected Product Category's Estimated Service Duration.
_Avoid_: merchant-created slot, stored slot, calendar event

**Booking Interval**:
The Merchant-configured cadence used to generate possible Bookable Time start times.
_Avoid_: service duration, appointment length, slot duration

**Booking Lead Time**:
The minimum amount of advance notice required before a shopper can book an Appointment.
_Avoid_: cancellation window, service duration, preparation time

**Booking Horizon**:
The maximum distance into the future that a shopper can book an Appointment.
_Avoid_: availability period, calendar range, retention window

**Appointment**:
A confirmed reservation for a Product at a Branch, assigned to exactly one Employee and one time window.
_Avoid_: pending request, order, visit

**Appointment Status**:
The lifecycle state of an Appointment, such as Booked, Cancelled, or Completed.
_Avoid_: payment status, order status, approval state

**Appointment Customer**:
The customer identity for an Appointment, either linked to a shopper account or represented by appointment contact details.
_Avoid_: shopper-only booking, anonymous booking

**Appointment Contact Details**:
The customer name and contact information captured for an Appointment so the Merchant can identify and reach the customer.
_Avoid_: profile data only, merchant note

**Shopper-Owned Contact Details**:
Appointment Contact Details supplied by a linked shopper account and editable only by that shopper.
_Avoid_: merchant-edited shopper profile, contact-only customer

**Merchant-Captured Contact Details**:
Appointment Contact Details entered by a Merchant Manager or Merchant Employee for a customer without a linked shopper account.
_Avoid_: shopper profile, anonymous appointment, customer account

**Appointment Note**:
Merchant-visible operational text attached to an Appointment.
_Avoid_: private customer note, chat message, medical record

**Appointment Reminder**:
A shopper-app notification sent before a linked shopper's Appointment.
_Avoid_: merchant reminder, SMS reminder, email reminder, contact-only reminder

**Appointment Notification**:
A shopper-app notification sent when a linked shopper's Appointment is booked, cancelled, or rescheduled.
_Avoid_: reminder, merchant notification, SMS notification, email notification

**Reminder Lead Time**:
The Merchant-configured amount of time before an Appointment when an Appointment Reminder should be sent.
_Avoid_: booking lead time, cancellation window, per-appointment reminder

**Appointment Setup**:
The merchant configuration needed to produce Bookable Time values, including active Employees, assignments, availability, branch opening, and schedulable services.
_Avoid_: offering toggle, onboarding completeness, branch status only

**Appointment Rescheduling**:
Changing an existing Appointment's Product, time, Employee, or Branch while keeping the same Appointment identity.
_Avoid_: cancel-and-rebook, new appointment copy

**Branch Timezone**:
The local timezone used to interpret appointment availability, Bookable Time values, and calendar dates for a Branch.
_Avoid_: merchant timezone, user device timezone, server timezone

**Merchant Manager**:
A merchant user who can manage appointments across the Merchant, including appointments assigned to other Employees.
_Avoid_: merchant chef, owner-only, admin, onboarding reviewer

**Merchant Employee**:
A merchant user linked to one Employee profile for operating that Employee's own calendar and appointments.
_Avoid_: legal person, onboarding actor, generic branch group member

**Merchant Operations Role**:
The appointment and calendar permission role a merchant user holds, such as Merchant Manager or Merchant Employee.
_Avoid_: onboarding role, legal person role, Keycloak group only

**Merchant User Assignment**:
The BlitzPay-owned assignment of a merchant user to Merchant Operations Roles and, when relevant, an Employee link.
_Avoid_: identity-provider-only role, raw group membership, legal person record

**Employee Calendar**:
The appointment view for one Employee, containing appointments assigned to that Employee.
_Avoid_: staff calendar, personal slots, employee schedule

**Merchant Calendar**:
The appointment view for a Merchant across Employees and Branches.
_Avoid_: admin calendar, chef calendar, global slots

**Branch Calendar**:
The appointment view for one Branch, containing appointments booked at that Branch.
_Avoid_: catalogue calendar, branch slots, admin page

**Shopper Appointment History**:
The shopper-facing view of upcoming and past Appointments linked to a shopper account.
_Avoid_: contact-only bookings, merchant calendar, order history

**Support Appointment Override**:
An admin support action that changes an Appointment when merchant-side self-service cannot resolve the issue.
_Avoid_: normal merchant operation, support booking flow, silent calendar edit

**Override Reason**:
The required explanation for a Support Appointment Override.
_Avoid_: optional note, internal comment, silent support edit

**Product Category**:
A merchant-defined classification for products or services that can be ordered or scheduled, such as haircut, shave, or color treatment.
_Avoid_: service category, service type, offering type

**Product**:
A merchant-defined item or service that belongs to a Product Category and may be booked as an Appointment.
_Avoid_: product category, appointment category, service category

**Bookable Product**:
An active Product available to shoppers for appointment booking at a Branch.
_Avoid_: inactive product, hidden product, category-only booking

**Appointment Price**:
The informational Product price shown for an Appointment without creating an Order or requiring payment.
_Avoid_: payment amount, order total, deposit

**Appointment Product Snapshot**:
The Product name and informational price captured when an Appointment is booked or rescheduled.
_Avoid_: live product reference only, order item snapshot, payment line item

**Appointment Duration Snapshot**:
The Estimated Service Duration captured when an Appointment is booked or rescheduled.
_Avoid_: live category duration only, slot duration, calendar display guess

**Estimated Service Duration**:
The expected duration in minutes for completing products within a Product Category, used for appointment scheduling and capacity planning.
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
- A **Branch** may have zero or more **Branch Opening Hours** entries
- A **Branch** may have zero or more **Branch Opening Exception** entries
- A **Branch Opening Exception** changes opening for a specific date without changing recurring **Branch Opening Hours**
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
- **Appointment Booking** is supported only for `BRICK_AND_MORTAR` **Merchant**s
- **Appointment Booking** may be enabled before **Appointment Setup** is complete
- **Merchant Offering** values come from a fixed platform-managed catalogue
- A **Merchant** enables offerings by selecting from the platform-managed catalogue, not by storing hard-coded boolean flags
- A **Merchant** may exist before any offering is enabled
- A **Merchant** may have zero or one **Booking Interval**
- A **Merchant** may have zero or one **Booking Lead Time**
- A **Merchant** may have zero or one **Booking Horizon**
- A **Merchant** may have zero or one **Reminder Lead Time**
- **Deferred Payment** may be enabled only when **Pre-Order** or **Walk-In Ordering** is enabled
- **Merchant Offering** is configured at the **Merchant** level only; branch-level overrides are out of scope in this context
- Customer actions such as order creation and appointment booking are allowed only when the corresponding **Merchant Offering** is enabled
- A **Merchant** owns zero or more **Employee** entries
- An **Employee** belongs to exactly one **Merchant**
- An **Employee** has exactly one **Employee Status**
- Inactive **Employee** entries remain visible for historical **Appointment** references but are not bookable
- An **Employee** with future **Booked** **Appointment** entries may not be set inactive until those appointments are cancelled or rescheduled
- **Employee** is separate from **Merchant User Assignment**; an **Employee** may exist without a merchant user login
- An **Employee** may have one or more **Employee Branch Assignment** entries
- An **Employee Branch Assignment** belongs to exactly one **Employee** and exactly one **Branch**
- **Employee Branch Assignment** is required before an **Employee** can have **Employee Availability** or **Appointment** entries at that **Branch**
- An **Employee** may have zero or more **Employee Availability** entries
- **Employee Availability** is scoped to exactly one **Employee** and exactly one **Branch**
- An **Employee** may have multiple **Employee Availability** windows on the same weekday and Branch when those windows do not overlap
- An **Employee** may have zero or more **Availability Exception** entries
- An **Availability Exception** changes availability for a specific date without changing the recurring **Employee Availability**
- An **Availability Exception** may remove availability or add extra availability for its date
- An **Employee** may have zero or more **Employee Service Assignment** entries
- An **Employee Service Assignment** belongs to exactly one **Employee** and exactly one **Product Category**
- **Employee Service Assignment** applies to all **Product** entries in the assigned **Product Category**
- **Bookable Time** is generated from **Branch Opening Hours**, **Branch Opening Exception**, **Employee Availability**, **Availability Exception**, existing appointments, **Employee Service Assignment**, and the selected **Product**'s **Product Category** **Estimated Service Duration**
- A **Bookable Time** is valid only when the full appointment time window fits within **Branch Opening Hours** and **Employee Availability**
- **Booking Interval** controls the cadence of generated **Bookable Time** start times and is separate from **Estimated Service Duration**
- **Booking Interval** is configured at the **Merchant** level and applies across all **Branch**es
- **Booking Lead Time** is configured at the **Merchant** level and applies across all **Branch**es
- A **Bookable Time** must satisfy **Booking Lead Time**
- **Booking Horizon** is configured at the **Merchant** level and applies across all **Branch**es
- A **Bookable Time** must fall within **Booking Horizon**
- **Bookable Time** is empty when **Appointment Setup** is incomplete
- **Bookable Time** is not manually created one by one by the **Merchant**
- **Appointment Search** requires a selected **Branch** and **Product Category**
- **Appointment Search** requires an active **Merchant** and active **Branch**
- **Appointment Search** returns only **Bookable Product** entries
- **Selectable Employee** includes only **Employee** entries that can perform the selected **Product Category** and have **Bookable Time** in the searched window
- A **Product Category** without **Estimated Service Duration** cannot be used for **Appointment** booking
- **Employee Selection** may name a specific **Employee** or request any available **Employee**
- **Any-Employee Assignment** chooses one qualified and available **Employee** deterministically for the selected **Bookable Time**
- **Any-Employee Assignment** prefers the **Employee** with the fewest **Booked** appointments at the selected **Branch** on the selected date, then earliest Employee display name, then stable Employee identifier
- An **Appointment** belongs to exactly one **Merchant**
- An **Appointment** belongs to exactly one **Branch**
- An **Appointment** is assigned to exactly one **Employee**
- An **Appointment** has exactly one **Product**
- An **Appointment** has exactly one **Appointment Status**
- An **Appointment** has exactly one **Appointment Customer**
- An **Appointment** has exactly one **Appointment Contact Details** snapshot
- An **Appointment Contact Details** snapshot is either **Shopper-Owned Contact Details** or **Merchant-Captured Contact Details**
- An **Appointment** may have zero or one **Appointment Note**
- A shopper may provide an initial **Appointment Note** when booking
- **Shopper-Owned Contact Details** are editable only by the linked shopper
- **Merchant-Captured Contact Details** may be edited by a **Merchant Manager**
- **Merchant-Captured Contact Details** may be edited by a **Merchant Employee** when the **Appointment** is assigned to their linked **Employee**
- Shopper booking creates a **Booked** **Appointment** immediately; merchant approval is not part of the lifecycle
- Shopper booking requires an active **Merchant** and active **Branch**
- An **Appointment** is reservation-only and does not create an **Order** or require payment in this context
- An **Appointment** may show an **Appointment Price** from its booked **Product**
- An **Appointment** keeps an **Appointment Product Snapshot**
- An **Appointment** keeps an **Appointment Duration Snapshot**
- A **Merchant Manager** may create an **Appointment** from **Appointment Contact Details** without a linked shopper account
- A **Merchant Employee** may create an **Appointment** from **Appointment Contact Details** for their linked **Employee** when the time is bookable
- Creating or rescheduling an **Appointment** must reject overlaps with the assigned **Employee**'s existing **Booked** **Appointment** time windows
- Only **Booked** **Appointment** time windows block **Bookable Time**
- **Completed** **Appointment** entries are historical and do not block future **Bookable Time**
- A shopper may cancel their own **Appointment**
- Shoppers may cancel only **Appointment** entries linked to their shopper account
- Shoppers may cancel only future **Booked** **Appointment** entries
- A **Merchant Employee** may cancel an **Appointment** assigned to their linked **Employee**
- A **Merchant Manager** may cancel any **Appointment** for the **Merchant**
- Appointment cancellation has no penalty or late-cancellation rule in this context
- A **Merchant Employee** may mark an **Appointment** assigned to their linked **Employee** as **Completed**
- A **Merchant Manager** may mark any **Appointment** for the **Merchant** as **Completed**
- A future **Appointment** may not be marked **Completed**
- A **Merchant Employee** may edit **Appointment Note** for appointments assigned to their linked **Employee**
- A **Merchant Manager** may edit **Appointment Note** for any **Appointment** for the **Merchant**
- Shopper editing of **Appointment Note** after booking is out of scope in this context
- **Appointment Rescheduling** keeps the same **Appointment** and requires the new Product, time, Employee, and Branch choice to be bookable
- **Appointment Rescheduling** refreshes the **Appointment Product Snapshot** from the current **Product**
- **Appointment Rescheduling** refreshes the **Appointment Duration Snapshot** from the current **Product Category** **Estimated Service Duration**
- **Appointment Rescheduling** that changes **Product** revalidates **Employee Service Assignment** against the new **Product Category**
- A shopper may reschedule their own **Appointment** when the new choice is bookable
- Shoppers may reschedule only **Appointment** entries linked to their shopper account
- Shoppers may reschedule only future **Booked** **Appointment** entries
- A **Merchant Manager** may reschedule any **Appointment** for the **Merchant** when the new choice is bookable
- A **Merchant Employee** may reschedule an **Appointment** assigned to their linked **Employee** when the new choice remains assigned to the same **Employee** and is bookable
- An admin user may perform a **Support Appointment Override** for support purposes
- **Support Appointment Override** permits the same appointment actions as **Merchant Manager**
- **Support Appointment Override** follows the same bookability and appointment validation rules as **Merchant Manager** actions
- **Support Appointment Override** requires an **Override Reason**
- A linked shopper account may not have overlapping **Booked** **Appointment** time windows
- Contact-only appointments are not checked for cross-merchant shopper overlaps because they have no linked shopper account
- Linking a contact-only **Appointment** to a shopper account is out of scope in this context
- A linked shopper account may see its **Shopper Appointment History**
- **Shopper Appointment History** excludes contact-only appointments
- Past appointments in **Shopper Appointment History** are read-only
- **Shopper Appointment History** may show cancelled appointments as read-only history
- **Appointment Reminder** applies only to **Appointment** entries linked to a shopper account
- **Appointment Reminder** applies only to **Booked** **Appointment** entries
- **Appointment Notification** applies only to **Appointment** entries linked to a shopper account
- **Appointment Notification** is sent when a linked-shopper **Appointment** is booked, cancelled, or rescheduled
- **Support Appointment Override** triggers the same **Appointment Notification** rules for linked-shopper appointments
- **Override Reason** is not exposed in shopper-facing **Appointment Notification**
- Merchant-app appointment notifications are out of scope in this context
- **Reminder Lead Time** is configured at the **Merchant** level and applies across all **Branch**es
- A future linked-shopper **Appointment** created or rescheduled inside **Reminder Lead Time** is eligible for an immediate **Appointment Reminder**
- Contact-only appointments do not receive **Appointment Reminder** notifications in this context
- Appointment availability, **Bookable Time** values, and calendar dates are interpreted in the **Branch Timezone**
- A **Merchant Employee** may see the **Employee Calendar** for their linked **Employee**
- A **Merchant Employee** may cancel or complete appointments for their linked **Employee**
- A **Merchant Employee** is linked to exactly one **Employee**
- An **Employee** may be linked to zero or one **Merchant Employee**
- A **Merchant Manager** may be linked to zero or one **Employee**
- **Merchant User Assignment** defines a merchant user's **Merchant Operations Role** entries and optional **Employee** link
- BlitzPay owns **Merchant User Assignment** as domain state; identity-provider groups are not the source of truth for appointment permissions
- A **Merchant User Assignment** may exist without an **Employee** link when the merchant user does not perform schedulable services
- A merchant user may hold both **Merchant Manager** and **Merchant Employee** roles
- When a merchant user holds both roles, **Merchant Manager** permissions apply for merchant-wide appointment workflows
- A **Merchant Manager** may see the **Merchant Calendar**
- An admin user may see the **Branch Calendar** for a selected **Branch**
- **Employee Calendar**, **Merchant Calendar**, and **Branch Calendar** may show cancelled appointments as history
- A **Merchant Manager** manages **Employee**, **Employee Branch Assignment**, **Employee Service Assignment**, **Employee Availability**, and **Availability Exception** entries
- A **Merchant Manager** manages **Branch Opening Hours** and **Branch Opening Exception** entries
- A **Merchant Manager** manages **Booking Interval**, **Booking Lead Time**, **Booking Horizon**, and **Reminder Lead Time**
- Employee self-service availability management is out of scope in this context
- **Merchant Status** acts as a global gate; an inactive merchant disables branch-facing flows even if a branch itself is active
- A **Merchant** with future **Booked** **Appointment** entries may not be deactivated until those appointments are cancelled or rescheduled
- A **Branch** with future **Booked** **Appointment** entries may not be deactivated until those appointments are cancelled or rescheduled
- A **Merchant** owns zero or more **Product Category** entries
- A **Product Category** belongs to exactly one **Merchant**
- A **Product Category** groups zero or more **Product** entries
- A **Product** belongs to exactly one **Merchant**
- A **Product** may belong to zero or one **Product Category**
- A **Bookable Product** must be active
- A **Bookable Product** must belong to the selected **Branch**
- A **Bookable Product** must belong to a **Product Category**
- A **Product** with future **Booked** **Appointment** entries may not be deactivated until those appointments are cancelled or rescheduled
- A **Product Category** may have zero or one **Estimated Service Duration**
- A **Product** uses its **Product Category** **Estimated Service Duration** for appointment scheduling
- **Estimated Service Duration** is stored in minutes and must be positive when present
- **Estimated Service Duration** is capped at 480 minutes (8 hours) for operational sanity
- **Estimated Service Duration** may only be written (on create or update) when the **Merchant** has **Appointment Booking** enabled; writing it without that offering is rejected
- **Estimated Service Duration** may not be removed from a **Product Category** while future **Booked** appointments exist for **Product** entries in that category
- Changing **Estimated Service Duration** does not change existing **Appointment** time windows unless those appointments are rescheduled
- Disabling **Appointment Booking** does not clear existing **Estimated Service Duration** values; they are inert and preserved for if the offering is re-enabled
- A `MOBILE` **Merchant** may not enable **Appointment Booking**
- **Walk-In Ordering** is decided by the backend using the customer's geolocation relative to the branch
- **Pre-Order** may be used regardless of the customer's proximity to the branch
- The customer requests an **Order Type**, and the backend validates it against merchant offerings and geolocation rules
- **Walk-In Ordering** requires customer geolocation and is rejected when geolocation is absent
- **Walk-In Ordering** requires geolocation that is recent and sufficiently accurate
- **Deferred Payment** being enabled on the merchant means an order may choose it, not that every order must use it
- **Appointment Booking** workflow and persistence are designed in ADR-0008

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
> **Dev:** "Can a `MOBILE` **Merchant** accept **Appointment** bookings during an **Appearance**?"
> **Domain expert:** "No. **Appointment Booking** is supported only for `BRICK_AND_MORTAR` **Merchant**s."
>
> **Dev:** "Can the admin enable **Appointment Booking** for a `MOBILE` **Merchant** anyway?"
> **Domain expert:** "No. A `MOBILE` **Merchant** may not enable **Appointment Booking**."
>
> **Dev:** "If **Appointment Booking** is enabled but no Employees are configured, does the shopper see bookable times?"
> **Domain expert:** "No. **Appointment Booking** can be enabled before **Appointment Setup** is complete, but **Bookable Time** remains empty."
>
> **Dev:** "Can shoppers book appointments for an inactive Branch?"
> **Domain expert:** "No. **Appointment Search** and shopper booking require an active **Merchant** and active **Branch**."
>
> **Dev:** "When does the shopper see appointment times and Employees?"
> **Domain expert:** "After selecting a **Branch** and **Product Category**, **Appointment Search** returns **Bookable Time** and **Selectable Employee** options."
>
> **Dev:** "Does the shopper have to pick a specific **Employee** when booking?"
> **Domain expert:** "No. The shopper may pick a specific **Employee** or choose any available **Employee**, but the confirmed appointment is assigned to one **Employee**."
>
> **Dev:** "If Sara and Ali are both available when the shopper chooses any available Employee, who gets assigned?"
> **Domain expert:** "Use **Any-Employee Assignment** to choose one qualified and available **Employee** deterministically."
>
> **Dev:** "If Sara has five bookings today and Ali has two, and both are available, who gets the any-employee booking?"
> **Domain expert:** "**Any-Employee Assignment** prefers the Employee with fewer **Booked** appointments for that Branch and date."
>
> **Dev:** "Should shoppers see Employees who cannot perform the selected service or have no available times?"
> **Domain expert:** "No. **Selectable Employee** includes only Employees with the selected service assignment and Bookable Time in the searched window."
>
> **Dev:** "If Sara works at two branches, is she two **Employee** records?"
> **Domain expert:** "No. Sara is one **Employee** of the **Merchant** with **Employee Branch Assignment** entries for the branches where she works."
>
> **Dev:** "Can Sara have availability at Branch B before she is assigned to Branch B?"
> **Domain expert:** "No. **Employee Branch Assignment** is required before an **Employee** can have availability or appointments at that **Branch**."
>
> **Dev:** "If Ali leaves the business, do we delete his **Employee** record?"
> **Domain expert:** "No. Set **Employee Status** to inactive so historical **Appointment** references stay meaningful while Ali is no longer bookable."
>
> **Dev:** "Can Ali be set inactive while he has future Booked appointments?"
> **Domain expert:** "No. Future **Booked** appointments assigned to Ali must be cancelled or rescheduled first."
>
> **Dev:** "Can Ali be bookable even if he never logs into the merchant app?"
> **Domain expert:** "Yes. **Employee** is separate from **Merchant User Assignment**."
>
> **Dev:** "If Ali is off next Monday, do we change his normal Monday **Employee Availability**?"
> **Domain expert:** "No. Use an **Availability Exception** for that date so the recurring weekly pattern remains intact."
>
> **Dev:** "Can an **Availability Exception** add a special Sunday shift?"
> **Domain expert:** "Yes. An **Availability Exception** can remove availability or add extra availability for a specific date."
>
> **Dev:** "How do we model Ali working Monday 09:00-12:00 and 13:00-17:00?"
> **Domain expert:** "Use two non-overlapping **Employee Availability** windows on the same weekday."
>
> **Dev:** "Does the **Merchant** create every appointment slot manually?"
> **Domain expert:** "No. The system exposes **Bookable Time** values calculated from availability, exceptions, existing appointments, and service duration."
>
> **Dev:** "Is a 30-minute service always offered only every 30 minutes?"
> **Domain expert:** "No. **Booking Interval** controls start-time cadence separately from **Estimated Service Duration**."
>
> **Dev:** "Can Branch A use 10-minute starts while Branch B uses 15-minute starts?"
> **Domain expert:** "No. **Booking Interval** is configured at the **Merchant** level and applies across all **Branch**es."
>
> **Dev:** "Can a shopper book an appointment that starts in ten minutes?"
> **Domain expert:** "Only if it satisfies the Merchant's **Booking Lead Time**."
>
> **Dev:** "Can a shopper book nine months into the future just because recurring availability exists?"
> **Domain expert:** "No. **Bookable Time** must fall within the Merchant's **Booking Horizon**."
>
> **Dev:** "Can any **Employee** be assigned to any **Product Category**?"
> **Domain expert:** "No. **Employee Service Assignment** defines which services each **Employee** can perform."
>
> **Dev:** "If Sara can perform Haircut, does that include Fade and Kids Haircut products under Haircut?"
> **Domain expert:** "Yes. **Employee Service Assignment** applies to all **Product** entries in the assigned **Product Category**."
>
> **Dev:** "Can shoppers book a **Product Category** that has no **Estimated Service Duration**?"
> **Domain expert:** "No. Services without duration can exist, but they cannot be scheduled as **Appointment**s."
>
> **Dev:** "Does an **Appointment** reserve only a category or a concrete product?"
> **Domain expert:** "An **Appointment** reserves a concrete **Product** inside the selected **Product Category**."
>
> **Dev:** "Can shoppers book an inactive Product?"
> **Domain expert:** "No. **Appointment Search** returns only active **Bookable Product** entries."
>
> **Dev:** "Can a Product be deactivated while future appointments still reserve it?"
> **Domain expert:** "No. Future **Booked** appointments for that **Product** must be cancelled or rescheduled first."
>
> **Dev:** "Can a product from Branch A be booked while the shopper is viewing Branch B?"
> **Domain expert:** "No. A **Bookable Product** must belong to the selected **Branch**."
>
> **Dev:** "Can an uncategorized Product be booked as an Appointment?"
> **Domain expert:** "No. A **Bookable Product** must belong to a **Product Category**."
>
> **Dev:** "Can products in the same category have different appointment durations?"
> **Domain expert:** "No. A **Product** uses its **Product Category** **Estimated Service Duration** for appointment scheduling."
>
> **Dev:** "Does an appointment remember the duration used when it was booked?"
> **Domain expert:** "Yes. **Appointment Duration Snapshot** captures the **Estimated Service Duration** used at booking or rescheduling."
>
> **Dev:** "When a shopper books a time, is it only a request waiting for approval?"
> **Domain expert:** "No. Booking creates a **Booked** **Appointment** immediately; approval is not part of the appointment lifecycle."
>
> **Dev:** "Does booking an **Appointment** create an **Order** or require payment?"
> **Domain expert:** "No. An **Appointment** is reservation-only in this context."
>
> **Dev:** "If the booked Product has a price, does the shopper pay during booking?"
> **Domain expert:** "No. **Appointment Price** is informational and does not create an **Order** or require payment."
>
> **Dev:** "If the merchant renames or reprices the Product after booking, does the old appointment change?"
> **Domain expert:** "Not immediately. The **Appointment Product Snapshot** is captured at booking time and refreshed if the **Appointment** is rescheduled."
>
> **Dev:** "Can the merchant create an **Appointment** for a customer who calls by phone?"
> **Domain expert:** "Yes. An **Appointment** can use **Appointment Contact Details** without being linked to a shopper account."
>
> **Dev:** "Can the merchant edit contact details supplied by the shopper app?"
> **Domain expert:** "No. **Shopper-Owned Contact Details** are editable only by the linked shopper; merchant users edit only **Merchant-Captured Contact Details**."
>
> **Dev:** "Can an appointment include a note like preferred style or allergy warning?"
> **Domain expert:** "Yes. Use an **Appointment Note** for merchant-visible operational text."
>
> **Dev:** "Can the shopper change the note after booking?"
> **Domain expert:** "No. The shopper may provide the initial **Appointment Note** when booking; later note editing is merchant-side in this context."
>
> **Dev:** "Can Sara create a phone booking directly on her own calendar?"
> **Domain expert:** "Yes. A **Merchant Employee** may create an **Appointment** for their linked **Employee** when the time is bookable."
>
> **Dev:** "If an appointment moves from Sara at 10:00 to Ali at 10:30, is it cancelled and recreated?"
> **Domain expert:** "No. Use **Appointment Rescheduling** so the same **Appointment** keeps its identity while the new Product, assignment, and time are validated."
>
> **Dev:** "Can a shopper move their appointment from 10:00 to 11:00?"
> **Domain expert:** "Yes, if the new time is bookable."
>
> **Dev:** "Can a shopper change a booked Kids Haircut to Fade without cancelling?"
> **Domain expert:** "Yes. **Appointment Rescheduling** may change the **Product** when the new choice is bookable."
>
> **Dev:** "Can an appointment keep Sara assigned if the Product changes to a category Sara cannot perform?"
> **Domain expert:** "No. **Appointment Rescheduling** revalidates **Employee Service Assignment** against the new **Product Category**."
>
> **Dev:** "Can Sara move her 10:00 appointment to Ali at 11:00?"
> **Domain expert:** "No. A **Merchant Employee** may reschedule within their linked **Employee** calendar only; a **Merchant Manager** can reassign across Employees."
>
> **Dev:** "If the UI accidentally shows Ali as free at 10:15 while he is already booked until 10:30, can booking still succeed?"
> **Domain expert:** "No. Creating or rescheduling an **Appointment** rejects overlaps with the assigned **Employee**'s existing **Booked** appointments."
>
> **Dev:** "When an **Appointment** is cancelled, does that time become bookable again?"
> **Domain expert:** "Yes. Only **Booked** appointments block **Bookable Time**."
>
> **Dev:** "Can a manager mark tomorrow's appointment **Completed** and free the time?"
> **Domain expert:** "No. A future **Appointment** cannot be completed; completed appointments are historical and do not block future **Bookable Time**."
>
> **Dev:** "Can a logged-in shopper book two appointments whose times overlap?"
> **Domain expert:** "No. A linked shopper account may not have overlapping **Booked** **Appointment** time windows."
>
> **Dev:** "If a manager in Berlin edits availability for a London branch, what does 09:00 mean?"
> **Domain expert:** "09:00 is interpreted in the **Branch Timezone**, not the manager's device timezone."
>
> **Dev:** "Can a shopper cancel five minutes before the appointment?"
> **Domain expert:** "Yes. Cancellation is allowed without a late-cancellation penalty in this context."
>
> **Dev:** "Can a shopper app user cancel a phone booking just because the phone number matches?"
> **Domain expert:** "No. Shoppers can cancel or reschedule only **Appointment** entries linked to their shopper account."
>
> **Dev:** "Can a customer claim a phone booking into their shopper app account?"
> **Domain expert:** "No. Linking contact-only appointments to shopper accounts is out of scope in this context."
>
> **Dev:** "What does the shopper Appointments area show?"
> **Domain expert:** "It shows **Shopper Appointment History** for Appointments linked to that shopper account, excluding contact-only bookings."
>
> **Dev:** "Can shoppers cancel or reschedule appointments from last week?"
> **Domain expert:** "No. Past appointments in **Shopper Appointment History** are read-only."
>
> **Dev:** "Does a cancelled shopper appointment disappear from the Appointments area?"
> **Domain expert:** "No. **Shopper Appointment History** may show cancelled appointments as read-only history."
>
> **Dev:** "Do phone bookings receive appointment reminders?"
> **Domain expert:** "No. **Appointment Reminder** notifications are sent only for appointments linked to shopper app users."
>
> **Dev:** "Is the reminder time configured per appointment?"
> **Domain expert:** "No. **Reminder Lead Time** is configured at the **Merchant** level and applies across all **Branch**es."
>
> **Dev:** "If a shopper books today at 09:00 for today at 15:00 and reminders are normally 24 hours ahead, is a reminder sent?"
> **Domain expert:** "Yes. A future linked-shopper **Appointment** created inside **Reminder Lead Time** is eligible for an immediate **Appointment Reminder**."
>
> **Dev:** "Does a cancelled appointment still send its scheduled reminder?"
> **Domain expert:** "No. **Appointment Reminder** applies only to **Booked** appointments."
>
> **Dev:** "If the merchant reschedules a shopper's appointment, does the shopper app get notified?"
> **Domain expert:** "Yes. **Appointment Notification** is sent when a linked-shopper appointment is booked, cancelled, or rescheduled."
>
> **Dev:** "Does Sara get a merchant-app push when a shopper books her?"
> **Domain expert:** "No. Merchant-app appointment notifications are out of scope in this context."
>
> **Dev:** "Can Sara cancel an appointment assigned to her?"
> **Domain expert:** "Yes. A **Merchant Employee** may cancel appointments assigned to their linked **Employee**."
>
> **Dev:** "Can Sara mark her completed haircut appointment as **Completed**?"
> **Domain expert:** "Yes. A **Merchant Employee** can complete an **Appointment** assigned to their linked **Employee**; a **Merchant Manager** can complete any **Appointment**."
>
> **Dev:** "Can every **Employee** see every other **Employee**'s appointments?"
> **Domain expert:** "No. A **Merchant Employee** sees the **Employee Calendar** for their linked **Employee**; a **Merchant Manager** sees the **Merchant Calendar** across Employees and Branches."
>
> **Dev:** "Do cancelled appointments disappear from merchant calendars?"
> **Domain expert:** "No. Merchant-facing calendars may show cancelled appointments as history, but cancelled appointments do not block availability."
>
> **Dev:** "Can an admin inspect appointments for a specific Branch?"
> **Domain expert:** "Yes. An admin user may see the **Branch Calendar** for a selected **Branch**."
>
> **Dev:** "Can an admin change an appointment from web admin?"
> **Domain expert:** "Yes, but only as a **Support Appointment Override** with the same appointment actions as **Merchant Manager**."
>
> **Dev:** "Can support force an appointment into an already-booked Employee's calendar?"
> **Domain expert:** "No. **Support Appointment Override** follows the same bookability and appointment validation rules as **Merchant Manager** actions."
>
> **Dev:** "Can support change an appointment silently?"
> **Domain expert:** "No. **Support Appointment Override** requires an **Override Reason**."
>
> **Dev:** "If support reschedules a shopper's appointment, does the shopper app get notified?"
> **Domain expert:** "Yes. **Support Appointment Override** triggers the same shopper-facing **Appointment Notification** rules, but **Override Reason** stays internal."
>
> **Dev:** "Are **Merchant Manager** and **Merchant Employee** the same as onboarding reviewer or legal person roles?"
> **Domain expert:** "No. They are merchant-operation roles for appointment and calendar work."
>
> **Dev:** "Can one **Merchant Employee** login operate calendars for two Employees?"
> **Domain expert:** "No. A **Merchant Employee** is linked to exactly one **Employee**."
>
> **Dev:** "Can the salon owner be a **Merchant Manager** and still have their own appointment calendar?"
> **Domain expert:** "Yes. A **Merchant Manager** may also be linked to an **Employee** profile."
>
> **Dev:** "Can one user be both **Merchant Manager** and **Merchant Employee**?"
> **Domain expert:** "Yes. When both roles are present, **Merchant Manager** permissions apply for merchant-wide appointment workflows."
>
> **Dev:** "Is Keycloak group membership the source of truth for appointment roles?"
> **Domain expert:** "No. BlitzPay owns **Merchant User Assignment**; identity-provider groups may support access but do not define the appointment domain role model."
>
> **Dev:** "Can Sara mark herself off next Friday?"
> **Domain expert:** "No. A **Merchant Manager** manages employee availability; employee self-service availability changes are out of scope."
>
> **Dev:** "Who configures a branch holiday closure?"
> **Domain expert:** "A **Merchant Manager** manages **Branch Opening Exception** entries; web admin changes are support overrides, not normal setup."
>
> **Dev:** "Who configures booking interval, lead time, horizon, and reminder timing?"
> **Domain expert:** "A **Merchant Manager** manages those merchant appointment policies; web admin changes are support overrides."
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
> **Dev:** "Is **Employee Availability** enough to make a Sunday appointment bookable?"
> **Domain expert:** "No. **Bookable Time** must also fall within **Branch Opening Hours**."
>
> **Dev:** "If a 45-minute service starts at 11:30 and availability ends at 12:00, is that bookable?"
> **Domain expert:** "No. The full appointment time window must fit within opening hours and availability."
>
> **Dev:** "If the whole branch is closed for a public holiday, do we add time off for every **Employee**?"
> **Domain expert:** "No. Use a **Branch Opening Exception** for that date."
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
> **Dev:** "Can a Merchant be deactivated while any Branch has future Booked appointments?"
> **Domain expert:** "No. Future **Booked** appointments must be cancelled or rescheduled before the **Merchant** is deactivated."
>
> **Dev:** "Can a Branch be deactivated while it has future Booked appointments?"
> **Domain expert:** "No. Future **Booked** appointments must be cancelled or rescheduled before the **Branch** is deactivated."
>
> **Dev:** "What is a **Product Category**?"
> **Domain expert:** "A merchant-defined classification for products or services like haircut, shave, or color treatment. They belong to the **Merchant**, not individual branches."
>
> **Dev:** "Does every **Product Category** need an **Estimated Service Duration**?"
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
>
> **Dev:** "Can a Product Category duration be removed while future appointments use products in that category?"
> **Domain expert:** "No. Future **Booked** appointments must be cancelled or rescheduled before **Estimated Service Duration** is removed."
>
> **Dev:** "If Haircut duration changes from 30 to 45 minutes, do existing appointments become longer?"
> **Domain expert:** "No. Existing **Appointment** time windows stay unchanged unless those appointments are rescheduled."

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

**Tap to Pay**:
The in-person payment mode where the merchant's phone acts as an NFC contactless reader using the Stripe Terminal SDK. Accepts contactless physical cards and digital wallets (Apple Pay, Google Pay). No external hardware required. Enabled per Merchant via `tap_to_pay_enabled` on the Stripe provider account. Either the shopper or merchant app may initiate a Tap to Pay payment; the Order is the coordination point between them.
_Avoid_: POS terminal, hardware reader, card machine, Stripe POS

**Terminal Location**:
The Stripe-assigned identifier (`loc_XXXX`) for a Branch's physical location in the Stripe Terminal system. Created lazily by the backend on the first Connection Token request for a Branch, using the Branch's existing address and name. Stored per Branch. Required by the Terminal SDK when connecting the NFC reader.
_Avoid_: Stripe location ID, reader location, terminal address

**Connection Token**:
A short-lived Stripe Terminal authentication secret issued by the BlitzPay backend, scoped to a Merchant's Connected Account and a specific Terminal Location. The merchant app's Terminal SDK fetches a fresh Connection Token at the start of each session. The backend creates it by calling Stripe `POST /v1/terminal/connection_tokens` with `Stripe-Account: acct_XXXX`.
_Avoid_: Terminal secret, SDK token, auth token

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
- Appointment booking could have been supported for `MOBILE` merchants during **Appearance** windows. Resolved: **Appointment Booking** is supported only for `BRICK_AND_MORTAR` **Merchant**s.
- `MOBILE` merchants could have been allowed to enable **Appointment Booking** despite being unable to accept appointments. Resolved: a `MOBILE` **Merchant** may not enable **Appointment Booking**.
- Appointment offering enablement could have required complete scheduling setup. Resolved: **Appointment Booking** may be enabled before **Appointment Setup** is complete, but **Bookable Time** is empty until setup can produce valid times.
- Appointment search could have ignored operational statuses. Resolved: **Appointment Search** and shopper booking require an active **Merchant** and active **Branch**.
- Shopper appointment search could have been merchant-wide without a branch. Resolved: **Appointment Search** requires a selected **Branch** and **Product Category** before returning **Bookable Time** and **Selectable Employee** options.
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
- "Employee", "staff", "barber", and "chef" could have been used interchangeably. Resolved: use **Employee** for the schedulable person who works for the **Merchant**; job-specific labels are display/category concerns, not the canonical domain term.
- Employee records could have doubled as user login records. Resolved: **Employee** is separate from **Merchant User Assignment** so schedulable people and merchant app users can differ.
- Employee removal could have been modelled as deletion. Resolved: use **Employee Status** so inactive **Employee** entries remain available for historical appointments but cannot receive new bookings.
- Employee deactivation could have stranded future appointments. Resolved: an **Employee** with future **Booked** appointments may not be set inactive until those appointments are cancelled or rescheduled.
- Employee could have been modelled as branch-owned, duplicating the same person at multiple branches. Resolved: **Employee** belongs to the **Merchant** and **Employee Branch Assignment** records define which **Branch**es they can serve.
- Employee availability and appointments could have implicitly created branch assignment. Resolved: **Employee Branch Assignment** must exist before an **Employee** can have availability or appointments at that **Branch**.
- Employee schedules could have been modelled only as recurring weekly windows or only as date-specific calendar entries. Resolved: **Employee Availability** captures recurring weekly working windows and **Availability Exception** captures date-specific changes.
- Availability exceptions could have been limited to time off only. Resolved: **Availability Exception** may remove availability or add extra availability for a specific date.
- Daily employee availability could have been limited to one continuous working window. Resolved: an **Employee** may have multiple non-overlapping **Employee Availability** windows on the same weekday and Branch.
- Employee service capability could have been assumed from merchant-level services. Resolved: **Employee Service Assignment** defines which **Product Category** entries each **Employee** can perform.
- Employee service capability could have been assigned per Product. Resolved: **Employee Service Assignment** is to **Product Category** and applies to all **Product** entries in that category.
- Appointment bookability could have depended only on **Employee Availability**. Resolved: the full appointment time window for a **Bookable Time** must fit within both **Branch Opening Hours** and **Employee Availability**.
- Whole-branch holiday closures could have been modelled as time off for every Employee. Resolved: use **Branch Opening Exception** for date-specific changes to **Branch Opening Hours**.
- Appointment start-time cadence could have been derived from service duration. Resolved: **Booking Interval** controls generated **Bookable Time** start times separately from **Estimated Service Duration**.
- Booking interval could have been branch-specific. Resolved: **Booking Interval** is configured at the **Merchant** level and applies across all **Branch**es.
- Last-second appointment booking could have been allowed whenever an employee is free. Resolved: **Bookable Time** must satisfy the Merchant's **Booking Lead Time**.
- Recurring availability could have made appointments bookable indefinitely far into the future. Resolved: **Bookable Time** must fall within the Merchant's **Booking Horizon**.
- Appointment start times could have been stored as merchant-created slots. Resolved: **Bookable Time** is generated from availability, exceptions, existing appointments, **Employee Service Assignment**, and **Estimated Service Duration** instead of being authored one by one.
- Product categories without duration could have been treated as bookable with a default duration. Resolved: a **Product Category** without **Estimated Service Duration** cannot be used for **Appointment** booking.
- Appointment booking could have reserved only a **Product Category**. Resolved: an **Appointment** reserves a concrete **Product** inside the selected **Product Category**.
- Inactive Products could have been bookable when their Product Category was schedulable. Resolved: **Appointment Search** returns only active **Bookable Product** entries.
- Product deactivation could have stranded future appointments. Resolved: a **Product** with future **Booked** appointments may not be deactivated until those appointments are cancelled or rescheduled.
- Products from other branches could have appeared in appointment search. Resolved: a **Bookable Product** must belong to the selected **Branch**.
- Uncategorized Products could have been appointment-bookable. Resolved: a **Bookable Product** must belong to a **Product Category**.
- Product-specific duration could have been introduced for appointment booking. Resolved: a **Product** uses its **Product Category** **Estimated Service Duration** for appointment scheduling.
- Appointment booking could have required the shopper to choose a specific Employee. Resolved: **Employee Selection** supports either a specific **Employee** or any available **Employee**, while the confirmed appointment is always assigned to exactly one **Employee**.
- Any-employee booking could have created unassigned or randomly assigned appointments. Resolved: **Any-Employee Assignment** deterministically assigns one qualified and available **Employee** for the selected **Bookable Time**.
- Any-employee assignment could have used arbitrary ordering. Resolved: **Any-Employee Assignment** balances by fewest **Booked** appointments at the selected **Branch** on the selected date, then Employee display name, then stable Employee identifier.
- Shopper employee selection could have shown all Employees. Resolved: **Selectable Employee** includes only Employees who can perform the selected **Product Category** and have **Bookable Time** in the searched window.
- Appointment booking could have created a pending request requiring merchant approval. Resolved: shopper booking immediately creates a **Booked** **Appointment**; the initial lifecycle is **Booked**, **Cancelled**, and **Completed**.
- Appointment booking could have created an **Order** or required upfront payment. Resolved: **Appointment** is reservation-only and remains independent from ordering and payment in this context.
- Product price could have made appointment booking a payment flow. Resolved: **Appointment Price** is informational and does not create an **Order** or require payment.
- Appointment display could have depended only on the live Product after booking. Resolved: **Appointment Product Snapshot** keeps the booked Product name and informational price from booking time.
- Appointment display could have depended only on the live Product Category duration. Resolved: **Appointment Duration Snapshot** captures the duration used at booking or rescheduling.
- Appointment customers could have required a registered shopper account. Resolved: **Appointment Customer** may be linked to a shopper account or represented by **Appointment Contact Details**, allowing merchant-created appointments for phone or walk-in bookings.
- Appointment contact details could have had one edit owner for both shopper-created and merchant-created bookings. Resolved: **Shopper-Owned Contact Details** are editable only by the linked shopper, while **Merchant-Captured Contact Details** are editable by merchant-side roles.
- Appointment notes could have become private customer notes or chat messages. Resolved: **Appointment Note** is merchant-visible operational text attached to an **Appointment**.
- Appointment note editing could have stayed shopper-owned after booking. Resolved: shoppers may provide the initial **Appointment Note**; later note editing is handled by **Merchant Employee** for their linked **Employee**'s appointments or **Merchant Manager** for any merchant appointment.
- Manual appointment creation could have been restricted to Merchant Managers. Resolved: a **Merchant Employee** may create appointments from **Appointment Contact Details** for their linked **Employee** when the time is bookable.
- Appointment time, employee, branch, or Product changes could have been modelled as cancel-and-rebook. Resolved: **Appointment Rescheduling** preserves the same **Appointment** identity while validating that the new choice is bookable.
- Appointment rescheduling could have preserved the original Product name and price snapshot. Resolved: **Appointment Rescheduling** refreshes the **Appointment Product Snapshot** from the current **Product**.
- Appointment rescheduling could have changed Product without rechecking employee service capability. Resolved: **Appointment Rescheduling** revalidates **Employee Service Assignment** against the new **Product Category**.
- Appointment rescheduling could have been restricted to Merchant Managers only. Resolved: shoppers may reschedule their own **Appointment** when the new choice is bookable; **Merchant Employee** may reschedule within their linked **Employee** calendar; **Merchant Manager** can reschedule any **Appointment** for the **Merchant**.
- Employee double-booking could have been treated as a UI-only concern. Resolved: creating or rescheduling an **Appointment** rejects overlaps with the assigned **Employee**'s existing **Booked** appointments.
- Cancelled appointments could have continued blocking availability. Resolved: only **Booked** **Appointment** time windows block **Bookable Time**.
- Completed appointments could have continued blocking availability or been usable to free future slots early. Resolved: **Completed** appointments are historical and do not block future **Bookable Time**, and future appointments may not be marked **Completed**.
- A linked shopper account could have been allowed to hold overlapping appointments across merchants. Resolved: overlapping **Booked** **Appointment** time windows are rejected for linked shopper accounts; contact-only appointments are not checked for cross-merchant shopper overlaps.
- Appointment scheduling could have interpreted local times using the merchant user's device timezone or server timezone. Resolved: appointment availability, **Bookable Time**, and calendar dates use the **Branch Timezone**.
- Appointment cancellation could have included late-cancellation fees or blocking rules. Resolved: shopper, **Merchant Employee** cancellation for their linked **Employee**, and **Merchant Manager** cancellation are allowed without penalty policy in this context.
- Contact-only appointments could have been automatically claimed by shopper accounts with matching contact details. Resolved: shoppers may cancel or reschedule only **Appointment** entries linked to their shopper account.
- Contact-only appointments could have supported a claiming workflow into shopper accounts. Resolved: linking contact-only **Appointment** entries to shopper accounts is out of scope in this context.
- Shopper appointment history could have included contact-only bookings with matching details. Resolved: **Shopper Appointment History** includes only Appointments linked to the shopper account.
- Past shopper appointments could have stayed editable from history. Resolved: past appointments in **Shopper Appointment History** are read-only; shopper cancellation and rescheduling apply only to future **Booked** appointments.
- Cancelled shopper appointments could have disappeared from history. Resolved: **Shopper Appointment History** may show cancelled appointments as read-only history.
- Appointment reminders could have applied to merchant users, SMS, email, or contact-only bookings. Resolved: **Appointment Reminder** is a shopper-app notification only for appointments linked to shopper app users.
- Appointment reminder timing could have been per appointment or branch-specific. Resolved: **Reminder Lead Time** is configured at the **Merchant** level and applies across all **Branch**es.
- Short-notice appointments could have missed reminders because the normal reminder time was already in the past. Resolved: future linked-shopper appointments created or rescheduled inside **Reminder Lead Time** are eligible for immediate **Appointment Reminder**.
- Cancelled or completed appointments could have still sent pending reminders. Resolved: **Appointment Reminder** applies only to **Booked** appointments.
- Appointment reminders could have been overloaded to include booking, cancellation, and rescheduling notices. Resolved: **Appointment Notification** covers booked, cancelled, and rescheduled events for linked-shopper appointments; **Appointment Reminder** covers time-based reminders.
- Merchant-app users could have received appointment push notifications. Resolved: merchant-app appointment notifications are out of scope; merchant users use calendars in this context.
- Appointment completion could have been restricted to Merchant Managers. Resolved: a **Merchant Employee** can mark appointments assigned to their linked **Employee** as **Completed** and a **Merchant Manager** can complete any **Appointment** for the **Merchant**.
- Merchant app calendar visibility could have exposed every Employee's appointments to every Employee. Resolved: a **Merchant Employee** sees the **Employee Calendar** for their linked **Employee** and a **Merchant Manager** sees the **Merchant Calendar** across Employees and Branches.
- Cancelled appointments could have disappeared from merchant-facing calendars. Resolved: **Employee Calendar**, **Merchant Calendar**, and **Branch Calendar** may show cancelled appointments as history while they do not block availability.
- Admin appointment visibility could have been merchant-wide only. Resolved: an admin user may inspect the **Branch Calendar** for a selected **Branch**.
- Web admin appointment changes could have been treated as ordinary merchant operations or given a separate support capability matrix. Resolved: admin changes are **Support Appointment Override** actions with the same appointment actions as **Merchant Manager**.
- Support appointment changes could have bypassed scheduling validation. Resolved: **Support Appointment Override** follows the same bookability and appointment validation rules as **Merchant Manager** actions.
- Support appointment changes could have been silent. Resolved: **Support Appointment Override** requires an **Override Reason**.
- Support appointment changes could have skipped shopper notifications or exposed internal support reasons. Resolved: **Support Appointment Override** triggers the same shopper-facing **Appointment Notification** rules for linked-shopper appointments, while **Override Reason** stays internal.
- Appointment roles could have reused onboarding reviewer or legal person roles. Resolved: use merchant-operation roles **Merchant Manager** and **Merchant Employee** for appointment and calendar workflows.
- Merchant Employee accounts could have been shared across multiple Employee profiles. Resolved: a **Merchant Employee** is linked to exactly one **Employee**, and an **Employee** may be linked to zero or one **Merchant Employee**.
- Merchant Manager accounts could have been prevented from having their own appointment calendar. Resolved: a **Merchant Manager** may optionally be linked to an **Employee** profile.
- Merchant Manager and Merchant Employee could have been mutually exclusive roles. Resolved: a merchant user may hold both roles, and **Merchant Manager** permissions apply for merchant-wide appointment workflows.
- Appointment permissions could have depended only on identity-provider groups. Resolved: BlitzPay owns **Merchant User Assignment** for Merchant Operations Roles and Employee links; identity-provider groups are not the appointment permission source of truth.
- Employees could have managed their own availability. Resolved: a **Merchant Manager** manages employee records, assignments, availability, and availability exceptions; employee self-service availability management is out of scope.
- Branch opening configuration could have been web-admin owned. Resolved: a **Merchant Manager** manages **Branch Opening Hours** and **Branch Opening Exception** entries; web admin changes are support overrides.
- Appointment policy configuration could have been web-admin owned. Resolved: a **Merchant Manager** manages **Booking Interval**, **Booking Lead Time**, **Booking Horizon**, and **Reminder Lead Time**; web admin changes are support overrides.
- The operational merchant could have retained onboarding statuses. Resolved: **Merchant Status** uses a simple operational lifecycle rather than onboarding workflow states.
- Branch availability could have been derived only from merchant state. Resolved: **Branch** has its own independent **Branch Status**.
- Merchant deactivation could have auto-cancelled or stranded future appointments across branches. Resolved: a **Merchant** with future **Booked** appointments may not be deactivated until those appointments are cancelled or rescheduled.
- Branch deactivation could have auto-cancelled or stranded future appointments. Resolved: a **Branch** with future **Booked** appointments may not be deactivated until those appointments are cancelled or rescheduled.
- Merchant and branch statuses could have conflicted with no precedence rule. Resolved: **Merchant Status** is the global gate and overrides active branches for customer-facing flows.
- Merchant capabilities could have been free-form merchant-defined values. Resolved: **Merchant Offering** values come from a fixed platform-managed catalogue.
- Merchant capabilities could have been stored as fixed boolean fields. Resolved: use a platform-managed offering catalogue plus merchant-to-offering assignment instead of hard-coded merchant flags.
- A merchant could have been forced to enable a capability at creation time. Resolved: a **Merchant** may temporarily exist with zero enabled offerings.
- Merchant address could have remained a single free-text field while branches used structured fields. Resolved: **Merchant** and **Branch** share the same structured address shape, but merchant address and location are entirely optional.
- Merchant and branch could have shared the same optionality rules. Resolved: **Merchant** address/location are optional, while **Branch** address/location are mandatory.
- Branch location could have meant only postal address. Resolved: every **Branch** requires both a structured postal address and geospatial location data.
- Branch validity could have depended on an external place provider. Resolved: `googlePlaceId` and other **Place Enrichment** data are optional for both **Merchant** and **Branch**.
- "Product category" and "service category" could have been split into separate concepts. Resolved: use existing **Product Category** for merchant-defined classifications relevant to ordering and appointments.
- Estimated duration could have been mandatory for all product categories. Resolved: **Estimated Service Duration** is optional; products or services without duration can exist but cannot be scheduled.
- Service duration could have been conflated with appointment slot size or staff availability. Resolved: **Estimated Service Duration** models only the expected service time; slot sizing and staff schedules are separate concerns.
- Product categories could have been branch-specific. Resolved: **Product Category** belongs to **Merchant** and applies uniformly across all branches.
- Estimated service duration could have been accepted for any merchant regardless of their offerings. Resolved: **Estimated Service Duration** may only be written when **Appointment Booking** is enabled; the write is rejected otherwise.
- Estimated service duration could have been removed while future appointments depend on it. Resolved: **Estimated Service Duration** may not be removed from a **Product Category** while future **Booked** appointments exist for Products in that category.
- Estimated service duration changes could have rewritten existing appointment windows. Resolved: changing **Estimated Service Duration** affects new searches and reschedules, but existing **Appointment** time windows stay unchanged unless rescheduled.
- Disabling **Appointment Booking** could have triggered a cascade-clear of all stored durations. Resolved: existing durations are left in place and treated as inert; clearing on toggle is destructive and non-reversible.
- Duration update could have had its own dedicated endpoint (`PATCH /product-categories/{id}/duration`). Resolved: duration is maintained through the standard category update endpoint (`PUT /product-categories/{id}`) alongside name; a dedicated endpoint was a smell that duplicated the create path's optionality.
- Stripe Connect and Adyen for Platforms could have been enabled simultaneously per Merchant. Resolved: a Merchant operates on exactly one platform PSP at a time; the active PSP is selected in the admin dashboard. Enabling one while the other is inactive is the only supported state.
- A single PSP-agnostic term (e.g. "Platform Sub-Account") could have covered both Stripe Connected Accounts and Adyen Account Holders. Resolved: PSP-specific terms are used side by side (**Connected Account** for Stripe, **Account Holder** for Adyen) because the two models differ structurally — Stripe uses one identifier (`acct_XXXX`) while Adyen uses two (**Account Holder ID** + **Balance Account ID**).
- **Tap to Pay** could have used a hardware smart reader with server-driven integration (`process_payment_intent`). Resolved: Tap to Pay (NFC soft reader, Terminal SDK in the merchant app) is the only supported no-hardware path; server-driven integration applies to WisePOS E and S700 only.
- **Terminal Location** could have been stored at the Merchant level (one location per Merchant). Resolved: a Location maps to a Branch, not a Merchant; it is stored per Branch as a nullable column so multi-branch merchants each have a distinct Stripe Location.
- **Terminal Location** creation could have been eager (at enable time) or explicit (admin endpoint). Resolved: created lazily on first **Connection Token** request for the Branch; eager creation is incomplete and a lazy fallback is unavoidable regardless.
- **Tap to Pay** payment initiation could have been restricted to the merchant app only. Resolved: either the shopper or the merchant app may call `create-intent` with `card_present`; the Order links the payment attempt and serves as the coordination point between the two apps without real-time signaling.
