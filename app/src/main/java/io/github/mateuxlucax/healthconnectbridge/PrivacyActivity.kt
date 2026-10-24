package io.github.mateuxlucax.healthconnectbridge

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

/** Privacy rationale opened from the Health Connect permission screen. The manifest alias targets it by name: do not move it. */
class PrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (16 * resources.displayMetrics.density).toInt()
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(TextView(this@PrivacyActivity).apply {
                setPadding(padding, padding, padding, padding)
                textSize = 16f
                setText(R.string.privacy_text)
            })
        })
    }
}
