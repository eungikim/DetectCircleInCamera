
package com.example.cameratest;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Range;
import org.opencv.core.Rect;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Size;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.video.BackgroundSubtractor;
import org.opencv.video.Video;
import org.opencv.objdetect.HOGDescriptor;
import org.opencv.core.MatOfPoint2f;

import com.example.cameratest.BluetoothService;

public class MainActivity extends Activity implements CvCameraViewListener2 {
    private static final String  TAG              = "OCVSample::Activity";

    private Mat                  mRgba;
    private Mat                  mHsva;
    private Mat                  mLower_red_hue_range;
    private Mat                  mUpper_red_hue_range;
    private Mat                  mGreen_hue_range;
    private Mat                  red_hue_image;
    private Scalar               mBlobColorRgba;
    private Scalar               mBlobColorHsv;
    //private ColorBlobDetector    mDetector;
    private Mat                  mSpectrum;
    private Size                 SPECTRUM_SIZE;
    private Scalar               CONTOUR_COLOR;
    private int					 xwidth;
    private int					 yhight;
    private int					 x_SCREEN_CENTER;
    private int					 y_SCREEN_CENTER;

    List<MatOfPoint> contours;
    Mat mRed_circles;
    Mat mGreen_circles;
    double mCannyUpperThreshold = 100;
    double mAccumulator = 30;
    int mMinRadius = 0;
    int mMaxRadius = 400;
	Point[] rcenter;
	Point[] gcenter;
    
    private CameraViewModi mOpenCvCameraView;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    //mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public MainActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }
    
	//for BluetoothService
    private String message;
	private byte[] sendData;
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;
	private BluetoothService btService = null;
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
		}
	};
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        mOpenCvCameraView = (CameraViewModi) findViewById(R.id.color_blob_detection_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);

        contours = new ArrayList<MatOfPoint>(); 
        rcenter = new Point[5];
        gcenter = new Point[5];

		// BluetoothService 클래스 생성
		if (btService == null) {
			btService = new BluetoothService(this, mHandler);
		}
		sendData = new byte[1];
		// Bluetooth 승낙후 디바이스 검색
		if(btService.getDeviceState()) {
			// 블루투스가 지원 가능한 기기일 때
			btService.enableBluetooth();
		} else {
			finish();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        
        unregisterReceiver(mVolumReceiver);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        //볼륨버튼 리시버 연결
        IntentFilter filter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        registerReceiver(mVolumReceiver, filter);
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        btService.stop();
    }
    
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			
			
			return true;
		}
		return super.onOptionsItemSelected(item);
	}


    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mHsva = new Mat(height, width, CvType.CV_8UC4);
        mLower_red_hue_range = new Mat(height, width, CvType.CV_8UC4);
        mUpper_red_hue_range = new Mat(height, width, CvType.CV_8UC4);
        mGreen_hue_range	 = new Mat(height, width, CvType.CV_8UC4);
        red_hue_image = new Mat(height, width, CvType.CV_8UC4);
        mRed_circles = new Mat();
        mGreen_circles = new Mat();
        //mDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(255,0,0,255);
        x_SCREEN_CENTER = mOpenCvCameraView.getWidth() / 2;
        y_SCREEN_CENTER = mOpenCvCameraView.getHeight() / 2;
    }

    public void onCameraViewStopped() {
        mRgba.release();
        mHsva.release();
        mLower_red_hue_range.release();
        mUpper_red_hue_range.release();
        mGreen_hue_range.release();
        red_hue_image.release();
    }
    

	@Override
	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		mRgba = inputFrame.rgba();
        Imgproc.medianBlur(mRgba, mRgba, 3);
        //Convert input frame to HSV
        Imgproc.cvtColor(mRgba, mHsva, Imgproc.COLOR_BGR2HSV_FULL);
        //Threshold the HSV image, keep only the red pixels
        Core.inRange(mHsva, new Scalar(0, 90, 90), new Scalar(10, 255, 255), mLower_red_hue_range);
        Core.inRange(mHsva, new Scalar(160, 90, 90), new Scalar(179, 255, 255), mUpper_red_hue_range);
        //Threshold the HSV image, keep only the green pixels
        Core.inRange(mHsva, new Scalar(40, 90, 90), new Scalar(80, 255, 255), mGreen_hue_range);
        //Combine the above two red images
        Core.addWeighted(mLower_red_hue_range, 1.0, mUpper_red_hue_range, 1.0, 0.0, red_hue_image);
        Imgproc.GaussianBlur(red_hue_image, red_hue_image, new Size(3,3), 2, 2);
        Imgproc.GaussianBlur(mGreen_hue_range, mGreen_hue_range, new Size(3,3), 2, 2);
        //Use the Hough transform to detect circles in the combined threshold image
		Imgproc.HoughCircles(red_hue_image, mRed_circles, Imgproc.CV_HOUGH_GRADIENT, 1.0, mGreen_hue_range.rows() / 5, 
				mCannyUpperThreshold, mAccumulator, mMinRadius, mMaxRadius);
		Imgproc.HoughCircles(mGreen_hue_range, mGreen_circles, Imgproc.CV_HOUGH_GRADIENT, 1.0, mGreen_hue_range.rows() / 5, 
				mCannyUpperThreshold, mAccumulator, mMinRadius, mMaxRadius);
		//Log.w("circles", circles.cols()+", "+circles.rows());
        
        //Loop over all detected circles and outline them on the original image
		for (int x = 0; x < mRed_circles.cols() && x < 5; x++){
			double rCircle[] = mRed_circles.get(0, x);
			rcenter[x] = new Point(Math.round(rCircle[0]),Math.round(rCircle[1]));
		}
		for (int x = 0; x < mGreen_circles.cols() && x < 5; x++){
			double gCircle[] = mGreen_circles.get(0, x);
			gcenter[x] = new Point(Math.round(gCircle[0]),Math.round(gCircle[1]));
		}
		for (int x = 0; x < rcenter.length-1; x++){
			for (int y = 0; y < gcenter.length-1; y++){
				if(rcenter[x] != null && gcenter[y] != null){
					//judgment two circles center point distance under 15px
					double dis = distance(rcenter[x], gcenter[y]);
					if(dis < 15){
						// draw the circle center
						//Imgproc.circle(mRgba, rcenter[x], 3, new Scalar(255, 0, 0), -1, 8, 0);
						//Imgproc.circle(mRgba, rcenter[x], 3, new Scalar(0, 0, 255), -1, 8, 0);
						// draw the circle outline
						Imgproc.circle(mRgba, rcenter[x], 20, new Scalar(255, 0, 0), 3, 8, 0);
						Imgproc.circle(mRgba, gcenter[y], 10, new Scalar(0, 0, 255), 3, 8, 0);
						this.xwidth = (int) Math.round(rcenter[x].x);
						this.yhight	 = (int) Math.round(rcenter[x].y);
					}
				}
			}
		}
		rcenter = new Point[5];
		gcenter = new Point[5];
        //Loop over all detected circles and outline them on the original image
		/*
        for (int x = 0; x < circles.cols(); x++) 
        {
                double vCircle[] = circles.get(0,x);

                Point center = new Point(Math.round(vCircle[0]), Math.round(vCircle[1]));
                int radius = (int)Math.round(vCircle[2]);
                // draw the circle center
                Imgproc.circle(mRgba, center, 3, new Scalar(0,255,0), -1, 8, 0 );
                // draw the circle outline
                Imgproc.circle(mRgba, center, radius, new Scalar(0,0,255), 3, 8, 0 );
                if(x == 0){
                	center.x = this.xwidth;
                	center.y = this.yhight;
                }

        }*/
	  	
		/*
		for (int j = 0; j < contours.size(); j++) {
			if (Imgproc.contourArea(contours.get(j)) > 700) {
				Rect rect2 = Imgproc.boundingRect(contours.get(j));
				if (rect2.height > 100) {
					Imgproc.rectangle(realtime, new Point(rect2.x, rect2.y),
							new Point(rect2.x + rect2.width, rect2.y
									+ rect2.height), new Scalar(0, 255, 0));
					// Imgproc.rectangle(realtime, new Point(0,0), new
					// Point(400,200),new Scalar(0,255,0));

					xwidth = (rect2.x + rect2.width / 2);
					yhight = (rect2.y + rect2.height / 2);
				}
			}
		}
		*/
		
		if (xwidth > x_SCREEN_CENTER * (120 / 100))
		{
			
		
			message = "1";
			sendData = message.getBytes();
			btService.write(sendData);
			
			
		}
		
		if (xwidth < x_SCREEN_CENTER * (80 / 100))
		{

			message = "2";
			sendData = message.getBytes();
			btService.write(sendData);
		
			
		}
		
		
		
		if (yhight > y_SCREEN_CENTER * (120 / 100))
		{
			message = "3";
			sendData = message.getBytes();
			btService.write(sendData);
			
		}
		
		if (yhight < y_SCREEN_CENTER * (80 / 100))
		{
			
			
			message = "4";
			sendData = message.getBytes();
			btService.write(sendData);
			
		}
		

		//message = null;
		
		  /* for (int j = 1; j < 2 ; j++) {  
		
			   byte buff[] = new byte[b.rows() * b.cols()];
			   
			   
		       int top  = b.get(j, Imgproc.CC_STAT_HEIGHT, buff);
		      // int width = b.put(j, Imgproc.CC_STAT_WIDTH, buff);  
		      // int height  = b.put(row, col, data);
			   
			  int ileft = j+10;
			  // int top = 3;
			   int width = 300;
			   int height = 100;

	       Imgproc.rectangle(realtime, new Point( ileft , top), new Point(ileft+width,top+height) ,new Scalar(255,0,0));
	   
		   }*/
	   
		//Imgproc.connectedComponentsWithStats(findrec, current, a, b);
		//Imgproc.findContours(findrec, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
		
	
			//if (Imgproc.contourArea(contours.get(i)) < 1800 ){
				//Rect rect = Imgproc.boundingRect(contours.get(i));
				//if (rect.height > 50){
					//Imgproc.rectangle(realtime, new Point(rect.x,rect.y), new Point(rect.x+rect.width,rect.y+rect.height),new Scalar(255,0,0));
	               
					//Mat ROI = findrec.submat(rect.y, rect.y + rect.height, rect.x, rect.x + rect.width);
				
					/*Imgproc.findContours(ROI, labeling, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
				
					
					for(int j=0; j< labeling.size();j++){
						if (Imgproc.contourArea(labeling.get(j)) > 700 ){
							Rect rect2 = Imgproc.boundingRect(labeling.get(j));
							if (rect2.height > 100){
								Imgproc.rectangle(realtime, new Point(rect2.x,rect2.y), new Point(rect2.x+rect2.width,rect2.y+rect2.height),new Scalar(0,255,0));
							}

					}
				}*/
					// Imgproc.pyrMeanShiftFiltering(realtime, ROI, 1 , 2);  
	               //네모칸의 무게중심 계산
	               // int xwidth = 0;
	               // int yhight =0;
	                  
	               // xwidth = (rect.x + rect.width)/2;
	               // yhight = (rect.y + rect.width)/2;
	                //}
				//}
			//}
		//Rect rect = Imgproc.boundingRect(wrapper);
		//Imgproc.boundingRect(contours.get(32));
		//Imgproc.drawContours(findrec, contours, -1, new Scalar(255, 0, 0), 2);
		//rectangle( realtime, Point( 30, 60 ), Point( 70, 50), Scalar( 0, 55, 255 ), +1, 4 );
		
		//Imgproc.rectangle(realtime, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), Detect_Color, 5);
		//Imgproc.rectangle(realtime, contours, contours, new Scalar(0,255,255), 2)
		//mRgba = inputFrame.rgba();
		//최소인접사각형 
		//Imgproc.drawContours(realtime, contours, BIND_ABOVE_CLIENT,new Scalar(255,255,255));
		
		//contours = null;
		//labeling = null;
	return mRgba;
	

	}

    double distance(Point p1, Point p2) {
        double dist;
        dist = Math.sqrt((p1.x-p2.x)*(p1.x-p2.x) +(p1.y-p2.y)*(p1.y-p2.y));
        return dist;
   }
	

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(TAG, "onActivityResult " + resultCode);

		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE:
			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				btService.getDeviceInfo(data);
			}
			break;
		case REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK) {
				// Next Step
				btService.scanDevice();
			} else {
				Log.d(TAG, "Bluetooth is not enabled");
			}
			break;
		}
	}
	/* 기존의 버튼
	public void onClick(View v) {
		Log.d(TAG, "btn clicked");
		int viewId = v.getId();
		if (viewId == cBtn.getId()) {
			if(btService.getDeviceState()) {
				// 블루투스가 지원 가능한 기기일 때
				btService.enableBluetooth();
			} else {
				finish();
			}
		} else if (viewId == upImage.getId()) {
			sendData[0] = 1;
			btService.write(sendData); //<- 보내는 항수
		} else if (viewId == downImage.getId()) {
			sendData[0] = 2;
			btService.write(sendData);
		} else if (viewId == leftImage.getId()) {
			sendData[0] = 3;
			btService.write(sendData);
		} else if (viewId == rightImage.getId()) {
			sendData[0] = 4;
			btService.write(sendData);
		}
	}*/
	//BroadcastReceiver for Detection Volume Changing
    private final BroadcastReceiver mVolumReceiver = new BroadcastReceiver() {
    	boolean leach = true;
		@Override
		public void onReceive(Context context, Intent intent) {
			if (leach) {
				leach = false;
				String actionName = intent.getAction();
				
				if (actionName.equals("android.media.VOLUME_CHANGED_ACTION")) {
					Log.d("TAG", "equals action name");
					takePicture();
				}
			}//if leach
			else{
				leach = true;
			}
		}
	};
	void takePicture(){
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String currentDateandTime = sdf.format(new Date());
        String fileName = Environment.getExternalStorageDirectory().getPath() +
                               "/sample_picture_" + currentDateandTime + ".jpg";
        mOpenCvCameraView.takePicture(fileName);
        Toast.makeText(getApplicationContext(), fileName, Toast.LENGTH_SHORT).show();
	}
}

