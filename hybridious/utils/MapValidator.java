package dev.hybridious.utils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.item.map.MapState;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class MapValidator {

    /**
     * Validate a map against the NSFW detection API
     * @param mapState The map state containing pixel data
     * @param apiUrl The base URL of the API
     * @param threshold The confidence threshold (null to use server default)
     * @param logResults Whether to log results
     * @return true if map is safe (SFW), false if NSFW
     */
    public static boolean validateMap(MapState mapState, String apiUrl, Double threshold, boolean logResults) {
        System.out.println("[MapFilter] ==================== VALIDATION START ====================");
        System.out.println("[MapFilter] API URL: " + apiUrl);
        System.out.println("[MapFilter] Threshold: " + (threshold != null ? threshold : "server default"));

        try {
            // Convert map to base64 image
            System.out.println("[MapFilter] Converting map to base64...");
            String base64Image = mapStateToBase64(mapState);
            System.out.println("[MapFilter] Converted map to base64 (length: " + base64Image.length() + " chars)");

            // Send to API
            System.out.println("[MapFilter] Sending request to API...");
            JsonObject response = sendToAPI(apiUrl, base64Image, threshold);
            System.out.println("[MapFilter] Received API response: " + response.toString());

            // Parse response
            boolean isSafe = response.get("is_safe").getAsBoolean();
            double confidence = response.get("confidence").getAsDouble();
            String classification = response.get("class").getAsString();

            System.out.println("[MapFilter] Classification: " + classification);
            System.out.println("[MapFilter] Confidence: " + String.format("%.2f%%", confidence * 100));
            System.out.println("[MapFilter] Is Safe: " + isSafe);
            System.out.println("[MapFilter] ==================== VALIDATION END ====================");

            if (logResults) {
                System.out.println("[MapFilter] RESULT: " + classification +
                        " (Confidence: " + String.format("%.2f%%", confidence * 100) + ")");
            }

            return isSafe;

        } catch (Exception e) {
            System.err.println("[MapFilter] ==================== VALIDATION ERROR ====================");
            System.err.println("[MapFilter] Error validating map: " + e.getMessage());
            System.err.println("[MapFilter] Error class: " + e.getClass().getName());
            e.printStackTrace();
            System.err.println("[MapFilter] ================================================================");

            // Don't throw - return true (safe) by default on error to avoid blocking gameplay
            System.err.println("[MapFilter] Defaulting to SAFE due to validation error");
            return true;
        }
    }

    /**
     * Convert MapState to base64-encoded PNG
     */
    private static String mapStateToBase64(MapState mapState) throws IOException, IllegalAccessException {
        System.out.println("[MapFilter] Creating 128x128 BufferedImage...");
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);

        // Extract pixel data from map colors
        // Based on the logs, the byte array field is field_122 (class [B)
        byte[] colors = null;

        try {
            // Try direct public access first (might work with Fabric API)
            System.out.println("[MapFilter] Attempting direct field access (colors)...");
            java.lang.reflect.Field colorsField = MapState.class.getField("colors");
            colors = (byte[]) colorsField.get(mapState);
            System.out.println("[MapFilter] ✓ Success: Direct field access");
        } catch (Exception e1) {
            try {
                // Try private field named "colors"
                System.out.println("[MapFilter] Attempting private field access (colors)...");
                java.lang.reflect.Field colorsField = MapState.class.getDeclaredField("colors");
                colorsField.setAccessible(true);
                colors = (byte[]) colorsField.get(mapState);
                System.out.println("[MapFilter] ✓ Success: Private field access");
            } catch (Exception e2) {
                try {
                    // Try the actual obfuscated field name: field_122
                    System.out.println("[MapFilter] Attempting obfuscated field access (field_122)...");
                    java.lang.reflect.Field colorsField = MapState.class.getDeclaredField("field_122");
                    colorsField.setAccessible(true);
                    colors = (byte[]) colorsField.get(mapState);
                    System.out.println("[MapFilter] ✓ Success: Obfuscated field access (field_122)");
                } catch (Exception e3) {
                    // Last resort: search for byte array field
                    System.out.println("[MapFilter] Attempting to find byte array field...");
                    for (java.lang.reflect.Field field : MapState.class.getDeclaredFields()) {
                        if (field.getType().equals(byte[].class)) {
                            System.out.println("[MapFilter] Found byte array field: " + field.getName());
                            field.setAccessible(true);
                            byte[] testArray = (byte[]) field.get(mapState);
                            if (testArray != null && testArray.length == 16384) { // 128x128
                                colors = testArray;
                                System.out.println("[MapFilter] ✓ Success: Found correct byte array field");
                                break;
                            }
                        }
                    }

                    if (colors == null) {
                        // Debug: list all fields
                        System.err.println("[MapFilter] Failed to find colors field. Available fields:");
                        for (java.lang.reflect.Field field : MapState.class.getDeclaredFields()) {
                            System.err.println("[MapFilter]   - " + field.getName() + " : " + field.getType());
                        }
                        throw new IOException("Failed to access map colors field", e3);
                    }
                }
            }
        }

        if (colors == null) {
            throw new IOException("Map colors array is null");
        }

        if (colors.length < 128 * 128) {
            throw new IOException("Map colors array is too small: " + colors.length + " (expected 16384)");
        }

        System.out.println("[MapFilter] Map colors array: " + colors.length + " bytes");

        // Convert map colors to RGB image
        int pixelsConverted = 0;
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                int index = x + z * 128;
                byte colorByte = colors[index];
                int rgbColor = getColorFromMapColor(colorByte);
                image.setRGB(x, z, rgbColor);
                pixelsConverted++;
            }
        }

        System.out.println("[MapFilter] Converted " + pixelsConverted + " pixels");

        // Convert to PNG bytes
        System.out.println("[MapFilter] Encoding image as PNG...");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "PNG", baos)) {
            throw new IOException("Failed to write PNG image");
        }
        byte[] imageBytes = baos.toByteArray();

        System.out.println("[MapFilter] Generated PNG: " + imageBytes.length + " bytes");

        // Encode to base64
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        System.out.println("[MapFilter] Base64 encoded: " + base64.length() + " characters");

        return base64;
    }

    /**
     * Convert Minecraft map color byte to RGB int
     */
    private static int getColorFromMapColor(byte colorByte) {
        int colorIndex = colorByte & 0xFF;

        // Color index 0-3 are transparent/none
        if (colorIndex < 4) {
            return 0x000000; // Black
        }

        // Each color has 4 shades (multipliers)
        int baseColorId = (colorIndex - 4) / 4;
        int shade = colorIndex % 4;

        // Get base RGB color
        int baseRgb = getMapColorRGB(baseColorId);

        // Extract RGB components
        int r = (baseRgb >> 16) & 0xFF;
        int g = (baseRgb >> 8) & 0xFF;
        int b = baseRgb & 0xFF;

        // Apply shade multiplier
        double multiplier = getShadeMultiplier(shade);
        r = Math.min(255, Math.max(0, (int) (r * multiplier)));
        g = Math.min(255, Math.max(0, (int) (g * multiplier)));
        b = Math.min(255, Math.max(0, (int) (b * multiplier)));

        // Return RGB (without alpha for TYPE_INT_RGB)
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Get shade multiplier for map colors
     */
    private static double getShadeMultiplier(int shade) {
        return switch (shade) {
            case 0 -> 180.0 / 255.0;  // Darkest
            case 1 -> 220.0 / 255.0;  // Dark
            case 2 -> 1.0;             // Normal/Bright
            case 3 -> 135.0 / 255.0;  // Very dark
            default -> 1.0;
        };
    }

    /**
     * Get base RGB color from Minecraft's map color palette
     */
    private static int getMapColorRGB(int baseColor) {
        return switch (baseColor) {
            case 0 -> 0x7FB238;   // GRASS
            case 1 -> 0xF7E9A3;   // SAND
            case 2 -> 0xC7C7C7;   // WOOL
            case 3 -> 0xFF0000;   // FIRE
            case 4 -> 0xA0A0FF;   // ICE
            case 5 -> 0xA7A7A7;   // METAL
            case 6 -> 0x007C00;   // PLANT
            case 7 -> 0xFFFFFF;   // SNOW
            case 8 -> 0xA4A8B8;   // CLAY
            case 9 -> 0x976D4D;   // DIRT
            case 10 -> 0x707070;  // STONE
            case 11 -> 0x4040FF;  // WATER
            case 12 -> 0x8B7653;  // WOOD
            case 13 -> 0xFFFFFF;  // QUARTZ
            case 14 -> 0xF87D23;  // ORANGE
            case 15 -> 0xC354CD;  // MAGENTA
            case 16 -> 0x6689D3;  // LIGHT_BLUE
            case 17 -> 0xE5E533;  // YELLOW
            case 18 -> 0x7FCC19;  // LIME
            case 19 -> 0xF27FA5;  // PINK
            case 20 -> 0x4C4C4C;  // GRAY
            case 21 -> 0x999999;  // LIGHT_GRAY
            case 22 -> 0x4C7F99;  // CYAN
            case 23 -> 0x7F3FB2;  // PURPLE
            case 24 -> 0x334CB2;  // BLUE
            case 25 -> 0x664C33;  // BROWN
            case 26 -> 0x667F33;  // GREEN
            case 27 -> 0x993333;  // RED
            case 28 -> 0x191919;  // BLACK
            case 29 -> 0xFAEE4D;  // GOLD
            case 30 -> 0x5CDBD5;  // DIAMOND
            case 31 -> 0x4A80FF;  // LAPIS
            case 32 -> 0x00D93A;  // EMERALD
            case 33 -> 0x815631;  // PODZOL
            case 34 -> 0x700200;  // NETHER
            case 35 -> 0xD1D1D1;  // WHITE_TERRACOTTA
            case 36 -> 0x9F5224;  // ORANGE_TERRACOTTA
            case 37 -> 0x95576C;  // MAGENTA_TERRACOTTA
            case 38 -> 0x706C8A;  // LIGHT_BLUE_TERRACOTTA
            case 39 -> 0xBA8524;  // YELLOW_TERRACOTTA
            case 40 -> 0x677535;  // LIME_TERRACOTTA
            case 41 -> 0xA04D4E;  // PINK_TERRACOTTA
            case 42 -> 0x392A23;  // GRAY_TERRACOTTA
            case 43 -> 0x876B62;  // LIGHT_GRAY_TERRACOTTA
            case 44 -> 0x575C5C;  // CYAN_TERRACOTTA
            case 45 -> 0x7A4958;  // PURPLE_TERRACOTTA
            case 46 -> 0x4C3E5C;  // BLUE_TERRACOTTA
            case 47 -> 0x4C3223;  // BROWN_TERRACOTTA
            case 48 -> 0x4C522A;  // GREEN_TERRACOTTA
            case 49 -> 0x8E3C2E;  // RED_TERRACOTTA
            case 50 -> 0x251610;  // BLACK_TERRACOTTA
            case 51 -> 0xBD3031;  // CRIMSON_NYLIUM
            case 52 -> 0x943F61;  // CRIMSON_STEM
            case 53 -> 0x5C191D;  // CRIMSON_HYPHAE
            case 54 -> 0x167E86;  // WARPED_NYLIUM
            case 55 -> 0x3A8E8C;  // WARPED_STEM
            case 56 -> 0x562C3E;  // WARPED_HYPHAE
            case 57 -> 0x14B485;  // WARPED_WART_BLOCK
            case 58 -> 0x646464;  // DEEPSLATE
            case 59 -> 0xD8AF93;  // RAW_IRON
            case 60 -> 0x7FA796;  // GLOW_LICHEN
            default -> 0x000000;  // Black
        };
    }

    /**
     * Send base64 image to API and get response
     * Enhanced for Minecraft environment with better error handling
     */
    private static JsonObject sendToAPI(String apiUrl, String base64Image, Double threshold) throws IOException {
        // Normalize API URL
        String urlString = apiUrl.trim();

        // Don't add /predict if it's already there
        if (urlString.endsWith("/predict")) {
            // Already has /predict, don't add it again
        } else {
            // Add /predict
            if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                urlString = "http://" + urlString;
            }
            if (!urlString.endsWith("/")) {
                urlString += "/";
            }
            urlString += "predict";
        }

        if (threshold != null) {
            urlString += "?threshold=" + threshold;
        }

        System.out.println("[MapFilter] ==================== HTTP REQUEST ====================");
        System.out.println("[MapFilter] Target URL: " + urlString);

        // Parse URL
        URL url;
        try {
            url = new URL(urlString);
        } catch (Exception e) {
            throw new IOException("Invalid API URL: " + urlString, e);
        }

        // Check if we're connecting to localhost/127.0.0.1
        boolean isLocalhost = url.getHost().equals("localhost") ||
                url.getHost().equals("127.0.0.1") ||
                url.getHost().equals("0.0.0.0");

        if (isLocalhost) {
            System.out.println("[MapFilter] Connecting to localhost - using DIRECT connection");
        }

        // Create connection - use NO_PROXY for localhost to avoid Minecraft's proxy issues
        HttpURLConnection conn;
        if (isLocalhost) {
            conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        } else {
            conn = (HttpURLConnection) url.openConnection();
        }

        try {
            // Configure connection with longer timeouts for Minecraft environment
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "MinecraftMapFilter/1.0");
            conn.setRequestProperty("Connection", "close"); // Don't keep alive
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setConnectTimeout(15000);  // 15 seconds to connect
            conn.setReadTimeout(45000);     // 45 seconds to read (ML inference can be slow)
            conn.setInstanceFollowRedirects(true);

            System.out.println("[MapFilter] Connection configured:");
            System.out.println("[MapFilter]   Host: " + url.getHost() + ":" + (url.getPort() > 0 ? url.getPort() : 80));
            System.out.println("[MapFilter]   Method: POST");
            System.out.println("[MapFilter]   Content-Type: application/json");
            System.out.println("[MapFilter]   Connect Timeout: 15s");
            System.out.println("[MapFilter]   Read Timeout: 45s");
            System.out.println("[MapFilter]   Proxy: " + (isLocalhost ? "NONE (direct)" : "DEFAULT"));

            // Build JSON request
            JsonObject request = new JsonObject();
            request.addProperty("image", base64Image);

            String requestBody = request.toString();
            byte[] requestBytes = requestBody.getBytes(StandardCharsets.UTF_8);

            System.out.println("[MapFilter] Request prepared:");
            System.out.println("[MapFilter]   Body size: " + requestBytes.length + " bytes");
            System.out.println("[MapFilter]   Base64 length: " + base64Image.length() + " chars");

            // Set content length
            conn.setFixedLengthStreamingMode(requestBytes.length);

            // Send request
            System.out.println("[MapFilter] Connecting to server...");
            try (OutputStream os = conn.getOutputStream()) {
                System.out.println("[MapFilter] Connection established, sending data...");
                os.write(requestBytes);
                os.flush();
                System.out.println("[MapFilter] Request sent successfully");
            } catch (Exception e) {
                System.err.println("[MapFilter] Failed to send request: " + e.getMessage());
                throw new IOException("Failed to send request to API: " + e.getMessage(), e);
            }

            // Read response
            System.out.println("[MapFilter] Waiting for response...");
            int responseCode;
            try {
                responseCode = conn.getResponseCode();
            } catch (Exception e) {
                System.err.println("[MapFilter] Failed to get response code: " + e.getMessage());
                throw new IOException("Failed to connect to API server. Is it running?", e);
            }

            String responseMessage = conn.getResponseMessage();
            System.out.println("[MapFilter] Response received:");
            System.out.println("[MapFilter]   Status: " + responseCode + " " + responseMessage);
            System.out.println("[MapFilter]   Content-Type: " + conn.getContentType());

            if (responseCode == 200) {
                System.out.println("[MapFilter] Reading response body...");
                byte[] responseBytes;
                try {
                    responseBytes = conn.getInputStream().readAllBytes();
                } catch (Exception e) {
                    throw new IOException("Failed to read response: " + e.getMessage(), e);
                }

                String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
                System.out.println("[MapFilter] Response: " + responseBody);
                System.out.println("[MapFilter] ==================== HTTP SUCCESS ====================");

                JsonObject jsonResponse;
                try {
                    jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                } catch (Exception e) {
                    throw new IOException("Failed to parse JSON response: " + responseBody, e);
                }

                // Validate response
                if (!jsonResponse.has("is_safe") || !jsonResponse.has("class") || !jsonResponse.has("confidence")) {
                    throw new IOException("Invalid API response format. Missing required fields. Got: " + responseBody);
                }

                return jsonResponse;

            } else {
                // Read error response
                String errorBody = "";
                try {
                    if (conn.getErrorStream() != null) {
                        errorBody = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                    }
                } catch (Exception e) {
                    errorBody = "(Could not read error: " + e.getMessage() + ")";
                }

                System.err.println("[MapFilter] ==================== HTTP ERROR ====================");
                System.err.println("[MapFilter] Status: " + responseCode + " " + responseMessage);
                System.err.println("[MapFilter] Error: " + errorBody);
                System.err.println("[MapFilter] ===============================================");

                throw new IOException("API error " + responseCode + ": " + errorBody);
            }

        } finally {
            conn.disconnect();
            System.out.println("[MapFilter] Connection closed");
        }
    }
}