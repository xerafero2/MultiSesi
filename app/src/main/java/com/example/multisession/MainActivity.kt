package com.example.multisession

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.text.method.HideReturnsTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.MaterialButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SessionProfile(
    val id: String, 
    var platform: String,
    var username: String,
    var password: String,
    var birthDate: String,
    var yearCreated: String,
    var category: String,
    var twoFactorSecret: String,
    var is2FaEnabled: Boolean,
    val dateAdded: Long = System.currentTimeMillis(),
    var dateUpdated: Long = System.currentTimeMillis(),
    var isWebRtcEnabled: Boolean = true
)

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("SessionData", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveProfile(profile: SessionProfile) {
        profile.dateUpdated = System.currentTimeMillis()
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
    private val onLaunchClick: (SessionProfile) -> Unit,
    private val onEditClick: (SessionProfile) -> Unit,
    private val onDeleteClick: (SessionProfile) -> Unit,
    private val onCopyClick: (String, String) -> Unit
) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {
    
    private var filteredProfiles = allProfiles.toList()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvBadgeId: TextView = view.findViewById(R.id.tvBadgeId)
        val tvBadgeYear: TextView = view.findViewById(R.id.tvBadgeYear)
        val tvPlatformTag: TextView = view.findViewById(R.id.tvPlatformTag)
        val tvPlatformInitial: TextView = view.findViewById(R.id.tvPlatformInitial)
        val tvUsername: TextView = view.findViewById(R.id.tvUsername)
        val tvPassword: TextView = view.findViewById(R.id.tvPassword)
        val tvCreationDate: TextView = view.findViewById(R.id.tvCreationDate)
        
        val layout2FA: LinearLayout = view.findViewById(R.id.layout2FA)
        val tv2FaCode: TextView = view.findViewById(R.id.tv2FaCode)
        
        val tvDateCreated: TextView = view.findViewById(R.id.tvDateCreated)
        val tvDateUpdated: TextView = view.findViewById(R.id.tvDateUpdated)

        val btnCopyUser: ImageView = view.findViewById(R.id.btnCopyUser)
        val btnCopyPass: ImageView = view.findViewById(R.id.btnCopyPass)
        val btnTogglePass: ImageView = view.findViewById(R.id.btnTogglePass)
        val btnCopy2Fa: ImageView = view.findViewById(R.id.btnCopy2Fa)

        val btnEditProfile: MaterialButton = view.findViewById(R.id.btnEditProfile)
        val btnLaunchBrowser: MaterialButton = view.findViewById(R.id.btnLaunchBrowser)
        val btnDeleteProfile: MaterialButton = view.findViewById(R.id.btnDeleteProfile)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val profile = filteredProfiles[position]
        
        holder.tvBadgeId.text = (position + 1).toString()
        holder.tvBadgeYear.text = if (profile.yearCreated.isNotEmpty()) profile.yearCreated else "-"
        holder.tvPlatformTag.text = if (profile.platform.isNotEmpty()) profile.platform.uppercase() else "WEB"
        holder.tvPlatformInitial.text = if (profile.platform.isNotEmpty()) profile.platform.substring(0, 1).uppercase() else "W"
        
        holder.tvUsername.text = profile.username
        holder.tvPassword.text = profile.password
        holder.tvPassword.transformationMethod = PasswordTransformationMethod.getInstance()
        
        holder.tvCreationDate.text = if (profile.birthDate.isNotEmpty()) profile.birthDate else "Tanggal tidak diatur"
        holder.tvDateCreated.text = "Dibuat: ${dateFormat.format(Date(profile.dateAdded))}"
        holder.tvDateUpdated.text = "Update: ${dateFormat.format(Date(profile.dateUpdated))}"

        if (profile.is2FaEnabled) {
            holder.layout2FA.visibility = View.VISIBLE
            // Simulasi TOTP Code, untuk produksi gunakan library TOTP (HMAC-SHA1)
            holder.tv2FaCode.text = if (profile.twoFactorSecret.isNotEmpty()) profile.twoFactorSecret.take(6).padEnd(6, '0') else "000000"
        } else {
            holder.layout2FA.visibility = View.GONE
        }

        var isPasswordVisible = false
        holder.btnTogglePass.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            holder.tvPassword.transformationMethod = if (isPasswordVisible) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
        }

        holder.btnCopyUser.setOnClickListener { onCopyClick("Username", profile.username) }
        holder.btnCopyPass.setOnClickListener { onCopyClick("Password", profile.password) }
        holder.btnCopy2Fa.setOnClickListener { onCopyClick("2FA Code", holder.tv2FaCode.text.toString()) }

        holder.btnLaunchBrowser.setOnClickListener { onLaunchClick(profile) }
        holder.btnEditProfile.setOnClickListener { onEditClick(profile) }
        holder.btnDeleteProfile.setOnClickListener { onDeleteClick(profile) }
    }

    override fun getItemCount() = filteredProfiles.size

    fun updateData(newProfiles: List<SessionProfile>) {
        allProfiles = newProfiles
        filteredProfiles = newProfiles.toList()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredProfiles = if (query.isEmpty()) allProfiles.toList()
        else allProfiles.filter { 
            it.username.contains(query, true) || it.platform.contains(query, true) || it.id.contains(query, true) 
        }
        notifyDataSetChanged()
    }
}

fun AppCompatActivity.setupTransparentStatusBar() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.parseColor("#F8FAFC")
    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
}

class MainActivity : AppCompatActivity() {
    private lateinit var manager: SessionManager
    private lateinit var adapter: SessionAdapter
    private lateinit var tvTotalAccounts: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupTransparentStatusBar()
        setContentView(R.layout.activity_main)
        
        manager = SessionManager(this)
        tvTotalAccounts = findViewById(R.id.tvTotalAccounts)
        
        val rv = findViewById<RecyclerView>(R.id.recyclerViewProfiles)
        val etSearch = findViewById<EditText>(R.id.etSearchAccount)
        rv.layoutManager = LinearLayoutManager(this)
        
        adapter = SessionAdapter(
            allProfiles = manager.getProfiles(), 
            onLaunchClick = { profile -> launchBrowser(profile) }, 
            onEditClick = { profile -> showAccountDialog(profile) },
            onDeleteClick = { profile -> showDeleteDialog(profile) },
            onCopyClick = { label, text -> copyToClipboard(label, text) }
        )
        rv.adapter = adapter
        updateAccountCount()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { adapter.filter(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<MaterialButton>(R.id.fabAddSession).setOnClickListener {
            showAccountDialog(null)
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "$label disalin", Toast.LENGTH_SHORT).show()
    }

    private fun updateAccountCount() {
        val count = manager.getProfiles().size
        tvTotalAccounts.text = "$count Akun tersimpan"
    }

    private fun launchBrowser(profile: SessionProfile) {
        val intent = Intent(this, BrowserActivity::class.java).apply {
            putExtra("SESSION_ID", profile.id)
            putExtra("START_URL", "https://www.${if(profile.platform.isNotEmpty()) profile.platform.lowercase() else "google"}.com")
        }
        startActivity(intent)
    }

    private fun showAccountDialog(existingProfile: SessionProfile?) {
        val dialog = Dialog(this, android.R.style.Theme_Light_NoTitleBar)
        dialog.setContentView(R.layout.dialog_add_account)
        
        val etPlatform = dialog.findViewById<EditText>(R.id.etPlatform)
        val etUsername = dialog.findViewById<EditText>(R.id.etUsername)
        val etPassword = dialog.findViewById<EditText>(R.id.etPassword)
        val etBirthDate = dialog.findViewById<EditText>(R.id.etBirthDate)
        val etYearCreated = dialog.findViewById<EditText>(R.id.etYearCreated)
        val etCategory = dialog.findViewById<EditText>(R.id.etCategory)
        val switch2FA = dialog.findViewById<Switch>(R.id.switch2FA)
        val et2FASecret = dialog.findViewById<EditText>(R.id.et2FASecret)
        
        if (existingProfile != null) {
            etPlatform.setText(existingProfile.platform)
            etUsername.setText(existingProfile.username)
            etPassword.setText(existingProfile.password)
            etBirthDate.setText(existingProfile.birthDate)
            etYearCreated.setText(existingProfile.yearCreated)
            etCategory.setText(existingProfile.category)
            switch2FA.isChecked = existingProfile.is2FaEnabled
            et2FASecret.setText(existingProfile.twoFactorSecret)
            if (existingProfile.is2FaEnabled) et2FASecret.visibility = View.VISIBLE
        }

        switch2FA.setOnCheckedChangeListener { _, isChecked ->
            et2FASecret.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        val saveAction = {
            val username = etUsername.text.toString().trim()
            if (username.isEmpty()) {
                etUsername.error = "Wajib diisi"
            } else {
                val profile = existingProfile ?: SessionProfile(id = UUID.randomUUID().toString().substring(0, 8), platform = "", username = "", password = "", birthDate = "", yearCreated = "", category = "", twoFactorSecret = "", is2FaEnabled = false)
                
                profile.platform = etPlatform.text.toString().trim()
                profile.username = username
                profile.password = etPassword.text.toString().trim()
                profile.birthDate = etBirthDate.text.toString().trim()
                profile.yearCreated = etYearCreated.text.toString().trim()
                profile.category = etCategory.text.toString().trim()
                profile.is2FaEnabled = switch2FA.isChecked
                profile.twoFactorSecret = et2FASecret.text.toString().trim()
                
                manager.saveProfile(profile)
                adapter.updateData(manager.getProfiles())
                updateAccountCount()
                dialog.dismiss()
            }
        }

        dialog.findViewById<ImageView>(R.id.btnCloseDialog).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<TextView>(R.id.btnSaveTop).setOnClickListener { saveAction() }
        dialog.findViewById<MaterialButton>(R.id.btnSaveBottom).setOnClickListener { saveAction() }

        dialog.show()
    }

    private fun showDeleteDialog(profile: SessionProfile) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Akun?")
            .setMessage("Akun '${profile.username}' dan seluruh sesi browser akan dihapus permanen.")
            .setPositiveButton("Hapus") { _, _ ->
                manager.deleteProfile(profile.id)
                val webViewDir = File(applicationInfo.dataDir, "app_webview_${profile.id}")
                if (webViewDir.exists()) webViewDir.deleteRecursively()
                adapter.updateData(manager.getProfiles())
                updateAccountCount()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
