package com.qingal.dizzystop

import android.Manifest
import android.app.StatusBarManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var permissionStatus: TextView
    private lateinit var serviceStatus: TextView
    private lateinit var statusDot: View
    private lateinit var statusCard: MaterialCardView
    private lateinit var grantButton: MaterialButton
    private lateinit var serviceSwitch: MaterialSwitch
    private lateinit var advancedContent: LinearLayout
    private lateinit var advancedButton: MaterialButton
    private val settingReloaders = mutableListOf<() -> Unit>()

    private var startAfterPermission = false
    private var updatingServiceSwitch = false

    private val overlaySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (startAfterPermission && Settings.canDrawOverlays(this)) {
            startAfterPermission = false
            requestNotificationPermissionAndStart()
        }
        updateUi()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startMotionCueService()
    }

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
        updateUi()
        handleLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            serviceStateReceiver,
            IntentFilter(MotionCueService.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(serviceStateReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun createContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 36.dp, 20.dp, 32.dp)
            setBackgroundColor(color(R.color.screen_background))
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.screen_eyebrow)
            textSize = 13f
            setTextColor(color(R.color.brand_primary))
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.08f
        }, matchWidth(wrapHeight()).withBottomMargin(8.dp))

        root.addView(TextView(this).apply {
            text = getString(R.string.screen_title)
            textSize = 32f
            setTextColor(color(R.color.text_primary))
            setTypeface(typeface, Typeface.BOLD)
        }, matchWidth(wrapHeight()).withBottomMargin(10.dp))

        root.addView(TextView(this).apply {
            text = getString(R.string.screen_description)
            textSize = 16f
            setTextColor(color(R.color.text_secondary))
            setLineSpacing(0f, 1.18f)
        }, matchWidth(wrapHeight()).withBottomMargin(22.dp))

        root.addView(createStatusCard(), matchWidth(wrapHeight()).withBottomMargin(14.dp))
        root.addView(createControlCard(), matchWidth(wrapHeight()).withBottomMargin(14.dp))
        root.addView(createQuickSettingsButton(), matchWidth(wrapHeight()).withBottomMargin(22.dp))

        root.addView(
            createSettingsCard(
                R.string.appearance_settings,
                R.string.basic_settings_hint
            ) { content ->
                addAppearanceSettings(content)
            },
            matchWidth(wrapHeight()).withBottomMargin(14.dp)
        )

        root.addView(
            createSettingsCard(
                R.string.motion_settings,
                R.string.motion_settings_hint
            ) { content ->
                addMotionSettings(content)
            },
            matchWidth(wrapHeight()).withBottomMargin(14.dp)
        )

        root.addView(createAdvancedCard(), matchWidth(wrapHeight()).withBottomMargin(14.dp))

        root.addView(TextView(this).apply {
            text = getString(R.string.passenger_warning)
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(color(R.color.text_secondary))
            setPadding(12.dp, 8.dp, 12.dp, 8.dp)
        }, matchWidth(wrapHeight()).withBottomMargin(12.dp))

        root.addView(MaterialButton(this).apply {
            text = getString(R.string.reset_settings)
            cornerRadius = 16.dp
            insetTop = 0
            insetBottom = 0
            setTextColor(color(R.color.brand_primary))
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            strokeColor = ColorStateList.valueOf(color(R.color.card_stroke))
            strokeWidth = 1.dp
            setOnClickListener {
                MotionCueSettings.reset(this@MainActivity)
                settingReloaders.forEach { it() }
            }
        }, matchWidth(52.dp))

        return ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(color(R.color.screen_background))
            addView(
                root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun createStatusCard(): MaterialCardView {
        statusDot = View(this).apply {
            background = ovalDrawable(color(R.color.status_warning))
        }
        permissionStatus = TextView(this).apply {
            textSize = 14f
            setTextColor(color(R.color.text_secondary))
        }
        serviceStatus = TextView(this).apply {
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color(R.color.text_primary))
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(serviceStatus, matchWidth(wrapHeight()).withBottomMargin(5.dp))
            addView(permissionStatus, matchWidth(wrapHeight()))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20.dp, 18.dp, 20.dp, 18.dp)
            addView(statusDot, LinearLayout.LayoutParams(12.dp, 12.dp).apply {
                marginEnd = 16.dp
            })
            addView(textColumn, LinearLayout.LayoutParams(0, wrapHeight(), 1f))
        }

        return modernCard().apply {
            addView(row)
        }.also { statusCard = it }
    }

    private fun createControlCard(): MaterialCardView {
        val title = TextView(this).apply {
            text = getString(R.string.motion_cue_switch_title)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color(R.color.text_primary))
        }
        val subtitle = TextView(this).apply {
            text = getString(R.string.settings_live_hint)
            textSize = 14f
            setTextColor(color(R.color.text_secondary))
        }
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title, matchWidth(wrapHeight()).withBottomMargin(5.dp))
            addView(subtitle, matchWidth(wrapHeight()))
        }

        serviceSwitch = MaterialSwitch(this).apply {
            setOnCheckedChangeListener { _, checked ->
                if (updatingServiceSwitch) return@setOnCheckedChangeListener
                if (checked) {
                    if (!Settings.canDrawOverlays(this@MainActivity)) {
                        updatingServiceSwitch = true
                        isChecked = false
                        updatingServiceSwitch = false
                        openOverlaySettings(startAfterGrant = true)
                    } else {
                        requestNotificationPermissionAndStart()
                    }
                } else if (MotionCueService.isRunning) {
                    startService(MotionCueService.stopIntent(this@MainActivity))
                }
            }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20.dp, 18.dp, 14.dp, 18.dp)
            addView(textColumn, LinearLayout.LayoutParams(0, wrapHeight(), 1f))
            addView(serviceSwitch, LinearLayout.LayoutParams(wrapHeight(), wrapHeight()))
        }

        grantButton = MaterialButton(this).apply {
            text = getString(R.string.grant_overlay_permission)
            cornerRadius = 14.dp
            insetTop = 0
            insetBottom = 0
            setOnClickListener { openOverlaySettings(startAfterGrant = false) }
        }

        return modernCard().apply {
            val content = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(row, matchWidth(wrapHeight()))
                addView(grantButton, matchWidth(48.dp).apply {
                    marginStart = 20.dp
                    marginEnd = 20.dp
                    bottomMargin = 18.dp
                })
            }
            addView(content)
        }
    }

    private fun createQuickSettingsButton() = MaterialButton(this).apply {
        text = getString(R.string.add_quick_settings_tile)
        cornerRadius = 16.dp
        insetTop = 0
        insetBottom = 0
        setTextColor(color(R.color.brand_primary))
        backgroundTintList = ColorStateList.valueOf(color(R.color.brand_soft))
        setOnClickListener { requestQuickSettingsTile() }
    }.also { it.layoutParams = matchWidth(52.dp) }

    private fun createSettingsCard(
        titleResource: Int,
        descriptionResource: Int,
        addContent: (LinearLayout) -> Unit
    ): MaterialCardView {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 20.dp, 20.dp, 12.dp)
            addView(sectionHeader(titleResource, descriptionResource))
        }
        addContent(content)
        return modernCard().apply { addView(content) }
    }

    private fun createAdvancedCard(): MaterialCardView {
        advancedContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        addAdvancedSettings(advancedContent)

        advancedButton = MaterialButton(this).apply {
            text = getString(R.string.expand_advanced_settings)
            cornerRadius = 14.dp
            insetTop = 0
            insetBottom = 0
            setTextColor(color(R.color.brand_primary))
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            setOnClickListener {
                val expanded = advancedContent.visibility != View.VISIBLE
                advancedContent.visibility = if (expanded) View.VISIBLE else View.GONE
                text = getString(
                    if (expanded) R.string.collapse_advanced_settings
                    else R.string.expand_advanced_settings
                )
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 20.dp, 20.dp, 12.dp)
            addView(sectionHeader(
                R.string.advanced_sensor_settings,
                R.string.advanced_settings_hint
            ))
            addView(advancedContent, matchWidth(wrapHeight()))
            addView(advancedButton, matchWidth(48.dp))
        }
        return modernCard().apply { addView(content) }
    }

    private fun sectionHeader(titleResource: Int, descriptionResource: Int) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                setText(titleResource)
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(color(R.color.text_primary))
            }, matchWidth(wrapHeight()).withBottomMargin(5.dp))
            addView(TextView(this@MainActivity).apply {
                setText(descriptionResource)
                textSize = 14f
                setTextColor(color(R.color.text_secondary))
            }, matchWidth(wrapHeight()).withBottomMargin(18.dp))
        }

    private fun addAppearanceSettings(parent: LinearLayout) {
        addSlider(parent, R.string.dot_count, 4, 10,
            { settingInt(MotionCueSettings.KEY_DOT_COUNT, MotionCueSettings.DEFAULT_DOT_COUNT) },
            { it.toString() },
            { saveSetting(MotionCueSettings.KEY_DOT_COUNT, it) })
        addSlider(parent, R.string.dot_size, 30, 100,
            { settingInt(MotionCueSettings.KEY_DOT_RADIUS_TENTHS_DP, MotionCueSettings.DEFAULT_DOT_RADIUS_TENTHS_DP) },
            { String.format(Locale.getDefault(), "%.1f dp", it / 10f) },
            { saveSetting(MotionCueSettings.KEY_DOT_RADIUS_TENTHS_DP, it) })
        addSlider(parent, R.string.edge_margin, 8, 40,
            { settingInt(MotionCueSettings.KEY_EDGE_MARGIN_DP, MotionCueSettings.DEFAULT_EDGE_MARGIN_DP) },
            { "$it dp" },
            { saveSetting(MotionCueSettings.KEY_EDGE_MARGIN_DP, it) })
        addSlider(parent, R.string.dot_opacity, 30, 100,
            { settingInt(MotionCueSettings.KEY_OPACITY_PERCENT, MotionCueSettings.DEFAULT_OPACITY_PERCENT) },
            { "$it%" },
            { saveSetting(MotionCueSettings.KEY_OPACITY_PERCENT, it) })
        addDarkDotsSwitch(parent)
    }

    private fun addMotionSettings(parent: LinearLayout) {
        addSlider(parent, R.string.motion_sensitivity, 8, 32,
            { settingInt(MotionCueSettings.KEY_SENSITIVITY_DP, MotionCueSettings.DEFAULT_SENSITIVITY_DP) },
            { "$it dp/(m/s²)" },
            { saveSetting(MotionCueSettings.KEY_SENSITIVITY_DP, it) })
        addSlider(parent, R.string.maximum_offset, 16, 80,
            { settingInt(MotionCueSettings.KEY_MAX_OFFSET_DP, MotionCueSettings.DEFAULT_MAX_OFFSET_DP) },
            { "$it dp" },
            { saveSetting(MotionCueSettings.KEY_MAX_OFFSET_DP, it) })
        addSlider(parent, R.string.motion_smoothing, 5, 35,
            { settingInt(MotionCueSettings.KEY_SMOOTHING_PERCENT, MotionCueSettings.DEFAULT_SMOOTHING_PERCENT) },
            { "$it%" },
            { saveSetting(MotionCueSettings.KEY_SMOOTHING_PERCENT, it) })
    }

    private fun addAdvancedSettings(parent: LinearLayout) {
        addSlider(parent, R.string.acceleration_dead_zone, 2, 30,
            { settingInt(MotionCueSettings.KEY_DEAD_ZONE_HUNDREDTHS, MotionCueSettings.DEFAULT_DEAD_ZONE_HUNDREDTHS) },
            { String.format(Locale.getDefault(), "%.2f m/s²", it / 100f) },
            { saveSetting(MotionCueSettings.KEY_DEAD_ZONE_HUNDREDTHS, it) })
        addSlider(parent, R.string.sensor_filter_time, 10, 80,
            { settingInt(MotionCueSettings.KEY_FILTER_TIME_HUNDREDTHS, MotionCueSettings.DEFAULT_FILTER_TIME_HUNDREDTHS) },
            { String.format(Locale.getDefault(), "%.2f s", it / 100f) },
            { saveSetting(MotionCueSettings.KEY_FILTER_TIME_HUNDREDTHS, it) })
        addSlider(parent, R.string.rotation_suppression_threshold, 3, 20,
            { settingInt(MotionCueSettings.KEY_ROTATION_THRESHOLD_TENTHS, MotionCueSettings.DEFAULT_ROTATION_THRESHOLD_TENTHS) },
            { String.format(Locale.getDefault(), "%.1f rad/s", it / 10f) },
            { saveSetting(MotionCueSettings.KEY_ROTATION_THRESHOLD_TENTHS, it) })
        addSlider(parent, R.string.rotation_settle_time, 1, 10,
            { settingInt(MotionCueSettings.KEY_ROTATION_SETTLE_HUNDREDS_MS, MotionCueSettings.DEFAULT_ROTATION_SETTLE_HUNDREDS_MS) },
            { "${it * 100} ms" },
            { saveSetting(MotionCueSettings.KEY_ROTATION_SETTLE_HUNDREDS_MS, it) })
    }

    private fun addSlider(
        parent: LinearLayout,
        labelResource: Int,
        min: Int,
        max: Int,
        currentValue: () -> Int,
        valueText: (Int) -> String,
        saveValue: (Int) -> Unit
    ) {
        val label = TextView(this).apply {
            setText(labelResource)
            textSize = 15f
            setTextColor(color(R.color.text_primary))
        }
        val value = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.END
            setTextColor(color(R.color.brand_primary))
            setTypeface(typeface, Typeface.BOLD)
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label, LinearLayout.LayoutParams(0, wrapHeight(), 1f))
            addView(value, LinearLayout.LayoutParams(wrapHeight(), wrapHeight()))
        }
        val slider = Slider(this).apply {
            valueFrom = min.toFloat()
            valueTo = max.toFloat()
            stepSize = 1f
            trackHeight = 4.dp
            thumbRadius = 9.dp
            haloRadius = 18.dp
            addOnChangeListener { _, sliderValue, fromUser ->
                val intValue = sliderValue.toInt()
                value.text = valueText(intValue)
                if (fromUser) saveValue(intValue)
            }
        }
        val reload = {
            val intValue = currentValue().coerceIn(min, max)
            value.text = valueText(intValue)
            slider.value = intValue.toFloat()
        }
        settingReloaders += reload
        reload()

        parent.addView(titleRow, matchWidth(wrapHeight()))
        parent.addView(slider, matchWidth(44.dp).withBottomMargin(10.dp))
    }

    private fun addDarkDotsSwitch(parent: LinearLayout) {
        val preferences = MotionCueSettings.preferences(this)
        val switch = MaterialSwitch(this).apply {
            text = getString(R.string.use_dark_dots)
            textSize = 15f
            setTextColor(color(R.color.text_primary))
            setPadding(0, 4.dp, 0, 8.dp)
            setOnCheckedChangeListener { _, checked ->
                preferences.edit().putBoolean(MotionCueSettings.KEY_DARK_DOTS, checked).apply()
            }
        }
        val reload = {
            switch.isChecked = preferences.getBoolean(
                MotionCueSettings.KEY_DARK_DOTS,
                MotionCueSettings.DEFAULT_DARK_DOTS
            )
        }
        settingReloaders += reload
        reload()
        parent.addView(switch, matchWidth(wrapHeight()))
    }

    private fun modernCard() = MaterialCardView(this).apply {
        radius = 24.dp.toFloat()
        cardElevation = 0f
        setCardBackgroundColor(color(R.color.card_background))
        strokeColor = color(R.color.card_stroke)
        strokeWidth = 1.dp
    }

    private fun ovalDrawable(fillColor: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fillColor)
    }

    private fun openOverlaySettings(startAfterGrant: Boolean) {
        startAfterPermission = startAfterGrant
        overlaySettingsLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun handleLaunchIntent(launchIntent: Intent?) {
        if (
            launchIntent?.getBooleanExtra(EXTRA_OPEN_OVERLAY_PERMISSION, false) == true &&
            !Settings.canDrawOverlays(this)
        ) {
            launchIntent.removeExtra(EXTRA_OPEN_OVERLAY_PERMISSION)
            window.decorView.post { openOverlaySettings(startAfterGrant = false) }
        }
    }

    private fun requestQuickSettingsTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.quick_settings_tile_manual, Toast.LENGTH_LONG).show()
            return
        }

        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, MotionCueTileService::class.java),
            getString(R.string.quick_settings_tile_label),
            Icon.createWithResource(this, R.drawable.ic_motion_cue),
            mainExecutor
        ) { result ->
            val message = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                    R.string.quick_settings_tile_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                    R.string.quick_settings_tile_already_added
                else -> R.string.quick_settings_tile_not_added
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermissionAndStart() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startMotionCueService()
        }
    }

    private fun startMotionCueService() {
        if (!Settings.canDrawOverlays(this)) {
            updateUi()
            return
        }
        try {
            ContextCompat.startForegroundService(this, MotionCueService.startIntent(this))
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.service_start_failed, Toast.LENGTH_LONG).show()
        }
        updateUi()
    }

    private fun updateUi() {
        if (!::permissionStatus.isInitialized) return

        val overlayGranted = Settings.canDrawOverlays(this)
        val running = MotionCueService.isRunning
        permissionStatus.setText(
            if (overlayGranted) R.string.overlay_permission_granted
            else R.string.overlay_permission_missing
        )
        serviceStatus.setText(
            if (running) R.string.service_running else R.string.service_stopped
        )
        statusDot.background = ovalDrawable(
            color(if (running) R.color.status_success else R.color.status_warning)
        )
        statusCard.setCardBackgroundColor(
            color(if (running) R.color.status_success_background else R.color.status_warning_background)
        )
        grantButton.visibility = if (overlayGranted) View.GONE else View.VISIBLE

        updatingServiceSwitch = true
        serviceSwitch.isChecked = running
        updatingServiceSwitch = false
    }

    private fun settingInt(key: String, defaultValue: Int) =
        MotionCueSettings.preferences(this).getInt(key, defaultValue)

    private fun saveSetting(key: String, value: Int) {
        MotionCueSettings.preferences(this).edit().putInt(key, value).apply()
    }

    private fun color(resource: Int) = ContextCompat.getColor(this, resource)
    private fun wrapHeight() = LinearLayout.LayoutParams.WRAP_CONTENT
    private fun matchWidth(height: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        height
    )
    private fun LinearLayout.LayoutParams.withBottomMargin(margin: Int) = apply {
        bottomMargin = margin
    }
    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_OPEN_OVERLAY_PERMISSION = "open_overlay_permission"
    }
}
