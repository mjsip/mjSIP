package org.mjsip.ua;


import java.io.File;
import java.util.Hashtable;
import java.util.Vector;

import javax.sound.sampled.AudioInputStream;

import org.mjsip.media.AudioFile;
import org.mjsip.media.MediaDesc;
import org.mjsip.media.MediaSpec;
import org.mjsip.media.RtpStreamSender;
import org.mjsip.sdp.MediaDescriptor;
import org.mjsip.sdp.OfferAnswerModel;
import org.mjsip.sdp.SdpMessage;
import org.mjsip.sdp.field.ConnectionField;
import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.address.SipURI;
import org.mjsip.sip.call.Call;
import org.mjsip.sip.call.CallListenerAdapter;
import org.mjsip.sip.call.CallState;
import org.mjsip.sip.call.Messaging;
import org.mjsip.sip.call.SipUser;
import org.mjsip.sip.message.SipMessage;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.sip.provider.SipStack;
import org.zoolu.net.IpAddress;
import org.zoolu.sound.SimpleAudioSystem;
import org.zoolu.util.Flags;
import org.zoolu.util.LoggerLevel;
import org.zoolu.util.LoggerWriter;
import org.zoolu.util.SystemUtils;
import org.zoolu.util.Timer;
import org.zoolu.util.TimerListener;;


/** Simple UAS that accepts incoming calls and streams a given media file.
 */
public class JukeboxUAS {
	
	/** URI resource parameter */
	public static String PARAM_RESOURCE="resource";
	
	/** Media file path */
	static String media_path=".";

	/** Maximum call duration in seconds */
	static int max_call_time=300;

	/** Default audio file */
	static String default_audio_file=null;

	/** Whether forcing reverse route */
	static boolean force_reverse_route=false;
	
	/** Whether serving multiple calls */
	static boolean server=false;
	
	/** Media address */
	static String media_addr=IpAddress.getLocalHostAddress().toString();

	/** First media port */
	static int first_media_port=4000;

	/** Last media port */
	static int last_media_port=first_media_port;

	/** Last media port */
	static int media_port=first_media_port;

	static MediaDesc media_desc=MediaDesc.parseMediaDesc("audio "+first_media_port+" RTP/AVP { audio 0 PCMU 8000 160 }");

	/** Media flows */
	static Hashtable media_flows=new Hashtable();

	
	/** Processes a new call invite. */
	static private void processCallInvite(final UAS uas, final SipProvider sip_provider, final Call call, final NameAddress callee, final NameAddress caller, String sdp, final SipMessage invite) {		
		SystemUtils.log(LoggerLevel.INFO,"JUKEBOX: processCallInvite()");
		if (server) uas.listen();
		String audio_file=callee.getAddress().getParameter(PARAM_RESOURCE);
		if (audio_file!=null) audio_file=media_path+"/"+audio_file;
		if (!new File(media_path+"/"+audio_file).isFile()) {
			SystemUtils.log(LoggerLevel.WARNING,"JUKEBOX: audio file '"+audio_file+"' not found");
			audio_file=default_audio_file;
		}
		SdpMessage remote_sdp=new SdpMessage(sdp);
		ConnectionField remote_cf=remote_sdp.getConnection();
		SdpMessage local_start_sdp=new SdpMessage();
		local_start_sdp.setConnection(new ConnectionField(null,media_addr));
		media_desc.setPort(media_port);
		local_start_sdp.addMediaDescriptor(media_desc.toMediaDescriptor());
		final SdpMessage local_sdp=OfferAnswerModel.makeSessionDescriptorProduct(local_start_sdp,remote_sdp);
		
		Vector media=local_sdp.getMediaDescriptors();
		if (media!=null && media.size()>0 && audio_file!=null) {
			MediaDescriptor remote_md=remote_sdp.getMediaDescriptor(media_desc.getMedia());
			ConnectionField cf=remote_md.getConnection();
			String remote_addr=(cf!=null?cf:remote_cf).getAddress();
			int remote_port=remote_md.getMedia().getPort();
			MediaSpec ms=media_desc.getMediaSpecs()[0];
			SystemUtils.log(LoggerLevel.DEBUG,"JUKEBOX: media: "+media_desc.getMedia());
			AudioInputStream audio_input_stream;
			try {
				audio_input_stream=AudioFile.getAudioFileInputStream(audio_file,SimpleAudioSystem.DEFAULT_AUDIO_FORMAT);
				RtpStreamSender rtp_sender=new RtpStreamSender(audio_input_stream,true,ms.getAVP(),ms.getSampleRate(),ms.getChannels(),20,ms.getPacketSize(),null,media_port,remote_addr,remote_port,null);
				rtp_sender.start();
				SystemUtils.log(LoggerLevel.DEBUG,"JUKEBOX: rtp stream: started");
				media_port++;
				if (media_port>last_media_port) media_port=first_media_port;
				media_flows.put(call,rtp_sender);
				new Timer(max_call_time*1000L,new TimerListener() {
					@Override
					public void onTimeout(Timer t) {
						if (call.getState().equals(CallState.C_ACTIVE)) call.hangup();
						processCallBye(uas,sip_provider,call,null);
					}			
				}).start();			
				call.setLocalSessionDescriptor(local_sdp.toString());
				CallListenerAdapter.accept(call);
			}
			catch (Exception e) {
				e.printStackTrace();
				call.refuse();
			}
		}			
	}
	
	/** Processes a new call bye. */
	static private void processCallBye(final UAS uas, final SipProvider sip_provider, final Call call, final SipMessage bye) {
		SystemUtils.log(LoggerLevel.INFO,"JUKEBOX: processCallBye()");
		RtpStreamSender rtp_sender=(RtpStreamSender)media_flows.get(call);
		if (rtp_sender!=null) {
			((RtpStreamSender)rtp_sender).halt();
			media_flows.remove(call);
		}
		if (!server) {
			uas.halt();
		}		
	}
	
	
	/** The main method. */
	public static void main(String[] args) {
		Flags flags=new Flags(args);
		boolean help=flags.getBoolean("-h","prints this help message");
		int sip_port=flags.getInteger("-p",SipStack.default_port,"<port>","SIP port");
		media_addr=flags.getString("--addr",media_addr,"<address>","media IP address");
		String[] media_port_interval=flags.getStringTuple("-m",2,null,"<port1> <port2>","media port interval (i.e. all ports between port1 and port2)");
		default_audio_file=flags.getString("-i",default_audio_file,"<file>","audio file");
		String uri=flags.getString("-r",null,"<uri>","registers the given user URI");
		String outbound_proxy=flags.getString("-o",null,"<uri>","outbound proxy");
		int keepalive_time=flags.getInteger("-t",0,"<secs>","keep-alive time [secs]");
		force_reverse_route=flags.getBoolean("--rroute","forces reverse route when replying with a request");
		server=flags.getBoolean("-s","serves multiple calls");
		boolean prompt_exit=flags.getBoolean("--prompt","prompt for exit");
		
		if (help) {
			System.out.println(flags.toUsageString(UAS.class.getName()));
			return;
		}
		
		SystemUtils.setDefaultLogger(new LoggerWriter(System.out,LoggerLevel.DEBUG));
		
		if (media_port_interval!=null) {
			first_media_port=Integer.parseInt(media_port_interval[0]);
			last_media_port=Integer.parseInt(media_port_interval[1]);
			if (first_media_port<=0 || last_media_port<first_media_port) throw new RuntimeException("Invalid media port range: "+first_media_port+"-"+last_media_port);
		}

		SipUser sip_user=uri!=null? new SipUser(new NameAddress(uri)):null;
		UAS uas=new UAS(sip_port,sip_user,outbound_proxy!=null?new SipURI(outbound_proxy):null,keepalive_time) {
			@Override
			public void processCallInvite(Call call, NameAddress callee, NameAddress caller, String sdp, SipMessage invite) {
				JukeboxUAS.processCallInvite(this,sip_provider,call,callee,caller,sdp,invite);
			}
			@Override
			public void processCallBye(Call call, SipMessage bye) {
				JukeboxUAS.processCallBye(this,sip_provider,call,bye);
			}
			@Override
			protected void processReceivedMessage(Messaging mg, NameAddress sender, NameAddress recipient, String subject, String content_type, byte[] content, SipMessage message) {
				// do nothing
			}			
		};
		
		if (prompt_exit) {
			System.out.println("press 'enter' to exit");
			SystemUtils.readLine();
			uas.halt();
		}
		
	}

}
