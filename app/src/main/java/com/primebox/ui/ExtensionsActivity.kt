package com.primebox.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.primebox.data.Store
import com.primebox.ext.ExtensionManager
import com.primebox.ext.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExtensionsActivity : AppCompatActivity() {

    private lateinit var installedBox: LinearLayout
    private lateinit var reposBox: LinearLayout
    private lateinit var providersBox: LinearLayout
    private lateinit var repoInput: EditText

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val (ok, message) = ExtensionManager.importFromUri(this, uri)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        if (ok) refreshAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this
        val scroll = NestedScrollView(this).apply { setBackgroundColor(Ui.bg(context)) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(context, 20), Ui.dp(context, 16), Ui.dp(context, 20), Ui.dp(context, 40))
        }
        Ui.applyTopInset(column, 8)
        scroll.addView(column)

        column.addView(Ui.iconButton(context, android.R.drawable.ic_menu_revert, Ui.text(context)) { finish() })
        column.addView(Ui.display(context, "Extensions", 28f), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(context, 4) })
        column.addView(Ui.body(context, "Add a repository, or import a .cs3 file directly"))

        column.addView(Ui.spacer(context, 18))
        val actionRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(Ui.filledButton(context, "Import .cs3") {
            runCatching { importLauncher.launch(arrayOf("*/*")) }
        })
        actionRow.addView(Ui.horizontalSpace(context, 8))
        actionRow.addView(Ui.tonalButton(context, "Reload") { reloadPlugins() })
        column.addView(actionRow)
        column.addView(Ui.spacer(context, 8))
        column.addView(Ui.tonalButton(context, "Scan Download folder for .cs3") {
            val (count, message) = ExtensionManager.scanDownloads(context)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            if (count > 0) refreshAll()
        })

        if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            column.addView(Ui.spacer(context, 10))
            column.addView(Ui.tonalButton(context, "Grant folder access") {
                runCatching {
                    startActivity(
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            android.net.Uri.parse("package:$packageName")
                        )
                    )
                }.onFailure {
                    startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            })
        }

        column.addView(Ui.spacer(context, 26))
        column.addView(Ui.sectionHeader(context, "Active source").apply { setPadding(0, 0, 0, Ui.dp(context, 8)) })
        providersBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(providersBox)

        column.addView(Ui.spacer(context, 26))
        column.addView(Ui.sectionHeader(context, "Installed").apply { setPadding(0, 0, 0, Ui.dp(context, 8)) })
        installedBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(installedBox)

        column.addView(Ui.spacer(context, 26))
        column.addView(Ui.sectionHeader(context, "Repositories").apply { setPadding(0, 0, 0, Ui.dp(context, 8)) })

        ExtensionManager.PRESETS.forEach { preset ->
            val presetRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 10), Ui.dp(context, 12))
                background = Ui.shape(context, 16, Ui.accentSoft(context), 1, Ui.accent(context))
            }
            val info = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            info.addView(Ui.textView(context, preset.name, 13f, Ui.text(context), bold = true, maxLines = 1))
            info.addView(Ui.textView(context, "One tap add", 10.5f, Ui.textDim(context)))
            presetRow.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            presetRow.addView(Ui.pill(context, "Add", true) {
                if (!Store.repositories.contains(preset.url)) {
                    Store.addRepository(preset.url)
                    renderRepositories()
                } else {
                    Toast.makeText(context, "Already added", Toast.LENGTH_SHORT).show()
                }
            })
            column.addView(presetRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(context, 10)
            })
        }

        val addRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.shape(context, 100, Ui.surface(context), 1, Ui.stroke(context))
            setPadding(Ui.dp(context, 16), Ui.dp(context, 2), Ui.dp(context, 6), Ui.dp(context, 2))
        }
        repoInput = EditText(context).apply {
            hint = "https://example.com/repo.json"
            setHintTextColor(Ui.textDim(context))
            setTextColor(Ui.text(context))
            textSize = 12.5f
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            background = null
            setSingleLine()
        }
        addRow.addView(repoInput, LinearLayout.LayoutParams(0, Ui.dp(context, 50), 1f))
        addRow.addView(Ui.filledButton(context, "Add") { addRepository() })
        column.addView(addRow)

        column.addView(Ui.spacer(context, 12))
        reposBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(reposBox)

        setContentView(scroll)
        refreshAll()
    }

    private fun refreshAll() {
        renderProviders()
        renderInstalled()
        renderRepositories()
    }

    private fun renderProviders() {
        val context = this
        providersBox.removeAllViews()
        val list = Providers.all()
        if (list.isEmpty()) {
            providersBox.addView(Ui.body(context, "No extension loaded yet"))
            return
        }
        val active = Store.activeProvider
        list.forEach { api ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(context, 14), Ui.dp(context, 13), Ui.dp(context, 14), Ui.dp(context, 13))
                background = Ui.ripple(
                    context,
                    Ui.shape(
                        context, 16,
                        if (api.name == active) Ui.accentSoft(context) else Ui.surface(context),
                        1,
                        if (api.name == active) Ui.accent(context) else Ui.stroke(context)
                    )
                )
                isClickable = true
                setOnClickListener {
                    Providers.select(api)
                    renderProviders()
                    Toast.makeText(context, "${api.name} selected", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(Ui.title(context, api.name), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (api.name == active) row.addView(Ui.badge(context, "ACTIVE", Ui.onAccent(context), Ui.accent(context)))
            providersBox.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, Ui.dp(context, 8))
            })
        }
    }

    private fun renderInstalled() {
        val context = this
        installedBox.removeAllViews()
        val loaded = ExtensionManager.loaded()
        if (loaded.isEmpty()) {
            installedBox.addView(Ui.body(context, "Nothing installed yet"))
            return
        }
        loaded.forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 10), Ui.dp(context, 12))
                background = Ui.shape(context, 16, Ui.surface(context), 1, Ui.stroke(context))
            }
            val info = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            info.addView(Ui.title(context, item.name))
            info.addView(
                Ui.textView(
                    context,
                    "${if (item.isLocal) "local file" else "downloaded"}  •  v${if (item.version == Int.MIN_VALUE) "?" else item.version}  •  ${item.providers.size} providers",
                    11f,
                    Ui.textDim(context)
                )
            )
            row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Ui.iconButton(context, android.R.drawable.ic_menu_delete, Ui.danger(context)) {
                ExtensionManager.unload(context, item)
                Toast.makeText(context, "Removed — restart to unload", Toast.LENGTH_SHORT).show()
                refreshAll()
            })
            installedBox.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, Ui.dp(context, 8))
            })
        }
    }

    private fun renderRepositories() {
        val context = this
        reposBox.removeAllViews()
        val repos = Store.repositories
        if (repos.isEmpty()) {
            reposBox.addView(Ui.body(context, "No repository added"))
            return
        }
        repos.forEach { repoUrl ->
            val block = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 14))
                background = Ui.shape(context, 18, Ui.surface(context), 1, Ui.stroke(context))
            }
            val head = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val titleBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            titleBox.addView(Ui.textView(context, repoUrl, 12f, Ui.textDim(context), maxLines = 1))
            val statusLine = Ui.textView(context, "Tap refresh to load", 11f, Ui.textDim(context))
            titleBox.addView(statusLine)
            head.addView(titleBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(Ui.iconButton(context, android.R.drawable.stat_notify_sync, Ui.accent(context)) {
                loadRepo(repoUrl, block)
            })
            head.addView(Ui.iconButton(context, android.R.drawable.ic_menu_close_clear_cancel, Ui.textDim(context)) {
                Store.removeRepository(repoUrl)
                renderRepositories()
            })
            block.addView(head)
            val pluginBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                id = View.generateViewId()
            }
            block.addView(pluginBox)
            block.tag = statusLine
            reposBox.addView(block, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, Ui.dp(context, 10))
            })
            loadRepo(repoUrl, block)
        }
    }

    private fun loadRepo(repoUrl: String, block: LinearLayout) {
        val context = this
        val pluginBox = (0 until block.childCount)
            .map { block.getChildAt(it) }
            .filterIsInstance<LinearLayout>()
            .lastOrNull() ?: return
        val statusLine = block.tag as? android.widget.TextView
        pluginBox.removeAllViews()
        statusLine?.text = "Loading…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { ExtensionManager.fetchRepo(repoUrl) } }
            pluginBox.removeAllViews()
            val info = result.getOrNull()
            if (info == null) {
                val reason = result.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "unknown error"
                statusLine?.text = "Failed"
                statusLine?.setTextColor(Ui.danger(context))
                pluginBox.addView(Ui.textView(context, reason, 11.5f, Ui.danger(context)))
                pluginBox.addView(
                    Ui.textView(context, "Check the link — it must point to a repo.json (raw link).", 11f, Ui.textDim(context)),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = Ui.dp(context, 4)
                    }
                )
                return@launch
            }

            statusLine?.text = "${info.name}  •  ${info.plugins.size} extensions"
            statusLine?.setTextColor(Ui.accent(context))

            val installedNames = ExtensionManager.loaded().map { it.name }.toSet()
            info.plugins.forEach { plugin ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, Ui.dp(context, 10), 0, 0)
                }
                val pluginInfo = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                pluginInfo.addView(Ui.textView(context, plugin.name, 13.5f, Ui.text(context), bold = true, maxLines = 1))
                pluginInfo.addView(
                    Ui.textView(
                        context,
                        buildString {
                            append("v${plugin.version}")
                            if (plugin.authors.isNotEmpty()) append("  •  ${plugin.authors.joinToString(", ")}")
                            if (plugin.language != null) append("  •  ${plugin.language}")
                        },
                        10.5f, Ui.textDim(context), maxLines = 1
                    )
                )
                row.addView(pluginInfo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val already = installedNames.contains(plugin.name)
                row.addView(Ui.pill(context, if (already) "Reinstall" else "Install", !already) {
                    installPlugin(plugin)
                })
                pluginBox.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
    }

    private fun installPlugin(plugin: ExtensionManager.RepoPlugin) {
        Toast.makeText(this, "Installing ${plugin.name}…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val (ok, message) = withContext(Dispatchers.IO) { ExtensionManager.install(this@ExtensionsActivity, plugin) }
            Toast.makeText(this@ExtensionsActivity, message, Toast.LENGTH_LONG).show()
            if (ok) {
                if (Store.activeProvider.isNullOrBlank()) {
                    Providers.all().firstOrNull()?.let { Providers.select(it) }
                }
                refreshAll()
            }
        }
    }

    private fun addRepository() {
        val url = repoInput.text.toString().trim()
        if (url.isBlank()) return
        Store.addRepository(url)
        repoInput.setText("")
        renderRepositories()
    }

    private fun reloadPlugins() {
        val context = this
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { ExtensionManager.loadAll(context) }
            refreshAll()
            Toast.makeText(context, "Extensions reloaded", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }
}
