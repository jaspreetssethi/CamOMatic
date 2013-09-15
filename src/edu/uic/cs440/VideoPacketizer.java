package edu.uic.cs440;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import android.os.SystemClock;

public class VideoPacketizer extends Thread {
	static final int packetSize = 1400;
	private BufferedInputStream fis = null;
	private PacketHandler streamer = null;
	private int SSRC = 0;
	private int CSRC = 0;
	byte[] sps = null;
	byte[] pps = null;
	private volatile boolean isStoping = false;
	
	public VideoPacketizer(InputStream inputStream, PacketHandler handler, byte[] SequencePerameterSet, byte[] PicturePerameterSet, int SSRC, int CSRC){
		fis = new BufferedInputStream(inputStream);
		streamer = handler;
		this.SSRC = SSRC; //not yet used
		this.CSRC = CSRC; //not yet used
		if(PicturePerameterSet != null && SequencePerameterSet != null){
			pps = PicturePerameterSet;
			sps = SequencePerameterSet;
		}else{
			sps = new byte[]{(byte) 0x42,(byte) 0x00,(byte) 0x1F,(byte) 0xE9,(byte) 0x01,(byte) 0x40,(byte) 0x7B,(byte) 0x20};
			pps = new byte[]{(byte) 0xCE,(byte) 0x06,(byte) 0xF2};
		}
	}
	
	public void _stop(){
		isStoping = true;
	}
	
	public void run(){
		isStoping = false;
		//remove existing header
		try {
			Utils.findStreamStart(fis);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			return;
		}
		
		//buffer
		byte[] buffer = new byte[packetSize + 12];
		buffer[0] = (byte)0x80;
		buffer[1] = (byte) 96;
		//buffer[2], buffer[3] is sequence number
		int seqn = (int) (Math.random() * Integer.MAX_VALUE);
		//4, 5, 6, 7 is timestamp
		long ts = SystemClock.elapsedRealtime() * 90;
		//8, 9, 10, 11 is the SSRC... should not change so we can set it now
		int SSRC = (int) (Math.random() * Integer.MAX_VALUE);
		buffer[8] = (byte) (0xFF & (SSRC >> 24));
		buffer[9] = (byte) (0xFF & (SSRC >> 16));
		buffer[10] = (byte) (0xFF & (SSRC >> 8));
		buffer[11] = (byte) (0xFF & SSRC);
		int NAL_Size = 0;
		int nalH = 0;
		int num = 0, number = 0;
		int toRead = 0;
		
		while(!isStoping){
			try {
				while(fis.available() < 4);
				//determine the size of the NAL unit
				NAL_Size = fis.read() << 24;
				NAL_Size|= fis.read() << 16;
				NAL_Size|= fis.read() << 8;
				NAL_Size|= fis.read();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				return;
			}
			if(NAL_Size > packetSize){
				//get the nal header
				try {
					while(fis.available() < 1);
					nalH = fis.read();
					number = 1;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					return;
				}
				//set timestamp
				ts = SystemClock.elapsedRealtime() * 90;
				buffer[4] = (byte) (0xFF & (ts >> 24));
				buffer[5] = (byte) (0xFF & (ts >> 16));
				buffer[6] = (byte) (0xFF & (ts >> 8));
				buffer[7] = (byte) (0xFF & ts);
				//if the nal type is 5... we need to send a sps and a pps
				if((0x1F & nalH) == 5){
					buffer[2] = (byte) (0xFF & (seqn >> 8));
					buffer[3] = (byte) (0xFF & seqn);
					buffer[12] = (byte)(0x60 | 7);
					for(int i = 0; i < sps.length; i++){
						buffer[13+i]=sps[i];
					}
					++seqn;
					streamer.newPacket(buffer, sps.length + 12);
					buffer[2] = (byte) (0xFF & (seqn >> 8));
					buffer[3] = (byte) (0xFF & seqn);
					buffer[12] = (byte)(0x60 | 8);
					for(int i = 0; i < pps.length; i++){
						buffer[13+i]=pps[i];
					}
					++seqn;
					streamer.newPacket(buffer, pps.length + 12);
				}
				buffer[1] = (byte) 96;
				//set sequence number
				buffer[2] = (byte) (0xFF & (seqn >> 8));
				buffer[3] = (byte) (0xFF & seqn);
				++seqn;
				//FU-A Indicator
				buffer[12] = (byte) (0x60 & nalH);
				buffer[12] |= (byte) (0x1F & 28);
				//FU Header
				buffer[13] = (byte) 0x80;
				buffer[13] |= (byte) (0x1F & nalH);
				//read into buffer
				try {
					while(fis.available() < packetSize-2);
					num = fis.read(buffer, 14, packetSize-2);
				} catch (IOException e) {
					e.printStackTrace();
				}
				number += num;
				//send first FU-A packet
				streamer.newPacket(buffer, buffer.length);
				//next packet
				buffer[13] &= 0x7F;
				while(number + packetSize - 2 <= NAL_Size){
					//set sequence number
					buffer[2] = (byte) (0xFF & (seqn >> 8));
					buffer[3] = (byte) (0xFF & seqn);
					++seqn;
					//fill buffer
					try {
						while(fis.available() < packetSize-2);
						num = fis.read(buffer, 14, packetSize-2);
					} catch (IOException e) {
						e.printStackTrace();
					}
					number += num;
					//send FU-A packet (middle of payload)
					streamer.newPacket(buffer, buffer.length);
				}
				//last packet for FU-A
				buffer[13] |= 0x40;
				//set sequence number
				buffer[2] = (byte) (0xFF & (seqn >> 8));
				buffer[3] = (byte) (0xFF & seqn);
				++seqn;
				//fill buffer
				toRead = Math.min(packetSize - 2, NAL_Size - number);
				try {
					while(fis.available() < toRead);
					num = fis.read(buffer, 14, toRead);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					return;
				}
				number += num;
				//set marker bit and send last FU-A packet
				buffer[1] = (byte) (0x80 | buffer[1]);
				streamer.newPacket(buffer, toRead + 14);
			}else{
				buffer[1] = (byte) (0x80 | buffer[1]);
				//set sequence number
				buffer[2] = (byte) (0xFF & (seqn >> 8));
				buffer[3] = (byte) (0xFF & seqn);
				++seqn;
				//set timestamp
				ts = SystemClock.elapsedRealtime() * 90;
				buffer[4] = (byte) (0xFF & (ts >> 24));
				buffer[5] = (byte) (0xFF & (ts >> 16));
				buffer[6] = (byte) (0xFF & (ts >> 8));
				buffer[7] = (byte) (0xFF & ts);
				try {
					while(fis.available() < NAL_Size);
					fis.read(buffer, 12, NAL_Size);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					return;
				}
				streamer.newPacket(buffer, NAL_Size + 12);
				//***NOTE: STAP-A packets have not been implemented... inclusion in future revisions may help manage bandwidth better but are not strictly necessary
			}
		}
	}
}
