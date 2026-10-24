package org.mr0btain.ticketman.server

/*
 * Every path the server mounts and the clients call, in one place, so renaming one on either side breaks the build
 * instead of the app. Each route file is mounted at an [ApiRoutes] path and its endpoints are the paths in its own
 * object below, relative to that. `{name}` parts are path parameters; clients fill them in with [fill].
 *
 * scripts/check_api_docs.py reads this file to work out the routes, so keep each path a plain string constant.
 */

/** Bumped when a change means an older client and this server can't work together */
const val API_VERSION = 8

/** Header the server answers `/api/ping` with, holding its [API_VERSION] */
const val API_VERSION_HEADER = "X-TicketMan-Api"

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
    const val ESTIMATE = "/api/estimate"

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
}

object TicketPaths {
    /** The caller's tickets, `?offset=&limit=` */
    const val FETCH_MINE = "/fetch/mine"
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
}

object InventoryPaths {
    const val GET_IMAGES = "/get/images/{identifier}"
    const val SET_IMAGES = "/set/images/{identifier}"
    const val SET_STOCK = "/set/stock/{identifier}"
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

    /** The customer approving or declining the waiting estimate: form fields `decision` and `name` */
    const val ESTIMATE = "/{token}/estimate"

    /** A photo from an update the customer can see, `thumb` or `display` */
    const val PHOTO = "/{token}/photo/{identifier}/{variant}"
}

object StationPaths {
    const val REGISTER = "/register"
}

/** The profile itself is a GET of [ApiRoutes.SHOP] */
object ShopPaths {
    const val UPDATE = "/update"
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
