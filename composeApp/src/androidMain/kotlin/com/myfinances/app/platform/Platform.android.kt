package com.myfinances.app.platform

import android.os.Build

actual object Platform {
    actual val name: String = "Android ${Build.VERSION.SDK_INT}"
}

