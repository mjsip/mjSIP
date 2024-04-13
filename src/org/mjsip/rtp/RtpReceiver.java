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


import org.zoolu.net.IpAddress;
import org.zoolu.net.SocketAddress;
import org.zoolu.net.UdpPacket;
import org.zoolu.net.UdpProvider;
import org.zoolu.net.UdpProviderListener;
import org.zoolu.net.UdpSocket;


/** RTP receiver.
  * It provides functionality for receiving data within a RTP flow.
  * <p>
  * RTP packets are received according to a given RTP context
  * that specifies the SSRC and payload type of the flow and maintains the sequence number and timestamp information.
  */
public class RtpReceiver {
	
	/** The RTP context */
	RtpContext rtp_context=null;

	/** The UDP provider */
	UdpProvider udp_provider=null;
	
	/** Remote source UDP socket address */
	SocketAddress remote_source_soaddr=null;

	/** Listener */
	RtpReceiverListener listener=null;



	/** Creates a new RTP receiver.
	  * @param udp_socket the local UDP socket used for receiving RTP packets 
	  * @param listener the RtpReceiver listener */
	public RtpReceiver(UdpSocket udp_socket, RtpReceiverListener listener) {
		this.listener=listener;
		udp_provider=new UdpProvider(udp_socket, new UdpProviderListener(){
			@Override
			public void onReceivedPacket(UdpProvider udp, UdpPacket udp_packet) {
				processUdpReceivedPacket(udp,udp_packet);				
			}
			@Override
			public void onServiceTerminated(UdpProvider udp, Exception error) {
				processUdpServiceTerminated(udp,error);
			}});
	}


	/** Gets the local port.
	  * @return the local UDP port */
	public int getLocalPort() {
		if (udp_provider!=null) return udp_provider.getUdpSocket().getLocalPort();
		else return 0;
	}


	/** Gets the remote socket address.
	  * @return the remote socket address */
	public SocketAddress getRemoteSoAddress() {
		return remote_source_soaddr;
	}


	/** Gets the synchronization source (SSRC) identifier.
	  * @return the SSRC */
	public long getSSRC() {
		if (rtp_context!=null) return rtp_context.getSsrc();
		else return -1;
	}


	/** Gets the payload type (PT).
	  * @return the paylod type */
	public int getPayloadType() {
		if (rtp_context!=null) return rtp_context.getPayloadType();
		else return -1;
	}


	/** Stops running. */
	public void halt() {
		udp_provider.halt();
	}


	/** Processes the reception of a new UDP packet. */
	private void processUdpReceivedPacket(UdpProvider udp, UdpPacket udp_packet) {
		//remote_source_soaddr=new SocketAddress(udp_packet.getIpAddress(),udp_packet.getPort());
		IpAddress remote_ipaddr=udp_packet.getIpAddress();
		int remote_port=udp_packet.getPort();
		if (remote_source_soaddr==null || !remote_source_soaddr.getAddress().equals(remote_ipaddr) || remote_source_soaddr.getPort()!=remote_port) remote_source_soaddr=new SocketAddress(remote_ipaddr,remote_port);
		processRtpReceivedPacket(new RtpPacket(udp_packet));
	}


	/** Processes the termination of UDP provider. */
	private void processUdpServiceTerminated(UdpProvider udp, Exception error) {
		if (listener!=null) listener.onServiceTerminated(this,error);
	}

	/** Processes the reception of a new RTP packet. */
	protected void processRtpReceivedPacket(RtpPacket rtp_packet) {
		if (rtp_context==null) rtp_context=new RtpContext(rtp_packet.getPayloadType(),rtp_packet.getSsrc(),rtp_packet.getSequenceNumber(),rtp_packet.getTimestamp());
		else {
			int sqn=rtp_packet.getSequenceNumber();
			if (sqn>rtp_context.getSequenceNumber()) rtp_context.setSequenceNumber(sqn);
			long timestamp=rtp_packet.getTimestamp();
			if (timestamp>rtp_context.getTimestamp()) rtp_context.setTimestamp(timestamp);
		}
		if (listener!=null) listener.onReceivedPacket(this,rtp_packet);
	}

}


