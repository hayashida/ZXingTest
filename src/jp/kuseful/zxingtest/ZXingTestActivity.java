package jp.kuseful.zxingtest;

import java.io.IOException;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.android.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

public class ZXingTestActivity extends Activity
	implements SurfaceHolder.Callback, Camera.PreviewCallback, Camera.AutoFocusCallback {
	
	private static final String TAG = "ZXingTest";
	
	private static final int MIN_PREVIEW_PIXCELS = 320 * 240;
	private static final int MAX_PREVIEW_PIXCELS = 800 * 480;

	private Camera myCamera;
	private SurfaceView surfaceView;
	
	private Boolean hasSurface;	
	private Boolean initialized;
	
	private Point screenPoint;
	private Point previewPoint;
	
    /** lifecycle */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        hasSurface = false;
        initialized = false;
        
        setContentView(R.layout.main);
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	
    	surfaceView = (SurfaceView)findViewById(R.id.preview_view);
    	SurfaceHolder holder = surfaceView.getHolder();
    	if (hasSurface) {
    		initCamera(holder);
    	} else {
    		holder.addCallback(this);
    		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    	}
    }
    
    @Override
    protected void onPause() {
    	closeCamera();
    	if (!hasSurface) {
    		SurfaceHolder holder = surfaceView.getHolder();
    		holder.removeCallback(this);
    	}
    	super.onPause();
    }

    /** SurfaceHolder.Callback */
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

	}

	/** Camera.AutoFocusCallback */
	@Override
	public void onAutoFocus(boolean success, Camera camera) {
		if (success)
			camera.setOneShotPreviewCallback(this);
	}
	
	/** devices */
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (myCamera != null) {
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				Camera.Parameters parameters = myCamera.getParameters();
				if (!parameters.getFocusMode().equals(Camera.Parameters.FOCUS_MODE_FIXED)) {
					myCamera.autoFocus(this);
				}
			}
		}
		return true;
	}

	/** Camera.PreviewCallback */
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		View centerView = (View)findViewById(R.id.center_view);
		
		int left = centerView.getLeft() * previewPoint.x / screenPoint.x;
		int top = centerView.getTop() * previewPoint.y / screenPoint.y;
		int width = centerView.getWidth() * previewPoint.x / screenPoint.x;
		int height = centerView.getHeight() * previewPoint.y / screenPoint.y;
		
		PlanarYUVLuminanceSource source	= new PlanarYUVLuminanceSource(
													data,
													previewPoint.x,
													previewPoint.y,
													left,
													top,
													width,
													height,
													false);
		
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
		MultiFormatReader reader = new MultiFormatReader();
		try {
			Result result = reader.decode(bitmap);
			Toast.makeText(this, result.getText(), Toast.LENGTH_LONG).show();
		} catch (Exception e) {
			Toast.makeText(this, "error: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}
	
	/**
	 * カメラ情報を初期化
	 * @param holder
	 */
	private void initCamera(SurfaceHolder holder) {
    	try {
    		openCamera(holder);
    	} catch (Exception e) {
    		Log.w(TAG, e);
    	}
	}
	
	private void openCamera(SurfaceHolder holder) throws IOException {
		if (myCamera == null) {
			myCamera = Camera.open();
			if (myCamera == null) {
				throw new IOException();
			}
		}
		myCamera.setPreviewDisplay(holder);
		
		if (!initialized) {
			initialized = true;
			initFromCameraParameters(myCamera);
		}
		
		setCameraParameters(myCamera);
		myCamera.startPreview();
	}
	
	/**
	 * カメラ情報を破棄
	 */
	private void closeCamera() {
		if (myCamera != null) {
			myCamera.stopPreview();
			myCamera.release();
			myCamera = null;
		}
	}
	
	/**
	 * カメラ情報を設定
	 * @param camera
	 */
	private void setCameraParameters(Camera camera) {
		Camera.Parameters parameters = camera.getParameters();
		
		parameters.setPreviewSize(previewPoint.x, previewPoint.y);
		camera.setParameters(parameters);
	}
	
	/**
	 * カメラのプレビューサイズ・画面サイズを設定
	 * @param camera
	 */
	private void initFromCameraParameters(Camera camera) {
		Camera.Parameters parameters = camera.getParameters();
		WindowManager manager = (WindowManager)getApplication().getSystemService(Context.WINDOW_SERVICE);
		Display display = manager.getDefaultDisplay();
		int width = display.getWidth();
		int height = display.getHeight();
		
		if (width < height) {
			int tmp = width;
			width = height;
			height = tmp;
		}
		
		screenPoint = new Point(width, height);
		Log.d(TAG, "screenPoint = " + screenPoint);
		previewPoint = findPreviewPoint(parameters, screenPoint, false);
		Log.d(TAG, "previewPoint = " + previewPoint);
	}
	
	/**
	 * 最適なプレビューサイズを設定
	 * @param parameters
	 * @param screenPoint
	 * @param portrait
	 * @return
	 */
	private Point findPreviewPoint(Camera.Parameters parameters, Point screenPoint, boolean portrait) {
		Point previewPoint = null;
		int diff = Integer.MAX_VALUE;
		
		for (Camera.Size supportPreviewSize : parameters.getSupportedPreviewSizes()) {
			int pixels = supportPreviewSize.width * supportPreviewSize.height;
			if (pixels < MIN_PREVIEW_PIXCELS || pixels > MAX_PREVIEW_PIXCELS) {
				continue;
			}
			
			int supportedWidth = portrait ? supportPreviewSize.height : supportPreviewSize.width;
			int supportedHeight = portrait ? supportPreviewSize.width : supportPreviewSize.height;
			int newDiff = Math.abs(screenPoint.x * supportedHeight - supportedWidth * screenPoint.y);
			
			if (newDiff == 0) {
				previewPoint = new Point(supportedWidth, supportedHeight);
				break;
			}
			
			if (newDiff < diff) {
				previewPoint = new Point(supportedWidth, supportedHeight);
				diff = newDiff;
			}
		}
		if (previewPoint == null) {
			Camera.Size defaultPreviewSize = parameters.getPreviewSize();
			previewPoint = new Point(defaultPreviewSize.width, defaultPreviewSize.height);
		}
		
		return previewPoint;
	}
}