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


import java.io.OutputStream;
import java.net.SocketException;

import org.mjsip.rtp.RtpControl;
import org.mjsip.rtp.RtpControlledReceiver;
import org.mjsip.rtp.RtpPacket;
import org.mjsip.rtp.RtpPayloadFormat;
import org.mjsip.rtp.RtpReceiver;
import org.mjsip.rtp.RtpReceiverListener;
import org.zoolu.net.SocketAddress;
import org.zoolu.net.UdpSocket;
import org.zoolu.util.Encoder;


/** RtpStreamReceiver is a generic RTP receiver.
  * It receives packets from RTP and writes media into a given OutputStream.
  */
public class RtpStreamReceiver implements RtpControlledReceiver {
	
	/** Whether working in debug mode. */
	public static boolean DEBUG=false;

	/** Time waited before starting playing out packets (in millisecs). All packet received in the meantime are dropped in order to reduce the effect of an eventual initial packet burst. */
	public static final int EARLY_DROP_TIME=200;

	/** Size of the receiver buffer (including the RTP header) */
	public static final int BUFFER_SIZE=32768;

	/** Maximum blocking time, spent waiting for reading new bytes [milliseconds] */
	public static final int SO_TIMEOUT=200;

	/** Integer value 2^16 */
	private static final int TWO_16=(1<<16);

	/** Long value 2^32 */
	private static final long TWO_32=(1<<32);


	/** Listener */
	RtpStreamReceiverListener listener=null;

	/** The OutputStream */
	OutputStream output_stream=null;

	/** RTP receiver */
	RtpReceiver rtp_receiver=null;

	/** UDP socket */
	 UdpSocket udp_socket;

	/** Whether the socket has been created here */
	boolean socket_is_local_attribute=false;

	/** Remote socket address */
	SocketAddress remote_soaddr=null;

	/** Packet random early drop (RED) value; if greater than 0, it is the inverse of the packet drop rate */
	int random_early_drop=0;

	/** Whether it is running */
	boolean running=false;

	/** Packet counter (incremented only if packet_drop_rate>0) */
	long packet_counter=0;

	/** RTCP */
	 RtpControl rtp_control=null;

	/** RTP payload format */
	RtpPayloadFormat rtp_payload_format=null;
	
	/** Whether discarding packets with wrong SSRC (i.e. with a different SSRC compared to the first packet) */
	boolean ssrc_check=false;

	/** Whether discarding out-of-sequence and duplicated packets */
	boolean sequence_check=false;

	/** Whether filling silence intervals with (silence-equivalent) void data */
	boolean silence_padding=false;
	
	/** Additional RTP payload decoder */
	Encoder additional_decoder;
	
	/** */
	long early_drop_to;
	
	/** */	
	int last_sqn=-1;
	
	/** */
	long last_timestamp=-1;
	
	/** */
	byte[] silence_buffer=new byte[4000];



	/** Creates a RTP stream receiver.
	  * @param output_stream the stream sink
	  * @param additional_decoder additional RTP payload decoder (optional)
	  * @param local_port the local receiver port */
	public RtpStreamReceiver(OutputStream output_stream, Encoder additional_decoder, int local_port) throws java.net.SocketException {
		this(output_stream,additional_decoder,local_port,null);
	}

	/** Creates a RTP stream receiver.
	  * @param output_stream the stream sink
	  * @param additional_decoder additional RTP payload decoder (optional)
	  * @param socket the local receiver UdpSocket */
	public RtpStreamReceiver(OutputStream output_stream, Encoder additional_decoder, UdpSocket socket) {
		this(output_stream,additional_decoder,socket,null);
	}

	/** Creates the RTP stream receiver.
	  * @param output_stream the stream sink
	  * @param additional_decoder additional RTP payload decoder (optional)
	  * @param local_port the local receiver port
	  * @param listener the RtpStreamReceiver listener */
	public RtpStreamReceiver(OutputStream output_stream, Encoder additional_decoder, int local_port, RtpStreamReceiverListener listener) throws java.net.SocketException {
		this(output_stream,additional_decoder,new UdpSocket(local_port),listener);
		socket_is_local_attribute=true;
	}

	/** Creates a RTP stream receiver.
	  * @param output_stream the stream sink
	  * @param additional_decoder additional RTP payload decoder (optional)
	  * @param udp_socket the local receiver UDP socket
	  * @param listener the RtpStreamReceiver listener */
	public RtpStreamReceiver(OutputStream output_stream, Encoder additional_decoder, UdpSocket udp_socket, RtpStreamReceiverListener listener) {
		this.output_stream=output_stream;
		this.listener=listener;
		this.additional_decoder=additional_decoder;
		this.udp_socket=udp_socket;
	}

	/** Starts receiving packets. */
	public void start() {
		if (udp_socket!=null) rtp_receiver=new RtpReceiver(udp_socket,new RtpReceiverListener(){
			@Override
			public void onReceivedPacket(RtpReceiver rtp_receiver, RtpPacket rtp_packet) {
				processReceivedPacket(rtp_receiver,rtp_packet);
			}
			@Override
			public void onServiceTerminated(RtpReceiver rtp_receiver, Exception error) {
				processServiceTerminated(rtp_receiver,error);
			}
		});
		running=true;

		if (DEBUG) println("RTP: localhost:"+udp_socket.getLocalPort()+" <-- remotesocket");
		try {
			udp_socket.setSoTimeout(SO_TIMEOUT);
		}
		catch (SocketException e) {
			e.printStackTrace();
		}
		early_drop_to=(EARLY_DROP_TIME>0)? System.currentTimeMillis()+EARLY_DROP_TIME : -1;
	}


	/** Sets RTCP. */
	public void setControl(RtpControl rtp_control) {
		this.rtp_control=rtp_control;
		if (rtp_control!=null) rtp_control.setRtpReceiver(this);
	}


	/** Gets the local port. */
	public int getLocalPort() {
		if (rtp_receiver!=null) return rtp_receiver.getLocalPort();
		else return 0;
	}



	/** Gets SSRC.
	  * @return he synchronization source (SSRC) identifier of the received RTP packets */
	public long getSSRC() {
		// to be implemented..
		return 0;
	}

	/** Gets fraction lost.
	  * @return the fraction of RTP data packets lost since the previous SR or RR packet was sent; the fraction loss is defined as the number of packets lost divided by the number of packets expected; it is represented by the integer part after multiplying the loss fraction by 256 (8 bit) */
	public int getFractionLost() {
		// to be implemented..
		return 0;
	}

	/** Gets cumulative number of packets lost.
	  * @return cumulative number of packets lost that is the total number of RTP data packets that have been lost since the beginning of reception; it is the number of packets expected less the number of packets actually received, where the number of packets received includes any which are late or duplicates */
	public long getCumulativePacketLost() {
		// to be implemented..
		return 0;
	}

	/** Gets the extended highest sequence number received.
	  * @return the extended highest sequence number received (32bit); the low 16 bits contain the highest sequence number received in an RTP data packet, and the most significant 16 bits extend that sequence number with the corresponding count of sequence number cycles */
	public long getHighestSqnReceived() {
		// to be implemented..
		return 0;
	}

	/** Gets the interarrival jitter.
	  * @return the interarrival jitter, that is an estimate of the statistical variance of the RTP data packet interarrival time, measured in timestamp units and expressed as an unsigned integer */
	public long getInterarrivalJitter() {
		// to be implemented..
		return 0;
	}

	/** Gets last SR timestamp (LSR).
	  * @return last SR timestamp (LSR), that is the middle 32 bits out of 64 in the NTP timestamp received as part of the most recent RTCP SR packet */
	public long getLSR() {
		// to be implemented..
		return 0;
	}

	/** Gets delay since last SR (DLSR).
	  * @return delay since last SR (DLSR), that is the delay, expressed in units of 1/65536 seconds, between receiving the last SR packet and sending this reception report block */ 
	public long getDLSR() {
		// to be implemented..
		return 0;
	}


	/** Whether is running */
	public boolean isRunning() {
		return running;
	}


	/** Stops running */
	public void halt() {
		rtp_receiver.halt();
		running=false;
	}


	/** Processes a received RTP packet. */
	private void processReceivedPacket(RtpReceiver rtp_receiver, RtpPacket rtp_packet) {
		// only if still running..
		if (!running) return; 
			
		// drop the first packets in order to reduce the effect of an eventual initial packet burst
		if (early_drop_to>0 && System.currentTimeMillis()<early_drop_to) return;
		else early_drop_to=-1;
		
		try {
			if (sequence_check) {
				// discard out of sequence and duplicated packets
				int sqn=rtp_packet.getSequenceNumber();
				if (sqn==last_sqn) return; // discarded duplicated packet
				// else
				if (last_sqn<0) last_sqn=sqn;
				if (sqn<last_sqn) sqn+=TWO_16;
				int sqn_diff=sqn-last_sqn;
				if (sqn_diff>TWO_16/2) return; // discarded out of sequence packet
				// else
				last_sqn=sqn&0xffff;

				if (silence_padding) {
					// silence padding
					long timestamp=rtp_packet.getTimestamp();
					if (last_timestamp<0) last_timestamp=timestamp;
					if (timestamp<last_timestamp) timestamp+=(TWO_32);
					long timestamp_diff=timestamp-last_timestamp;
					last_timestamp=timestamp&0xffffffff;
					if (rtp_payload_format!=null) {
						int silence_len=rtp_payload_format.getSilencePad(sqn_diff,timestamp_diff,silence_buffer,0);
						if (silence_len>0) {
							output_stream.write(silence_buffer,0,silence_len);
						}
					}
				}
			}
			// get payload
			byte[] payload_buf=rtp_packet.getPacketBuffer();
			int payload_off=rtp_packet.getHeaderLength();
			int payload_len=rtp_packet.getPayloadLength();
			
			// remove possible RTP payload format
			int unformatted_len=(rtp_payload_format!=null)? rtp_payload_format.removeRtpPayloadFormat(payload_buf,payload_off,payload_len) : payload_len;
		
			// drop a small percentage of packets
			if (random_early_drop>0 && (++packet_counter)%random_early_drop==0) return;
			// else 

			if (additional_decoder!=null) unformatted_len=additional_decoder.encode(payload_buf,payload_off,unformatted_len,payload_buf,payload_off);

			// write the payload data to the output_stream
			output_stream.write(payload_buf,payload_off,unformatted_len);
			
			// check whether remote socket address is changed
			SocketAddress source_soaddr=rtp_receiver.getRemoteSoAddress();
			if (remote_soaddr==null || !remote_soaddr.equals(source_soaddr)) {
				remote_soaddr=source_soaddr;
				if (listener!=null) listener.onRemoteSoAddressChanged(this,remote_soaddr);
			}
		}	
		catch (Exception e) {
			e.printStackTrace();
			halt();
		}		
	}

	
	/** When the RTP receiver ends. */
	private void processServiceTerminated(RtpReceiver rtp_receiver, Exception error) {
		// close RtpSocket and local UdpSocket
		rtp_receiver.halt();
		if (socket_is_local_attribute && udp_socket!=null) udp_socket.close();		
		// free all
		output_stream=null;
		rtp_receiver=null;
		udp_socket=null;
		
		if (DEBUG) println("rtp receiver terminated");
		if (listener!=null) listener.onRtpStreamReceiverTerminated(this,error);		
	}
	

	/** Sets the packet random early drop (RED) value; if greater than 0, it is the inverse of the packet drop rate.
	  * @param random_early_drop the number of packets that separates two drops; a value of 0 means no drop. */
	public void setRED(int random_early_drop) {
		this.random_early_drop=random_early_drop;
	}


	/** Gets the packet random early drop (RED) value; if greater than 0, it is the inverse of the packet drop rate. */
	public int getRED() {
		return random_early_drop;
	}


	/** Sets RTP payload format. */
	public void setRtpPayloadFormat(RtpPayloadFormat rtp_payload_format) {
		this.rtp_payload_format=rtp_payload_format;
	}


	/** Sets SSRC check mode.
	  * In ssrc-check mode packets with a SSRC that differs from the one in the first  received packet are discarded */
	public void setSsrcCheck(boolean ssrc_check) {
		this.ssrc_check=ssrc_check;
	}


	/** Sets out-of-sequence-check mode.
	  * In out-of-sequence-check mode out-of-sequence and duplicated packets are discarded */
	public void setSequenceCheck(boolean sequence_check) {
		this.sequence_check=sequence_check;
		if (!sequence_check) silence_padding=false;
	}


	/** Sets silence padding mode.
	  * In silence padding mode silence intervals are filled with (silence-equivalent) void data */
	public void setSilencePadding(boolean silence_padding) {
		this.silence_padding=silence_padding;
		if (silence_padding) sequence_check=true;
	}


	/** Debug output */
	private static void println(String str) {
		System.err.println("RtpStreamReceiver: "+str);
	}
	

	public static int byte2int(byte b) {
		//return (b>=0)? b : -((b^0xFF)+1);
		//return (b>=0)? b : b+0x100; 
		return (b+0x100)%0x100;
	}

	public static int byte2int(byte b1, byte b2) {
		return (((b1+0x100)%0x100)<<8)+(b2+0x100)%0x100; 
	}
}


