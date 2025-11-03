package dev.hybridious.utils;

import com.google.gson.JsonArray;
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
import java.util.*;

public class BatchMapValidator {

    public static class ValidationResult {
        public final int mapId;
        public final boolean isSafe;
        public final double confidence;
        public final String classification;

        public ValidationResult(int mapId, boolean isSafe, double confidence, String classification) {
            this.mapId = mapId;
            this.isSafe = isSafe;
            this.confidence = confidence;
            this.classification = classification;
        }
    }

    /**
     * Validate multiple maps in a single batch request
     */
    public static Map<Integer, ValidationResult> validateBatch(
            Map<Integer, MapState> mapStates,
            String apiUrl,
            Double threshold,
            boolean logResults) {

        Map<Integer, ValidationResult> results = new HashMap<>();

        if (mapStates.isEmpty()) return results;

        try {
            // Convert all maps to base64
            List<Integer> mapIds = new ArrayList<>(mapStates.keySet());
            List<String> base64Images = new ArrayList<>();

            for (int mapId : mapIds) {
                try {
                    String base64 = mapStateToBase64(mapStates.get(mapId));
                    base64Images.add(base64);
                } catch (Exception e) {
                    if (logResults) {
                        System.err.println("[MapFilter] Failed to encode map " + mapId + ": " + e.getMessage());
                    }
                    // Skip this map
                    mapIds.remove(Integer.valueOf(mapId));
                }
            }

            if (base64Images.isEmpty()) return results;

            // Send batch request
            JsonObject response = sendBatchToAPI(apiUrl, base64Images, threshold, logResults);

            // Parse batch results
            JsonArray resultsArray = response.getAsJsonArray("results");

            for (int i = 0; i < resultsArray.size() && i < mapIds.size(); i++) {
                JsonObject result = resultsArray.get(i).getAsJsonObject();
                int mapId = mapIds.get(i);

                boolean isSafe = result.get("is_safe").getAsBoolean();
                double confidence = result.get("confidence").getAsDouble();
                String classification = result.get("class").getAsString();

                results.put(mapId, new ValidationResult(mapId, isSafe, confidence, classification));

                if (logResults) {
                    System.out.println("[MapFilter] Batch result [" + mapId + "]: " +
                            classification + " (" + String.format("%.1f%%", confidence * 100) + ")");
                }
            }

            if (logResults) {
                double batchTime = response.get("batch_inference_time_ms").getAsDouble();
                double avgTime = response.get("avg_time_per_image_ms").getAsDouble();
                System.out.println("[MapFilter] Batch complete: " + results.size() + " maps in " +
                        String.format("%.1fms (avg %.1fms/map)", batchTime, avgTime));
            }

        } catch (Exception e) {
            if (logResults) {
                System.err.println("[MapFilter] Batch validation error: " + e.getMessage());
            }
        }

        return results;
    }

    /**
     * Send batch request to API
     */
    private static JsonObject sendBatchToAPI(String apiUrl, List<String> base64Images,
                                             Double threshold, boolean logResults) throws IOException {
        String urlString = apiUrl.trim();

        if (!urlString.endsWith("/predict/batch")) {
            if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
                urlString = "http://" + urlString;
            }
            if (!urlString.endsWith("/")) {
                urlString += "/";
            }
            urlString += "predict/batch";
        }

        URL url = new URL(urlString);
        boolean isLocalhost = url.getHost().equals("localhost") ||
                url.getHost().equals("127.0.0.1") ||
                url.getHost().equals("0.0.0.0");

        HttpURLConnection conn = isLocalhost ?
                (HttpURLConnection) url.openConnection(Proxy.NO_PROXY) :
                (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000); // Longer timeout for batch

            // Build JSON request
            JsonObject request = new JsonObject();
            JsonArray imagesArray = new JsonArray();
            for (String base64 : base64Images) {
                imagesArray.add(base64);
            }
            request.add("images", imagesArray);

            if (threshold != null) {
                request.addProperty("threshold", threshold);
            }

            byte[] requestBytes = request.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(requestBytes.length);

            if (logResults) {
                System.out.println("[MapFilter] Sending batch of " + base64Images.size() + " maps...");
            }

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                byte[] responseBytes = conn.getInputStream().readAllBytes();
                String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
                return JsonParser.parseString(responseBody).getAsJsonObject();
            } else {
                String errorBody = conn.getErrorStream() != null ?
                        new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8) : "";
                throw new IOException("API error " + responseCode + ": " + errorBody);
            }

        } finally {
            conn.disconnect();
        }
    }

    /**
     * Convert MapState to base64-encoded PNG
     */
    private static String mapStateToBase64(MapState mapState) throws IOException, IllegalAccessException {
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        byte[] colors = extractMapColors(mapState);

        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                int index = x + z * 128;
                byte colorByte = colors[index];
                int rgbColor = getColorFromMapColor(colorByte);
                image.setRGB(x, z, rgbColor);
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private static byte[] extractMapColors(MapState mapState) throws IOException, IllegalAccessException {
        try {
            java.lang.reflect.Field colorsField = MapState.class.getField("colors");
            return (byte[]) colorsField.get(mapState);
        } catch (Exception e1) {
            try {
                java.lang.reflect.Field colorsField = MapState.class.getDeclaredField("colors");
                colorsField.setAccessible(true);
                return (byte[]) colorsField.get(mapState);
            } catch (Exception e2) {
                try {
                    java.lang.reflect.Field colorsField = MapState.class.getDeclaredField("field_122");
                    colorsField.setAccessible(true);
                    return (byte[]) colorsField.get(mapState);
                } catch (Exception e3) {
                    for (java.lang.reflect.Field field : MapState.class.getDeclaredFields()) {
                        if (field.getType().equals(byte[].class)) {
                            field.setAccessible(true);
                            byte[] array = (byte[]) field.get(mapState);
                            if (array != null && array.length == 16384) {
                                return array;
                            }
                        }
                    }
                    throw new IOException("Failed to extract map colors", e3);
                }
            }
        }
    }

    private static int getColorFromMapColor(byte colorByte) {
        int colorId = colorByte & 0xFF;
        if (colorId < 4) return 0x000000;

        int baseColorIndex = colorId >> 2;
        int shade = colorId & 0x03;

        int[] baseColor = getBaseColor(baseColorIndex);
        double multiplier = switch (shade) {
            case 0 -> 0.71;
            case 1 -> 0.86;
            case 2 -> 1.0;
            case 3 -> 0.53;
            default -> 1.0;
        };

        int r = (int) (baseColor[0] * multiplier);
        int g = (int) (baseColor[1] * multiplier);
        int b = (int) (baseColor[2] * multiplier);

        return (r << 16) | (g << 8) | b;
    }

    private static int[] getBaseColor(int index) {
        return switch (index) {
            case 4 -> new int[]{127, 178, 56};
            case 5 -> new int[]{247, 233, 163};
            case 6 -> new int[]{199, 199, 199};
            case 7 -> new int[]{255, 0, 0};
            case 8 -> new int[]{160, 160, 255};
            case 9 -> new int[]{167, 167, 167};
            case 10 -> new int[]{0, 124, 0};
            case 11 -> new int[]{255, 255, 255};
            case 12 -> new int[]{164, 168, 184};
            case 13 -> new int[]{151, 109, 77};
            case 14 -> new int[]{112, 112, 112};
            case 15 -> new int[]{64, 64, 255};
            case 16 -> new int[]{143, 119, 72};
            case 17 -> new int[]{255, 252, 245};
            case 18 -> new int[]{216, 127, 51};
            case 19 -> new int[]{178, 76, 216};
            case 20 -> new int[]{102, 153, 216};
            case 21 -> new int[]{229, 229, 51};
            case 22 -> new int[]{127, 204, 25};
            case 23 -> new int[]{242, 127, 165};
            case 24 -> new int[]{76, 76, 76};
            case 25 -> new int[]{153, 153, 153};
            case 26 -> new int[]{76, 127, 153};
            case 27 -> new int[]{127, 63, 178};
            case 28 -> new int[]{51, 76, 178};
            case 29 -> new int[]{102, 76, 51};
            case 30 -> new int[]{102, 127, 51};
            case 31 -> new int[]{153, 51, 51};
            case 32 -> new int[]{25, 25, 25};
            case 33 -> new int[]{250, 238, 77};
            case 34 -> new int[]{92, 219, 213};
            case 35 -> new int[]{74, 128, 255};
            case 36 -> new int[]{0, 217, 58};
            case 37 -> new int[]{129, 86, 49};
            case 38 -> new int[]{112, 2, 0};
            case 39 -> new int[]{209, 177, 161};
            case 40 -> new int[]{159, 82, 36};
            case 41 -> new int[]{149, 87, 108};
            case 42 -> new int[]{112, 108, 138};
            case 43 -> new int[]{186, 133, 36};
            case 44 -> new int[]{103, 117, 53};
            case 45 -> new int[]{160, 77, 78};
            case 46 -> new int[]{57, 41, 35};
            case 47 -> new int[]{135, 107, 98};
            case 48 -> new int[]{87, 92, 92};
            case 49 -> new int[]{122, 73, 88};
            case 50 -> new int[]{76, 62, 92};
            case 51 -> new int[]{76, 50, 35};
            case 52 -> new int[]{76, 82, 42};
            case 53 -> new int[]{142, 60, 46};
            case 54 -> new int[]{37, 22, 16};
            case 55 -> new int[]{189, 48, 49};
            case 56 -> new int[]{148, 63, 97};
            case 57 -> new int[]{92, 25, 29};
            case 58 -> new int[]{22, 126, 134};
            case 59 -> new int[]{58, 142, 140};
            case 60 -> new int[]{86, 44, 62};
            case 61 -> new int[]{20, 180, 133};
            default -> new int[]{0, 0, 0};
        };
    }
}