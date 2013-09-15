package edu.uic.cs440;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

public class Utils {
	
	public static class Loopback {
		private LocalSocket receiver, sender;			
		private LocalServerSocket lss;		
		private final String localAddress;
		private final int bufferSize;
		
		public Loopback(String localAddress, int bufferSize) 
		{
			this.localAddress = localAddress;
			this.bufferSize = bufferSize;
		}
		

		boolean initLoopback()
		{		
			releaseLoopback();

			receiver = new LocalSocket();
			try {
				lss = new LocalServerSocket(localAddress);
				receiver.connect(new LocalSocketAddress(localAddress));
				receiver.setReceiveBufferSize(bufferSize);
				receiver.setSendBufferSize(bufferSize);
			} catch (IOException e) {
				return false;
			}	
			
			try {
				sender = lss.accept(); // blocks until a new connection arrives!
				sender.setReceiveBufferSize(bufferSize);
				sender.setSendBufferSize(bufferSize);
			} catch (IOException e) {
				return false;
			}

			return true;
		}

		void releaseLoopback()
		{
			try {
				if ( lss != null){
					lss.close();
				}
				if ( receiver != null){
					receiver.close();
				}
				if ( sender != null){
					sender.close();
				}
			} catch (IOException e) {}
			
			lss = null;
			sender = null;
			receiver = null;
		}
		
		FileDescriptor getTargetFileDescriptor()
		{
			return sender.getFileDescriptor();
		}

		public InputStream getReceiverInputStream() throws IOException {
			return receiver.getInputStream();
		}	
	}

	
	private static int MAX_READS = 4096;
	//The inputstream is parsed until we reach the start of the stream...
	public static boolean findStreamStart(InputStream is) throws IOException {
		int c, n = 0;
		int stat = 0;
		while((c = is.read()) != -1 && n++ < MAX_READS){
			switch(c){
			case 'm':
				stat = (stat == 0) ? 1 : 0;
				break;
			case 'd':
				stat = (stat == 1) ? 2 : 0;
				break;
			case 'a':
				stat = (stat == 2) ? 3 : 0;
				break;
			case 't':
				stat = (stat == 3) ? 4 : 0;
				break;
			default:
				stat = 0;
				break;
			}
			if(stat == 4)
				return true;
		}
		return false;
	}
}
