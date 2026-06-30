package com.example.multisession

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.system.exitProcess

data class SessionProfile(
    val id: String, 
    var name: String,
    var startUrl: String,
    val customUserAgent: String, 
    val dateAdded: Long = System.currentTimeMillis()
)

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("SessionData", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveProfile(profile: SessionProfile) {
        val profiles = getProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index != -1) profiles[index] = profile else profiles.add(profile)
        prefs.edit().putString("PROFILES_LIST", gson.toJson(profiles)).apply()
    }

    fun deleteProfile(id: String) {
        val profiles = getProfiles().filter { it.id != id }
        prefs.edit().putString("PROFILES_LIST", gson.toJson(profiles)).apply()
    }

    fun getProfiles(): List<SessionProfile> {
        val json = prefs.getString("PROFILES_LIST", null) ?: return emptyList()
        return gson.fromJson(json, object : TypeToken<List<SessionProfile>>() {}.type)
    }
}

class SessionAdapter(
    private var allProfiles: List<SessionProfile>,
    private val onClick: (SessionProfile) -> Unit,
    private val onEditClick: (SessionProfile) -> Unit,
    private val onDeleteClick: (SessionProfile) -> Unit
) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {
    
    private var filteredProfiles = allProfiles.toList()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvProfileName)
        val tvId: TextView = view.findViewById(R.id.tvProfileId)
        val tvUrl: TextView = view.findViewById(R.id.tvProfileUrl)
        val tvDate: TextView = view.findViewById(R.id.tvProfileDate)
        val btnEdit: View = view.findViewById(R.id.btnEditContainer)
        val btnDelete: View = view.findViewById(R.id.btnDeleteProfile)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val profile = filteredProfiles[position]
        holder.tvName.text = profile.name
        holder.tvId.text = "ID: ${profile.id}"
        holder.tvUrl.text = profile.startUrl
        holder.tvDate.text = dateFormat.format(Date(profile.dateAdded))
        
        holder.itemView.setOnClickListener { onClick(profile) }
        holder.btnEdit.setOnClickListener { onEditClick(profile) }
        holder.btnDelete.setOnClickListener { onDeleteClick(profile) }
    }

    override fun getItemCount() = filteredProfiles.size

    fun updateData(newProfiles: List<SessionProfile>) {
        allProfiles = newProfiles
        filteredProfiles = newProfiles.toList()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredProfiles = if (query.isEmpty()) allProfiles.toList()
        else allProfiles.filter { it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true) }
        notifyDataSetChanged()
    }
}

class DrawerNavAdapter(
    private val profiles: List<SessionProfile>,
    private val currentId: String,
    private val onClick: (SessionProfile) -> Unit
) : RecyclerView.Adapter<DrawerNavAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvDrawerName)
        val tvUrl: TextView = view.findViewById(R.id.tvDrawerUrl)
        val container: LinearLayout = view.findViewById(R.id.drawerItemContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_drawer_session, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val p = profiles[position]
        holder.tvName.text = p.name
        holder.tvUrl.text = p.startUrl
        
        val bgDrawable = GradientDrawable().apply { cornerRadius = 32f }
        
        if (p.id == currentId) {
            bgDrawable.setColor(Color.parseColor("#0F172A"))
            holder.container.background = bgDrawable
            holder.tvName.setTextColor(Color.WHITE)
            holder.tvUrl.setTextColor(Color.parseColor("#94A3B8"))
        } else {
            bgDrawable.setColor(Color.TRANSPARENT)
            holder.container.background = bgDrawable
            holder.tvName.setTextColor(Color.parseColor("#0F172A"))
            holder.tvUrl.setTextColor(Color.parseColor("#64748B"))
        }
        
        holder.itemView.setOnClickListener { if (p.id != currentId) onClick(p) }
    }
    
    override fun getItemCount() = profiles.size
}

fun AppCompatActivity.setupTransparentStatusBar() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.parseColor("#F8FAFC")
    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
}

class MainActivity : AppCompatActivity() {
    private lateinit var manager: SessionManager
    private lateinit var adapter: SessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupTransparentStatusBar()
        setContentView(R.layout.activity_main)
        
        manager = SessionManager(this)
        
        val rv = findViewById<RecyclerView>(R.id.recyclerViewProfiles)
        val etSearch = findViewById<EditText>(R.id.etSearchAccount)
        rv.layoutManager = LinearLayoutManager(this)
        
        adapter = SessionAdapter(
            allProfiles = manager.getProfiles(), 
            onClick = { profile -> launchBrowser(profile) }, 
            onEditClick = { profile -> showEditDialog(profile) },
            onDeleteClick = { profile -> showDeleteDialog(profile) }
        )
        rv.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<FloatingActionButton>(R.id.fabAddSession).setOnClickListener {
            showAddDialog()
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra("SWITCH_TO_SESSION")?.let { id ->
            manager.getProfiles().find { it.id == id }?.let { profile ->
                launchBrowser(profile)
            }
        }
    }

    private fun launchBrowser(profile: SessionProfile) {
        val intent = Intent(this, BrowserActivity::class.java).apply {
            putExtra("SESSION_ID", profile.id)
            putExtra("START_URL", profile.startUrl)
            putExtra("CUSTOM_UA", profile.customUserAgent)
        }
        startActivity(intent)
    }

    private fun showAddDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 24)
        }
        val etName = EditText(this).apply { 
            hint = "Nama Akun (Wajib)" 
            setPadding(0, 32, 0, 32)
        }
        val etUrl = EditText(this).apply { 
            hint = "Start URL (Opsional)" 
            setPadding(0, 32, 0, 32)
        }
        layout.addView(etName)
        layout.addView(etUrl)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Tambah Akun Baru")
            .setView(layout)
            .setPositiveButton("Simpan", null) 
            .setNegativeButton("Batal", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = etName.text.toString().trim()
            var url = etUrl.text.toString().trim()
            
            if (name.isEmpty()) {
                etName.error = "Nama wajib diisi!"
                return@setOnClickListener
            }
            
            if (url.isEmpty()) url = "https://www.google.com"
            else if (!url.startsWith("http")) url = "https://$url"

            val id = UUID.randomUUID().toString().substring(0, 8)
            manager.saveProfile(SessionProfile(id, name, url, ""))
            adapter.updateData(manager.getProfiles())
            dialog.dismiss()
        }
    }

    private fun showEditDialog(profile: SessionProfile) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 24)
        }
        val etName = EditText(this).apply { 
            setText(profile.name)
            setPadding(0, 32, 0, 32)
        }
        val etUrl = EditText(this).apply { 
            setText(profile.startUrl)
            setPadding(0, 32, 0, 32)
        }
        layout.addView(etName)
        layout.addView(etUrl)
        
        AlertDialog.Builder(this)
            .setTitle("Ubah Data Akun")
            .setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = etName.text.toString().trim()
                var newUrl = etUrl.text.toString().trim()
                if (newName.isNotEmpty()) {
                    profile.name = newName
                    if (newUrl.isNotEmpty() && !newUrl.startsWith("http")) newUrl = "https://$newUrl"
                    profile.startUrl = newUrl
                    manager.saveProfile(profile)
                    adapter.updateData(manager.getProfiles())
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showDeleteDialog(profile: SessionProfile) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Permanen?")
            .setMessage("Sesi '${profile.name}' dan semua riwayat penelusurannya akan dihapus.")
            .setPositiveButton("Hapus") { _, _ ->
                manager.deleteProfile(profile.id)
                adapter.updateData(manager.getProfiles())
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}

class BrowserActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var etUrlBar: EditText
    private lateinit var drawerLayout: DrawerLayout
    
    // Variabel state listener agar tidak terjadi bentrok saat proses isolasi dimatikan
    private var pendingAction: String? = null
    private var targetSwitchId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupTransparentStatusBar()
        
        val sessionId = intent.getStringExtra("SESSION_ID") ?: "default"
        val startUrl = intent.getStringExtra("START_URL") ?: "https://www.google.com"
        val ua = intent.getStringExtra("CUSTOM_UA")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) WebView.setDataDirectorySuffix(sessionId)

        setContentView(R.layout.activity_browser)
        webView = findViewById(R.id.webView)
        etUrlBar = findViewById(R.id.etUrlBar)
        drawerLayout = findViewById(R.id.drawerLayout)
        
        setupDrawer(sessionId)
        setupNavigationButtons()

        findViewById<ImageView>(R.id.btnMenu).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        findViewById<TextView>(R.id.btnCookie).setOnClickListener {
            extractCookies()
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            if (!ua.isNullOrEmpty()) userAgentString = ua
        }
        
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && !etUrlBar.isFocused) etUrlBar.setText(url)
            }
        }

        etUrlBar.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                var query = etUrlBar.text.toString().trim()
                if (query.isNotEmpty()) {
                    if (!query.startsWith("http://") && !query.startsWith("https://")) {
                        query = if (query.contains(".") && !query.contains(" ")) "https://$query"
                        else "https://www.google.com/search?q=${Uri.encode(query)}"
                    }
                    webView.loadUrl(query)
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    etUrlBar.clearFocus()
                }
                true
            } else false
        }

        webView.loadUrl(startUrl)
    }

    private fun setupNavigationButtons() {
        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        findViewById<TextView>(R.id.btnForward).setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        findViewById<TextView>(R.id.btnRefresh).setOnClickListener {
            webView.reload()
        }
        findViewById<TextView>(R.id.btnDrawerHome).setOnClickListener {
            pendingAction = "HOME"
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    private fun setupDrawer(currentSessionId: String) {
        val rvDrawer = findViewById<RecyclerView>(R.id.rvDrawerNav)
        rvDrawer.layoutManager = LinearLayoutManager(this)
        val profiles = SessionManager(this).getProfiles()
        
        rvDrawer.adapter = DrawerNavAdapter(profiles, currentSessionId) { targetProfile ->
            pendingAction = "SWITCH"
            targetSwitchId = targetProfile.id
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Pengecekan state murni untuk perpindahan instan (tanpa jeda handler buatan)
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
            override fun onDrawerClosed(drawerView: View) {
                when (pendingAction) {
                    "SWITCH" -> {
                        if (targetSwitchId != null) {
                            val intent = Intent(this@BrowserActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra("SWITCH_TO_SESSION", targetSwitchId)
                            }
                            startActivity(intent)
                            finish()
                        }
                    }
                    "HOME" -> {
                        finish()
                    }
                }
                pendingAction = null
                targetSwitchId = null
            }
        })
    }

    private fun extractCookies() {
        val currentUrl = webView.url
        if (currentUrl == null) {
            Toast.makeText(this, "Halaman belum termuat sepenuhnya", Toast.LENGTH_SHORT).show()
            return
        }

        val cookies = CookieManager.getInstance().getCookie(currentUrl) ?: "Tidak ada cookie ditemukan untuk sesi ini."
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
            addView(EditText(this@BrowserActivity).apply {
                setText(cookies)
                background = null
                textSize = 13f
                isFocusable = false
                isCursorVisible = false
                setPadding(0, 16, 0, 16)
            })
        }

        AlertDialog.Builder(this)
            .setTitle("Data Cookie Sesi")
            .setView(layout)
            .setPositiveButton("Salin") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Cookies", cookies))
                Toast.makeText(this, "Cookie berhasil disalin", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else if (webView.canGoBack()) {
            webView.goBack() 
        } else {
            super.onBackPressed() 
        }
    }

    override fun onDestroy() { 
        CookieManager.getInstance().flush()
        super.onDestroy() 
        exitProcess(0) 
    }
}
