package com.examshield.ai

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.examshield.ai.ui.screens.MonitorScreen
import com.examshield.ai.ui.screens.SignalFinderScreen
import com.examshield.ai.ui.screens.MonitorScreenViewModel
import com.examshield.ai.ui.theme.ExamShieldAITheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions -> 
        // After permissions, check if Bluetooth is actually enabled
        checkAndEnableBluetooth()
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            android.widget.Toast.makeText(this, "Bluetooth is required.", android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestRequiredPermissions()

        setContent {
            ExamShieldAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val sharedViewModel: MonitorScreenViewModel = hiltViewModel()
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "monitor") {
                        composable("monitor") {
                            MonitorScreen(navController = navController, viewModel = sharedViewModel)
                        }
                        composable("finder/{macAddress}") { backStackEntry ->
                            val rawMac = backStackEntry.arguments?.getString("macAddress") ?: ""
                            val decodedMac = try { java.net.URLDecoder.decode(rawMac, "UTF-8") } catch(e: Exception) { rawMac }
                            SignalFinderScreen(
                                macAddress = decodedMac,
                                viewModel = sharedViewModel
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun checkAndEnableBluetooth() {
        val bluetoothManager = getSystemService(android.bluetooth.BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter
        
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled) {
            val enableBtIntent = android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }
    }
}
