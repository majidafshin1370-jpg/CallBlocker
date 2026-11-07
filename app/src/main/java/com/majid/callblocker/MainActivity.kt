package com.majid.callblocker

import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MainScreen(resumeTick.intValue)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}

@Composable
fun MainScreen(tick: Int) {
    val ctx = LocalContext.current

    var items by remember { mutableStateOf(BlockStore.load(ctx)) }
    var enabled by remember { mutableStateOf(BlockStore.isEnabled(ctx)) }
    var showManual by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val roleManager = ctx.getSystemService(RoleManager::class.java)
    val roleHeld = remember(tick) {
        roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
    }
    var roleState by remember(tick) { mutableStateOf(roleHeld) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        roleState = roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            ctx.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0) ?: ""
                    val number = c.getString(1) ?: ""
                    val added = BlockStore.add(ctx, name, number)
                    items = BlockStore.load(ctx)
                    message = if (added) "«$name» به لیست اضافه شد" else "این شماره قبلاً در لیست بود"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("مسدودکننده تماس", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        if (!roleState) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("برنامه هنوز به‌عنوان «برنامه فیلتر تماس» فعال نشده است.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (roleManager != null &&
                            roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                        ) {
                            roleLauncher.launch(
                                roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                            )
                        } else {
                            message = "این گوشی از فیلتر تماس پشتیبانی نمی‌کند"
                        }
                    }) { Text("فعال‌سازی") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("رد خودکار تماس شماره‌های لیست")
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                BlockStore.setEnabled(ctx, it)
            })
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                pickLauncher.launch(
                    Intent(
                        Intent.ACTION_PICK,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                    )
                )
            }) { Text("افزودن از مخاطبین") }
            OutlinedButton(onClick = { showManual = true }) { Text("افزودن شماره") }
        }

        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(16.dp))
        Text("لیست رد تماس (${items.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))

        if (items.isEmpty()) {
            Text("هنوز کسی اضافه نشده است.")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(items, key = { BlockStore.key(it.number) }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge)
                            Text(item.number, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = {
                            BlockStore.remove(ctx, item)
                            items = BlockStore.load(ctx)
                        }) { Text("حذف") }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "توجه: تماس‌گیرنده بوق «مشغول/رد شده» می‌شنود. پیام اپراتور «در دسترس نمی‌باشد» " +
                "را فقط خود شبکه می‌تواند پخش کند.",
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (showManual) {
        var name by remember { mutableStateOf("") }
        var number by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManual = false },
            title = { Text("افزودن شماره") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("نام (اختیاری)") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text("شماره تلفن") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val added = BlockStore.add(ctx, name, number)
                    items = BlockStore.load(ctx)
                    message = if (added) "شماره اضافه شد" else "شماره نامعتبر یا تکراری است"
                    showManual = false
                }) { Text("افزودن") }
            },
            dismissButton = {
                TextButton(onClick = { showManual = false }) { Text("انصراف") }
            }
        )
    }
}
