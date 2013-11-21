package edu.uic.cs440;


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnInfoListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.h264.AvcConfigurationBox;

public class CamOMaticActivity extends Activity 
									   implements OnSharedPreferenceChangeListener{
	Utils.Loopback videoLoop = null;
	Utils.Loopback audioLoop = null;
	MediaRecorder videoRecorder = null;
	MediaRecorder audioRecorder = null;
	SurfaceHolder mHolder = null;
	SurfaceView mCameraView = null;
	Camera mCamera = null;
	
	static byte sps[] = null;
	static byte pps[] = null;
	
	Button btnStart = null;
	//Button btnStop = null;
	Button btnConfig = null;
	//Button btncfg = null;
	//EditText IpPort = null;
	//EditText portField = null;
	//EditText dirField = null;
	
	VideoPacketizer videoPacketizer = null;
	AudioPacketizer audioPacketizer = null;
	
	String ipAddress;
	int port;
	String configured;
	
	View recordingIndicator;
	SharedPreferences prefs;
	//TextView recordingIndicatorText;
	
	public enum ButtonState {
		RUNNING, NOT_RUNNING
	}
	ButtonState state;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        setContentView(R.layout.other);
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        
        mCameraView = (SurfaceView) findViewById(R.id.CameraView);
        mHolder = mCameraView.getHolder();
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        
        btnStart = (Button) findViewById(R.id.interactiveButton);
        btnStart.setOnClickListener(startButton);
        btnStart.setBackgroundResource(R.drawable.start_selector_new);
        btnStart.setText("Start");
        
        btnConfig = (Button) findViewById(R.id.configButton);
        btnConfig.setOnClickListener(configButton);
        btnConfig.setBackgroundResource(R.drawable.config_selector_new);
        btnConfig.setText("Config");

        // Get Recording Indicator
        recordingIndicator = (View) findViewById(R.id.recording_indicator);
        recordingIndicator.setVisibility(View.INVISIBLE);
        //recordingIndicatorText = (TextView) findViewById(R.id.recording_indicator_text);
        //recordingIndicatorText.setVisibility(View.INVISIBLE);
        
        // Initialize port and ip from preferences
        SharedPreferences prefs = PreferenceManager
        								.getDefaultSharedPreferences(getBaseContext());
        ipAddress = prefs.getString("camomatic_ip_address", "127.0.0.1");
        port = Integer.parseInt(prefs.getString("camomatic_port", "1337"));
        configured = prefs.getString("camomatic_config_state", "false");
        
        // Initialize state
        state = ButtonState.NOT_RUNNING;
    }
    
    View.OnClickListener startButton = new View.OnClickListener() {
		public void onClick(View v) {
			configured = prefs.getString("camomatic_config_state", "false");
			
			// Change button appearance on state change
			if (configured.equals("false")) {
				Toast.makeText(getBaseContext(), 
									"App has never been configured. Please press Config",
									Toast.LENGTH_LONG).show();
			}
			else{
				changeButtonState();
				setup("/sdcard/test.mp4");	
				
				//Start Button Interaction
				videoLoop = new Utils.Loopback("com.GoogleCompitition.video", 4096);
				audioLoop = new Utils.Loopback("com.GoogleCompitition.audio", 4096);
				videoLoop.initLoopback();
				audioLoop.initLoopback();
				if(videoRecorder == null)
					videoRecorder = new MediaRecorder();
				else{
					videoRecorder.release();
					videoRecorder = new MediaRecorder();
				}
				if(audioRecorder == null)
					audioRecorder = new MediaRecorder();
				else{
					audioRecorder.release();
					audioRecorder = new MediaRecorder();
				}
				initRecorder();
				videoRecorder.setOutputFile(videoLoop.getTargetFileDescriptor());
				audioRecorder.setOutputFile(audioLoop.getTargetFileDescriptor());
				//videoRecorder.setOutputFile(Environment.getExternalStorageDirectory().getAbsolutePath()+"/vid.mp4");
				PacketHandler VideoSender = new PacketHandler() {
					String sendIP = prefs.getString("camomatic_ip_address", "10.10.10.1");
					int sendPort = Integer.parseInt(prefs.getString("camomatic_port", "1337"));
					DatagramSocket toServer = null;
					DatagramPacket rtpPacket = null;
					InetAddress sendTo = null;
					public synchronized void newPacket(byte[] p, int length) {
						if(toServer == null || sendTo == null){
							try {
								sendTo = InetAddress.getByName(sendIP);
							} catch (UnknownHostException e2) {
								e2.printStackTrace();
								return;
							}
							try {
								toServer = new DatagramSocket();
							} catch (SocketException e1) {
								e1.printStackTrace();
								return;
							}
						}
						rtpPacket = new DatagramPacket(p, length,sendTo,sendPort);
						try {
							toServer.send(rtpPacket);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				};
				PacketHandler AudioSender = new PacketHandler() {
					String sendIP = prefs.getString("camomatic_ip_address", "10.10.10.1");
					int sendPort = Integer.parseInt(prefs.getString("camomatic_port", "1337"));
					DatagramSocket toServer = null;
					DatagramPacket rtpPacket = null;
					InetAddress sendTo = null;
					public synchronized void newPacket(byte[] p, int length) {
						if(toServer == null || sendTo == null){
							try {
								sendTo = InetAddress.getByName(sendIP);
							} catch (UnknownHostException e2) {
								e2.printStackTrace();
								return;
							}
							try {
								toServer = new DatagramSocket();
							} catch (SocketException e1) {
								e1.printStackTrace();
								return;
							}
						}
						rtpPacket = new DatagramPacket(p, length,sendTo,sendPort);
						try {
							toServer.send(rtpPacket);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				};
				try {
					videoPacketizer = new VideoPacketizer(videoLoop.getReceiverInputStream(), VideoSender, sps, pps, 0, 0 );
					audioPacketizer = new AudioPacketizer(audioLoop.getReceiverInputStream(), AudioSender, 0, 0);
				} catch (IOException e) {
					//Auto-generated catch block
					e.printStackTrace();
					return;
				}
				try {
					videoRecorder.prepare();
					audioRecorder.prepare();
				} catch (IllegalStateException e) {
					//Auto-generated catch block
					e.printStackTrace();
					return;
				} catch (IOException e) {
					//Auto-generated catch block
					e.printStackTrace();
				}
				videoRecorder.start();
				//audioRecorder.start();
				videoPacketizer.start();
				//audioPacketizer.start();
				
			}
		}
	};
	
	View.OnClickListener stopButton = new View.OnClickListener() {
		public void onClick(View v) {
			changeButtonState();
			
			videoPacketizer._stop();
			//audioPacketizer._stop();
			while(videoPacketizer.isAlive());
			videoPacketizer = null;
			//while(audioPacketizer.isAlive());
			//audioPacketizer = null;
			videoLoop.releaseLoopback();
			videoLoop = null;
			videoRecorder.stop();
			videoRecorder.release();
			//audioRecorder.stop();
			//audioRecorder.release();
			try {
				mCamera.reconnect();
			} catch (IOException e) {
				//Auto-generated catch block
				e.printStackTrace();
			}
			videoRecorder = null;

		}
	};
	
	View.OnClickListener configButton = new View.OnClickListener() {
		public void onClick(View v) {
			if (state != ButtonState.RUNNING){
				// Check if app has been configured
				if (configured.equals("false")){
					// Do initial configuration
					initConfig();
					
					// Change config state
					SharedPreferences prefs = PreferenceManager
										.getDefaultSharedPreferences(getBaseContext());
					SharedPreferences.Editor editor = prefs.edit();
					editor.putString("camomatic_config_state", "true");
					editor.commit();
				}
				Intent preferencesActivity = new Intent(getBaseContext(), Preferences.class);
				startActivity(preferencesActivity);
			}
		}
	};
	
	public static boolean setup(String autodetection_filename)
	{
		IsoFile VidFile = null;
		try {
			VidFile = new IsoFile(autodetection_filename);
		} catch (IOException e) {
			return false;
		}
		AvcConfigurationBox configBox;
		configBox = VidFile.getBoxes(AvcConfigurationBox.class).get(0);
		if(configBox == null){
			try {
				VidFile.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return false;
		}
		sps = configBox.getSequenceParameterSets().get(0);
		pps = configBox.getPictureParameterSets().get(0);
		try {
			VidFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}
	
	private void initRecorder(){
		//videoRecorder.setCamera(mCamera);
		mCamera = Camera.open();
		mCamera.setDisplayOrientation(90);
		mCamera.unlock();
		videoRecorder.setPreviewDisplay(mHolder.getSurface());
		videoRecorder.setCamera(mCamera);
		videoRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		//videoRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
		videoRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
		videoRecorder.setVideoFrameRate(10);
		videoRecorder.setVideoSize(640, 480);
		videoRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		//videoRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
		
		audioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		audioRecorder.setOutputFormat(MediaRecorder.OutputFormat.RAW_AMR);
		audioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
	}
	
	/**
	 * Automatically handles preference changes
	 */
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		// Get IP address, return "127.0.0.1" if no value exists
		if (key.equals("camomatic_ip_address")){
			ipAddress = prefs.getString("camomatic_ip_address", "127.0.0.1");
		}
		// Get port, return "1337" if no value exists
		if (key.equals("camomatic_port")){
			port = Integer.parseInt(prefs.getString("camomatic_port", "1337"));
		}
		if (key.equals("camomatic_config_state")){
			configured = prefs.getString("camomatic_config_state", "false");
		}
	}
	
	/**
	 * Controls the appearance of the buttons based on the state of the program
	 */
	private void changeButtonState() {
		if (state == ButtonState.NOT_RUNNING){
			btnStart.setBackgroundResource(R.drawable.stop_selector_new);
	        btnStart.setText("Stop");
	        btnConfig.setBackgroundResource(R.drawable.config_disabled_new);
	        recordingIndicator.setVisibility(View.VISIBLE);
	        //recordingIndicatorText.setVisibility(View.VISIBLE);
			state = ButtonState.RUNNING;
			
			btnStart.setOnClickListener(stopButton);
		}
		else if (state == ButtonState.RUNNING){
			btnStart.setBackgroundResource(R.drawable.start_selector_new);
	        btnStart.setText("Start");
	        btnConfig.setBackgroundResource(R.drawable.config_selector_new);
	        recordingIndicator.setVisibility(View.INVISIBLE);
	        //recordingIndicatorText.setVisibility(View.INVISIBLE);
			state = ButtonState.NOT_RUNNING;
			
			btnStart.setOnClickListener(startButton);
		}
	}
	
	/**
	 * This is Jaspreet's configButton's onClickListener, moved to its own method
	 */
	private void initConfig() {
		if(videoRecorder != null)
			return;
		videoRecorder = new MediaRecorder();
		videoRecorder.setPreviewDisplay(mHolder.getSurface());
		videoRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
		//videoRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
		videoRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
		videoRecorder.setVideoFrameRate(15);
		videoRecorder.setVideoSize(640, 480);
		videoRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		//videoRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
		videoRecorder.setMaxDuration(3000);
		
		audioRecorder = new MediaRecorder();
		audioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		audioRecorder.setOutputFormat(MediaRecorder.OutputFormat.RAW_AMR);
		audioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
		
		videoRecorder.setOnInfoListener(new OnInfoListener(){
			public void onInfo(MediaRecorder arg0, int what, int extra) {
				if(what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED){
					arg0.stop();
					arg0.release();
					videoRecorder = null;
					
					audioRecorder.stop();
					audioRecorder.release();
					audioRecorder = null;
				}
			}
		});
		videoRecorder.setOutputFile("/sdcard/test.mp4");
		audioRecorder.setOutputFile("/sdcard/audio.amr");
		try {
			videoRecorder.prepare();
			videoRecorder.start();
			
			audioRecorder.prepare();
			audioRecorder.start();
		} catch (IllegalStateException e) {
			//Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			//Auto-generated catch block
			e.printStackTrace();
		}
	}
}