package com.vell.vins;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SlamRecorder {
    private static final String TAG = "SlamRecorder";
    private volatile boolean isRecording = false;
    private File recordRootDir = new File(System.getProperty("java.io.tmpdir", "/data/local/tmp"), "0_vins_record");
    private Context exportContext;
    private File recordDir;
    private File imageSaveDir;
    private FileWriter frameTxtWriter;
    private FileWriter imuTxtWriter;
    private FileWriter gpsTxtWriter;
    private HandlerThread threadHandler;
    private Handler recordHandler;

    public void setRecordRootDir(File recordRootDir) {
        this.recordRootDir = recordRootDir;
    }

    public File getRecordRootDir() {
        return recordRootDir;
    }

    public void setExportContext(Context context) {
        exportContext = context;
    }

    public void startRecord() {
        if (isRecording || recordHandler != null) {
            return;
        }
        recordDir = new File(recordRootDir,
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()));
        if (!recordDir.exists() && !recordDir.mkdirs()) {
            return;
        }
        imageSaveDir = new File(recordDir, "image");
        if (!imageSaveDir.exists() && !imageSaveDir.mkdirs()) {
            return;
        }
        try {
            frameTxtWriter = new FileWriter(new File(recordDir, "frame.txt"));
            imuTxtWriter = new FileWriter(new File(recordDir, "imu.txt"));
            gpsTxtWriter = new FileWriter(new File(recordDir, "gps.txt"));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        threadHandler = new HandlerThread("VINSRecordThread");
        threadHandler.start();
        recordHandler = new Handler(threadHandler.getLooper());
        isRecording = true;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void stopRecord() {
        if (!isRecording) {
            return;
        }
        isRecording = false;
        final File completedRecordDir = recordDir;
        final HandlerThread completedThread = threadHandler;
        recordHandler.post(() -> {
            closeWriters();
            exportRecording(completedRecordDir);
            completedThread.quitSafely();
            recordDir = null;
            imageSaveDir = null;
            threadHandler = null;
            recordHandler = null;
        });
    }

    private void closeWriters() {
        try {
            if (frameTxtWriter != null) {
                frameTxtWriter.flush();
                frameTxtWriter.close();
                frameTxtWriter = null;
            }
            if (imuTxtWriter != null) {
                imuTxtWriter.flush();
                imuTxtWriter.close();
                imuTxtWriter = null;
            }
            if (gpsTxtWriter != null) {
                gpsTxtWriter.flush();
                gpsTxtWriter.close();
                gpsTxtWriter = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to close recording files", e);
        }
    }

    private void exportRecording(File sourceDir) {
        if (exportContext == null || sourceDir == null || !sourceDir.isDirectory()) {
            Log.e(TAG, "Recording export skipped: invalid context or source directory");
            return;
        }
        String zipName = "VINS_" + sourceDir.getName() + ".zip";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, zipName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/VINS");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }

        Uri uri = exportContext.getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            Log.e(TAG, "Unable to create Downloads entry for " + zipName);
            return;
        }
        try (OutputStream output = exportContext.getContentResolver().openOutputStream(uri)) {
            if (output == null) {
                throw new IOException("MediaStore returned a null output stream");
            }
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
            addToZip(zip, sourceDir, sourceDir.getName());
            zip.finish();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues published = new ContentValues();
                published.put(MediaStore.MediaColumns.IS_PENDING, 0);
                exportContext.getContentResolver().update(uri, published, null, null);
            }
            Log.i(TAG, "Recording exported to Downloads/VINS/" + zipName);
        } catch (IOException e) {
            exportContext.getContentResolver().delete(uri, null, null);
            Log.e(TAG, "Unable to export recording " + zipName, e);
        }
    }

    private void addToZip(ZipOutputStream zip, File file, String entryName) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addToZip(zip, child, entryName + "/" + child.getName());
                }
            }
            return;
        }
        zip.putNextEntry(new ZipEntry(entryName));
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                zip.write(buffer, 0, read);
            }
        }
        zip.closeEntry();
    }

    public void recvImu(final double timeSec, final double ax, final double ay, final double az, final double gx, final double gy, final double gz) {
        if (isRecording) {
            recordHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (imuTxtWriter == null) return;
                        imuTxtWriter.append(String.format(Locale.CHINA, "%.6f %.6f %.6f %.6f %.6f %.6f %.6f\n", timeSec, ax, ay, az, gx, gy, gz));
                        if (timeSec % 100 <= 10) {
                            imuTxtWriter.flush();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            });
        }
    }

    public void recvImage(final double timeSec, final Mat originMat) {
        if (isRecording) {
            final Mat bgrMat = new Mat();
            Imgproc.cvtColor(originMat, bgrMat, Imgproc.COLOR_RGB2BGR);
            recordHandler.post(new Runnable() {
                @Override
                public void run() {
                    String fileName = String.format(Locale.CHINA, "%.6f.jpg", timeSec);
                    File imageFile = new File(imageSaveDir, fileName);

                    try {
                        if (Imgcodecs.imwrite(imageFile.getAbsolutePath(), bgrMat)) {
                            try {
                                if (frameTxtWriter != null) {
                                    frameTxtWriter.append(String.format(Locale.CHINA, "%.6f\n", timeSec));
                                    frameTxtWriter.flush();
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    } finally {
                        bgrMat.release();
                    }

                }
            });
        }
    }

    public void recvGPS(final double timeSec, final double latitude, final double longitude, final double altitude,
                        final double posAccuracy) {
        if (isRecording) {
            recordHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (gpsTxtWriter == null) return;
                        gpsTxtWriter.append(String.format(Locale.CHINA, "%.6f %.6f %.6f %.6f %.6f\n", timeSec, latitude, longitude, altitude, posAccuracy));
                        gpsTxtWriter.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            });
        }
    }
}
