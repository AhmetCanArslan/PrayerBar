@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.arslan.prayerbar.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arslan.prayerbar.R
import com.arslan.prayerbar.ui.MainViewModel
import com.arslan.prayerbar.ui.settings.WIDGET_PREVIEW_CAPACITY
import com.arslan.prayerbar.ui.settings.WidgetEditor
import com.arslan.prayerbar.ui.theme.PrayerBarTheme

class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(Activity.RESULT_CANCELED, result())

        setContent {
            PrayerBarTheme {
                val viewModel: MainViewModel = viewModel(
                    factory = MainViewModel.factory(application),
                )
                val state by viewModel.state.collectAsStateWithLifecycle()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(title = { Text(stringResource(R.string.widget_config_title)) })
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = { confirm() },
                            icon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                            text = { Text(stringResource(R.string.widget_config_done)) },
                        )
                    },
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        WidgetEditor(
                            widget = state.settings.widget(appWidgetId),
                            preview = viewModel.widgetPreview(appWidgetId, WIDGET_PREVIEW_CAPACITY),
                            labelOf = viewModel::labelOf,
                            onEdit = { transform -> viewModel.editWidget(appWidgetId, transform) },
                            onTogglePrayer = { prayer ->
                                viewModel.toggleWidgetPrayer(appWidgetId, prayer)
                            },
                        )
                    }
                }
            }
        }
    }

    private fun confirm() {
        setResult(Activity.RESULT_OK, result())
        sendBroadcast(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = ComponentName(
                    this@WidgetConfigActivity,
                    PrayerWidgetProvider::class.java,
                )
                putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_IDS,
                    intArrayOf(appWidgetId),
                )
            },
        )
        finish()
    }

    private fun result(): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
