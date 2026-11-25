package com.primebox.ui

import android.content.Intent
import android.graphics.Color
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
            setPadding(Ui.dp(context, 16), Ui.dp(context, 20), Ui.dp(context, 16), Ui.dp(context, 32))
        }
        scroll.addView(column)

        column.addView(Ui.textView(context, "Extensions", 24f, Ui.text(context), bold = true))
        column.addView(Ui.textView(context, "Add a repository, or import a .cs3 file directly", 12f, Ui.textDim(context)))
        column.addView(Ui.spacer(context, 16))

        // ---- import + active source
        val actionRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(Ui.pill(context, "Import .cs3 file", true) {
            runCatching { importLauncher.launch(arrayOf("*/*")) }
        })
        actionRow.addView(Ui.horizontalSpace(context, 8))
        actionRow.addView(Ui.pill(context, "Reload extensions", false) { reloadPlugins() })
        column.addView(actionRow)

        // The drop-in folder (/sdcard/Cloudstream3/plugins) needs all-files access.
        if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            column.addView(Ui.spacer(context, 8))
            column.addView(Ui.pill(context, "Grant folder access", false) {
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

        column.addView(Ui.spacer(context, 20))
        column.addView(sectionTitle("Active source"))
        providersBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(providersBox)

        column.addView(Ui.spacer(context, 20))
        column.addView(sectionTitle("Installed extensions"))
        installedBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(installedBox)

        column.addView(Ui.spacer(context, 20))
        column.addView(sectionTitle("Repositories"))

        val addRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = Ui.dp(context, 12).toFloat()
                setColor(Ui.surface(context))
                setStroke(Ui.dp(context, 1), Ui.stroke(context))
            }
            setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 10), 0)
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
        addRow.addView(repoInput, LinearLayout.LayoutParams(0, Ui.dp(context, 48), 1f))
        addRow.addView(Ui.pill(context, "Add", true) { addRepository() })
        column.addView(addRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(context, 6)
        })

        column.addView(Ui.spacer(context, 10))
        reposBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(reposBox)

        setContentView(scroll)
        refreshAll()
    }

    private fun sectionTitle(value: String): android.widget.TextView =
        Ui.textView(this, value.uppercase(), 11f, Ui.accent(this), bold = true).apply {
            letterSpacing = 0.12f
            setPadding(0, 0, 0, Ui.dp(this@ExtensionsActivity, 8))
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
            providersBox.addView(Ui.textView(context, "No extension loaded", 12.5f, Ui.textDim(context)))
            return
        }
        val active = Store.activeProvider
        list.forEach { api ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = Ui.dp(context, 12).toFloat()
                    setColor(if (api.name == active) Ui.surfaceHigh(context) else Ui.surface(context))
                    setStroke(Ui.dp(context, 1), if (api.name == active) Ui.accent(context) else Ui.stroke(context))
                }
                isClickable = true
                setOnClickListener {
                    Providers.select(api)
                    renderProviders()
                    Toast.makeText(context, "${api.name} selected", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(Ui.textView(context, api.name, 14f, Ui.text(context), bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Ui.textView(context, if (api.name == active) "ACTIVE" else "", 10f, Ui.accent(context), bold = true))
            providersBox.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, Ui.dp(context, 6))
            })
        }
    }

    private fun renderInstalled() {
        val context = this
        installedBox.removeAllViews()
        val loaded = ExtensionManager.loaded()
        if (loaded.isEmpty()) {
            installedBox.addView(Ui.textView(context, "Nothing installed yet", 12.5f, Ui.textDim(context)))
            return
        }
        loaded.forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = Ui.dp(context, 12).toFloat()
                    setColor(Ui.surface(context))
                    setStroke(Ui.dp(context, 1), Ui.stroke(context))
                }
            }
            val info = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            info.addView(Ui.textView(context, item.name, 14f, Ui.text(context), bold = true))
            info.addView(
                Ui.textView(
                    context,
                    "${if (item.isLocal) "local file" else "downloaded"}  •  v${if (item.version == Int.MIN_VALUE) "?" else item.version.toString()}  •  ${item.providers.size} providers",
                    11f,
                    Ui.textDim(context)
                )
            )
            row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Ui.pill(context, "Remove", false) {
                ExtensionManager.unload(context, item)
                Toast.makeText(context, "Removed — restart app to fully unload", Toast.LENGTH_SHORT).show()
                refreshAll()
            })
            installedBox.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, Ui.dp(context, 6))
            })
        }
    }

    private fun renderRepositories() {
        val context = this
        reposBox.removeAllViews()
        val repos = Store.repositories
        if (repos.isEmpty()) {
            reposBox.addView(Ui.textView(context, "No repository added", 12.5f, Ui.textDim(context)))
            return
        }
        repos.forEach { repoUrl ->
            val block = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = Ui.dp(context, 12).toFloat()
                    setColor(Ui.surface(context))
                    setStroke(Ui.dp(context, 1), Ui.stroke(context))
                }
            }
            val head = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(
                Ui.textView(context, repoUrl, 12f, Ui.text(context), maxLines = 1),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            head.addView(Ui.pill(context, "Load", true) { loadRepo(repoUrl, block) })
            head.addView(Ui.horizontalSpace(context, 6))
            head.addView(Ui.pill(context, "Remove", false) {
                Store.removeRepository(repoUrl)
                renderRepositories()
            })
            block.addView(head)
            val pluginBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                id = android.view.View.generateViewId()
            }
            block.addView(pluginBox)
            reposBox.addView(block, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, Ui.dp(context, 8))
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
        pluginBox.removeAllViews()
        pluginBox.addView(Ui.textView(context, "Loading repository…", 11f, Ui.textDim(context)))
        lifecycleScope.launch {
            val plugins = withContext(Dispatchers.IO) {
                runCatching { ExtensionManager.fetchRepo(repoUrl) }.getOrNull()
            }
            pluginBox.removeAllViews()
            if (plugins == null) {
                pluginBox.addView(Ui.textView(context, "Could not load repository", 11f, Ui.danger(context)))
                return@launch
            }
            if (plugins.isEmpty()) {
                pluginBox.addView(Ui.textView(context, "Repository has no extensions", 11f, Ui.textDim(context)))
                return@launch
            }
            val installedPaths = ExtensionManager.loaded().map { it.name }.toSet()
            plugins.forEach { plugin ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, Ui.dp(context, 8), 0, 0)
                }
                val info = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                info.addView(Ui.textView(context, plugin.name, 13f, Ui.text(context), bold = true, maxLines = 1))
                info.addView(
                    Ui.textView(
                        context,
                        buildString {
                            append("v${plugin.version}")
                            if (plugin.authors.isNotEmpty()) append("  •  ${plugin.authors.joinToString(", ")}")
                            if (plugin.language != null) append("  •  ${plugin.language}")
                        },
                        10.5f,
                        Ui.textDim(context),
                        maxLines = 1
                    )
                )
                row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val already = installedPaths.contains(plugin.name)
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
            if (ok) refreshAll()
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
