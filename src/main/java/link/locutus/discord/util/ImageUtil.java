package link.locutus.discord.util;

import ai.djl.util.Pair;
import cn.easyproject.easyocr.EasyOCR;
import cn.easyproject.easyocr.ImageType;
import link.locutus.discord.config.Settings;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.IntIntPair;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

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

    public static String getText(String imageUrl, ImageType type) {
        String pathStr = Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.TESSERACT_LOCATION;
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
        result = result.replace("|", "I");
        fileTmp.delete();
        return result;
    }

    public static File downloadImageWithSizeLimit(String imageUrl, long maxSizeBytes) throws IOException {
        if (!imageUrl.startsWith("https://cdn.discordapp.com/")) {
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
}
