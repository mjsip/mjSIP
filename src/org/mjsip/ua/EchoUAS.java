package org.mjsip.ua;


import java.util.Hashtable;
import java.util.Vector;

import org.mjsip.media.FlowSpec;
import org.mjsip.media.LoopbackMediaStreamer;
import org.mjsip.sdp.MediaDescriptor;
import org.mjsip.sdp.SdpMessage;
import org.mjsip.sdp.field.ConnectionField;
import org.mjsip.sdp.field.MediaField;
import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.address.SipURI;
import org.mjsip.sip.call.Call;
import org.mjsip.sip.call.CallListenerAdapter;
import org.mjsip.sip.call.CallState;
import org.mjsip.sip.call.Messaging;
import org.mjsip.sip.call.SipUser;
import org.mjsip.sip.header.RouteHeader;
import org.mjsip.sip.message.SipMessage;
import org.mjsip.sip.message.SipMessageFactory;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.sip.provider.SipStack;
import org.mjsip.sip.transaction.TransactionClient;
import org.zoolu.net.IpAddress;
import org.zoolu.util.Flags;
import org.zoolu.util.LoggerLevel;
import org.zoolu.util.LoggerWriter;
import org.zoolu.util.SystemUtils;
import org.zoolu.util.Timer;
import org.zoolu.util.TimerListener;;


/** Simple UAS that accepts incoming calls and replays to incoming SIP MESSAGE messages.
 * Incoming media flows are replayed back to the remote clients.
 */
public class EchoUAS {
	
	/** Maximum call duration in seconds */
	public static int MAX_CALL_TIME=180;

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

	/** Media relays */
	static Hashtable media_relays=new Hashtable();

	
	/** Processes a new call invite. */
	static private void processCallInvite(final UAS uas, final SipProvider sip_provider, final Call call, final NameAddress callee, final NameAddress caller, String sdp, final SipMessage invite) {		
		SystemUtils.log(LoggerLevel.INFO,"ECHO: processCallInvite()");
		if (server) uas.listen();
		SdpMessage remote_sdp=new SdpMessage(sdp);
		ConnectionField remote_cf=remote_sdp.getConnection();
		SdpMessage local_sdp=new SdpMessage();
		local_sdp.setConnection(new ConnectionField(null,media_addr));
		Vector remote_media=remote_sdp.getMediaDescriptors();
		Vector call_media_relays=new Vector();
		for (int i=0; i<remote_media.size(); i++) {
			MediaDescriptor remote_md=(MediaDescriptor)remote_media.get(i);
			MediaField mf=remote_md.getMedia();
			MediaDescriptor local_md=new MediaDescriptor(new MediaField(mf.getMedia(),media_port,0,mf.getTransport(),mf.getFormats()),null,remote_md.getAttributes());
			local_sdp.addMediaDescriptor(local_md);
			ConnectionField cf=remote_md.getConnection();
			if (cf==null) cf=remote_cf;
			LoopbackMediaStreamer streamer=new LoopbackMediaStreamer(new FlowSpec(null,media_port,cf.getAddress(),mf.getPort(),FlowSpec.FULL_DUPLEX));
			call_media_relays.add(streamer);
			media_port++;
			if (media_port>last_media_port) media_port=first_media_port;
		}
		media_relays.put(call,call_media_relays);
		new Timer(MAX_CALL_TIME*1000L,new TimerListener() {
			@Override
			public void onTimeout(Timer t) {
				if (call.getState().equals(CallState.C_ACTIVE)) call.hangup();
				processCallBye(uas,sip_provider,call,null);
			}			
		}).start();
		call.setLocalSessionDescriptor(local_sdp.toString());
		CallListenerAdapter.accept(call);
	}
	
	/** Processes a new call bye. */
	static private void processCallBye(final UAS uas, final SipProvider sip_provider, final Call call, final SipMessage bye) {
		SystemUtils.log(LoggerLevel.INFO,"ECHO: processCallBye()");
		Vector call_media_relays=(Vector)media_relays.get(call);
		if (call_media_relays!=null) {
			for (int i=0; i<call_media_relays.size(); i++) {
				((LoopbackMediaStreamer)call_media_relays.get(i)).halt();
			}
			media_relays.remove(call);
		}
		if (!server) {
			uas.halt();
		}		
	}
	
	/** Processes a new received message. */
	static private void processReceivedMessage(final UAS uas, final SipProvider sip_provider, final Messaging mg, final NameAddress sender, final NameAddress recipient, final String subject, final String content_type, final byte[] content, final SipMessage message) {
		SystemUtils.log(LoggerLevel.INFO,"ECHO: processReceivedMessage()");
		SipMessage reply=SipMessageFactory.createMessageRequest(sender,recipient,sip_provider.pickCallId(),null,content_type,content);
		if (force_reverse_route) {
			SipURI previous_hop=new SipURI(message.getRemoteAddress(),message.getRemotePort());
			previous_hop.addLr();
			reply.addRouteHeader(new RouteHeader(new NameAddress(previous_hop)));
			TransactionClient tc=new TransactionClient(sip_provider,reply,null);
			tc.request();
		}
		else {
			mg.send(sender,subject,content_type,content);
		}		
	}

	
	/** The main method. */
	public static void main(String[] args) {
		Flags flags=new Flags(args);
		boolean help=flags.getBoolean("-h","prints this help message");
		int sip_port=flags.getInteger("-p",SipStack.default_port,"<port>","SIP port");
		media_addr=flags.getString("--addr",media_addr,"<address>","media IP address");
		String[] media_port_interval=flags.getStringTuple("-m",2,null,"<port1> <port2>","media UDP port interval (i.e. all ports between port1 and port2)");
		String uri=flags.getString("-r",null,"<uri>","registers the given user URI");
		String outbound_proxy=flags.getString("-o",null,"<uri>","outbound proxy");
		int keepalive_time=flags.getInteger("-t",0,"<secs>","keep-alive time [secs]");
		force_reverse_route=flags.getBoolean("--rroute","forces reverse route when replying with a request");
		server=flags.getBoolean("-s","serves multiple calls");
		boolean prompt_exit=flags.getBoolean("--prompt","prompt for exit");
		
		if (help) {
			System.out.println(flags.toUsageString(EchoUAS.class.getName()));
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
				EchoUAS.processCallInvite(this,sip_provider,call,callee,caller,sdp,invite);
			}
			@Override
			public void processCallBye(Call call, SipMessage bye) {
				EchoUAS.processCallBye(this,sip_provider,call,bye);
			}
			@Override
			protected void processReceivedMessage(Messaging mg, NameAddress sender, NameAddress recipient, String subject, String content_type, byte[] content, SipMessage message) {
				EchoUAS.processReceivedMessage(this,sip_provider,mg,sender,recipient,subject,content_type,content,message);
			}			
		};
		
		if (prompt_exit) {
			System.out.println("press 'enter' to exit");
			SystemUtils.readLine();
			uas.halt();
		}
		
	}

}
