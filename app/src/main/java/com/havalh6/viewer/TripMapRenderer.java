package com.havalh6.viewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

final class TripMapRenderer {
    static final String ATTRIBUTION = "© OpenStreetMap contributors";
    static final int HEIGHT = 400;
    private static final int HTTP_TIMEOUT_MS = 8000;
    private static final int MAX_ZOOM = 17;
    private static final int MIN_ZOOM = 3;
    private static final int PAD = 36;
    private static final String TAG = "H6Trip";
    private static final long TILE_MAX_AGE_MS = 2592000000L;
    private static final String TILE_URL = "https://tile.openstreetmap.org/%d/%d/%d.png";
    private static final String USER_AGENT = "HavalH6Viewer/1.0 (head-unit trip map)";
    static final int WIDTH = 640;
    private final File outDir;
    private final File tileCache;

    static final class Result {
        final File file;
        final String view;

        Result(File file, String view) {
            this.file = file;
            this.view = view;
        }
    }

    TripMapRenderer(Context context) {
        this.tileCache = new File(context.getCacheDir(), "osm-tiles");
        this.outDir = new File(context.getFilesDir(), "trip-maps");
    }

    private Bitmap tile(int i, int i2, int i3) {
        Bitmap bitmap;
        Bitmap bitmapDecodeFile;
        File file = new File(this.tileCache, i + File.separator + i2 + File.separator + i3 + ".png");
        if (file.isFile() && System.currentTimeMillis() - file.lastModified() < 2592000000L && (bitmapDecodeFile = BitmapFactory.decodeFile(file.getPath())) != null) {
            return bitmapDecodeFile;
        }
        HttpURLConnection httpURLConnection = null;
        try {
            try {
                Locale locale = Locale.US;
                Integer numValueOf = Integer.valueOf(i);
                Integer numValueOf2 = Integer.valueOf(i2);
                Integer numValueOf3 = Integer.valueOf(i3);
                Object[] objArr = new Object[3];
                bitmap = null;
                try {
                    objArr[0] = numValueOf;
                    objArr[1] = numValueOf2;
                    objArr[2] = numValueOf3;
                    HttpURLConnection httpURLConnection2 = (HttpURLConnection) new URL(String.format(locale, "https://tile.openstreetmap.org/%d/%d/%d.png", objArr)).openConnection();
                    httpURLConnection2.setRequestProperty("User-Agent", "HavalH6Viewer/1.0 (head-unit trip map)");
                    httpURLConnection2.setConnectTimeout(8000);
                    httpURLConnection2.setReadTimeout(8000);
                    if (httpURLConnection2.getResponseCode() != 200) {
                        Log.w("H6Trip", "tile " + i + "/" + i2 + "/" + i3 + " HTTP " + httpURLConnection2.getResponseCode());
                        if (httpURLConnection2 != null) {
                            httpURLConnection2.disconnect();
                        }
                        return null;
                    }
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    InputStream inputStream = httpURLConnection2.getInputStream();
                    try {
                        byte[] bArr = new byte[16384];
                        while (true) {
                            int i4 = inputStream.read(bArr);
                            if (i4 <= 0) {
                                break;
                            }
                            byteArrayOutputStream.write(bArr, 0, i4);
                        }
                        if (inputStream != null) {
                            inputStream.close();
                        }
                        byte[] byteArray = byteArrayOutputStream.toByteArray();
                        Bitmap bitmapDecodeByteArray = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
                        if (bitmapDecodeByteArray == null) {
                            if (httpURLConnection2 != null) {
                                httpURLConnection2.disconnect();
                            }
                            return null;
                        }
                        File parentFile = file.getParentFile();
                        if (parentFile != null && (parentFile.isDirectory() || parentFile.mkdirs())) {
                            try {
                                FileOutputStream fileOutputStream = new FileOutputStream(file);
                                try {
                                    fileOutputStream.write(byteArray);
                                    fileOutputStream.close();
                                } catch (Throwable th) {
                                    try {
                                        fileOutputStream.close();
                                        throw th;
                                    } catch (Throwable th2) {
                                        th.addSuppressed(th2);
                                        throw th;
                                    }
                                }
                            } catch (Exception e) {
                            }
                        }
                        if (httpURLConnection2 != null) {
                            httpURLConnection2.disconnect();
                        }
                        return bitmapDecodeByteArray;
                    } catch (Throwable th3) {
                        if (inputStream == null) {
                            throw th3;
                        }
                        try {
                            inputStream.close();
                            throw th3;
                        } catch (Throwable th4) {
                            th3.addSuppressed(th4);
                            throw th3;
                        }
                    }
                } catch (Exception e2) {
                    Log.w("H6Trip", "tile fetch failed: " + e2.getMessage());
                    if (0 != 0) {
                        httpURLConnection.disconnect();
                    }
                    return bitmap;
                }
            } catch (Throwable th5) {
                if (0 != 0) {
                    httpURLConnection.disconnect();
                }
                throw th5;
            }
        } catch (Exception e3) {
            Log.w("H6Trip", "tile fetch failed: " + e3.getMessage());
            return null;
        }
    }

    File fileFor(long j) {
        return new File(this.outDir, j + ".jpg");
    }

    /*
        Code decompiled incorrectly, please refer to instructions dump.
    */
    Result render(long j, double[] dArr, double[] dArr2) throws Exception {
        Bitmap bitmapCreateBitmap;
        int i;
        int i2;
        int iMin = Math.min(dArr.length, dArr2.length);
        Result result = null;
        if (iMin < 2) {
            return null;
        }
        double dMin = 90.0d;
        double dMax = -90.0d;
        double dMin2 = 180.0d;
        double dMax2 = -180.0d;
        int i3 = 0;
        while (i3 < iMin) {
            dMin = Math.min(dMin, dArr[i3]);
            dMax = Math.max(dMax, dArr[i3]);
            dMin2 = Math.min(dMin2, dArr2[i3]);
            dMax2 = Math.max(dMax2, dArr2[i3]);
            i3++;
        }
        Result result2 = result;
        int iFitZoom = TripMapMath.fitZoom(dMin, dMax, dMin2, dMax2, 640, 400, 36, 3, 17);
        double dWorldX = ((TripMapMath.worldX(dMin2, iFitZoom) + TripMapMath.worldX(dMax2, iFitZoom)) / 2.0d) - 320.0d;
        double dWorldY = ((TripMapMath.worldY(dMin, iFitZoom) + TripMapMath.worldY(dMax, iFitZoom)) / 2.0d) - 200.0d;
        bitmapCreateBitmap = Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapCreateBitmap);
        canvas.drawColor(-1513759);
        int i4 = 1 << iFitZoom;
        int i5 = iFitZoom;
        int iFloor = (int) Math.floor(dWorldX / 256.0d);
        int iFloor2 = (int) Math.floor(((dWorldX + 640.0d) - 1.0d) / 256.0d);
        int iFloor3 = (int) Math.floor(dWorldY / 256.0d);
        int iFloor4 = (int) Math.floor(((dWorldY + 400.0d) - 1.0d) / 256.0d);
        Paint paint = new Paint(2);
        int i6 = iFloor3;
        while (i6 <= iFloor4) {
            if (i6 < 0) {
                i = iFloor2;
                i2 = i4;
            } else if (i6 >= i4) {
                i = iFloor2;
                i2 = i4;
            } else {
                i2 = i4;
                int i7 = iFloor;
                while (i7 <= iFloor2) {
                    int i8 = iFloor2;
                    int i9 = i5;
                    int i10 = iFloor4;
                    int i11 = iMin;
                    Bitmap bitmapTile = tile(i9, ((i7 % i2) + i2) % i2, i6);
                    if (bitmapTile == null) {
                        bitmapCreateBitmap.recycle();
                        return result2;
                    }
                    canvas.drawBitmap(bitmapTile, (float) (((double) (i7 * 256)) - dWorldX), (float) (((double) (i6 * 256)) - dWorldY), paint);
                    bitmapTile.recycle();
                    i7++;
                    iMin = i11;
                    iFloor2 = i8;
                    iFloor4 = i10;
                    i5 = i9;
                }
                i = iFloor2;
            }
            i6++;
            i4 = i2;
            iFloor2 = i;
        }
        int i12 = iMin;
        int i13 = i5;
        Paint paint2 = new Paint(1);
        paint2.setTextSize(12.0f);
        paint2.setColor(-13421773);
        float fMeasureText = paint2.measureText("© OpenStreetMap contributors");
        Paint paint3 = new Paint();
        paint3.setColor(-855638017);
        canvas.drawRect((640.0f - fMeasureText) - 10.0f, 382.0f, 640.0f, 400.0f, paint3);
        canvas.drawText("© OpenStreetMap contributors", (640.0f - fMeasureText) - 5.0f, 395.0f, paint2);
        if (!this.outDir.isDirectory() && !this.outDir.mkdirs()) {
            throw new IllegalStateException("cannot create " + this.outDir);
        }
        File fileFileFor = fileFor(j);
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(fileFileFor);
            try {
                bitmapCreateBitmap.compress(Bitmap.CompressFormat.JPEG, 82, fileOutputStream);
                fileOutputStream.close();
                bitmapCreateBitmap.recycle();
                String str = String.format(Locale.US, "%d,%.3f,%.3f", Integer.valueOf(i13), Double.valueOf(dWorldX), Double.valueOf(dWorldY));
                Log.w("H6Trip", String.format(Locale.US, "trip map %d: z%d, %d points, %d bytes", Long.valueOf(j), Integer.valueOf(i13), Integer.valueOf(i12), Long.valueOf(fileFileFor.length())));
                return new Result(fileFileFor, str);
            } catch (Throwable th) {
                try {
                    fileOutputStream.close();
                    throw th;
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                    throw th;
                }
            }
        } catch (Throwable th3) {
            bitmapCreateBitmap.recycle();
            throw th3;
        }
    }
}
