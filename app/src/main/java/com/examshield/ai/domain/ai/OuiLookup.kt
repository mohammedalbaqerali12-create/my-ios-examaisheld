package com.examshield.ai.domain.ai

object OuiLookup {

    // A small, curated list of Organizationally Unique Identifiers (OUIs)
    // In a real-world app, this would be a much larger database.
    private val ouiMap = mapOf(
        // Apple, Inc.
        "3C:D0:F8" to "Apple",
        "A8:66:7F" to "Apple",
        "F8:E0:79" to "Apple",
        "E0:AC:CB" to "Apple",
        "D8:A0:1D" to "Apple",
        "40:A6:D9" to "Apple",
        "9C:FC:E8" to "Apple",

        // Samsung Electronics Co.,Ltd
        "E8:1C:78" to "Samsung",
        "D0:03:4B" to "Samsung",
        "BC:A9:20" to "Samsung",
        "A0:2D:18" to "Samsung",
        "84:27:BE" to "Samsung",
        "60:D0:A9" to "Samsung",

        // Google, Inc.
        "F8:0D:F9" to "Google",
        "94:EB:2C" to "Google",
        "D8:EB:46" to "Google",

        // Common for small electronics/earbuds
        "AC:87:A3" to "Xiaomi", // Xiaomi Communications Co Ltd
        "7C:B9:60" to "Anker", // Anker Innovations Limited (Soundcore)
        "00:1D:D7" to "Jabra", // GN Netcom A/S (Jabra)
        "24:62:AB" to "Espressif (ESP32)",
        "30:AE:A4" to "Espressif (ESP32)",
        "DC:A6:32" to "Raspberry Pi",
        "B8:27:EB" to "Raspberry Pi",
        "E4:5F:01" to "Raspberry Pi",
        "00:0C:BF" to "Intel (Mini-PC)",
        "00:E0:4C" to "Realtek (Cheap-Module)",
        "C4:4F:33" to "Espressif (ESP8266)"
    )
    private val lookupCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Looks up the manufacturer based on the MAC address via an online API.
     * Falls back to offline curated list if API fails or device is offline.
     * @param macAddress The full MAC address of the device.
     * @return The manufacturer name or null if not found.
     */
    suspend fun lookup(macAddress: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (macAddress.length < 8) return@withContext null
        
        // 0. Check Cache First
        lookupCache[macAddress]?.let { return@withContext it }
        
        // 1. Try Primary API (macvendors.com)
        try {
            val result = fetchUrl("https://api.macvendors.com/$macAddress")
            if (!result.isNullOrBlank()) {
                lookupCache[macAddress] = result
                return@withContext result
            }
        } catch (e: Exception) {
            // Priority 1.5: Try Backup MAC API 1 (macaddress.io - JSON format)
            try {
                val json = fetchUrl("https://api.macaddress.io/v1?apiKey=at_0_MOCK_KEY&output=json&search=$macAddress")
                // Simple regex extraction for vendorName if JSON library isn't available
                val match = "\"vendorName\":\"([^\"]+)\"".toRegex().find(json ?: "")
                match?.groupValues?.get(1)?.let {
                    lookupCache[macAddress] = it
                    return@withContext it
                }
            } catch (e2: Exception) {
                // Priority 1.6: Try Backup MAC API 2 (macvendor.io)
                try {
                    val vendor = fetchUrl("https://macvendor.io/api/vendor/$macAddress")
                    if (!vendor.isNullOrBlank() && !vendor.contains("error")) {
                         lookupCache[macAddress] = vendor
                         return@withContext vendor
                    }
                } catch (e3: Exception) {}
            }
        }

        // 2. Fall back to local map
        val prefix = macAddress.substring(0, 8).uppercase()
        val localVendor = ouiMap[prefix]
        if (localVendor != null) {
            lookupCache[macAddress] = localVendor
            return@withContext localVendor
        }

        return@withContext null
    }

    private fun fetchUrl(urlString: String): String? {
        return try {
            val url = java.net.URL(urlString)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            if (connection.responseCode == 200) {
                java.util.Scanner(connection.inputStream).useDelimiter("\\A").next()
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
