/*
 * Copyright (C) 2026 The XPerience Project
 * SPDX-License-Identifier: Apache-2.0
 */
package mx.xperience.gamespace

import android.app.GameManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mainLayout = findViewById<View>(R.id.main_container)
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { view, insets ->

            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)

            insets

        }

        startService(Intent(this, GameSpaceService::class.java))

        setupGameGrid()
    }

    private fun setupGameGrid() {
        val recyclerView = findViewById<RecyclerView>(R.id.game_recycler_view)
        recyclerView.layoutManager = GridLayoutManager(this, 3) // 3-column mosaic

        val pm = packageManager
        val myPackageName = packageName

        val manualGames = getManualGames()

        // We filter apps that the system recognises as games.
        val games = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { appInfo ->
            // Condition 1: The system must indicate that it is a game.
            val isGame = appInfo.category == ApplicationInfo.CATEGORY_GAME ||
                        (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0

            // condition 2: was manually added
            val isManual = manualGames.contains(appInfo.packageName)

            // Condition 3: It must NOT be this same app!
            val isNotMe = appInfo.packageName != myPackageName

            (isGame || isManual) && isNotMe
        }
        .map { appInfo ->
            GameModel(
                name = pm.getApplicationLabel(appInfo).toString(),
                      packageName = appInfo.packageName,
                      icon = pm.getApplicationIcon(appInfo)
            )
        }

       val adapter = GameAdapter(games.toMutableList(), { pkg ->

            showOptimizationToast(pkg)

            // We use the AOSP API to put the kernel into high-performance mode.
            val gameManager = getSystemService(Context.GAME_SERVICE) as GameManager
            gameManager.setGameMode(pkg, GameManager.GAME_MODE_PERFORMANCE)

            Handler(Looper.getMainLooper()).postDelayed({
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                launchIntent?.let { startActivity(it) }
            }, 500)
       }, { pkg ->
           // (onLongClick) NUEVO: Verifica si es manual antes de intentar borrar
           val manualGames = getManualGames()
           if (manualGames.contains(pkg)) {
               showRemoveDialog(pkg)
           } else {
               Toast.makeText(this, getString(R.string.system_games_no_remove), Toast.LENGTH_SHORT).show()
           }
        }) {
            Toast.makeText(this, getString(R.string.open_app_picker_dialog), Toast.LENGTH_SHORT).show()
            showAddGameDialog()
        }

        recyclerView.adapter = adapter
    }

    private fun showOptimizationToast(packageName: String = "") {
        val layout = layoutInflater.inflate(R.layout.layout_opt_toast, null)
        val tvMessage = layout.findViewById<TextView>(R.id.opt_message)

        val coreType = getCpuCoreType()
        tvMessage.text = getString(R.string.optimizing_cpu, coreType)

        val toast = Toast(applicationContext)
        toast.duration = Toast.LENGTH_SHORT
        toast.view = layout
        toast.setGravity(Gravity.BOTTOM, 0, 100)
        toast.show()
    }

    private fun getCpuCoreType(): String {
        // We obtain the name of the board or hardware
        // ‘pineapple’ = Snapdragon 8 Gen 3
        // ‘sun’ = Snapdragon 8 Elite (Oryon)
        // ‘kalama’ = Snapdragon 8 Gen 2
        val board = Build.BOARD.lowercase()
        val hardware = Build.HARDWARE.lowercase()

        return when {
            // Snapdragon 8 Elite
            board == "sun" || hardware == "sun" -> "Oryon"
            // Snapdragon 8 Gen 3 (Arquitectura con Cortex-X4)
            board == "pineapple" || hardware == "pineapple" -> "Cortex-X4"

            // Snapdragon 8 Gen 2 (Arquitectura con Cortex-X3)
            board == "kalama" || hardware == "kalama" -> "Cortex-X3"

            // Snapdragon 8 Gen 1 (Arquitectura con Cortex-X2)
            board == "taro" || hardware == "taro" -> "Cortex-X2"

            board == "lahaina" || hardware == "lahaina" -> "Cortex-X1"
            board == "yupik" || hardware == "yupik" -> "Cortex-A78"

            // --- MEDIATEK DIMENSITY ---
            // Dimensity 9400 (Cortex-X925)
            board.contains("mt6991") || hardware.contains("mt6991") -> "Cortex-X925"
            // Dimensity 9300 / 9300+
            board.contains("mt6989") || hardware.contains("mt6989") -> "Cortex-X4"
            // Dimensity 9200
            board.contains("mt6985") || hardware.contains("mt6985") -> "Cortex-X3"
            // Dimensity 9000
            board.contains("mt6983") || hardware.contains("mt6983") -> "Cortex-X2"
            // Dimensity 8100 / 8200 / 1200 / 1100 (Serie A78)
            board.contains("mt6895") || board.contains("mt6893") -> "Cortex-A78"

            // --- SAMSUNG EXYNOS ---
            // Exynos 2500 (Cortex-X5 / X925 base)
            board == "s5e9955" || hardware == "s5e9955" -> "Cortex-X5"
            // Exynos 2400 (S24 series)
            board == "s5e9945" || hardware == "s5e9945" -> "Cortex-X4"
            // Exynos 2200 (S22 series)
            board == "s5e9925" || hardware == "s5e9925" -> "Cortex-X2"
            // Exynos 2100 (S21 series)
            board == "exynos2100" || hardware == "exynos2100" -> "Cortex-X1"
            // Exynos 1480 / 1380
            board == "s5e8845" || board == "s5e8835" -> "Cortex-A78"

            // --- GOOGLE TENSOR (PIXEL SERIES) ---
            // Tensor G5 (Pixel 10 X925)
            board == "franklin" -> "Cortex-X925"
            // Tensor G4 (Pixel 9 / 9 Pro)
            board == "tokay" || board == "comet" || board == "caiman" || board == "komodo" -> "Cortex-X4"
            // Tensor G3 (Pixel 8 / 8a)
            board == "shiba" || board == "husky" || board == "akita" || hardware.contains("zuma") -> "Cortex-X3"
            // Tensor G2 (Pixel 7 / 7a / Fold)
            board == "cheetah" || board == "panther" || board == "lynx" || hardware.contains("cloudripper") -> "Cortex-X1"
            // Tensor G1 (Pixel 6 / 6a)
            board == "oriole" || board == "raven" || board == "bluejay" || hardware.contains("whitechapel") -> "Cortex-X1"

            // Fallback: If it is not a known board, we try searching in /proc/cpuinfo,
            // although it is likely that we will never find anything,
            // but we will never know if this works.
            else -> searchInCpuInfo()
        }
    }

    private fun searchInCpuInfo(): String {
        return try {
            val cpuInfo = java.io.File("/proc/cpuinfo").readText()
            when {
                cpuInfo.contains("Oryon", ignoreCase = true) -> "Oryon"
                cpuInfo.contains("Kryo", ignoreCase = true) -> "Kryo"
                cpuInfo.contains("Cortex", ignoreCase = true) -> "Cortex"
                else -> "Generic CPU"
            }
        } catch (e: Exception) {
            "Unknown CPU"
        }
    }

    private fun getManualGames(): Set<String> {
        val prefs = getSharedPreferences("gamespace_prefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("manual_games", emptySet()) ?: emptySet()
    }

    private fun addManualGame(packageName: String) {
        val prefs = getSharedPreferences("gamespace_prefs", Context.MODE_PRIVATE)

        //clone the list to avoid reference problems
        val currentGames = prefs.getStringSet("manual_games", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentGames.add(packageName)

        // use commit instead of apply (originally we used apply)
        // so that the save is immediate and synchronous
        prefs.edit().putStringSet("manual_games", currentGames).commit()

        //refresh the service
        restartGameSpaceService()
    }

    private fun showAddGameDialog() {
        val pm = packageManager
        val manualGames = getManualGames()
        
        // Obtenemos todas las apps instaladas que se pueden abrir (que tienen ícono en el launcher)
        val availableApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                val pkg = app.packageName
                // Que no sea GameSpace, que se pueda lanzar y que no esté ya añadida
                pkg != packageName && 
                pm.getLaunchIntentForPackage(pkg) != null && 
                !manualGames.contains(pkg)
            }
            .map { app ->
                GameModel(
                    name = pm.getApplicationLabel(app).toString(),
                    packageName = app.packageName,
                    icon = pm.getApplicationIcon(app)
                )
            }
            .sortedBy { it.name.lowercase() } // Orden alfabético

        // Nombres para mostrar en la lista
        //val appNames = availableApps.map { pm.getApplicationLabel(it).toString() }.toTypedArray()
        val adapter = object : android.widget.ArrayAdapter<GameModel>(
            this,
            R.layout.item_app_picker,
            availableApps
        ) {
            override fun getView(position: Int, convertView: android.view.View?,parent: android.view.ViewGroup): android.view.View {
                val view = convertView ?: layoutInflater.inflate(R.layout.item_app_picker, parent, false)
                val item = getItem(position)!!

                val iconView = view.findViewById<android.widget.ImageView>(R.id.app_icon)
                val nameView = view.findViewById<android.widget.TextView>(R.id.app_name)

                iconView.setImageDrawable(item.icon)
                nameView.text = item.name

                return view
            }
        }

        // Mostramos un diálogo nativo del sistema
        android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(R.string.select_app)
            .setAdapter(adapter) { _, which ->
                val selectedApp = availableApps[which]
                addManualGame(selectedApp.packageName)
                
                // Recargamos la lista para que aparezca el nuevo juego
                setupGameGrid() 
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun removeManualGame(packageName: String) {
        val prefs = getSharedPreferences("gamespace_prefs", Context.MODE_PRIVATE)
        val currentGames = getManualGames().toMutableSet()

        if (currentGames.contains(packageName)) {
            currentGames.remove(packageName)
            prefs.edit().putStringSet("manual_games", currentGames).commit()
            setupGameGrid() // Recargar la vista
            Toast.makeText(this, getString(R.string.game_removed), Toast.LENGTH_SHORT).show()
        }

        restartGameSpaceService()
    }

    private fun showRemoveDialog(packageName: String) {
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        .setTitle(R.string.remove_game_title)
        .setMessage(R.string.remove_game_dialog)
        .setPositiveButton(R.string.btn_remove) { _, _ ->
            removeManualGame(packageName)
        }
        .setNegativeButton(R.string.btn_cancel, null)
        .show()
    }

    private fun restartGameSpaceService() {
        val serviceIntent = Intent(this, GameSpaceService::class.java)

        stopService(serviceIntent)

        startService(serviceIntent)
    }

}
