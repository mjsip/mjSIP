/*
 * Copyright (C) 2006 Luca Veltri - University of Parma - Italy
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

package org.mjsip.ua;



import java.io.PrintStream;

import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.address.SipURI;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.sip.provider.SipStack;
import org.mjsip.ua.cli.UserAgentCli;
import org.mjsip.ua.gui.UserAgentGui;
import org.zoolu.util.Flags;



/** Simple SIP UA (User Agent) that can be executed with a GUI (it runs a {@link org.mjsip.ua.gui.UserAgentGui})
  * or with a command-line interface (it runs a {@link org.mjsip.ua.cli.UserAgentCli}).
  * <p>
  * Class UA allows the user to set several configuration parameters
  * directly from comman-line before starting the proper UA.
  */
public class UA {
	

	/** No GUI */ 
	protected static Boolean no_gui=new Boolean(false);

	/** Print stream */ 
	public static PrintStream stdout=System.out;


	/** Configuration file */ 
	//public static String file=null;

	/** SipProvider */ 
	public static SipProvider sip_provider=null;

	/** UserAgentProfile */ 
	public static UserAgentProfile ua_profile=null;


	/** Parses command-line options and inits the SIP stack, a SIP provider and an UA profile. */
	public static boolean init(String program, String[] args) {
		
		Flags flags=new Flags(args);
		
		while (flags.getBoolean("--skip",null)); // skip
		boolean help=flags.getBoolean("-h","prints this message");
		String config_file=flags.getString("-f",null,"<file>","loads configuration from the given file");
		
		Boolean unregist=flags.getBoolean("-u",null,"unregisters the contact address with the registrar server (the same as -g 0)");
		Boolean unregist_all=flags.getBoolean("-z",null,"unregisters ALL contact addresses");
		String regist_aor=flags.getString("-r",null,"<uri>","registers the given Address-Of-Record (e.g. alice@example.com)");
		int expire_time=flags.getInteger("-e",-1,"<secs>","registration expiration time, in seconds");

		Boolean no_offer=flags.getBoolean("-n",null,"no offer in invite (offer/answer in 2xx/ack)");
		String call_to=flags.getString("-c",null,"<call_to>","calls a remote user");      
		int accept_time=flags.getInteger("-y",-1,"<secs>","auto answers after given seconds");      
		int hangup_time=flags.getInteger("-t",-1,"<secs>","auto hangups after given seconds (0 means manual hangup)");
		int re_call_time=flags.getInteger("--re-call-time",-1,"<time>","re-calls after given seconds");
		int re_call_count=flags.getInteger("--re-call-count",-1,"<n>","number of successive automatic re-calls");
		String redirect_to=flags.getString("--redirect",null,"<uri>","redirects the call to new user <uri>");
		String[] transfer=flags.getStringTuple("-q",2,null,"<uri> <secs>","transfers the call to <uri> after <secs> seconds");
		String transfer_to=transfer!=null? transfer[0] : null; 
		int transfer_time=transfer!=null? Integer.parseInt(transfer[1]) : -1;
		int re_invite_time=flags.getInteger("-i",-1,"<secs>","re-invites after given seconds");
		
		int host_port=flags.getInteger("-p",SipStack.default_port,"<port>","local SIP port, used ONLY without -f option");
		int media_port=flags.getInteger("-m",0,"<port>","(first) local media port");
		String via_addr=flags.getString("--via-addr",SipProvider.AUTO_CONFIGURATION,"<addr>","host via address, used ONLY without -f option");
		String outbound_proxy=flags.getString("-o",null,"<addr>[:<port>]","uses the given outbound proxy");
		long keepalive_time=flags.getLong("--keep-alive",-1,"<msecs>","send keep-alive packets each given milliseconds");

		Boolean audio=flags.getBoolean("-a",null,"audio");
		Boolean video=flags.getBoolean("-v",null,"video");

		Boolean loopback=flags.getBoolean("--loopback",null,"loopback mode, received media are sent back to the remote sender");
		Boolean recv_only=flags.getBoolean("--recv-only",null,"receive only mode, no media is sent");
		Boolean send_only=flags.getBoolean("--send-only",null,"send only mode, no media is received");
		Boolean send_tone=flags.getBoolean("--send-tone",null,"send only mode, an audio test tone is generated");
		String  send_file=flags.getString("--send-file",null,"<file>","audio is played from the specified file");
		String  recv_file=flags.getString("--recv-file",null,"<file>","audio is recorded to the specified file");
		String  send_video_file=flags.getString("--send-video-file",null,"<file>","video is played from the specified file");
		String  recv_video_file=flags.getString("--recv-video-file",null,"<file>","video is recorded to the specified file");
		Boolean no_system_audio=flags.getBoolean("--no-audio",null,"do not use system audio");

		String contact_uri=flags.getString("--contact-uri",null,"<uri>","user's contact URI");
		String display_name=flags.getString("--display-name",null,"<str>","display name");
		String user=flags.getString("--user",null,"<user>","user name");
		
		String proxy=flags.getString("--proxy",null,"<proxy>","proxy server");
		String registrar=flags.getString("--registrar",null,"<registrar>","registrar server");
		
		String transport=flags.getString("--transport",null,"<proto>","use the given transport protocol for SIP (only without config file)");
		
		String auth_user=flags.getString("--auth-user",null,"<user>","user name used for authenticat");
		String auth_realm=flags.getString("--auth-realm",null,"<realm>","realm used for authentication");
		String auth_passwd=flags.getString("--auth-passwd",null,"<passwd>","passwd used for authentication");

		int debug_level=flags.getInteger("--debug-level",-1,"<level>","debug level (level=0 means no log)");
		String log_path=flags.getString("--log-path",null,"<path>","log folder");

		no_gui=flags.getBoolean("--no-gui",no_gui,"do not use graphical user interface");
		Boolean no_prompt=flags.getBoolean("--no-prompt",null,"do not prompt");
		String[] remaining_params=flags.getRemainingStrings(true,null,null);
		
		if (remaining_params.length>0) {
			println("unrecognized param '"+remaining_params[0]+"'\n");
		}
		if (remaining_params.length>0 || help) {
			println(flags.toUsageString(program));
			return false;
		}
		try {
			// init SipStack
			SipStack.init(config_file);
			if (debug_level>=0) SipStack.debug_level=debug_level;
			if (log_path!=null) SipStack.log_path=log_path;

			// init sip_provider
			if (config_file!=null) sip_provider=new SipProvider(config_file);
			else  {
				if (transport==null) sip_provider=new SipProvider(via_addr,host_port);
				else sip_provider=new SipProvider(via_addr,host_port,new String[]{transport});
			}
			if (outbound_proxy!=null) sip_provider.setOutboundProxy(new SipURI(outbound_proxy));

			// init ua_profile
			ua_profile=new UserAgentProfile(config_file);
			if (no_prompt!=null) ua_profile.no_prompt=no_prompt.booleanValue();
			if (no_system_audio!=null) ua_profile.no_system_audio=no_system_audio.booleanValue();

			if (unregist!=null) ua_profile.do_unregister=unregist.booleanValue();
			if (unregist_all!=null) ua_profile.do_unregister_all=unregist_all.booleanValue();
			if (expire_time>=0) ua_profile.expires=expire_time;
			if (keepalive_time>=0) ua_profile.keepalive_time=keepalive_time;
			if (no_offer!=null) ua_profile.no_offer=no_offer.booleanValue();
			if (call_to!=null) ua_profile.call_to=new NameAddress(call_to);
			if (redirect_to!=null) ua_profile.redirect_to=new NameAddress(redirect_to);
			if (transfer_to!=null) ua_profile.transfer_to=new NameAddress(transfer_to);
			if (transfer_time>0) ua_profile.transfer_time=transfer_time;
			if (accept_time>=0) ua_profile.accept_time=accept_time;
			if (hangup_time>0) ua_profile.hangup_time=hangup_time;
			if (re_invite_time>0) ua_profile.re_invite_time=re_invite_time;
			if (re_call_time>0) ua_profile.re_call_time=re_call_time;
			if (re_call_count>0) ua_profile.re_call_count=re_call_count;
			if (audio!=null) ua_profile.audio=audio.booleanValue();
			if (video!=null) ua_profile.video=video.booleanValue();
			if (media_port>0) ua_profile.setMediaPort(media_port);

			if (regist_aor!=null) {
				ua_profile.setAOR(new NameAddress(regist_aor));
				ua_profile.do_register=true;
			}
			if (display_name!=null) ua_profile.display_name=display_name;
			if (user!=null) ua_profile.user=user;
			if (proxy!=null) ua_profile.proxy=proxy;
			if (registrar!=null) ua_profile.registrar=registrar;   
			if (auth_user!=null) ua_profile.auth_user=auth_user;
			if (auth_realm!=null) ua_profile.auth_realm=auth_realm;
			if (auth_passwd!=null) ua_profile.auth_passwd=auth_passwd; 
			
			if (loopback!=null) ua_profile.loopback=loopback.booleanValue();
			if (recv_only!=null) ua_profile.recv_only=recv_only.booleanValue();
			if (send_only!=null) ua_profile.send_only=send_only.booleanValue();             
			if (send_tone!=null) ua_profile.send_tone=send_tone.booleanValue();
			if (send_file!=null) ua_profile.send_file=send_file;
			if (recv_file!=null) ua_profile.recv_file=recv_file;
			if (send_video_file!=null) ua_profile.send_video_file=send_video_file;
			if (recv_video_file!=null) ua_profile.recv_video_file=recv_video_file;

			// use audio as default media in case of..
			if ((recv_only!=null || send_only!=null || send_tone!=null || send_file!=null || recv_file!=null) && video==null) ua_profile.audio=true;

			return true;
		}
		catch (Exception e) {			
			e.printStackTrace();
			return false;
		}
	}


	/** Prints a message to standard output. */
	protected static void println(String str) {
		if (stdout!=null) stdout.println(str);
	}


	/** The main method. */
	public static void main(String[] args) {
		
		println("MJSIP UA "+SipStack.version);

		if (!init(UA.class.getName(),args)) System.exit(0);
		// else
		if (no_gui.booleanValue()) new UserAgentCli(sip_provider,ua_profile);
		else new UserAgentGui(sip_provider,ua_profile);
	}

}
