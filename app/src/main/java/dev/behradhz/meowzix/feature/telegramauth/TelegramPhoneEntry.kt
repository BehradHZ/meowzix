package dev.behradhz.meowzix.feature.telegramauth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.ui.components.TelegramGlassInput
import java.util.Locale

@Composable
internal fun TelegramPhoneEntry(
    region: String,
    onRegionChange: (String) -> Unit,
    nationalNumber: String,
    onNationalNumberChange: (String) -> Unit,
    enabled: Boolean,
    onSubmit: (String) -> Unit,
) {
    val countries = remember { telegramDialCountries() }
    val selected = countries.firstOrNull { it.region == region } ?:
        countries.firstOrNull { it.region == "IR" } ?: countries.first()
    var countryPickerOpen by rememberSaveable { mutableStateOf(false) }
    var invalidPhone by rememberSaveable { mutableStateOf(false) }

    MessageState(
        icon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
        title = "Connect Telegram",
        message = "Choose your country code and enter your phone number to receive a Telegram sign-in code.",
    )

    OutlinedButton(
        onClick = { countryPickerOpen = true },
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(selected.flag, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(10.dp))
        Text(selected.name, modifier = Modifier.weight(1f), maxLines = 1)
        Text(selected.dialCode, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(6.dp))
        Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Choose country")
    }
    Spacer(Modifier.height(12.dp))
    TelegramGlassInput(
        value = nationalNumber,
        onValueChange = {
            onNationalNumberChange(it)
            invalidPhone = false
        },
        label = "Phone number (without country code)",
        modifier = Modifier.fillMaxWidth(),
        keyboardType = KeyboardType.Phone,
        enabled = enabled,
    )
    if (invalidPhone) {
        Text(
            "Enter a valid national phone number for ${selected.name}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
    Button(
        onClick = {
            val phone = telegramInternationalPhone(selected, nationalNumber)
            if (phone == null) invalidPhone = true else onSubmit(phone)
        },
        enabled = enabled && nationalNumber.isNotBlank(),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
    ) {
        Text("Continue", fontWeight = FontWeight.SemiBold)
    }

    if (countryPickerOpen) {
        CountryDialPicker(
            countries = countries,
            onDismiss = { countryPickerOpen = false },
            onSelect = {
                onRegionChange(it.region)
                invalidPhone = false
                countryPickerOpen = false
            },
        )
    }
}

@Composable
private fun CountryDialPicker(
    countries: List<TelegramDialCountry>,
    onDismiss: () -> Unit,
    onSelect: (TelegramDialCountry) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(countries, query) { searchTelegramCountries(countries, query) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                "Select country",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Search country or dial code") },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            if (results.isEmpty()) {
                Text(
                    "No countries found.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 28.dp),
                )
            }
            LazyColumn {
                items(results, key = { it.region }) { country ->
                    ListItem(
                        headlineContent = { Text(country.name) },
                        supportingContent = if (country.name != country.englishName) {
                            { Text(country.englishName) }
                        } else null,
                        leadingContent = { Text(country.flag, style = MaterialTheme.typography.titleMedium) },
                        trailingContent = { Text(country.dialCode) },
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(country) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
            }
        }
    }
}
