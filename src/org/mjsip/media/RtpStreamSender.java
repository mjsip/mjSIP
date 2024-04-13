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

package org.mjsip.media;


import java.io.InputStream;

import org.mjsip.rtp.RtpControl;
import org.mjsip.rtp.RtpControlledSender;
import org.mjsip.rtp.RtpPayloadFormat;
import org.mjsip.rtp.RtpSender;
import org.zoolu.net.SocketAddress;
import org.zoolu.net.UdpSocket;
import org.zoolu.util.Encoder;


/** RtpStreamSender is a generic RTP sender.
  * It takes media from a given InputStream and sends it through RTP packets to a remote destination.
  */
public class RtpStreamSender extends Thread implements RtpControlledSender {
	
	/** Inter-time of RTCP Sending Report (SR) packets [millisecs]. */
	public static long RTCP_SR_TIME=5000;

	/** Size of the input buffer (that must fit the RTP header plus the input data) */
	public static final int BUFFER_SIZE=1472; // 1500 (Etherent MTU) - 20 (IPH) - 8 (UDPH)

	/** Minimum inter-packet time as packet time fraction (min_time=pcket_time/x); default value is 2. */
	public static int MIN_INTER_PACKET_TIME_FRACTION=2;

	/** Force this SSRC if greater than 0 */
	public static long STATIC_SSRC=-1;

	/** Force this initial sequence number if greater than 0 */
	public static int STATIC_SQN=-1;

	/** Force this initial timestamp if greater than 0 */
	public static long STATIC_TIMESTAMP=-1;

	/** Whether working in debug mode. */
	public static boolean DEBUG=true;

	// DEBUG DROP RATE
	/** Mean interval between two dropping periods (in mean number of packets) */
	//public static int DEBUG_DROP_RATE=500; // drop interval every 10s 
	public static int DEBUG_DROP_RATE=0;
	/** Duration of a packet dropping period (in number of dropped packets) */
	public static int DEBUG_DROP_TIME=100; // drop interval duration = 2s 

	/** RTP header length. */
	private static final int RTPH_LEN=12;

	
	/** Listener */
	RtpStreamSenderListener listener=null;

	/** The InputStream */
	InputStream input_stream=null;

	/** RTP sender */
	RtpSender rtp_sender=null;
	
	/** UDP socket */
	 UdpSocket udp_socket;

	/** Whether the socket has been created here */
	boolean socket_is_local_attribute=false;   

	/** Number of samples per second */
	long sample_rate;
	
	/** Number of audio channels */
	int channels;

	/** Inter-packet time (in milliseconds) */
	long packet_time;

	/** Number of payload bytes per packet */
	int payload_size;

	/** Whether it works synchronously with a local clock, or it it acts as slave of the InputStream  */
	boolean do_sync=true;

	/** Synchronization correction value, in milliseconds.
	  * It accellarates (sync_adj<0) or reduces (sync_adj>0) the sending rate respect to the nominal value. */
	long sync_adj=0;

	/** Whether it is running */
	boolean running=false;   

	 /** RTCP */
	 RtpControl rtp_control=null;

	/** RTP payload format */
	RtpPayloadFormat rtp_payload_format=null;
	
	/** Additional RTP payload encoder */
	Encoder additional_encoder;

	

	/** Creates a new RTP stream sender.
	  * @param input_stream the stream source
	  * @param do_sync whether time synchronization must be performed by the RtpStreamSender,
	  *        or it is performed by the InputStream (e.g. by the system audio input)
	  * @param payload_type the payload type
	  * @param sample_rate audio sample rate
	  * @param channels number of audio channels (1 for mono, 2 for stereo)
	  * @param packet_time the inter-packet time (in milliseconds); it is used in the calculation of the the next departure time, in case of do_sync==true,
	  * @param payload_size the size of the payload
	  * @param additional_encoder additional RTP payload encoder (optional)
	  * @param dest_addr the destination address
	  * @param dest_port the destination port */
	public RtpStreamSender(InputStream input_stream, boolean do_sync, int payload_type, long sample_rate, int channels, long packet_time, int payload_size, Encoder additional_encoder, int local_port, String dest_addr, int dest_port, RtpStreamSenderListener listener) throws java.net.SocketException, java.net.UnknownHostException {
		this(input_stream,do_sync,payload_type,sample_rate,channels,packet_time,payload_size,additional_encoder,new UdpSocket(local_port>0?local_port:0),dest_addr,dest_port,listener);
		socket_is_local_attribute=true;
	}                


	/** Creates a new RTP stream sender.
	  * @param input_stream the stream to be sent
	  * @param do_sync whether time synchronization must be performed by the RtpStreamSender,
	  *        or it is performed by the InputStream (e.g. by the system audio input)
	  * @param payload_type the payload type
	  * @param sample_rate audio sample rate
	  * @param channels number of audio channels (1 for mono, 2 for stereo)
	  * @param packet_time the inter-packet time (in milliseconds); it is used in the calculation of the next departure time, in case of do_sync==true,
	  * @param payload_size the size of the payload
	  * @param additional_encoder additional RTP payload encoder (optional)
	  * @param udp_socket the UDP socket used to send the RTP packet
	  * @param dest_addr the destination address
	  * @param dest_port the destination port */
	public RtpStreamSender(InputStream input_stream, boolean do_sync, int payload_type, long sample_rate, int channels, long packet_time, int payload_size, Encoder additional_encoder, UdpSocket udp_socket, String dest_addr, int dest_port, RtpStreamSenderListener listener) throws java.net.UnknownHostException {
		this.listener=listener;
		this.input_stream=input_stream;
		this.sample_rate=sample_rate;
		this.channels=channels;
		this.packet_time=packet_time;
		this.payload_size=payload_size;
		this.additional_encoder=additional_encoder;
		this.do_sync=do_sync;
		this.udp_socket=udp_socket;
		rtp_sender=new RtpSender(payload_type,udp_socket,new SocketAddress(dest_addr,dest_port));
	}          


	/** Sets RTCP. */
	public void setControl(RtpControl rtp_control) {
		this.rtp_control=rtp_control;
		if (rtp_control!=null) rtp_control.setRtpSender(this);
	}


	/** Gets the local port. */
	public int getLocalPort() {
		if (rtp_sender!=null) return rtp_sender.getLocalPort();
		else return 0;
	}


	/** Changes the destination socket address. */
	public void setDestSoAddress(SocketAddress dest_soaddr) {
		if (rtp_sender!=null) rtp_sender.setDestSoAddress(dest_soaddr);
	}


	/** Gets the destination socket address. */
	public SocketAddress getDestSoAddress() {
		return rtp_sender.getDestSoAddress();
	}


	/** Sets the synchronization adjustment time (in milliseconds). 
	  * It accellarates (sync_adj &lt; 0) or reduces (sync_adj &gt; 0) the sending rate respect to the nominal value.
	  * @param sync_adj the difference between the actual inter-packet sending time respect to the nominal value (in milliseconds). */
	public void setSyncAdj(long sync_adj) {
		this.sync_adj=sync_adj;
	}


	/** Gets the synchronization source (SSRC) identifier. */
	public long getSSRC() {
		return rtp_sender.getSSRC();
	}


	/** Gets the current RTP timestamp value. */
	public long getRtpTimestamp() {
		return rtp_sender.getRtpTimestamp();
	}


	/** Gets the total number of sent packets. */
	public long getPacketCounter() {
		return rtp_sender.getPacketCounter();
	}


	/** Gets the total number of sent octects. */
	public long getOctectCounter() {
		return rtp_sender.getOctectCounter();
	}


	/** Whether is running */
	public boolean isRunning() {
		return running;
	}


	/** Stops running */
	public void halt() {
		running=false;
	}


	/** Runs it in a new Thread. */
	public void run() {
		
		if (rtp_sender==null || input_stream==null) return;
		//else
		
		// number of payload bytes after RTP formatting
		int formatted_len=(rtp_payload_format!=null)? rtp_payload_format.getRtpPayloadFormatLength(payload_size) : payload_size;		
		byte[] packet_buffer=new byte[BUFFER_SIZE];

		long time=0;
		long time_sync=0;
		//long timestamp=0;
		long start_time=System.currentTimeMillis();
		//long byte_rate=payload_size*1000/packet_time;
		
		// sending report counters
		//long packet_count=0;
		//long octect_count=0;
		long next_report_time=0;

		running=true;

		if (DEBUG) println("RTP: localhost:"+rtp_sender.getLocalPort()+" --> "+rtp_sender.getDestSoAddress());
		if (DEBUG) println("RTP: sending RTP pkts with "+(formatted_len)+" bytes as payload");

		// DEBUG DROP RATE
		int debug_drop_count=0;

		Exception error=null;
		try {
			while (running) {
				
				if (time>=next_report_time) {
					//if (rtp_control!=null) rtp_control.send(new org.mjsip.net.SrRtcpPacket(rtp_packet.getSsrc(),System.currentTimeMillis(),timestamp,packet_count,octect_count));
					if (rtp_control!=null) rtp_control.sendReport();
					next_report_time+=RTCP_SR_TIME;
				}
				int len=input_stream.read(packet_buffer,0,payload_size);
				// check running state, since the read() method may be blocking..
				if (!running) break;
				// else
				if (len>0) {					
					// apply possible RTP payload format (if required, e.g. in case of AMR)
					formatted_len=(rtp_payload_format!=null)? rtp_payload_format.setRtpPayloadFormat(packet_buffer,RTPH_LEN,len) : len;

					// do additional encoding (if defined)
					formatted_len=(additional_encoder!=null)? additional_encoder.encode(packet_buffer,RTPH_LEN,formatted_len,packet_buffer,RTPH_LEN): formatted_len;

					// packet time
					long this_packet_time=packet_time*len/payload_size/channels;
					long normalized_packet_time=(this_packet_time*sample_rate)/1000;

					// send packet
					rtp_sender.send(packet_buffer,0,formatted_len,normalized_packet_time);
					
					// wait for next departure
					if (do_sync) {
						time_sync+=packet_time+sync_adj;
						// wait before next departure..
						long sleep_time=start_time+time_sync-System.currentTimeMillis();
						// compensate possible inter-time reduction due to the approximated time obtained by System.currentTimeMillis()
						if (MIN_INTER_PACKET_TIME_FRACTION>1) {
							long min_time=packet_time/MIN_INTER_PACKET_TIME_FRACTION;
							if (sleep_time<min_time) sleep_time=min_time;
						}
						// sleep
						if (sleep_time>0) try {  Thread.sleep(sleep_time);  } catch (Exception e) {}
					}
				}
				else
				if (len<0) {
					if (DEBUG) println("Error reading from input stream");
					running=false;
				}
			}
		}
		catch (Exception e) {
			running=false;
			error=e;
			if (DEBUG) e.printStackTrace();
		}     

		//if (DEBUG) println("rtp time:  "+time);
		//if (DEBUG) println("real time: "+(System.currentTimeMillis()-start_time));

		// close RtpSocket and local UdpSocket
		rtp_sender.close();
		if (socket_is_local_attribute && udp_socket!=null) udp_socket.close();
		
		// free all references
		input_stream=null;
		udp_socket=null;

		if (DEBUG) println("rtp sender terminated");
		if (listener!=null) listener.onRtpStreamSenderTerminated(this,error);
	}
	
	
	/** Sets RTP payload format. */
	public void setRtpPayloadFormat(RtpPayloadFormat rtp_payload_format) {
		this.rtp_payload_format=rtp_payload_format;
	}


	/** Gets the total number of UDP sent packets. */
	public long getUdpPacketCounter() {
		if (rtp_sender!=null) return rtp_sender.getPacketCounter();
		else return 0;
	}


	/** Gets the total number of UDP sent octects. */
	public long getUdpOctectCounter() {
		if (rtp_sender!=null) return rtp_sender.getOctectCounter();
		else return 0;
	}


	/** Debug output */
	private static void println(String str) {
		System.err.println("RtpStreamSender: "+str);
	}

}
