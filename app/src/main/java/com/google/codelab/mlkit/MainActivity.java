// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.codelab.mlkit;

import android.Manifest;
import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.YuvImage;
import android.graphics.Rect;
import android.hardware.SensorListener;
import android.hardware.camera2.CameraCharacteristics;
import android.media.Image;
import android.nfc.Tag;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.os.PowerManager;
import android.util.Pair;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
//import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
//import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class MainActivity extends AppCompatActivity implements SensorEventListener{
        //AdapterView.OnItemSelectedListener
    private static final String TAG = "MainActivity";
    private int REQUEST_CODE_FOR_PERMISSIONS = 1234;;
    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };
    static ImageView mVideoView;
    private PreviewView mPreviewView;
    private GraphicOverlay mGraphicOverlay;
    //private Bitmap mSelectedImage;
    private Integer mImageMaxWidth, mImageMaxHeight;
    private SensorManager mSensorManagerP, mSensorManagerA, mSensorManagerO;
    private Sensor mProximity, mAccelerometer, mOrientation;
    private TextView mAccelerometerInfo, mProximityInfo, mOrientationInfo, mEyeSightInfo;
    //private PowerManager mPowerManager;
    //private PowerManager.WakeLock mWakeLock;

    /*** For CameraX ***/
    private Camera camera = null;
    private Preview preview = null;
    private ImageAnalysis imageAnalysis = null;
    private ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();

    static {
        System.loadLibrary("opencv_java4");
    }

    /**
     * Number of results to show in the UI.
     */
    //private static final int RESULTS_TO_SHOW = 3;
    /**
     * Dimensions of inputs.
     */
    //private static final int DIM_BATCH_SIZE = 1;
    //private static final int DIM_PIXEL_SIZE = 3;
    //private static final int DIM_IMG_SIZE_X = 224;
    //private static final int DIM_IMG_SIZE_Y = 224;
    //private final PriorityQueue<Map.Entry<String, Float>> sortedLabels = new PriorityQueue<>(RESULTS_TO_SHOW, new Comparator<Map.Entry<String, Float>>() {@Override public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {return (o1.getValue()).compareTo(o2.getValue());}});

    private Timer timer;
    private VideoTask videoTask;

    @SuppressLint({"InvalidWakeLockTag", "MissingInflatedId"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mVideoView = findViewById(R.id.videoView);
        mPreviewView = findViewById(R.id.previewView);
        //mPreviewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        mGraphicOverlay = findViewById(R.id.graphicOverlay);

        mSensorManagerP = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorManagerA = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorManagerO = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        mProximity = mSensorManagerP.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mAccelerometer = mSensorManagerA.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        //mOrientation = mSensorManagerO.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        mProximityInfo = findViewById(R.id.textProximity);
        mAccelerometerInfo = findViewById(R.id.textLinearAcceleration);
        //mOrientationInfo = findViewById(R.id.textOrientation);

        mEyeSightInfo = findViewById(R.id.textEyeSight);

        //mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        //mWakeLock = mPowerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "WakeLock");

        if (checkPermissions()) {
            Log.i(TAG, "[OnCreate] Get permissions!");
            startCamera();
        } else {
            Log.i(TAG, "[OnCreate] Request permissions");
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_FOR_PERMISSIONS);
        }

        videoTask = new VideoTask(this);
        timer = new Timer();
        timer.scheduleAtFixedRate(videoTask, 1000, 15);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStop() {
        if(videoTask!=null)
        {
            videoTask.close();
            videoTask = null;
        }
        if(timer!=null)
        {
            timer.cancel();
            timer = null;
        }
        super.onStop();
    }
    //private void faceContourDetection(@NonNull Bitmap bitMap) {
    private void faceContourDetection(@NonNull InputImage image) {
        //InputImage image = InputImage.fromBitmap(bitMap, 0);
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                        .build();

        FaceDetector detector = FaceDetection.getClient(options);
        detector.process(image)
                .addOnSuccessListener(
                        new OnSuccessListener<List<Face>>() {
                            @Override
                            public void onSuccess(List<Face> faces) {
                                Log.i(TAG, "[faceContourDetection] Success faceContourDetection!");
                                processFaceContourDetectionResult(faces);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.i(TAG, "[faceContourDetection] Fail faceContourDetection!");
                                e.printStackTrace();
                            }
                        });

    }

    private void processFaceContourDetectionResult(List<Face> faces) {
        // Task completed successfully
        if (faces.size() == 0) {
            //showToast("No face found");
            //Log.i(TAG, "[processFaceContourDetectionResult] No face found...");
            return;
        }
        mGraphicOverlay.clear();
        for (int i = 0; i < faces.size(); ++i) {
            Face face = faces.get(i);
            FaceContourGraphic faceGraphic = new FaceContourGraphic(mGraphicOverlay);
            mGraphicOverlay.setCameraInfo(getImageMaxWidth(), getImageMaxHeight(), CameraCharacteristics.LENS_FACING_FRONT);
            mGraphicOverlay.add(faceGraphic);
            Log.i(TAG, "[onCreate_mPreviewView]: " + getImageMaxWidth() + ", " + getImageMaxHeight());
            Log.i(TAG, "[onCreate_mGraphicOverlay_after]: " + mGraphicOverlay.getWidth() + ", " + mGraphicOverlay.getHeight());
            faceGraphic.updateFace(face);
            //Log.i(TAG, ""+face.getHeadEulerAngleX()+","+face.getHeadEulerAngleY()+","+face.getHeadEulerAngleZ());
            judgeEyeSight(face.getHeadEulerAngleY());
        }
    }

    private void judgeEyeSight(float y){
        if (y < 20 && y > -20){
            mEyeSightInfo.setText("EyeSight: " + y + " :: Watching now!!");
            mEyeSightInfo.setTextColor(Color.RED);
        }else{
            mEyeSightInfo.setText("EyeSight: " + y + " :: Not watching now..");
            mEyeSightInfo.setTextColor(Color.BLUE);
        }
    }

    private void showToast(String message) {
        showToast(message,0,0);
    }

    private void showToast(String message, int posX, int posY) {
        Toast t = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
        t.setGravity(Gravity.TOP|Gravity.LEFT, posX, posY);
        t.show();
    }

    // Functions for loading images from app assets.

    // Returns max image width, always for portrait mode. Caller needs to swap width / height for landscape mode.
    private Integer getImageMaxWidth() {
        if (mImageMaxWidth == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to
            // wait for
            // a UI layout pass to get the right values. So delay it to first time image
            // rendering time.
            mImageMaxWidth = mPreviewView.getWidth();
        }
        return mImageMaxWidth;
    }

    // Returns max image height, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    private Integer getImageMaxHeight() {
        if (mImageMaxHeight == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to
            // wait for
            // a UI layout pass to get the right values. So delay it to first time image
            // rendering time.
            mImageMaxHeight =
                    mPreviewView.getHeight();
        }
        return mImageMaxHeight;
    }

    // Gets the targeted width / height.
    /*private Pair<Integer, Integer> getTargetedWidthHeight() {
        int targetWidth;
        int targetHeight;
        int maxWidthForPortraitMode = getImageMaxWidth();
        int maxHeightForPortraitMode = getImageMaxHeight();
        targetWidth = maxWidthForPortraitMode;
        targetHeight = maxHeightForPortraitMode;
        return new Pair<>(targetWidth, targetHeight);
    }*/

    /*public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
        mGraphicOverlay.clear();
        mSelectedImage = getBitmapFromAsset(this, "grace_hopper.jpg");
        if (mSelectedImage != null) {
            // Get the dimensions of the View
            Pair<Integer, Integer> targetedSize = getTargetedWidthHeight();

            int targetWidth = targetedSize.first;
            int maxHeight = targetedSize.second;

            // Determine how much to scale down the image
            float scaleFactor =
                    Math.max(
                            (float) mSelectedImage.getWidth() / (float) targetWidth,
                            (float) mSelectedImage.getHeight() / (float) maxHeight);

            Bitmap resizedBitmap =
                    Bitmap.createScaledBitmap(
                            mSelectedImage,
                            (int) (mSelectedImage.getWidth() / scaleFactor),
                            (int) (mSelectedImage.getHeight() / scaleFactor),
                            true);

            mImageView.setImageBitmap(resizedBitmap);
            mSelectedImage = resizedBitmap;
        }
    }*/

    //@Override
    /*public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing
    }*/

    private Bitmap getBitmapFromAsset(String strName)
    {
        AssetManager assetManager = getAssets();
        InputStream istr = null;
        try {
            istr = assetManager.open(strName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Bitmap bitmap = BitmapFactory.decodeStream(istr);
        return bitmap;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManagerP.registerListener((SensorEventListener) this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManagerA.registerListener((SensorEventListener) this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManagerO.registerListener((SensorEventListener) this, mOrientation, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManagerP.unregisterListener((SensorListener) this);
        mSensorManagerA.unregisterListener((SensorListener) this);
        mSensorManagerO.unregisterListener((SensorListener) this);
    }

    private static String arrayToString( String str, float[] values ) {
        for(float dv : values)
        {
            int iv = (int)(dv * 100.0f);
            str += " " + (iv / 100) + "." + (iv % 100);
        }
        return str;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            //event.values[0]=0 or 5.000025.
            if (event.values[0] > 2.0) {
                mProximityInfo.setText("PRO: far " + Arrays.toString(event.values));
                /*if (mWakeLock.isHeld()) {
                    //mWakeLock.release();
                }*/
            } else{
                mProximityInfo.setText("PRO: near " + Arrays.toString(event.values));
                /*if (!mWakeLock.isHeld()) {
                    //mWakeLock.acquire();
                }*/
            }
        }else if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            mAccelerometerInfo.setText(arrayToString("ACC:", event.values));
        }/*else if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            mOrientationInfo.setText(arrayToString("ORI: ",event.values));
        }*/
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        Context context = this;
        Log.i(TAG, "[startCamera] Start!");
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    preview = new Preview.Builder().build();
                    imageAnalysis = new ImageAnalysis.Builder().build();
                    imageAnalysis.setAnalyzer(cameraExecutor, new MyFaceImageAnalyzer());
                    CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();
                    cameraProvider.unbindAll();
                    camera = cameraProvider.bindToLifecycle((LifecycleOwner)context, cameraSelector, preview, imageAnalysis);
                    //Log.i(TAG, "[startCamera_cameraInfo]" + );
                    //camera = cameraProvider.bindToLifecycle((LifecycleOwner)context, cameraSelector, preview);
                    preview.setSurfaceProvider(mPreviewView.createSurfaceProvider(camera.getCameraInfo()));
                } catch(Exception e) {
                    Log.e(TAG, "[startCamera] Use case binding failed", e);
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private class MyAbsImageAnalyzer implements ImageAnalysis.Analyzer {
        private Mat matPrevious = null;

        @Override
        public void analyze(@NonNull ImageProxy image) {
            Mat matOrg = getMatFromImage(image);

            Mat mat = fixMatRotation(matOrg);

            Mat matOutput = new Mat(mat.rows(), mat.cols(), mat.type());
            if (matPrevious == null) {
                matPrevious = mat;
            }
            Core.absdiff(mat, matPrevious, matOutput);
            matPrevious = mat;

            Bitmap bitmap = Bitmap.createBitmap(matOutput.cols(), matOutput.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(matOutput, bitmap);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //mImageView.setImageBitmap(bitmap);
                    //mImageView.setImageBitmap(VideoTask.bitmap);
                }
            });

            image.close();
        }

        private Mat getMatFromImage(ImageProxy image) {
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();
            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();
            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);
            Mat yuv = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth(), CvType.CV_8UC1);
            yuv.put(0, 0, nv21);
            Mat mat = new Mat();
            Imgproc.cvtColor(yuv, mat, Imgproc.COLOR_YUV2RGB_NV21, 3);
            return mat;
        }

        private Mat fixMatRotation(Mat matOrg) {
            Mat mat;
            switch (mPreviewView.getDisplay().getRotation()){
                default:
                case Surface.ROTATION_0:
                    mat = new Mat(matOrg.cols(), matOrg.rows(), matOrg.type());
                    Core.transpose(matOrg, mat);
                    Core.flip(mat, mat, 1);
                    break;
                case Surface.ROTATION_90:
                    mat = matOrg;
                    break;
                case Surface.ROTATION_270:
                    mat = matOrg;
                    Core.flip(mat, mat, -1);
                    break;
            }
            return mat;
        }
    }

    private class MyFaceImageAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            /*Bitmap bm = toBitmap(imageProxy);
            Bitmap bm = getBitmapFromAsset("grace_hopper.jpg");
            if (bm != null) {
                // Get the dimensions of the View
                Pair<Integer, Integer> targetedSize = getTargetedWidthHeight();

                int targetWidth = targetedSize.first;
                int maxHeight = targetedSize.second;

                // Determine how much to scale down the image
                float scaleFactor =
                        Math.max(
                                (float) bm.getWidth() / (float) targetWidth,
                                (float) bm.getHeight() / (float) maxHeight);

                Bitmap resizedBitmap =
                        Bitmap.createScaledBitmap(
                                bm,
                                (int) (bm.getWidth() / scaleFactor),
                                (int) (bm.getHeight() / scaleFactor),
                                true);

                //bm.setImageBitmap(resizedBitmap);
                bm = resizedBitmap;
            }
            faceContourDetection(bm);*/
            InputImage image;
            @SuppressLint("UnsafeExperimentalUsageError") Image mediaImage = imageProxy.getImage();
            if (mediaImage != null) {
                image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            }else{
                Bitmap bm = getBitmapFromAsset("grace_hopper.jpg");
                image = InputImage.fromBitmap(bm, 0);;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    faceContourDetection(image);
                    //Bitmap bm = toBitmap(imageProxy);
                    //mVideoView.setImageBitmap(bm);
                }
            });
            try {
                Thread.sleep(500);
                imageProxy.close();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        /*private Bitmap toBitmap(ImageProxy image) {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            //U and V are swapped
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

            byte[] imageBytes = out.toByteArray();
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        }*/

        /*private Bitmap YUV420_888toBitmap(ImageProxy image){
            int w = image.getWidth();
            int h = image.getHeight();

            ImageProxy.PlaneProxy[] planes = image.getPlanes();

            ImageProxy.PlaneProxy yplane = planes[0];
            ImageProxy.PlaneProxy uplane = planes[1];
            ImageProxy.PlaneProxy vplane = planes[2];

            ByteBuffer ybuf = yplane.getBuffer();
            ByteBuffer ubuf = uplane.getBuffer();
            ByteBuffer vbuf = vplane.getBuffer();

            int uvx = vplane.getPixelStride();
            int uvw = vplane.getRowStride();

            int[] bmp = new int[w * h];
            int[] col = new int[(uvw / uvx) * 3];

            int bp = 0;
            for(int dy = 0;dy < h;++dy)
            {
                //4ピクセルブロック単位なので隔行、隔ピクセルで計算処理したものを4回使いまわす
                if((dy & 1) == 0)
                {
                    int uvp = 0;
                    int idx = uvw * (dy>>1);
                    for(int dx = 0;dx < uvw;dx += uvx)
                    {
                        vbuf.position(idx + dx);
                        ubuf.position(idx + dx);

                        int v = vbuf.get();
                        int u = ubuf.get();

                        v = (v & 0xFF) - 128; //0xFFでANDしないと暗黙Intでなのか変な値になる。
                        u = (u & 0xFF) - 128;

                        col[uvp++] = (int)(1634 * v);
                        col[uvp++] = -(int)((833 * v) + (400 * u));
                        col[uvp++] = (int)(2066 * u);
                    }
                }

                //輝度情報は全pixel分あるので、そこに上であらかじめ計算しているカラーを乗せてBitmap化していく
                int p = 0;
                for(int dx = 0;dx < w;++dx)
                {
                    int y = 1192 * ((ybuf.get() & 0xFF) - 16);

                    int r = (y + col[p]);
                    int g = (y + col[p+1]);
                    int b = (y + col[p+2]);

                    r = r < 0 ? 0 : r > 262143 ? 262143 : r;
                    g = g < 0 ? 0 : g > 262143 ? 262143 : g;
                    b = b < 0 ? 0 : b > 262143 ? 262143 : b;
                    r >>= 10;
                    g >>= 10;
                    b >>= 10;

                    bmp[bp++] = (0xFF000000 | ((r&0xFF)<<16) | ((g&0xFF)<<8) | ((b&0xFF)<<0));
                    if((dx & 1) == 1) p += 3;
                }
            }

            return Bitmap.createBitmap(bmp, w , h, Bitmap.Config.ARGB_8888);
        }*/

    }

    private boolean checkPermissions(){
        for(String permission : REQUIRED_PERMISSIONS){
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                Log.i(TAG, "[checkPermissions] Fail: " + permission);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == REQUEST_CODE_FOR_PERMISSIONS){
            if(checkPermissions()){
                Log.i(TAG, "[onRequestPermissionsResult] Get permissions!");
                startCamera();
            } else{
                Log.i(TAG, "[onRequestPermissionsResult] Failed to get permissions");
                //ActivityCompat.requestPermissions( this, REQUIRED_PERMISSIONS, REQUEST_CODE_FOR_PERMISSIONS);
                this.finish();
            }
        }
    }
}
