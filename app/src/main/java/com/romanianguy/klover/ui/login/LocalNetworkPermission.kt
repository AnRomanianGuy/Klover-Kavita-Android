package com.romanianguy.klover.ui.login

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

// Android 17 gates LAN connections (192.168.x.x, *.local) behind this permission, without it they never complete
fun hasLocalNetworkPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
        PackageManager.PERMISSION_GRANTED
