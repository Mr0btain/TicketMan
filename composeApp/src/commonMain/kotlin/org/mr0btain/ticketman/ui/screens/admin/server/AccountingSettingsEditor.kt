package org.mr0btain.ticketman.ui.screens.admin.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import org.mr0btain.ticketman.data.invoice.AccountingSettings
import org.mr0btain.ticketman.data.invoice.ExportDateFormat
import org.mr0btain.ticketman.data.invoice.TaxClass
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * The names the shop's books use, for the QuickBooks and Xero exports on the Invoices tab: QuickBooks items for each
 * kind of line, Xero account codes and tax rates (one per tax class too), the name on sales with no customer, when
 * invoices are due and how dates are written
 */
@Composable
fun AccountingSettingsEditor(settings: AccountingSettings, taxClasses: List<TaxClass>, onChange: (AccountingSettings) -> Unit) {
    val spacing = TmTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = "Accounting export", style = TmTheme.typography.label, color = TmTheme.colors.textMuted)
        Text(
            text = "The names your books use, for the QuickBooks and Xero exports on the Invoices tab.",
            style = TmTheme.typography.caption,
            color = TmTheme.colors.textMuted
        )

        TmFieldPair(
            { TmTextField(modifier = it, value = settings.laborItem, onValueChange = { v -> onChange(settings.copy(laborItem = v)) }, label = "QuickBooks labor item") },
            { TmTextField(modifier = it, value = settings.partsItem, onValueChange = { v -> onChange(settings.copy(partsItem = v)) }, label = "Parts item") }
        )
        TmFieldPair(
            { TmTextField(modifier = it, value = settings.otherItem, onValueChange = { v -> onChange(settings.copy(otherItem = v)) }, label = "Other item") },
            { TmTextField(modifier = it, value = settings.giftCardItem, onValueChange = { v -> onChange(settings.copy(giftCardItem = v)) }, label = "Gift card item") }
        )
        TmFieldPair(
            { TmTextField(modifier = it, value = settings.salesAccount, onValueChange = { v -> onChange(settings.copy(salesAccount = v)) }, label = "Xero sales account") },
            {
                TmTextField(
                    modifier = it,
                    value = settings.giftCardAccount,
                    onValueChange = { v -> onChange(settings.copy(giftCardAccount = v)) },
                    label = "Gift card account",
                    placeholder = "Same as sales"
                )
            }
        )
        TmFieldPair(
            { TmTextField(modifier = it, value = settings.taxType, onValueChange = { v -> onChange(settings.copy(taxType = v)) }, label = "Xero tax rate") },
            { TmTextField(modifier = it, value = settings.exemptTaxType, onValueChange = { v -> onChange(settings.copy(exemptTaxType = v)) }, label = "Untaxed lines") }
        )
        taxClasses.forEach { taxClass ->
            TmTextField(
                modifier = Modifier.fillMaxWidth(),
                value = settings.classTaxTypes[taxClass.identifier].orEmpty(),
                onValueChange = { v -> onChange(settings.copy(classTaxTypes = (settings.classTaxTypes + (taxClass.identifier to v)).filterValues { it.isNotBlank() })) },
                label = "Xero tax rate for ${taxClass.name.ifBlank { "this class" }}",
                placeholder = settings.taxType
            )
        }
        TmFieldPair(
            { TmTextField(modifier = it, value = settings.walkInCustomer, onValueChange = { v -> onChange(settings.copy(walkInCustomer = v)) }, label = "Customer on sales without one") },
            {
                TmTextField(
                    modifier = it,
                    value = settings.dueDays.takeIf { days -> days > 0 }?.toString().orEmpty(),
                    onValueChange = { text -> onChange(settings.copy(dueDays = text.filter { c -> c.isDigit() }.take(3).toIntOrNull() ?: 0)) },
                    label = "Due after",
                    placeholder = "Same day",
                    supportingText = "Days",
                    textStyle = TmTheme.typography.numeric,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        )
        TmDropdown(
            modifier = Modifier.fillMaxWidth(),
            label = "Dates",
            options = ExportDateFormat.entries,
            selected = settings.dateFormat,
            onSelect = { onChange(settings.copy(dateFormat = it)) },
            optionLabel = { it.label }
        )

        settings.problem()?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
    }
}
