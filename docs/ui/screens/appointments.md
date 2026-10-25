# Appointments screen

`Destination.APPOINTMENTS` ([`AppointmentsScreen`](../../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/ui/screens/appointments/AppointmentsScreen.kt)),
"Calendar" in the nav for everyone; Restricted users can look but every button that changes something is hidden,
and `AppointmentsHandler` refuses anyway. The server side is the [Appointments API](../../api/appointment.md).

A week at a time, Monday first: the header steps a week back or forward and **Today** comes back to this week. Each
day lists its appointments by time (`AppointmentWeek`), with the length, who, the device and problem, a new
customer's phone, the technician and notes, and the status as a badge (`TmColors.tone(AppointmentStatus)` in the
kit). A booked one opens `AppointmentDialog` when clicked and has **Check in** and a menu (Edit, No show, Cancel,
Delete); a cancelled or no show one can be booked again.

**Book** opens `AppointmentDialog` for the next hour today (or 10:00 on the week's first day): the day and time
typed (`AppointmentTimes`), a length, a customer found by name or phone, or someone new's name, phone and email, the
device, what's wrong, notes and a technician.

**Check in** loads the customer, if it has one, and starts the counter's check in with the appointment
(`CounterCheckIn.startFromAppointment`, then the screen goes to the counter). The wizard starts filled in from it:
the device, the problem and the technician, and for someone new the customer search starts with their phone (or
name) and **New customer** starts with their details (`TmCustomerForm(initial = ...)`). The check in sends the
appointment's id, so the server marks it checked in with the new ticket.

State lives in `viewModel.appointments`
([`AppointmentsHandler`](../../../composeApp/src/commonMain/kotlin/org/mr0btain/ticketman/ui/screens/appointments/components/AppointmentsHandler.kt)),
loaded when the screen opens; saving one shows its week.

Render tests: `ScreenRenderTest.appointments()` and `appointmentDialog()`.
