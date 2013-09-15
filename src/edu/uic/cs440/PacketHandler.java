package edu.uic.cs440;

public interface PacketHandler {
	public void newPacket(byte[] p, int length);
}
