package com.greedykid.codexremote;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;

import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Decodes a QR code out of a still image the user picked from their gallery.
 *
 * A photo or screenshot is a much harder input than a camera preview: it can be
 * huge, tiny, or — very often here — a terminal screenshot where the code is
 * drawn light-on-dark. So rather than one attempt, this walks a short ladder of
 * variants and stops at the first that decodes.
 */
public final class QrImageDecoder {

    /** Long edge we downscale to. Big enough for detail, small enough for memory. */
    private static final int MAX_DIMENSION = 1600;

    /** Below this, upscaling helps ZXing lock onto the finder patterns. */
    private static final int MIN_USEFUL_DIMENSION = 480;

    private QrImageDecoder() {}

    public static String decode(ContentResolver resolver, Uri uri) {
        Bitmap bitmap = loadScaled(resolver, uri);
        if (bitmap == null) return null;

        try {
            String direct = tryAllStrategies(bitmap);
            if (direct != null) return direct;

            // A small screenshot often carries just enough pixels once enlarged.
            int longEdge = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (longEdge < MIN_USEFUL_DIMENSION) {
                float factor = (float) MIN_USEFUL_DIMENSION / longEdge;
                Bitmap bigger = null;
                try {
                    bigger = Bitmap.createScaledBitmap(bitmap,
                            Math.round(bitmap.getWidth() * factor),
                            Math.round(bitmap.getHeight() * factor), true);
                    return tryAllStrategies(bigger);
                } catch (Throwable ignored) {
                    return null;
                } finally {
                    if (bigger != null && bigger != bitmap) bigger.recycle();
                }
            }
            return null;
        } finally {
            bitmap.recycle();
        }
    }

    private static String tryAllStrategies(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        LuminanceSource source = new RGBLuminanceSource(width, height, pixels);

        String text = attempt(source);
        if (text != null) return text;

        // Terminal QR codes are usually white modules on a black background;
        // ZXing expects the opposite, so try the inverted image too.
        text = attempt(source.invert());
        if (text != null) return text;

        // Crop to the centre: gallery shots often frame the code loosely, and
        // trimming the surroundings raises the contrast ZXing sees.
        if (width > 200 && height > 200) {
            int cropX = width / 6;
            int cropY = height / 6;
            try {
                LuminanceSource cropped = source.crop(cropX, cropY, width - cropX * 2, height - cropY * 2);
                text = attempt(cropped);
                if (text != null) return text;
                text = attempt(cropped.invert());
                if (text != null) return text;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String attempt(LuminanceSource source) {
        String text = decodeWith(new BinaryBitmap(new HybridBinarizer(source)));
        if (text != null) return text;
        // Hybrid assumes a photo with uneven lighting; a flat screenshot decodes
        // more reliably with a global threshold.
        return decodeWith(new BinaryBitmap(new GlobalHistogramBinarizer(source)));
    }

    private static String decodeWith(BinaryBitmap bitmap) {
        MultiFormatReader reader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        reader.setHints(hints);

        try {
            Result result = reader.decodeWithState(bitmap);
            if (result != null && result.getText() != null && !result.getText().isEmpty()) {
                return result.getText();
            }
        } catch (Throwable ignored) {
        } finally {
            reader.reset();
        }
        return null;
    }

    /** Reads bounds first so a 50-megapixel photo never lands in memory whole. */
    private static Bitmap loadScaled(ContentResolver resolver, Uri uri) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        } catch (Throwable ignored) {
            return null;
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        int longEdge = Math.max(bounds.outWidth, bounds.outHeight);
        while (longEdge / sample > MAX_DIMENSION) {
            sample *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        try (InputStream in = resolver.openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, options);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
