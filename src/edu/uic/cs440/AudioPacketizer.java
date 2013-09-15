package edu.uic.cs440;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import android.os.SystemClock;

public class AudioPacketizer extends Thread{
	static final int packetSize = 1400;
	private BufferedInputStream fis = null;
	private PacketHandler streamer = null;
	private int SSRC = 0;
	private int CSRC = 0;
	private volatile boolean isStoping = false;
	
	public AudioPacketizer(InputStream inputStream, PacketHandler handler, int SSRC, int CSRC){
		fis = new BufferedInputStream(inputStream);
		streamer = handler;
		this.SSRC = SSRC; //not yet used
		this.CSRC = CSRC; //not yet used
	}
	
	public void _stop(){
		isStoping = true;
	}
	
	public void run(){
		isStoping = false;
		//remove existing header
		byte[] buffer = new byte[packetSize + 12];
		buffer[0] = (byte)0x80;
		buffer[1] = (byte) 97 | (byte) 0x80;
		//buffer[2], buffer[3] is sequence number
		int seqn = (int) (Math.random() * Integer.MAX_VALUE);
		//4, 5, 6, 7 is timestamp
		long ts = SystemClock.elapsedRealtime() * 1000;
		//8, 9, 10, 11 is the SSRC... should not change so we can set it now
		int SSRC = (int) (Math.random() * Integer.MAX_VALUE);
		buffer[8] = (byte) (0xFF & (SSRC >> 24));
		buffer[9] = (byte) (0xFF & (SSRC >> 16));
		buffer[10] = (byte) (0xFF & (SSRC >> 8));
		buffer[11] = (byte) (0xFF & SSRC);
		int num = 0, number = 0;
		
		buffer[12] = (byte) 0xF0;
		
		try {
			while(fis.available() < 6);
			num = fis.read(buffer, 13, 6);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		while(!isStoping){
				//set timestamp
				ts += 160;
				buffer[4] = (byte) (0xFF & (ts >> 24));
				buffer[5] = (byte) (0xFF & (ts >> 16));
				buffer[6] = (byte) (0xFF & (ts >> 8));
				buffer[7] = (byte) (0xFF & ts);
				//set sequence number
				buffer[2] = (byte) (0xFF & (seqn >> 8));
				buffer[3] = (byte) (0xFF & seqn);
				++seqn;
				try {
					while(fis.available() < 33);
					num = fis.read(buffer, 13, 32);
				} catch (IOException e) {
					e.printStackTrace();
				}
				number += num;
				//send first FU-A packet
				streamer.newPacket(buffer, 45);
		}
	}
}
