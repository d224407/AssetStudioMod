package com.min.assetstudio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {

    private val modes = listOf("export", "exportRaw", "extract", "dump", "jsonDump", "info", "live2d", "splitObjects", "animator")
    private val types = listOf(
        "tex2d", "tex2dArray", "sprite", "textAsset", "monoBehaviour", "font",
        "shader", "movieTexture", "audio", "video", "mesh"
    )
    private val images = listOf("png", "jpg", "webp", "bmp", "tga", "none")
    private val audios = listOf("wav", "none")
    private val groups = listOf("container", "type", "none", "fileName", "containerFull", "sceneHierarchy")
    private val names = listOf("assetName", "assetName_pathID", "pathID", "assetName_hash")

    private lateinit var input: TextInputEditText
    private lateinit var output: TextInputEditText
    private lateinit var filter: TextInputEditText
    private lateinit var extra: TextInputEditText
    private lateinit var mode: Spinner
    private lateinit var image: Spinner
    private lateinit var audio: Spinner
    private lateinit var group: Spinner
    private lateinit var name: Spinner
    private lateinit var overwrite: MaterialSwitch
    private lateinit var chips: ChipGroup
    private lateinit var run: MaterialButton
    private lateinit var bar: LinearProgressIndicator
    private lateinit var log: TextView
    private lateinit var logScroll: ScrollView

    private val prefs by lazy { getSharedPreferences("p", Context.MODE_PRIVATE) }

    private val pickDir = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        setPath(uri, true, input)
    }
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        setPath(uri, false, input)
    }
    private val pickOut = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        setPath(uri, true, output)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        restore()
        requestStorage()
    }

    override fun onStart() {
        super.onStart()
        CliState.listener = { refresh() }
        refresh()
    }

    override fun onStop() {
        CliState.listener = null
        save()
        super.onStop()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun field(label: String): TextInputEditText {
        val til = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle)
        til.hint = label
        val et = TextInputEditText(til.context)
        et.setSingleLine()
        til.addView(et)
        et.tag = til
        return et
    }

    private fun wrap(et: TextInputEditText, vararg buttons: MaterialButton): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(et.tag as View, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttons.forEach { row.addView(it) }
        return row
    }

    private fun btn(text: String, onClick: () -> Unit) = MaterialButton(
        this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun spinner(items: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, items)
    }

    private fun labeled(label: String, v: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(TextView(context).apply { text = label }, LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun buildUi() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(16))
            }
        fun add(v: View, top: Int = 8) {
            col.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dp(top)
            })
        }

        input = field("Input (file or folder)")
        output = field("Output folder")
        filter = field("Filter by name (a,b)")
        extra = field("Extra arguments")
        mode = spinner(modes); image = spinner(images); audio = spinner(audios); group = spinner(groups); name = spinner(names)
        overwrite = MaterialSwitch(this).apply { text = "Overwrite existing" }
        chips = ChipGroup(this)
        types.forEach { t -> chips.addView(Chip(this).apply { text = t; isCheckable = true; tag = t }) }

        add(wrap(input, btn("Folder") { pickDir.launch(null) }, btn("File") { pickFile.launch(arrayOf("*/*")) }), 0)
        add(wrap(output, btn("Pick") { pickOut.launch(null) }))
        add(labeled("Mode", mode))
        add(TextView(this).apply { text = "Asset types" })
        add(chips, 0)
        add(labeled("Image", image), 0)
        add(labeled("Audio", audio), 0)
        add(labeled("Group by", group), 0)
        add(labeled("File name", name), 0)
        add(filter.tag as View)
        add(extra.tag as View)
        add(overwrite)

        run = MaterialButton(this).apply { setOnClickListener { onRun() } }
        add(run, 12)
        bar = LinearProgressIndicator(this).apply { visibility = View.GONE }
        add(bar, 8)

        log = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        logScroll = ScrollView(this).apply { addView(log) }
        col.addView(logScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)).also { it.topMargin = dp(8) })

        setContentView(ScrollView(this).apply { addView(col); isFillViewport = true })
    }

    private fun setPath(uri: Uri?, tree: Boolean, target: TextInputEditText) {
        if (uri == null) return
        val p = PathResolver.resolve(uri, tree)
        if (p == null) Toast.makeText(this, "Unsupported location", Toast.LENGTH_LONG).show()
        else target.setText(p)
    }

    private fun running() = CliState.phase == CliState.Phase.INSTALLING || CliState.phase == CliState.Phase.RUNNING

    private fun onRun() {
        if (running()) {
            CliService.stop(this)
            return
        }
        val inp = input.text.toString().trim()
        if (inp.isEmpty()) {
            input.error = "Required"
            return
        }
        save()
        val out = output.text.toString().trim().ifEmpty { "/sdcard/AssetStudio" }
        val args = mutableListOf(inp, "-m", mode.selectedItem.toString(), "-o", out)
        val sel = (0 until chips.childCount).map { chips.getChildAt(it) as Chip }.filter { it.isChecked }.map { it.tag as String }
        val split = mode.selectedItem.toString().let { it == "splitObjects" || it == "animator" }
        if (sel.isNotEmpty() && !split) args += listOf("-t", sel.joinToString(","))
        args += listOf("--image-format", image.selectedItem.toString())
        args += listOf("--audio-format", audio.selectedItem.toString())
        args += listOf("-g", group.selectedItem.toString())
        args += listOf("-f", name.selectedItem.toString())
        filter.text.toString().trim().let { if (it.isNotEmpty()) args += listOf("--filter-by-name", it) }
        if (overwrite.isChecked) args += "-r"
        extra.text.toString().trim().let { if (it.isNotEmpty()) args += it.split(Regex("\\s+")) }
        CliService.start(this, args.toTypedArray())
    }

    private fun refresh() {
        val busy = running()
        run.text = if (busy) "Stop" else "Run"
        bar.visibility = if (busy) View.VISIBLE else View.GONE
        if (CliState.phase == CliState.Phase.INSTALLING) {
            bar.isIndeterminate = false
            bar.progress = CliState.progress
        } else bar.isIndeterminate = true
        log.text = CliState.text()
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun save() {
        prefs.edit()
            .putString("in", input.text.toString()).putString("out", output.text.toString())
            .putString("name", filter.text.toString()).putString("extra", extra.text.toString())
            .putInt("mode", mode.selectedItemPosition).putInt("img", image.selectedItemPosition)
            .putInt("aud", audio.selectedItemPosition).putInt("grp", group.selectedItemPosition).putInt("fmt", name.selectedItemPosition)
            .putBoolean("ow", overwrite.isChecked)
            .putStringSet("types", (0 until chips.childCount).map { chips.getChildAt(it) as Chip }
                .filter { it.isChecked }.map { it.tag as String }.toSet())
            .apply()
    }

    private fun restore() {
        input.setText(prefs.getString("in", "")); output.setText(prefs.getString("out", "/sdcard/AssetStudio"))
        filter.setText(prefs.getString("name", "")); extra.setText(prefs.getString("extra", ""))
        mode.setSelection(prefs.getInt("mode", 0)); image.setSelection(prefs.getInt("img", 0))
        audio.setSelection(prefs.getInt("aud", 0)); group.setSelection(prefs.getInt("grp", 0)); name.setSelection(prefs.getInt("fmt", 0))
        overwrite.isChecked = prefs.getBoolean("ow", false)
        val sel = prefs.getStringSet("types", emptySet()) ?: emptySet()
        (0 until chips.childCount).map { chips.getChildAt(it) as Chip }.forEach { it.isChecked = (it.tag as String) in sel }
    }

    private fun requestStorage() {
        val need = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need.toTypedArray(), 1)
    }
}
