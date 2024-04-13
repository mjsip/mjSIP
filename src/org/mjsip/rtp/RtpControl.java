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


import java.io.IOException;

import org.zoolu.net.IpAddress;
import org.zoolu.net.SocketAddress;
import org.zoolu.net.UdpPacket;
import org.zoolu.net.UdpProvider;
import org.zoolu.net.UdpProviderListener;
import org.zoolu.net.UdpSocket;
import org.zoolu.util.ByteUtils;


/** It sends and receives RTP control (RTCP) packets.
  */
public class RtpControl {
	
	/** Debug mode */
	public static boolean DEBUG=true;

	/** In case of no sender RTP stream, whether the SSRC of RR packets is set equal to the SSRC of the received RTP stream */
	public static boolean DEBUG_RR_SSRC_SYMMETRIC=true;

	/** Canonical end-point identifier (CNAME) */
	String cname;
	
	/** Start timestamp */
	long start_timestamp=-1;

	/** RTP sender */
	RtpControlledSender rtp_sender=null;

	/** RTP receiver */
	RtpControlledReceiver rtp_receiver=null;

	/** UDP */
	UdpProvider udp;
	
	/** Whether the UDP socket has been created here */
	boolean udp_socket_is_local;   

	/** Remote destination socket address */
	SocketAddress remote_dest_soaddr;

	/** Remote source socket address */
	SocketAddress remote_source_soaddr=null;

	/** Whether outgoing RTCP packets have to be sent to the same address where incoming RTCP packets come from (symmetric RTCP mode) */
	boolean symmetric_rtcp=false;

	/** UDP provider listener */
	UdpProviderListener this_udp_provider_listener=new UdpProviderListener() {
		@Override
		public void onReceivedPacket(UdpProvider udp, UdpPacket udp_packet) {
			processReceivedPacket(udp,udp_packet);
		}
		@Override
		public void onServiceTerminated(UdpProvider udp, Exception error) {
			processServiceTerminated(udp,error);
		}		
	};
	

	/** Creates a new RTP control.
	  * @param cname canonical end-point identifier (CNAME)
	  * @param local_port local RTCP port */
	public RtpControl(String cname, int local_port) throws IOException {
		this(cname,local_port,null,-1);
	}


	/** Creates a new RTP control.
	  * @param cname canonical end-point identifier (CNAME)
	  * @param local_port local RTCP port
	  * @param remote_addr the remote RTCP address
	  * @param remote_port the remote RTCP port */
	public RtpControl(String cname, int local_port, String remote_addr, int remote_port) throws IOException {
		this(cname,new UdpSocket(local_port),remote_addr,remote_port);
		udp_socket_is_local=true;
	}


	/** Creates a new RTP control.
	  * @param cname canonical end-point identifier (CNAME)
	  * @param local_socket local UDP socket for RTCP */
	public RtpControl(String cname, UdpSocket local_socket) {
		this(cname,local_socket,null,-1);
	}


	/** Creates a new RTP control.
	  * @param cname canonical end-point identifier (CNAME)
	  * @param local_socket local UDP socket for RTCP
	  * @param remote_addr the remote RTCP address
	  * @param remote_port the remote RTCP port */
	public RtpControl(String cname, UdpSocket local_socket, String remote_addr, int remote_port) {
		if (DEBUG) System.out.println("DEBUG: RtpControl(): "+local_socket+","+remote_addr+":"+remote_port+")");
		this.cname=cname;
		this.remote_dest_soaddr=remote_addr!=null? new SocketAddress(remote_addr,remote_port) : null; 
		udp=new UdpProvider(local_socket,this_udp_provider_listener);
		udp_socket_is_local=false;
	}


	/** Sets symmetric RTCP mode.
	  * In symmetric RTCP mode outgoing RTCP packets are sent to the source address of the incoming RTCP packets. 
	  * @param symmetric_rtcp whether working in symmetric RTCP mode */
	public void setSymmetricRtcpMode(boolean symmetric_rtcp) {
		this.symmetric_rtcp=symmetric_rtcp;
	}

	/** Sets the RTP sender. */
	public void setRtpSender(RtpControlledSender rtp_sender) {
		this.rtp_sender=rtp_sender;
	}


	/** Sets the RTP receiver. */
	public void setRtpReceiver(RtpControlledReceiver rtp_receiver) {
		this.rtp_receiver=rtp_receiver;
	}


	/** When a new UDP packet is received. */
	private void processReceivedPacket(UdpProvider udp, UdpPacket udp_packet) {
		//remote_source_soaddr=new SocketAddress(udp_packet.getIpAddress(),udp_packet.getPort());
		IpAddress remote_ipaddr=udp_packet.getIpAddress();
		int remote_port=udp_packet.getPort();
		if (remote_source_soaddr==null || !remote_source_soaddr.getAddress().equals(remote_ipaddr) || remote_source_soaddr.getPort()!=remote_port) remote_source_soaddr=new SocketAddress(remote_ipaddr,remote_port);
		if (symmetric_rtcp) remote_dest_soaddr=remote_source_soaddr;
		RtcpCompoundPacket rcomp_packet=new RtcpCompoundPacket(udp_packet);
		RtcpPacket[] rtcp_packets=rcomp_packet.getRtcpPackets();
		for (int i=0; i<rtcp_packets.length; i++) processReceivedPacket(rtcp_packets[i]);
	}


	/** When UDP terminates. */
	private void processServiceTerminated(UdpProvider udp, Exception error) {
		if (udp_socket_is_local) udp.getUdpSocket().close();
	}


	/** When a new RTCP packet is received. */
	protected void processReceivedPacket(RtcpPacket rtcp_packet) {
		if (DEBUG) System.out.println("\nDEBUG: RtpControl: new RTCP packet received: "+rtcp_packet.getPacketLength()+"B");
		if (DEBUG) System.out.println("DEBUG: RtpControl: bytes: "+ByteUtils.asHex(rtcp_packet.getPacketBuffer(),rtcp_packet.getPacketOffset(),rtcp_packet.getPacketLength()));
		if (DEBUG) System.out.println("DEBUG: RtpControl: type: "+rtcp_packet.getPayloadType());
		if (rtcp_packet.getPayloadType()==RtcpPacket.PT_SR) {
			SrRtcpPacket sr_packet=new SrRtcpPacket(rtcp_packet);
			SrRtcpPacket.SenderInfo si=sr_packet.getSenderInfo();
			if (DEBUG) System.out.println("DEBUG: RtpControl: SR: packet count: "+si.getPacketCount());
			if (DEBUG) System.out.println("DEBUG: RtpControl: SR: octect count: "+si.getOctectCount());
			long timestamp=si.getRtpTimestamp();
			if (start_timestamp<0) start_timestamp=timestamp;
			if (DEBUG) System.out.println("DEBUG: RtpControl: SR: timestamp: "+timestamp+" ("+(timestamp-start_timestamp)+")");
		}
	}

	
	/** Sends a RTCP compound packet.   
	  * @param rcomp_packet the RTCP compound packet to be sent */
	public void send(RtcpCompoundPacket rcomp_packet) {
		try {
			if (remote_dest_soaddr==null) {
				if (!symmetric_rtcp) throw new IOException("Null destination address");
				return;
			}
			// else
			udp.send(rcomp_packet.toUdpPacket(remote_dest_soaddr));
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}


	/** Sends a RTCP packet.   
	  * @param rtcp_packet the RTCP packet to be sent */
	public void send(RtcpPacket rtcp_packet) {
		try {
			if (remote_dest_soaddr==null) {
				if (!symmetric_rtcp) throw new IOException("Null destination address");
				return;
			}
			// else
			udp.send(rtcp_packet.toUdpPacket(remote_dest_soaddr));
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}


	/** Sends a RTCP report (SR or RR). */
	public void sendReport() {
		if (DEBUG) System.out.println("DEBUG: sendReport()");
		long ssrc=(rtp_sender!=null)? rtp_sender.getSSRC() : 0;
		SrRtcpPacket.SenderInfo si=(rtp_sender!=null)? new SrRtcpPacket.SenderInfo(System.currentTimeMillis(),rtp_sender.getRtpTimestamp(),rtp_sender.getPacketCounter(),rtp_sender.getOctectCounter()) : null;
		if (DEBUG) System.out.println("DEBUG: sendReport(): sender info: "+si);
		RrRtcpPacket.ReportBlock rb=(rtp_receiver!=null)? new RrRtcpPacket.ReportBlock(rtp_receiver.getSSRC(),rtp_receiver.getFractionLost(),rtp_receiver.getCumulativePacketLost(),rtp_receiver.getHighestSqnReceived(),rtp_receiver.getInterarrivalJitter(),rtp_receiver.getLSR(),rtp_receiver.getDLSR()) : null;
		
		if (si!=null) {
			SrRtcpPacket sr=(rb!=null)? new SrRtcpPacket(ssrc,si,new RrRtcpPacket.ReportBlock[]{rb}) : new SrRtcpPacket(ssrc,si);
			if (cname==null) send(sr);
			else {
				SdesRtcpPacket sdes=new SdesRtcpPacket(ssrc,cname);
				RtcpCompoundPacket cp=new RtcpCompoundPacket(new RtcpPacket[]{ sr, sdes });
				send(cp);
			}
		}
		else
		if (rb!=null) {
			long rr_ssrc=(DEBUG_RR_SSRC_SYMMETRIC)? rb.getSSRC(): 0;
			RrRtcpPacket rr=new RrRtcpPacket(rr_ssrc,new RrRtcpPacket.ReportBlock[]{rb});
			if (cname==null) send(rr);
			else {
				SdesRtcpPacket sdes=new SdesRtcpPacket(rr_ssrc,cname);
				RtcpCompoundPacket cp=new RtcpCompoundPacket(new RtcpPacket[]{ rr, sdes });
				send(cp);
			}
		}
	}


	/** Closes RTCP. */
	public void halt() {
		udp.halt();
		rtp_sender=null;
		rtp_receiver=null;
	}

}


