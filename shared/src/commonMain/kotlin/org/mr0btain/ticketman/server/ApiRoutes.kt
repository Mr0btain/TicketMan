package org.mr0btain.ticketman.server

/*
 * Every path the server mounts and the clients call, in one place, so renaming one on either side breaks the build
 * instead of the app. Each route file is mounted at an [ApiRoutes] path and its endpoints are the paths in its own
 * object below, relative to that. `{name}` parts are path parameters; clients fill them in with [fill].
 *
 * scripts/check_api_docs.py reads this file to work out the routes, so keep each path a plain string constant.
 */

/** Bumped when a change means an older client and this server can't work together */
const val API_VERSION = 13

/** Header the server answers `/api/ping` with, holding its [API_VERSION] */
const val API_VERSION_HEADER = "X-TicketMan-Api"

/**
 * Header every client request carries with the location it's working at, a location's id, or [ALL_LOCATIONS] for an
 * admin looking at every one. Without it the server uses the caller's home location
 */
const val LOCATION_HEADER = "X-TicketMan-Location"

/** [LOCATION_HEADER]'s value for every location at once, which only admins get */
const val ALL_LOCATIONS = "all"

/** Where each route file is mounted */
object ApiRoutes {
    const val AUTH = "/api/auth"
    const val USER = "/api/user"
    const val ADMIN = "/api/admin"
    const val COUNTER = "/api/counter"
    const val INVOICE = "/api/invoice"
    const val STATION = "/api/station"
    const val SHOP = "/api/shop"
    const val IMAGE = "/api/image"
    const val CUSTOMER = "/api/data/customer"
    const val TICKET = "/api/data/ticket"
    const val INVENTORY = "/api/data/inventory"

    const val NOTIFY = "/api/notify"
    const val GIFT_CARD = "/api/giftcard"
    const val PURCHASE = "/api/purchase"
    const val ESTIMATE = "/api/estimate"
    const val REPORT = "/api/report"
    const val APPOINTMENT = "/api/appointment"
    const val TIME_CLOCK = "/api/timeclock"
    const val PAYMENT = "/api/payment"
    const val LOCATION = "/api/location"
    const val SCHEDULE = "/api/schedule"
    const val TIME_OFF = "/api/timeoff"

    /** The customer status page, server rendered HTML with no login. The only part meant for the internet */
    const val STATUS = "/status"
}

/** Query parameters the paged and search reads take */
object ApiQuery {
    const val OFFSET = "offset"
    const val LIMIT = "limit"
    const val QUERY = "query"
}

/** Paths every record type has (customers, tickets, inventory and users), under its [ApiRoutes] mount */
object RecordPaths {
    const val ADD = "/add"
    const val UPDATE = "/update"
    const val REMOVE = "/remove/{identifier}"
    const val GET = "/get/{identifier}"

    /** A page, `?offset=&limit=` */
    const val FETCH = "/fetch"

    /** `?query=&limit=` */
    const val SEARCH = "/search"

    /** Where a record is in `/fetch`, so a list can jump to it */
    const val POSITION = "/position/{identifier}"
}

/** The note paths every record type mounts through the server's shared noteRoute */
object NotePaths {
    const val ADD = "/add/note/{identifier}"
    const val GET = "/get/notes/{identifier}"
    const val UPDATE = "/update/note/{identifier}"
    const val REMOVE = "/remove/note/{identifier}"
}

object CustomerPaths {
    const val GET_TICKETS = "/get/tickets/{identifier}"
    const val ADD_TICKET = "/add/ticket/{identifier}"

    /** `GET` the store credit history, newest first. `POST` a `BalanceAdjustRequest`, admin only */
    const val CREDIT = "/credit/{identifier}"

    /** The customer's invoices, tickets' and sales', newest first */
    const val INVOICES = "/get/invoices/{identifier}"

    /** `GET` the customer's devices, `POST` a `CustomerDevice` to add one */
    const val DEVICES = "/devices/{identifier}"

    /** Body `CustomerDevice` */
    const val DEVICE_UPDATE = "/device/update"

    /** `DELETE`: the device goes, its tickets stay */
    const val DEVICE = "/device/{identifier}"

    /** The device's tickets, newest first */
    const val DEVICE_TICKETS = "/device/{identifier}/tickets"
}

object TicketPaths {
    /** The caller's tickets, `?offset=&limit=` */
    const val FETCH_MINE = "/fetch/mine"

    /** Where a ticket is in `/fetch/mine` */
    const val POSITION_MINE = "/position/mine/{identifier}"
    const val BY_NUMBER = "/number/{number}"
    const val HISTORY = "/get/history/{identifier}"
    const val SIGNATURE = "/signature/{identifier}"
    const val GET_CUSTOMER = "/get/customer/{identifier}"
    const val GET_USER = "/get/user/{identifier}"
    const val SET_CUSTOMER = "/set/customer/{identifier}"
    const val SET_USER = "/set/user/{identifier}"

    /** The ticket's work log, oldest first */
    const val GET_WORK = "/get/work/{identifier}"
    const val ADD_WORK = "/add/work/{identifier}"

    /** Body a list of `WorkRequest`: all of them are logged, or none */
    const val ADD_WORK_ALL = "/add/work/all/{identifier}"

    /** Takes the work entry with this identifier off, and its invoice line */
    const val REMOVE_WORK = "/remove/work/{identifier}"

    /** `POST`: gives the ticket a new status token, so the old status page link stops working */
    const val STATUS_TOKEN = "/status-token/{identifier}"

    /** `GET` shows the device's passcode (audited), `POST` a `PasscodeBody` sets it, `DELETE` clears it */
    const val PASSCODE = "/passcode/{identifier}"

    /** `POST` a `MoveTicketRequest`: the ticket, and its open invoice, go to another location */
    const val MOVE = "/move/{identifier}"
}

object InventoryPaths {
    const val GET_IMAGES = "/get/images/{identifier}"
    const val SET_IMAGES = "/set/images/{identifier}"
    const val SET_STOCK = "/set/stock/{identifier}"

    /** `POST` a `StockTransferRequest`: parts from one location to another */
    const val TRANSFER = "/transfer/{identifier}"

    /** An item's moves between locations, newest first */
    const val TRANSFERS = "/transfers/{identifier}"

    /** The item whose barcode or SKU is exactly this, for a scanner */
    const val BY_CODE = "/code/{code}"

    /** Every category in use, by name */
    const val CATEGORIES = "/categories"
}

object UserPaths {
    const val SETUP = "/setup"
    const val ME = "/me"
    const val ME_PIN = "/me/pin"
    const val PIN_UNLOCK = "/pin/{identifier}/unlock"
    const val PIN = "/pin/{identifier}"
    const val PENDING = "/pending"
    const val APPROVE = "/approve/{identifier}"
    const val REJECT = "/reject/{identifier}"
    const val INVITE = "/invite"
    const val INVITE_RENEW = "/invite/{identifier}/renew"
    const val PASSWORD = "/password/{identifier}"

    /** Body `UserPhotoRequest`. Admins for anyone, Normal users for themselves */
    const val SET_PHOTO = "/set/photo/{identifier}"

    /** Admin only: `GET` the ID card code, `POST` a new one, which cancels the old card */
    const val BADGE = "/badge/{identifier}"
}

/** Logging in is a POST to [ApiRoutes.AUTH] itself */
object AuthPaths {
    const val REQUEST_ACCOUNT = "/request-account"
    const val SETUP_INVITE = "/setup/invite"
    const val PIN = "/pin"
    const val STATION_STATUS = "/station/status"
    const val STATION_STAFF = "/station/staff"
    const val LOGOUT = "/logout"
    const val REFRESH = "/refresh"
    const val KIOSK_LEAVE = "/kiosk/leave"
}

object AdminPaths {
    const val STATS = "/stats"
    const val DAILY_REPORT = "/report/daily"
    const val LOW_STOCK = "/inventory/low-stock"
    const val SESSIONS = "/sessions"
    const val SESSION = "/sessions/{sessionId}"
    const val USER_SESSIONS = "/sessions/user/{identifier}"

    /** Usernames and addresses locked out for wrong passwords */
    const val LOGIN_LIMITS = "/logins"
    const val LOGIN_UNLOCK = "/logins/unlock"
    const val STATIONS = "/stations"
    const val STATION_APPROVE = "/stations/{identifier}/approve"
    const val STATION_KIOSK = "/stations/{identifier}/kiosk"

    /** `POST` a `StationLocationRequest`: which location the station is at */
    const val STATION_LOCATION = "/stations/{identifier}/location"
    const val STATION = "/stations/{identifier}"
    const val SERVER = "/server"
    const val BACKUP = "/backup"
    const val BACKUP_DOWNLOAD = "/backup/download/{id}"
    const val BACKUP_START = "/backup/start"
    const val BACKUP_STATUS = "/backup/status/{id}"
    const val RESTORE = "/restore"
    const val RESTART = "/restart"
    const val SEED = "/seed"

    /** A list of customers to add, `?dryRun=true` to only say what would happen */
    const val IMPORT_CUSTOMERS = "/import/customers"

    /** A list of inventory items to add, `?dryRun=true` to only say what would happen */
    const val IMPORT_INVENTORY = "/import/inventory"

    /** A page of the audit log, `?offset=&limit=` */
    const val AUDIT = "/audit/fetch"
}

object CounterPaths {
    const val CHECK_IN = "/check-in"
    const val PICKUP = "/pickup"

    /** Body `SaleRequest`: sells from stock without a ticket, paid there and then */
    const val SALE = "/sale"

    /** `?query=&limit=` */
    const val SEARCH = "/search"
    const val QUEUE = "/queue"
    const val REPEAT_CHECK = "/repeat-check"
}

object InvoicePaths {
    const val FOR_TICKET = "/ticket/{ticketIdentifier}"
    const val GET = "/{identifier}"

    /** A page, `?offset=&limit=` */
    const val FETCH = "/fetch"
    const val UPDATE = "/{identifier}/update"
    const val PAYMENTS = "/{identifier}/payments"
    const val PAYMENT = "/{identifier}/payments/{paymentIdentifier}"
    const val VOID = "/{identifier}/void"

    /** Admin only, body `RefundRequest`: money back on a paid invoice, optionally parts back in stock */
    const val REFUND = "/{identifier}/refund"

    /** `DELETE`, admin only: an invoice kept after its ticket was deleted */
    const val REMOVE = "/{identifier}"

    /** The invoice as a PDF, laid out like the printed one */
    const val PDF = "/{identifier}/pdf"

    /** Invoices made in a range as CSV, `?from=2026-10-01&to=2026-10-31`, both days included */
    const val EXPORT = "/export"
}

object NotifyPaths {
    /** Admin only: `GET` the settings without passwords, `POST` to save them */
    const val SETTINGS = "/settings"

    /** Admin only: sends a `TestMessageRequest` now */
    const val TEST = "/test"

    /** Admin only: every message, newest first, `?offset=&limit=` */
    const val OUTBOX = "/outbox"
    const val RETRY = "/outbox/{identifier}/retry"
    const val CANCEL = "/outbox/{identifier}/cancel"

    /** The messages sent about a ticket, newest first */
    const val TICKET = "/ticket/{identifier}"

    /** Body `TicketMessageRequest`: sends one to the ticket's customer now */
    const val TICKET_SEND = "/ticket/{identifier}/send"
}

/** Under [ApiRoutes.APPOINTMENT] */
object AppointmentPaths {
    /** `?from=&to=` days like 2026-10-01, both included, by when they start */
    const val RANGE = "/range"
    const val GET = "/get/{identifier}"
    const val ADD = "/add"
    const val UPDATE = "/update"

    /** Body `AppointmentStatusRequest`: cancelled, no show, or booked again */
    const val STATUS = "/status/{identifier}"
    const val REMOVE = "/remove/{identifier}"
}

/** Under [ApiRoutes.SCHEDULE] */
object SchedulePaths {
    /** `?from=&to=` days like 2026-10-01, both included, every entry touching them. `&user=` for one person's */
    const val RANGE = "/range"
    const val GET = "/get/{identifier}"

    /** Admin only, body `ScheduleRequest`: answers with every entry it made */
    const val ADD = "/add"

    /** Admin only, body `ScheduleEntry`, `?scope=` ONE, FOLLOWING or SERIES: answers with every entry changed */
    const val UPDATE = "/update"

    /** Admin only, `?scope=` like update */
    const val REMOVE = "/remove/{identifier}"
}

/** Under [ApiRoutes.TIME_OFF] */
object TimeOffPaths {
    /** The caller's requests and balance, `?year=` or this year */
    const val ME = "/me"

    /** Body `TimeOffRequest`: asks for time off */
    const val REQUEST = "/request"

    /** Takes back the caller's own request while it's waiting */
    const val WITHDRAW = "/withdraw/{identifier}"

    /** Admin only: requests, `?status=PENDING` for those waiting */
    const val REQUESTS = "/requests"

    /** Admin only, body `TimeOffDecision` */
    const val APPROVE = "/approve/{identifier}"
    const val DECLINE = "/decline/{identifier}"

    /** Admin only: every active user's balance, `?year=` or this year */
    const val BALANCES = "/balances"

    /** Admin only, body `TimeOffAllowance`: someone's yearly allowance */
    const val ALLOWANCE = "/allowance/{identifier}"
}

/** Under [ApiRoutes.TIME_CLOCK] */
object TimeClockPaths {
    /** Body `PinLoginRequest`: clocks in or out at a station by PIN, with no login */
    const val PUNCH = "/punch"

    /** The caller: whether they're on the clock, and clocking in or out from their own device */
    const val ME = "/me"
    const val ME_PUNCH = "/me/punch"

    /** `?from=&to=`: the caller's own time */
    const val ME_TIMESHEET = "/me/timesheet"

    /** Who's on the clock now */
    const val NOW = "/now"

    /** `?from=&to=` and optionally `user=`, admin only */
    const val TIMESHEET = "/timesheet"
    const val ENTRY_ADD = "/entry/add"
    const val ENTRY_UPDATE = "/entry/update"
    const val ENTRY_REMOVE = "/entry/remove/{identifier}"
}

/** Under [ApiRoutes.REPORT], admin only */
object ReportPaths {
    /** `?from=&to=` days like 2026-10-01, both included: a `PeriodReport` */
    const val PERIOD = "/period"

    /** Each user's commission on labor, read and replaced as a whole list */
    const val COMMISSIONS = "/commissions"
}

object PurchasePaths {
    /** Every supplier, by name */
    const val SUPPLIERS = "/suppliers"
    const val SUPPLIER_ADD = "/suppliers/add"
    const val SUPPLIER_UPDATE = "/suppliers/update"
    const val SUPPLIER = "/suppliers/{identifier}"

    /** A page of orders, newest first, `?status=&offset=&limit=` */
    const val ORDERS = "/orders"
    const val ORDER = "/orders/{identifier}"

    /** Orders with lines for a ticket */
    const val TICKET = "/orders/ticket/{ticketIdentifier}"

    /** Body `PurchaseOrderRequest`: a new draft */
    const val ADD = "/orders/add"

    /** Body `PurchaseOrderRequest`: changes a draft */
    const val UPDATE = "/orders/{identifier}/update"

    /** The draft goes to the supplier */
    const val PLACE = "/orders/{identifier}/place"

    /** Body `ReceiveRequest` */
    const val RECEIVE = "/orders/{identifier}/receive"
    const val CANCEL = "/orders/{identifier}/cancel"

    /** Body `OrderPartRequest`: a part for a ticket onto the supplier's draft */
    const val PART = "/orders/part"
}

object GiftCardPaths {
    /** Looks a card up by its code, to check the balance */
    const val GET = "/code/{code}"

    /** Admin only, every card newest first, `?offset=&limit=` */
    const val FETCH = "/fetch"

    /** Admin only, body `BalanceAdjustRequest` */
    const val ADJUST = "/code/{code}/adjust"
}

object EstimatePaths {
    /** `GET` the ticket's estimates, newest first. `POST` an `EstimateRequest` to send a new one */
    const val TICKET = "/ticket/{ticketIdentifier}"

    /** Body `EstimateDecisionRequest`: what the customer said, written down by staff */
    const val DECIDE = "/{identifier}/decide"
    const val WITHDRAW = "/{identifier}/withdraw"
}

/** Under [ApiRoutes.STATUS], whose own path is the lookup form (`GET`) and its answer (`POST`) */
object StatusPaths {
    const val PAGE = "/{token}"

    /** Booking a visit, when the shop takes bookings online: `GET` the page (`?location=&day=`), `POST` the form */
    const val BOOK = "/book"

    /** The customer approving or declining the waiting estimate: form fields `decision` and `name` */
    const val ESTIMATE = "/{token}/estimate"

    /** A photo from an update the customer can see, `thumb` or `display` */
    const val PHOTO = "/{token}/photo/{identifier}/{variant}"

    /** Off to the processor's checkout page for the balance, when the shop takes payments online */
    const val PAY = "/{token}/pay"

    /** The card processor telling the server a payment changed, `stripe` or `square`. Here so it's reachable from outside */
    const val PAYMENT_WEBHOOK = "/payments/{provider}/webhook"
}

/** The list itself is a GET of [ApiRoutes.LOCATION], for anyone logged in */
object LocationPaths {
    /** Admin only, body a `Location` */
    const val ADD = "/add"

    /** Admin only, body a `Location` */
    const val UPDATE = "/update"
}

object PaymentPaths {
    /** Admin only: `GET` the settings without keys, `POST` to save them */
    const val SETTINGS = "/settings"

    /** Admin only: the readers on the processor account, with the saved settings */
    const val ACCOUNT_READERS = "/readers"

    /** What the counter can offer, no keys */
    const val OPTIONS = "/options"

    /** Body `ReaderChargeRequest`: sends an amount to a card reader */
    const val READER = "/charge/reader"

    /** Body `PaymentLinkRequest`: a checkout page for an invoice's balance */
    const val LINK = "/charge/link"

    /** A charge, checked with the processor first while it's waiting */
    const val CHARGE = "/charge/{identifier}"
    const val CANCEL = "/charge/{identifier}/cancel"

    /** Body `SendLinkRequest`: emails or texts a waiting payment link to the invoice's customer */
    const val SEND = "/charge/{identifier}/send"

    /** Admin only: gives back a paid charge that never made it onto an invoice */
    const val REFUND = "/charge/{identifier}/refund"

    /** Admin only: every charge, newest first, `?offset=&limit=` */
    const val CHARGES = "/charges"

    /** An invoice's charges, newest first */
    const val FOR_INVOICE = "/charges/invoice/{identifier}"
}

object StationPaths {
    const val REGISTER = "/register"
}

/** The profile itself is a GET of [ApiRoutes.SHOP] */
object ShopPaths {
    const val UPDATE = "/update"

    /** The profile as it applies at the location the caller works at: its address, contacts and tax */
    const val HERE = "/here"
}

object ImagePaths {
    const val UPLOAD = "/upload"
    const val GET = "/{identifier}"
    const val FILE = "/{identifier}/{variant}"
}

// Android's regex engine (ICU) refuses a bare `}`, the JVM's doesn't, so keep it escaped
private val PARAMETER = Regex("""\{[^}]+\}""")

/**
 * The path with each `{name}` part replaced by the next of [values], in order, like
 * `NotePaths.GET.fill(identifier)` giving `/get/notes/0199...`
 */
fun String.fill(vararg values: Any): String {
    var next = 0
    val filled = PARAMETER.replace(this) {
        require(next < values.size) { "$this needs more than ${values.size} values" }
        values[next++].toString()
    }

    require(next == values.size) { "$this takes $next values, got ${values.size}" }
    return filled
}
