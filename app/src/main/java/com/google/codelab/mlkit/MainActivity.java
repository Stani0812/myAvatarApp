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
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.SensorListener;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
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
import org.opencv.core.Rect;
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
    //private ImageView mImageView;
    private PreviewView mPreviewView;
    //private GraphicOverlay mGraphicOverlay;
    //private Bitmap mSelectedImage;
    //private Integer mImageMaxWidth, mImageMaxHeight;
    private SensorManager mSensorManagerP, mSensorManagerA, mSensorManagerO;
    private Sensor mProximity, mAccelerometer, mOrientation;
    private TextView mAccelerometerInfo, mProximityInfo, mOrientationInfo;
    //private PowerManager mPowerManager;
    //private PowerManager.WakeLock mWakeLock;

    /*** For CameraX ***/
    private Camera camera = null;
    private Preview preview = null;
    //private ImageAnalysis imageAnalysis = null;
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

    @SuppressLint({"InvalidWakeLockTag", "MissingInflatedId"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //mImageView = findViewById(R.id.imageView);
        mPreviewView = findViewById(R.id.previewView);
        mPreviewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        //mGraphicOverlay = findViewById(R.id.graphicOverlay);

        mSensorManagerP = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorManagerA = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorManagerO = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        mProximity = mSensorManagerP.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mAccelerometer = mSensorManagerA.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mOrientation = mSensorManagerO.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        mProximityInfo = findViewById(R.id.textProximity);
        mAccelerometerInfo = findViewById(R.id.textLinearAcceleration);
        mOrientationInfo = findViewById(R.id.textOrientation);

        //mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        //mWakeLock = mPowerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "WakeLock");

        if (checkPermissions()) {
            Log.i(TAG, "[OnCreate] Get permissions!");
            startCamera();
        } else {
            Log.i(TAG, "[OnCreate] Request permissions");
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_FOR_PERMISSIONS);
        }
    }

    /*private void runFaceContourDetection() {
        InputImage image = InputImage.fromBitmap(mSelectedImage, 0);
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                        .build();

        FaceDetector detector = FaceDetection.getClient(options);
        detector.process(image)
                .addOnSuccessListener(
                        new OnSuccessListener<List<Face>>() {
                            @Override
                            public void onSuccess(List<Face> faces) {
                                processFaceContourDetectionResult(faces);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                e.printStackTrace();
                            }
                        });

    }

    private void processFaceContourDetectionResult(List<Face> faces) {
        // Task completed successfully
        if (faces.size() == 0) {
            showToast("No face found");
            return;
        }
        mGraphicOverlay.clear();
        for (int i = 0; i < faces.size(); ++i) {
            Face face = faces.get(i);
            FaceContourGraphic faceGraphic = new FaceContourGraphic(mGraphicOverlay);
            mGraphicOverlay.add(faceGraphic);
            faceGraphic.updateFace(face);
        }
    }*/

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
    /*private Integer getImageMaxWidth() {
        if (mImageMaxWidth == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to
            // wait for
            // a UI layout pass to get the right values. So delay it to first time image
            // rendering time.
            mImageMaxWidth = mImageView.getWidth();
        }
        return mImageMaxWidth;
    }*/

    // Returns max image height, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    /*private Integer getImageMaxHeight() {
        if (mImageMaxHeight == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to
            // wait for
            // a UI layout pass to get the right values. So delay it to first time image
            // rendering time.
            mImageMaxHeight =
                    mImageView.getHeight();
        }
        return mImageMaxHeight;
    }*/

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

    /*public static Bitmap getBitmapFromAsset(Context context, String filePath) {
        AssetManager assetManager = context.getAssets();

        InputStream is;
        Bitmap bitmap = null;
        try {
            is = assetManager.open(filePath);
            bitmap = BitmapFactory.decodeStream(is);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return bitmap;
    }*/

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
        }else if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            mOrientationInfo.setText(arrayToString("ORI: ",event.values));
        }
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
                    //imageAnalysis = new ImageAnalysis.Builder().build();
                    //imageAnalysis.setAnalyzer(cameraExecutor, new MyImageAnalyzer());
                    CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();

                    //cameraProvider.unbindAll();
                    camera = cameraProvider.bindToLifecycle((LifecycleOwner)context, cameraSelector, preview
                                                            //, imageAnalysis
                                                            );
                    preview.setSurfaceProvider(mPreviewView.createSurfaceProvider(camera.getCameraInfo()));
                } catch(Exception e) {
                    Log.e(TAG, "[startCamera] Use case binding failed", e);
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /*private class MyImageAnalyzer implements ImageAnalysis.Analyzer {
        private Mat matPrevious = null;

        @Override
        public void analyze(@NonNull ImageProxy image) {
            Mat matOrg = getMatFromImage(image);

            Mat mat = fixMatRotation(matOrg);

            Log.i(TAG, "[analyze] width = " + image.getWidth() + ", height = " + image.getHeight() + "Rotation = " + mPreviewView.getDisplay().getRotation());
            Log.i(TAG, "[analyze] mat width = " + matOrg.cols() + ", mat height = " + matOrg.rows());

            Mat matOutput = new Mat(mat.rows(), mat.cols(), mat.type());
            if (matPrevious == null) matPrevious = mat;
            Core.absdiff(mat, matPrevious, matOutput);
            matPrevious = mat;

            Imgproc.rectangle(matOutput, new Rect(10, 10, 100, 100), new Scalar(255, 0, 0));
            Imgproc.putText(matOutput, "leftTop", new Point(10, 10), 1, 1, new Scalar(255, 0, 0));

            Bitmap bitmap = Bitmap.createBitmap(matOutput.cols(), matOutput.rows(),Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(matOutput, bitmap);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mImageView.setImageBitmap(bitmap);
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
    }*/

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
