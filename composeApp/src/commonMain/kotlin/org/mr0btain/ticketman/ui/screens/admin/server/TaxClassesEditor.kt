package org.mr0btain.ticketman.ui.screens.admin.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.data.invoice.TaxClass
import org.mr0btain.ticketman.data.invoice.TaxClasses
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.x

/**
 * The shop's other tax rates, each a name and a percent. Invoices keep the rates they were made with, so changing
 * one only changes invoices made after
 */
@Composable
fun TaxClassesEditor(classes: List<TaxClass>, onChange: (List<TaxClass>) -> Unit) {
    val spacing = TmTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = "Other tax rates", style = TmTheme.typography.label, color = TmTheme.colors.textMuted)
        Text(
            text = "For things taxed differently from the main rate, like food or services. Pick one on an inventory item or an invoice line.",
            style = TmTheme.typography.caption,
            color = TmTheme.colors.textMuted
        )

        classes.forEachIndexed { index, taxClass ->
            fun change(changed: TaxClass) = onChange(classes.toMutableList().also { it[index] = changed })

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                TmTextField(
                    modifier = Modifier.weight(1f),
                    value = taxClass.name,
                    onValueChange = { change(taxClass.copy(name = it)) },
                    placeholder = "Name, like Food"
                )
                TmTextField(
                    modifier = Modifier.width(96.dp),
                    value = taxClass.rate,
                    onValueChange = { text -> change(taxClass.copy(rate = text.filter { c -> c.isDigit() || c == '.' }.take(6))) },
                    placeholder = "Percent",
                    textStyle = TmTheme.typography.numeric,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                TmIconButton(
                    icon = Res.drawable.x,
                    contentDescription = "Remove ${taxClass.name}",
                    onClick = { onChange(classes - taxClass) },
                    size = TmControlSize.SMALL
                )
            }
        }

        TaxClasses.problem(classes)?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }

        TmButton(
            text = "Add a tax rate",
            variant = TmButtonVariant.SECONDARY,
            leadingIcon = Res.drawable.plus,
            onClick = { onChange(classes + TaxClass(TaxClasses.newIdentifier(), "", "")) }
        )
    }
}
