package org.mjsip.media;


import org.mjsip.net.UdpRelay;
import org.zoolu.util.ExceptionPrinter;
import org.zoolu.util.LoggerLevel;
import org.zoolu.util.SystemUtils;


/** End-point that sends back the incoming stream.
  */
public class LoopbackMediaStreamer implements MediaStreamer {
	
	/** UdpRelay */
	UdpRelay udp_relay=null;
	

	/** Creates a new media streamer. */
	public LoopbackMediaStreamer(FlowSpec flow_spec) {
		try {
			udp_relay=new UdpRelay(flow_spec.getLocalPort(),flow_spec.getRemoteAddress(),flow_spec.getRemotePort(),null);
			SystemUtils.log(LoggerLevel.INFO,this,udp_relay.toString()+" started");
		}
		catch (Exception e) {
			SystemUtils.log(LoggerLevel.WARNING,this,ExceptionPrinter.getStackTraceOf(e));
		}
	}


	/** Starts media streams. */
	public boolean start() {
		// do nothing, already started..  
		return true;      
	}


	/** Stops media streams. */
	public boolean halt() {
		if (udp_relay!=null) {
			udp_relay.halt();
			udp_relay=null;
			SystemUtils.log(LoggerLevel.DEBUG,this,"relay halted");
		}      
		return true;
	}

}
