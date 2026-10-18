# Stations API

`/api/station`, from [`StationRoute.kt`](../../server/src/main/kotlin/org/mr0btain/ticketman/components/routing/StationRoute.kt).
A device asking to be a counter station, where staff sign in by PIN. The rest of a station's life is elsewhere:
an admin approves it and sets kiosk mode on the [Admin API](admin.md#stations), and the station checks its status,
lists its staff and PINs people in on the [Auth API](auth.md#stations). See
[PINs and stations](../server/pins-and-stations.md).

### `POST /api/station/register`

**Auth:** normal

Registers the calling device as a station, waiting for an admin. Registering the same `deviceId` again replaces
its old registration: a new id and secret, and it has to be approved again.

- **Body:** `StationRegistrationRequest { deviceId, name }`.
- **`201`** `StationRegistrationResponse { station, credentials }`. `credentials` is the station's id and secret,
  which the device keeps; this is the only time the secret is sent.
- **`400`** `Device and name are required`.

## See also

- [PINs and stations](../server/pins-and-stations.md)
- [Kiosk](../ui/screens/kiosk.md)
