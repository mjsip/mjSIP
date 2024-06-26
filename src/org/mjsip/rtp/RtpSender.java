/*
 * Copyright (C) 2013 Luca Veltri - University of Parma - Italy
 * 
 * This source code is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 * Author(s):
 * Luca Veltri (luca.veltri@unipr.it)
 */

package org.mjsip.rtp;


import org.zoolu.net.SocketAddress;
import org.zoolu.net.UdpSocket;


/** RTP sender.
  * It provides functionality for sending data within a RTP flow.
  * <p>
  * RTP packets are sent according to a given RTP context
  * that specifies the SSRC and payload type of the flow and maintains the sequence number and timestamp information.
  */
public class RtpSender {
	
	/** The RTP context */
	RtpContext rtp_context=null;

	/** UDP socket */
	UdpSocket udp_socket;
		  
	/** Destination UDP socket address */
	SocketAddress dest_soaddr;
	
	/** Packet counter */
	long packet_count=0;

	/** Octect counter */
	long octect_count=0;


	/** Creates a new RTP sender.
	  * @param pt payload type
	  * @param udp_socket the local UDP socket used for sending the RTP packets
	  * @param dest_soaddr the destination socket address */
	public RtpSender(int pt, UdpSocket udp_socket, SocketAddress dest_soaddr) {
		this(new RtpContext(pt),udp_socket,dest_soaddr);
	}                


	/** Creates a new RTP sender.
	  * @param rtp_context the RTP context
	  * @param udp_socket the local UDP socket used for sending the RTP packets
	  * @param dest_soaddr the destination socket address */
	public RtpSender(RtpContext rtp_context, UdpSocket udp_socket, SocketAddress dest_soaddr) {
		this.rtp_context=rtp_context;
		this.udp_socket=udp_socket;
		this.dest_soaddr=dest_soaddr;
	}          


	/** Gets the local port. */
	public int getLocalPort() {
		if (udp_socket!=null) return udp_socket.getLocalPort();
		else return 0;
	}


	/** Changes the destination socket address. */
	public void setDestSoAddress(SocketAddress dest_soaddr) {
		this.dest_soaddr=dest_soaddr;
	}


	/** Gets the destination socket address. */
	public SocketAddress getDestSoAddress() {
		return dest_soaddr;
	}


	/** Gets the synchronization source (SSRC) identifier. */
	public long getSSRC() {
		return rtp_context.getSsrc();
	}


	/** Gets the current RTP timestamp value. */
	public long getRtpTimestamp() {
		return rtp_context.getTimestamp();
	}


	/** Sets the timestamp.
	  * @param timestamp RTP timestamp */
	public void setTimestamp(long timestamp) {
		rtp_context.setTimestamp(timestamp);
	}


	/** Increments the sequence number.
	  * @param delta_timestamp the time lapse from the previous timestamp (in sampling periods) */
	public void incTimestamp(long delta_timestamp) {
		rtp_context.incTimestamp(delta_timestamp);
	}


	/** Gets the total number of sent packets. */
	public long getPacketCounter() {
		return packet_count;
	}


	/** Gets the total number of sent octects. */
	public long getOctectCounter() {
		return octect_count;
	}


	/** Sends a new RTP packet.
	  * @param data payload data
	  * @param packet_time the normalized packet time (in sampling periods) used to increment the next timestamp */
	public void send(byte[] data, long packet_time) throws java.io.IOException {
		send(data,0,data.length,packet_time);
	}


	/** Sends a new RTP packet.
	  * @param buf payload buffer
	  * @param off payload offset
	  * @param len payload length 
	  * @param packet_time the normalized packet time (in sampling periods) used to calculate the next timestamp */
	public void send(byte[] buf, int off, int len, long packet_time) throws java.io.IOException {
		RtpPacket rtp_packet=new RtpPacket(rtp_context,buf,off,len);
		//rtp_context.incSequenceNumber();
		//rtp_context.incTimestamp(packet_time);
		//packet_count++;
		//octect_count+=len;
		//if (rtp_socket!=null) rtp_socket.send(rtp_packet);
		send(rtp_packet,packet_time);
	}


	/** Sends a new RTP packet.
	  * @param rtp_packet the RTP packet
	  * @param packet_time the normalized packet time (in sampling periods) used to calculate the next timestamp */
	public void send(RtpPacket rtp_packet, long packet_time) throws java.io.IOException {
		rtp_context.setSequenceNumber(rtp_packet.getSequenceNumber()+1);
		rtp_context.setTimestamp(rtp_packet.getTimestamp()+packet_time);
		packet_count++;
		octect_count+=rtp_packet.getPacketLength();
		if (udp_socket!=null) udp_socket.send(rtp_packet.toUdpPacket(dest_soaddr));
	}


	/** Closes this RTP sender. */      
	public void close() {
		//udp_socket.close();
		udp_socket=null;
	}

}
