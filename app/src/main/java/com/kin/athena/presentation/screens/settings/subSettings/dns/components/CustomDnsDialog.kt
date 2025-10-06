/*
 * Copyright (C) 2025 Vexzure
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.kin.athena.R
import com.kin.athena.presentation.components.material.MaterialTextField
import com.kin.athena.presentation.screens.settings.components.SettingDialog
import com.kin.athena.service.vpn.network.util.NetworkConstants

@Composable
fun CustomDnsDialog(
    onExit: () -> Unit,
    onDone: (dns1: String, dns2: String) -> Unit,
    dns1Key: String?,
    dns2Key: String?
) {
    val context = LocalContext.current

    var dns1 by remember { mutableStateOf(TextFieldValue(dns1Key ?: "")) }
    var dns2 by remember { mutableStateOf(TextFieldValue(dns2Key ?: "")) }

    fun isValidIpv4(ip: String): Boolean {
        val ipv4Regex = Regex(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        )
        return ipv4Regex.matches(ip)
    }

    SettingDialog(
        text = stringResource(R.string.dns_custom),
        onExit = { onExit() }
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                MaterialTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = dns1,
                    onValueChange = { dns1 = it },
                    placeholder = NetworkConstants.DNS_SERVERS[0].second.first,
                    singleLine = true,
                    keyboardType = KeyboardType.Number
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                MaterialTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = dns2,
                    onValueChange = { dns2 = it },
                    placeholder = NetworkConstants.DNS_SERVERS[0].second.second,
                    singleLine = true,
                    keyboardType = KeyboardType.Number
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (isValidIpv4(dns1.text) && isValidIpv4(dns2.text)) {
                        onDone(dns1.text, dns2.text)
                        onExit()
                    } else {
                        Toast.makeText(context, context.getString(R.string.dns_invalid), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = stringResource(R.string.common_done))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}