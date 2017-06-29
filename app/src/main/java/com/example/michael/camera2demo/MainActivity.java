package com.example.michael.camera2demo;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends Activity{

    private final String TAG = "MainActivity";

    private SurfaceView mSurfaceView;
    private ImageView imagePreview;
    private SurfaceHolder mSurfaceHolder;
    private CameraManager mCameraManager;
    private Handler mainHandler, childHandler;
    private String mCameraId;
    private ImageReader mImageReader;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraDevice mCameraDevice;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    ///为了使照片竖直显示
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void initView(){
        mSurfaceView = findViewById(R.id.surface_view_activity);
        mSurfaceView.setOnClickListener(new MyOnClickListener());
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.setKeepScreenOn(true);

        imagePreview = findViewById(R.id.image_view_preview);

        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                initCamera2();
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                if (null != mCameraDevice){
                    mCameraDevice.close();
                    MainActivity.this.mCameraDevice = null;
                }
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void initCamera2(){
        HandlerThread handlerThread = new HandlerThread("Camera2");
        handlerThread.start();
        childHandler = new Handler(handlerThread.getLooper());
        mainHandler = new Handler(getMainLooper());

        mCameraId = "" + CameraCharacteristics.LENS_FACING_FRONT;
        mImageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1);
        mImageReader.setOnImageAvailableListener(new OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                mCameraDevice.close();
                Image image = imageReader.acquireNextImage();
                ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[byteBuffer.remaining()];
                if (null != bytes){
                    save2SDCard(bytes);
                }
                byteBuffer.get(bytes);
                final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (null != bitmap){
                    imagePreview.setImageBitmap(bitmap);
                    imagePreview.setVisibility(View.VISIBLE);
                }

            }
        }, mainHandler);

        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try{
            mCameraManager.openCamera(mCameraId, stateCallback, mainHandler);
        } catch (CameraAccessException e){
            e.printStackTrace();
        }

    }

    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback(){

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            if (null != mCameraDevice){
                mCameraDevice.close();
                MainActivity.this.mCameraDevice = null;
            }
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            Toast.makeText(MainActivity.this, "摄像头不开心。。。sorry!", Toast.LENGTH_SHORT).show();
        }
    };

    private void startPreview(){
        try {
            final CaptureRequest.Builder previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(mSurfaceHolder.getSurface());
            mCameraDevice.createCaptureSession(Arrays.asList(mSurfaceHolder.getSurface(), mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (null == mCameraDevice){
                        return;
                    }
                    mCameraCaptureSession = cameraCaptureSession;

                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                    CaptureRequest previewRequest = previewRequestBuilder.build();
                    try {
                        mCameraCaptureSession.setRepeatingRequest(previewRequest, null, childHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "配置失败", Toast.LENGTH_SHORT).show();
                }
            }, childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    public class MyOnClickListener implements View.OnClickListener{
        @Override
        public void onClick(View view) {
            takePicture();
        }
    }


    private void exist(int millis){
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
               MainActivity.this.finish();
            }
        }, millis);
    }

    private void takePicture(){
        if ( null == mCameraDevice ){
            return;
        }
        final CaptureRequest.Builder captureBuilder;
        try {
            captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            CaptureRequest mCaptureRequest = captureBuilder.build();
            mCameraCaptureSession.capture(mCaptureRequest, null, childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void save2SDCard(byte[] data){
        File mediaStorageDir =
                new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Camera2Demo");
        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("Camera2Demo", "failed to create directory");
                return;
            }
        }

        File pictureFile = new File(mediaStorageDir.getPath()+File.separator+"IMG_"+System.currentTimeMillis()+".jpeg");
        Log.i(TAG, pictureFile.getAbsolutePath());

        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            fos.write(data);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }
    }

}
