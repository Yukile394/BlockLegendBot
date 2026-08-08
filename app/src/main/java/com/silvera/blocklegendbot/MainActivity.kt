package com.silvera.blocklegendbot

import com.android.keyboard.assistant.R
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val REQ_OVERLAY    = 1001
    private val REQ_PROJECTION = 1002

    private lateinit var tvStatus  : TextView
    private lateinit var etX       : EditText
    private lateinit var etY       : EditText
    private lateinit var etInterval: EditText
    private lateinit var etBurst   : EditText
    private lateinit var swEsp     : Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus   = findViewById(R.id.tvStatus)
        etX        = findViewById(R.id.etX)
        etY        = findViewById(R.id.etY)
        etInterval = findViewById(R.id.etInterval)
        etBurst    = findViewById(R.id.etBurst)
        swEsp      = findViewById(R.id.swEsp)

        findViewById<Button>(R.id.btnOpenAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            when {
                !isAccessibilityEnabled() -> toast("Önce Erişilebilirlik servisini aç!")
                !Settings.canDrawOverlays(this) -> requestOverlayPermission()
                swEsp.isChecked -> requestProjection()
                else -> launchOverlay(null, -1)
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            AutoClickService.instance?.stopClicking()
            tvStatus.text = "Durum: Durdu ■"
        }

        updateStatus()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_OVERLAY    -> if (Settings.canDrawOverlays(this)) toast("Overlay izni tamam → Başlat'a bas")
            REQ_PROJECTION -> if (resultCode == RESULT_OK && data != null) launchOverlay(data, resultCode)
                              else toast("Ekran paylaşımı reddedildi, ESP devre dışı")
        }
    }

    private fun launchOverlay(projData: Intent?, resultCode: Int) {
        val x   = etX.text.toString().toFloatOrNull()        ?: 540f
        val y   = etY.text.toString().toFloatOrNull()        ?: 960f
        val ms  = etInterval.text.toString().toLongOrNull()  ?: 100L
        val bst = etBurst.text.toString().toIntOrNull()      ?: 1

        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_TARGET_X,    x)
            putExtra(OverlayService.EXTRA_TARGET_Y,    y)
            putExtra(OverlayService.EXTRA_INTERVAL_MS, ms)
            putExtra(OverlayService.EXTRA_BURST,       bst)
            putExtra(OverlayService.EXTRA_ESP_ON,      swEsp.isChecked)
            if (projData != null) {
                putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
                putExtra(OverlayService.EXTRA_RESULT_DATA, projData)
            }
        }
        startService(intent)
        tvStatus.text = "Durum: Çalışıyor ▶  (${ms}ms × $bst burst)"
    }

    private fun requestOverlayPermission() {
        startActivityForResult(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            REQ_OVERLAY
        )
    }

    private fun requestProjection() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val services = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return services.contains(packageName)
    }

    private fun updateStatus() {
        tvStatus.text = if (isAccessibilityEnabled()) "Durum: Hazır ✓" else "Durum: Erişilebilirlik gerekli ⚠"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
