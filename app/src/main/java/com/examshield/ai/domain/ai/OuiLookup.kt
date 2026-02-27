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
        "00:1D:D7" to "Jabra" // GN Netcom A/S (Jabra)
    )
    /**
     * Looks up the manufacturer based on the MAC address via an online API.
     * Falls back to offline curated list if API fails or device is offline.
     * @param macAddress The full MAC address of the device.
     * @return The manufacturer name or null if not found.
     */
    suspend fun lookup(macAddress: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (macAddress.length < 8) return@withContext null
        
        // 1. Try public MAC vendors API
        try {
            val url = java.net.URL("https://api.macvendors.com/$macAddress")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            if (connection.responseCode == 200) {
                val scanner = java.util.Scanner(connection.inputStream)
                if (scanner.hasNext()) {
                    val vendorName = scanner.useDelimiter("\\A").next()
                    return@withContext vendorName
                }
            }
        } catch (e: Exception) {
            // Network failed or rate limited (api.macvendors.com limits to 1 req/sec on free tier)
            // Fallthrough to local mapping
        }

        // 2. Fall back to local map
        val prefix = macAddress.substring(0, 8).uppercase()
        return@withContext ouiMap[prefix]
    }
}
