package com.souhenriza.sefazba.domain.impl;

import com.souhenriza.sefazba.domain.exceptions.CaptchaSolveException;
import com.souhenriza.sefazba.domain.port.browser.CaptchaSolver;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Solves the 4-character captcha of the "consulta destinatário" page.
 * <p>
 * The noise dots share the glyphs' colour, so they are told apart by size: after binarisation only components
 * tall enough to be a glyph are kept. Each glyph is then cropped, normalised to the height Tesseract reads best
 * and recognised on its own, keeping the most confident reading among a few segmentation modes.
 */
@Slf4j
public class CaptchaDestinatarioSolver implements CaptchaSolver {

    private static final int CAPTCHA_LENGTH = 4;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final int SCALE_FACTOR = 4;
    private static final int OPENING_KERNEL_SIZE = 5;
    private static final double MIN_GLYPH_HEIGHT_RATIO = 0.30;
    private static final double SAME_GLYPH_OVERLAP_RATIO = 0.5;
    /* Tried in order; 32 px reads best, the others only rescue glyphs it returns nothing for. */
    private static final int[] GLYPH_TARGET_HEIGHTS = {32, 48, 24};

    /* 10 = single character, 13 = raw line; on these glyphs they fail on different characters. */
    private static final int[] GLYPH_PAGE_SEG_MODES = {10, 13};
    private static final int WHOLE_WORD_PAGE_SEG_MODE = 8;

    private final String tesseractDatapath;

    public CaptchaDestinatarioSolver(String tesseractDatapath) {
        this.tesseractDatapath = tesseractDatapath;
    }

    static {
        nu.pattern.OpenCV.loadLocally();
    }

    @Override
    public String solve(File imageFile) throws CaptchaSolveException {
        try {
            Mat cleaned = removeNoise(imageFile);
            saveProcessedImage(imageFile, cleaned);

            List<Rect> glyphs = segmentGlyphs(cleaned);
            String result = glyphs.size() == CAPTCHA_LENGTH
                    ? recognizeGlyphs(cleaned, glyphs)
                    : recognizeWholeWord(cleaned);

            if (result.length() != CAPTCHA_LENGTH) {
                throw new CaptchaSolveException("Captcha read as '" + result + "' (" + glyphs.size()
                        + " glyphs found); expected " + CAPTCHA_LENGTH + " characters");
            }
            return result;
        } catch (IOException e) {
            throw new CaptchaSolveException("Failed to solve captcha: " + e.getMessage(), e);
        }
    }

    public Tesseract doTesseract(int pageSegMode) {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tesseractDatapath);
        tesseract.setLanguage("eng");
        tesseract.setPageSegMode(pageSegMode);
        tesseract.setOcrEngineMode(3);
        tesseract.setVariable("tessedit_char_whitelist", ALPHABET);
        tesseract.setVariable("user_defined_dpi", "300");

        return tesseract;
    }

    /**
     * Returns the captcha as white glyphs on black, with the noise dots removed.
     */
    private Mat removeNoise(File imageFile) throws IOException {
        Mat img = Imgcodecs.imread(imageFile.getAbsolutePath(), Imgcodecs.IMREAD_GRAYSCALE);
        if (img.empty()) {
            throw new IOException("Could not read captcha image " + imageFile);
        }

        Imgproc.resize(img, img, new Size(), SCALE_FACTOR, SCALE_FACTOR, Imgproc.INTER_CUBIC);
        Imgproc.threshold(img, img, 0, 255, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);

        // Detaches dots that touch a glyph without eating its strokes
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,
                new Size(OPENING_KERNEL_SIZE, OPENING_KERNEL_SIZE));
        Imgproc.morphologyEx(img, img, Imgproc.MORPH_OPEN, kernel);

        Mat labels = new Mat(), stats = new Mat(), centroids = new Mat();
        int n = Imgproc.connectedComponentsWithStats(img, labels, stats, centroids, 8, CvType.CV_32S);

        Mat clean = Mat.zeros(img.size(), CvType.CV_8U);
        Mat mask = new Mat();
        double minGlyphHeight = img.rows() * MIN_GLYPH_HEIGHT_RATIO;
        for (int i = 1; i < n; i++) {
            if (stats.get(i, Imgproc.CC_STAT_HEIGHT)[0] >= minGlyphHeight) {
                Core.compare(labels, new Scalar(i), mask, Core.CMP_EQ);
                clean.setTo(new Scalar(255), mask);
            }
        }
        return clean;
    }

    /**
     * Bounding boxes of the glyphs, left to right. Pieces of the same glyph (e.g. a broken serif) are merged,
     * and the result is adjusted towards {@link #CAPTCHA_LENGTH} boxes when possible.
     */
    private List<Rect> segmentGlyphs(Mat cleaned) {
        Mat labels = new Mat(), stats = new Mat(), centroids = new Mat();
        int n = Imgproc.connectedComponentsWithStats(cleaned, labels, stats, centroids, 8, CvType.CV_32S);

        List<Rect> boxes = new ArrayList<>();
        for (int i = 1; i < n; i++) {
            boxes.add(new Rect((int) stats.get(i, Imgproc.CC_STAT_LEFT)[0], (int) stats.get(i, Imgproc.CC_STAT_TOP)[0],
                    (int) stats.get(i, Imgproc.CC_STAT_WIDTH)[0], (int) stats.get(i, Imgproc.CC_STAT_HEIGHT)[0]));
        }
        boxes.sort(Comparator.comparingInt(box -> box.x));

        List<Rect> glyphs = new ArrayList<>();
        for (Rect box : boxes) {
            if (!glyphs.isEmpty()) {
                Rect last = glyphs.getLast();
                int overlap = Math.min(last.x + last.width, box.x + box.width) - Math.max(last.x, box.x);
                if (overlap > Math.min(last.width, box.width) * SAME_GLYPH_OVERLAP_RATIO) {
                    glyphs.set(glyphs.size() - 1, union(last, box));
                    continue;
                }
            }
            glyphs.add(box);
        }

        // Touching glyphs come out as one wide box: split the widest until the expected count is reached
        while (!glyphs.isEmpty() && glyphs.size() < CAPTCHA_LENGTH) {
            Rect widest = glyphs.stream().max(Comparator.comparingInt(box -> box.width)).orElseThrow();
            int index = glyphs.indexOf(widest);
            int half = widest.width / 2;
            glyphs.set(index, new Rect(widest.x, widest.y, half, widest.height));
            glyphs.add(index + 1, new Rect(widest.x + half, widest.y, widest.width - half, widest.height));
        }

        // Leftover blobs: keep the largest boxes, back in reading order
        if (glyphs.size() > CAPTCHA_LENGTH) {
            glyphs = new ArrayList<>(glyphs.stream()
                    .sorted(Comparator.comparingDouble(Rect::area).reversed())
                    .limit(CAPTCHA_LENGTH)
                    .sorted(Comparator.comparingInt(box -> box.x))
                    .toList());
        }
        return glyphs;
    }

    private String recognizeGlyphs(Mat cleaned, List<Rect> glyphs) throws IOException {
        List<Tesseract> engines = new ArrayList<>();
        for (int mode : GLYPH_PAGE_SEG_MODES) {
            engines.add(doTesseract(mode));
        }

        StringBuilder result = new StringBuilder();
        for (Rect glyph : glyphs) {
            String best = "";
            // Tesseract sometimes returns nothing for a glyph at one size but reads it at another
            for (int height : GLYPH_TARGET_HEIGHTS) {
                best = recognizeGlyph(engines, toBufferedImage(normalizeGlyph(new Mat(cleaned, glyph), height)));
                if (!best.isEmpty()) {
                    break;
                }
            }
            result.append(best);
        }
        return result.toString();
    }

    /**
     * Most confident single-character reading among the engines, or empty when none reads one.
     */
    private String recognizeGlyph(List<Tesseract> engines, BufferedImage glyphImage) {
        String best = "";
        float bestConfidence = -1;
        for (Tesseract engine : engines) {
            for (Word word : engine.getWords(glyphImage, ITessAPI.TessPageIteratorLevel.RIL_WORD)) {
                String text = word.getText().trim();
                // A glyph is sometimes read twice ("YY"); that still means a single character
                boolean singleCharacter = !text.isEmpty() && text.chars().allMatch(c -> c == text.charAt(0));
                if (singleCharacter && word.getConfidence() > bestConfidence) {
                    best = text.substring(0, 1);
                    bestConfidence = word.getConfidence();
                }
            }
        }
        return best;
    }

    private String recognizeWholeWord(Mat cleaned) throws IOException {
        Mat inverted = new Mat();
        Core.bitwise_not(cleaned, inverted);
        try {
            return doTesseract(WHOLE_WORD_PAGE_SEG_MODE).doOCR(toBufferedImage(inverted)).replaceAll("\\s", "");
        } catch (net.sourceforge.tess4j.TesseractException e) {
            throw new IOException(e);
        }
    }

    /**
     * Black glyph on white, scaled to the height Tesseract reads best and padded with margin around it.
     */
    private Mat normalizeGlyph(Mat glyph, int targetHeight) {
        Mat normalized = new Mat();
        double scale = (double) targetHeight / glyph.rows();
        Imgproc.resize(glyph, normalized, new Size(), scale, scale, Imgproc.INTER_AREA);
        Imgproc.threshold(normalized, normalized, 127, 255, Imgproc.THRESH_BINARY_INV);

        int padding = targetHeight / 2;
        Core.copyMakeBorder(normalized, normalized, padding, padding, padding, padding,
                Core.BORDER_CONSTANT, new Scalar(255));
        return normalized;
    }

    private static Rect union(Rect a, Rect b) {
        int x = Math.min(a.x, b.x), y = Math.min(a.y, b.y);
        return new Rect(x, y, Math.max(a.x + a.width, b.x + b.width) - x, Math.max(a.y + a.height, b.y + b.height) - y);
    }

    private static BufferedImage toBufferedImage(Mat mat) throws IOException {
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".png", mat, buffer);
        return ImageIO.read(new ByteArrayInputStream(buffer.toArray()));
    }

    private void saveProcessedImage(File originalFile, Mat processed) {
        String name = originalFile.getName().replaceFirst("\\.[^.]+$", "") + "_processed.png";
        File processedFile = new File(originalFile.getAbsoluteFile().getParentFile(), name);

        Mat inverted = new Mat();
        Core.bitwise_not(processed, inverted);
        if (Imgcodecs.imwrite(processedFile.getAbsolutePath(), inverted)) {
            log.info("Imagem processada foi salva em: {}", processedFile.getAbsolutePath());
        } else {
            log.warn("Falha ao salvar imagem processada em: {}", processedFile.getAbsolutePath());
        }
    }
}
