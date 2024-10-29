package link.locutus.discord.util;

import cn.easyproject.easyocr.EasyOCR;
import cn.easyproject.easyocr.ImageType;
import com.mxgraph.layout.mxFastOrganicLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.util.io.PagePriority;
import org.jgrapht.Graph;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ImageUtil {



    public static BufferedImage readImage(String urlAddr) {
        try {
            URL url = new URL(urlAddr);
            BufferedImage image = ImageIO.read(url);
            return image;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
//    public static byte[] generateTreatyGraph(Collection<Treaty> treaties) throws IOException {
//        Map<Integer, Integer> treatiesByAlliance = new HashMap<>();
//        for (Treaty treaty : treaties) {
//            treatiesByAlliance.merge(treaty.getFromId(), 1, Integer::sum);
//            treatiesByAlliance.merge(treaty.getToId(), 1, Integer::sum);
//        }
//        Map<Integer, Integer> treatiesByAlliance2ndOrder = new HashMap<>();
//        for (Treaty treaty : treaties) {
//            int from = treaty.getFromId();
//            int to = treaty.getToId();
//            int fromTreaties = treatiesByAlliance.get(from);
//            int toTreaties = treatiesByAlliance.get(to);
//            treatiesByAlliance2ndOrder.merge(from, toTreaties, Integer::sum);
//            treatiesByAlliance2ndOrder.merge(to, fromTreaties, Integer::sum);
//        }
//        List<Treaty> treatiesSorted = new ArrayList<>(treaties);
//        // sort by sum of # of connections 2nd order
//        treatiesSorted.sort((a, b) -> {
//            int aSum = treatiesByAlliance2ndOrder.get(a.getFromId()) + treatiesByAlliance2ndOrder.get(a.getToId());
//            int bSum = treatiesByAlliance2ndOrder.get(b.getFromId()) + treatiesByAlliance2ndOrder.get(b.getToId());
//            return Integer.compare(aSum, bSum);
//        });
//
//        mxGraph graph = new mxGraph();
//        Object parent = graph.getDefaultParent();
//
//        graph.getModel().beginUpdate();
//        try {
//            for (Treaty treaty : treatiesSorted) {
//                String country1 = treaty.getFrom().getName();
//                String country2 = treaty.getTo().getName();
////                vertices.putIfAbsent(country1, graph.insertVertex(parent, null, country1, ThreadLocalRandom.current().nextInt(240), ThreadLocalRandom.current().nextInt(240), 80, 30));
////                vertices.putIfAbsent(country2, graph.insertVertex(parent, null, country2, ThreadLocalRandom.current().nextInt(240), ThreadLocalRandom.current().nextInt(240), 80, 30));
////                // Create edges between the vertices
//                Object edge = graph.insertEdge(parent, null, treaty.getType().getName(), country1, country2);
//                String color = treaty.getType().getColor();
//                String style = mxConstants.STYLE_STROKECOLOR + "=" + color;
//                ((mxCell) edge).setStyle(style);
//            }
//        } finally {
//            graph.getModel().endUpdate();
//        }
//
//        mxFastOrganicLayout layout = new mxFastOrganicLayout(graph);
//        layout.setForceConstant(100); // Higher value gives more distance between nodes
//        layout.setMaxIterations(10000);
//        layout.setInitialTemp(200);
//        layout.setMaxDistanceLimit(25);
//        layout.setMinDistanceLimit(25);
//        layout.execute(graph.getDefaultParent());
//
//        BufferedImage image = mxCellRenderer.createBufferedImage(graph, null, 1, Color.WHITE, true, null);
//        // Write the image to a file
//        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
//            ImageIO.write(image, "png", baos);
//            baos.flush();
//            return baos.toByteArray();
//        }
//    }

    public static byte[] generateTreatyGraph(Collection<Treaty> treaties) throws IOException {
        Map<Integer, Integer> treatiesByAlliance = new HashMap<>();
        for (Treaty treaty : treaties) {
            treatiesByAlliance.merge(treaty.getFromId(), 1, Integer::sum);
            treatiesByAlliance.merge(treaty.getToId(), 1, Integer::sum);
        }
        Map<Integer, Integer> treatiesByAlliance2ndOrder = new HashMap<>();
        for (Treaty treaty : treaties) {
            int from = treaty.getFromId();
            int to = treaty.getToId();
            int fromTreaties = treatiesByAlliance.get(from);
            int toTreaties = treatiesByAlliance.get(to);
            treatiesByAlliance2ndOrder.merge(from, toTreaties, Integer::sum);
            treatiesByAlliance2ndOrder.merge(to, fromTreaties, Integer::sum);
        }
        List<Treaty> treatiesSorted = new ArrayList<>(treaties);
        // sort by sum of # of connections 2nd order
        treatiesSorted.sort((a, b) -> {
            int aSum = treatiesByAlliance2ndOrder.get(a.getFromId()) + treatiesByAlliance2ndOrder.get(a.getToId());
            int bSum = treatiesByAlliance2ndOrder.get(b.getFromId()) + treatiesByAlliance2ndOrder.get(b.getToId());
            return Integer.compare(bSum, aSum);
        });

//         Create a graph
        Graph<String, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
        // Create a map to store edge labels
        Map<DefaultEdge, String> edgeToLabelMap = new HashMap<>();

        // Add vertices and edges from connections
        for (Treaty connection : treatiesSorted) {
            String country1 = connection.getFrom().getName();
            String country2 = connection.getTo().getName();
            graph.addVertex(country1);
            graph.addVertex(country2);
            DefaultEdge edge = graph.addEdge(country1, country2);

            String label = connection.getType().getName();
            edgeToLabelMap.put(edge, label);
        }

        // Create a JGraphXAdapter
        JGraphXAdapter<String, DefaultEdge> graphAdapter = new JGraphXAdapter<String, DefaultEdge>(graph) {
            @Override
            public String convertValueToString(Object cell) {
                if (cell instanceof mxCell) {
                    Object value = ((mxCell) cell).getValue();
                    if (value instanceof DefaultEdge) {
                        DefaultEdge edge = (DefaultEdge) value;
                        // return the label for the edge
                        String label = edgeToLabelMap.get(edge);
                        TreatyType type = TreatyType.parse(label);
                        String color = type.getColor();
                        String style = mxConstants.STYLE_STROKECOLOR + "=" + color;
                        mxCell mxCell = (mxCell) cell;
                        mxCell.setStyle(style);
                        return label;
                    }
                }
                return super.convertValueToString(cell);
            }
        };

        mxFastOrganicLayout layout = new mxFastOrganicLayout(graphAdapter);
        layout.setForceConstant(100);
        layout.setMaxIterations(5000);
        layout.setInitialTemp(200);
        layout.setMaxDistanceLimit(200);
        layout.setMinDistanceLimit(50);
        layout.setUseBoundingBox(true);
        layout.execute(graphAdapter.getDefaultParent());

        // Create a mxGraphComponent
        mxGraphComponent graphComponent = new mxGraphComponent(graphAdapter);
        graphComponent.setEnabled(false);

        // Render the graph to an image
        BufferedImage image = mxCellRenderer.createBufferedImage(graphAdapter, null, 1, Color.WHITE, true, null);

        if (image == null) return null;
        // Write the image to a file
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            baos.flush();
            return baos.toByteArray();
        }
    }

    private static final List<String> SUPPORTED_DOMAINS = Arrays.asList(
            "discord.gg",
            "discord.com",
            "discordapp.com",
            "discord.media",
            "discordapp.net",
            "discordcdn.com",
            "discord.dev",
            "discord.new",
            "discord.gift",
            "discordstatus.com",
            "dis.gd",
            "discord.co",
            "pasteboard.co",
            "imgur.com",
            "imgbb.com",
            "postimages.org",
            "freeimage.host",
            "doerig.dev"
    );

    public static boolean isDiscordImage(String url) {
        try {
            URI parsedUrl = new URI(url);
            String host = parsedUrl.getHost();
            return SUPPORTED_DOMAINS.stream().anyMatch(host::endsWith);
        } catch (Exception e) {
            // Invalid URL
            return false;
        }
    }



    public static String getTextLocal(String imageUrl, ImageType type) {
        String pathStr = Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OCR.TESSERACT_LOCATION;
        if (pathStr == null || pathStr == null) {
            return null;
        }
        EasyOCR ocr = new EasyOCR(pathStr);
        ocr.setTesseractOptions(EasyOCR.OPTION_LANG_ENG);
        File fileTmp = null; // 50MB limit
        try {
            fileTmp = downloadImageWithSizeLimit(imageUrl, 50 * 1024 * 1024);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        File fileAbs = fileTmp.getAbsoluteFile();
        String result = ocr.discernAndAutoCleanImage(fileAbs, type);
        result = fixEncodingIssues(result.replace("|", "I"));
        fileTmp.delete();
        return result;
    }

    private static String fixEncodingIssues(String text) {
        return text.replaceAll("\u00e2\u20ac\u2122", "\u2019")
                .replaceAll("\u00e2\u20ac\u0153", "\u201C")
                .replaceAll("\u00e2\u20ac\u017d", "\u201D")
                .replaceAll("\u00e2\u20ac\u201c", "\u2013")
                .replaceAll("\u00e2\u20ac\u201d", "\u2014")
                .replaceAll("\u00e2\u20ac\u02dc", "\u2018")
                .replaceAll("\u00e2\u20ac\u00a2", "\u2022")
                .replaceAll("\u00e2\u20ac\u00a6", "\u2026")
                .replaceAll("\u00e2\u20ac", "\u2020")
                .replaceAll("\u00e2\u201e\u00a2", "\u2122")
                .replaceAll("\u00c2", "");
    }

    public static File downloadImageWithSizeLimit(String imageUrl, long maxSizeBytes) throws IOException {
        if (!isDiscordImage(imageUrl)) {
            throw new IllegalArgumentException("URL is not from cdn.discordapp.com");
        }

        // Open a connection to the image URL
        URL url = new URL(imageUrl);
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");

        // Verify that the content type is an image
        String contentType = connection.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IOException("Invalid content type: " + contentType);
        }

        // Verify that the content length is within the size limit
        int contentLength = connection.getContentLength();
        if (contentLength > maxSizeBytes) {
            throw new IOException("Image size exceeds limit: " + contentLength + " bytes");
        }

        // Download the image to a temporary file
        InputStream inputStream = connection.getInputStream();
        File tempFile = new File("images/" + UUID.randomUUID().toString() + ".png");
        // create folder
        tempFile.getParentFile().mkdirs();
//        File tempFile = File.createTempFile("image", null);
//        tempFile.deleteOnExit();
        FileOutputStream outputStream = new FileOutputStream(tempFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.close();
        inputStream.close();

        // Verify that the downloaded content is a valid image
        BufferedImage image = ImageIO.read(tempFile);
        if (image == null) {
            throw new IOException("Invalid image content");
        }

        // ensure file exists
        if (!tempFile.exists()) {
            throw new RuntimeException("File not found at " + tempFile.getAbsolutePath());
        }

        return tempFile;
    }

    private static Set<String> englishWordsCache = null;
    private static int countEnglishWords(String text) {
        if (englishWordsCache == null) {
            englishWordsCache = loadEnglishWordList("/ocr/words.txt");
        }
        String[] words = text.split("\\s+");
        int count = 0;
        for (String word : words) {
            if (isValidEnglishWord(word, englishWordsCache)) {
                count++;
            }
        }
        return count;
    }

    private static Set<String> loadEnglishWordList(String filePath) {
        Set<String> englishWords = new HashSet<>();
        String text = FileUtil.readFile(filePath);
        for (String line : text.split("\n")) {
            englishWords.add(line.trim().toLowerCase());
        }
        return englishWords;
    }

    private static boolean isValidEnglishWord(String word, Set<String> englishWords) {
        // Convert the word to lowercase for case-insensitive matching
        word = word.toLowerCase();
        return englishWords.contains(word);
    }

    private static final String OCR_API_URL = "https://api.ocr.space/parse/image"; // OCR API Endpoints

    public static String convertImageUrlToText(String apiKey, boolean isOverlayRequired, String imageUrl, String language) {
        try {
            URL obj = new URL(OCR_API_URL);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            // Add request header
            con.setRequestMethod("POST");
            con.setRequestProperty("User-Agent", "Mozilla/5.0");
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

            JSONObject postDataParams = new JSONObject();
            postDataParams.put("apikey", apiKey);
            postDataParams.put("isOverlayRequired", isOverlayRequired);
            postDataParams.put("url", imageUrl);
            postDataParams.put("language", language);

            // Send post request
            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(getPostDataString(postDataParams));
            wr.flush();
            wr.close();

            // Read the response
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Return the result
            return response.toString();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private static String getPostDataString(JSONObject params) throws Exception {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (String key : params.keySet()) {
            Object value = params.get(key);

            if (first) {
                first = false;
            } else {
                result.append("&");
            }

            result.append(URLEncoder.encode(key, "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(value.toString(), "UTF-8"));
        }
        return result.toString();
    }

    public static String getText(String imageUrl) {
        if (!Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OCR.OCR_SPACE_KEY.isEmpty()) {
            try {
                return getTextAPI(imageUrl);
            } catch (IOException | IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        return getTextLocal(imageUrl, ImageType.CLEAR);
    }

    // Example usage:
    public static String getTextAPI(String imageUrl) throws IOException {
        String endpoint = "https://api.ocr.space/parse/imageurl?apikey=" + Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OCR.OCR_SPACE_KEY + "&isTable=true&OCREngine=2&url=";

        String url = endpoint + imageUrl;
        String jsonStr = FileUtil.readStringFromURL(PagePriority.API_OCR, url);
        JSONObject json = new JSONObject(jsonStr);
        // ParsedResults > ParsedText
        String parsedText = null;
        String errorMessage = null;

        if (json.has("ParsedResults")) {
            JSONArray parsedResultsArray = json.getJSONArray("ParsedResults");
            if (parsedResultsArray.length() > 0) {
                JSONObject parsedResults = parsedResultsArray.getJSONObject(0);
                if (parsedResults.has("ParsedText")) {
                    parsedText = parsedResults.getString("ParsedText");
                }
            }
        }

        if (json.has("ErrorMessage")) {
            errorMessage = json.get("ErrorMessage") + "";
        }

        if (parsedText != null) {
            return parsedText;
        } else if (errorMessage != null) {
            throw new IllegalArgumentException(errorMessage);
        } else {
            throw new IllegalArgumentException("Unknown result: Neither ParsedResults > ParsedText nor ErrorMessage found:\n" + jsonStr);
        }
    }

    public static byte[] addWatermark(BufferedImage image, String watermarkText2, Color color, float opacity, Font font, boolean repeat) {
        try {
            String[] words = watermarkText2.replaceAll("\\n", "\n").split("\n");
            Graphics2D g2d = (Graphics2D) image.getGraphics();

            AlphaComposite alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity);
            g2d.setComposite(alphaChannel);
            g2d.setColor(color);

            int fontSize = 1024;
            font = setFontSize(font, fontSize);
            g2d.setFont(font);
            FontMetrics fontMetrics = g2d.getFontMetrics();
            Rectangle2D rect;

            // Word wrap
            StringBuilder currentLine = new StringBuilder(words[0]);
            java.util.List<String> lines = new ArrayList<>();
            for (int i = 1; i < words.length; i++) {
                if (fontMetrics.stringWidth(currentLine + words[i]) < image.getWidth()) {
                    currentLine.append(" ").append(words[i]);
                } else {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(words[i]);
                }
            }
            lines.add(currentLine.toString());

            // Scale font to fit image width and height
            for (String line : lines) {
                while (true) {
                    if (fontSize <= 16) break;
                    font = setFontSize(font, fontSize);
                    g2d.setFont(font);
                    fontMetrics = g2d.getFontMetrics();
                    rect = fontMetrics.getStringBounds(line, g2d);
                    if (rect.getWidth() > image.getWidth() || rect.getHeight() * lines.size() > image.getHeight()) {
                        fontSize--;
                    } else {
                        break;
                    }
                }
            }

            // Draw each line of the watermark text
            int lineHeight = g2d.getFontMetrics().getAscent();
            int padding = g2d.getFontMetrics().getHeight() - g2d.getFontMetrics().getAscent();
            int totalTextHeight = lines.size() * lineHeight;
            int y = (repeat ? 0 : (image.getHeight() - totalTextHeight) / 2) - padding;
            while (y < image.getHeight()) {
                for (String line : lines) {
                    int x = (image.getWidth() - fontMetrics.stringWidth(line)) / 2;
                    g2d.drawString(line, x, y += lineHeight);
                }
                if (!repeat) break;
            }

            // Write image to bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            baos.flush();
            byte[] imageInByte = baos.toByteArray();
            baos.close();

            g2d.dispose();

            return imageInByte;
        } catch (IOException ex) {
            System.err.println(ex);
            throw new RuntimeException(ex);
        }
    }

    public static BufferedImage image(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        BufferedImage image = ImageIO.read(url);
        return image;
    }

    public static Color getAverageColor(BufferedImage image) {
        long sumRed = 0;
        long sumGreen = 0;
        long sumBlue = 0;
        long totalPixels = (long) image.getWidth() * image.getHeight();

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixel = new Color(image.getRGB(x, y));
                sumRed += pixel.getRed();
                sumGreen += pixel.getGreen();
                sumBlue += pixel.getBlue();
            }
        }

        int averageRed = (int) (sumRed / totalPixels);
        int averageGreen = (int) (sumGreen / totalPixels);
        int averageBlue = (int) (sumBlue / totalPixels);

        return new Color(averageRed, averageGreen, averageBlue);
    }

    public static float getLuminance(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return hsb[2];
    }

    public static Color getDefaultWatermarkColor(BufferedImage image) {
        Color color = getAverageColor(image);
        return getLuminance(color) < 0.5 ? Color.LIGHT_GRAY : Color.DARK_GRAY;
    }

    public static Font setFontSize(Font font, int size) {
        return font.deriveFont((float) size);
    }
}
