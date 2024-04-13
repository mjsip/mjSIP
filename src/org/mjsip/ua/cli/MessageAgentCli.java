/*
 * Copyright (C) 2019 Luca Veltri - University of Parma - Italy
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

package org.mjsip.ua.cli;


import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.mjsip.sip.address.GenericURI;
import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.address.SipURI;
import org.mjsip.sip.call.SipUser;
import org.mjsip.sip.provider.SipId;
import org.mjsip.sip.provider.SipStack;
import org.mjsip.ua.MessageAgent;
import org.mjsip.ua.MessageAgentListener;
import org.mjsip.ua.UAS;
import org.zoolu.util.Flags;


/** Simple command-line short-message UA.
  * It allows a user to send and receive short messages, using a command-line interface.
  */
public class MessageAgentCli {
	
	/** Message agent */
	MessageAgent ma;
	
	/** Remote user's address */
	String remote_user=null;
	
	/** Message agent listener */
	MessageAgentListener this_ma_listener=new MessageAgentListener() {
		@Override
		public void onMaReceivedMessage(MessageAgent ma, NameAddress sender, NameAddress recipient, String subject, String content_type, byte[] body) {
			processMaReceivedMessage(ma,sender,recipient,subject,new String(body));
		}
		@Override
		public void onMaDeliverySuccess(MessageAgent ma, SipId id, NameAddress recipient, String subject) {
			// do nothing
		}
		@Override
		public void onMaDeliveryFailure(MessageAgent ma, SipId id, NameAddress recipient, String subject, String result) {
			// do nothing
		}
		@Override
		public void onMaRegistrationSuccess(MessageAgent ma, NameAddress target, NameAddress contact, int expires) {
			// do nothing
		}
		@Override
		public void onMaRegistrationFailure(MessageAgent ma, NameAddress target, NameAddress contact, String result) {
			// do nothing
		}
	};
	
	
	/** Creates a new MA.
	 * @param sip_port SIP port
	 * @param sip_user user configuration
	 * @param outbound_proxy outbound proxy address
	 * @param remote_user remote user's address
	 * @param keepalive_time keepalive time, in seconds */
	public MessageAgentCli(int sip_port, SipUser sip_user, String outbound_proxy, String remote_user, int keepalive_time) {
		ma=new MessageAgent(sip_port,sip_user,outbound_proxy!=null?new SipURI(outbound_proxy):null,keepalive_time,this_ma_listener);
		this.remote_user=remote_user;
	}

	/** Sends a new message. */
	public void send(String recipient, String subject, String text) {
		ma.send(recipient,subject,text);
	}   

	/** When a new Message is received. */
	private void processMaReceivedMessage(MessageAgent ma, NameAddress sender, NameAddress recipient, String subject, String content) {
		remote_user=sender.toString();
		String user_name=sender.getDisplayName();
		if (user_name==null) user_name=new SipURI(sender.getAddress()).getUserName();
		if (user_name==null) user_name=remote_user;
		StringBuffer sb=new StringBuffer();
		sb.append("From ").append(user_name).append(": ");
		if (subject!=null) sb.append("subject: ").append(subject).append(": ");
		sb.append(content.trim());
		println(sb.toString());
	}

	/** Writes a message. */
	private void println(String str) {
		//System.out.println("MA: "+str);  
		System.out.println(str);  
	}

	/** Command-line interface. */
	public void cli() {
		System.out.println("Type the messages to send or 'exit' to quit:");
		while (true) {
			try {
				BufferedReader in=new BufferedReader(new InputStreamReader(System.in));
				String subject=null;
				String message=in.readLine();
				if (message.length()==0 || message.equals("exit")) {
					println("exiting, wait...");
					ma.halt();
					break;
				}
				ma.send(remote_user,subject,message);
			}
			catch (Exception e) {  e.printStackTrace();  }
		} 
	}
	
	
	/** The main method. */
	public static void main(String[] args) {	
		Flags flags=new Flags(args);
		boolean help=flags.getBoolean("-h","prints this help message");
		int sip_port=flags.getInteger("-p",SipStack.default_port,"<port>","SIP port");
		String user=flags.getString("-r",null,"<uri>","registers the given user URI");
		String outbound_proxy=flags.getString("-o",null,"<uri>","outbound proxy");
		int keepalive_time=flags.getInteger("-t",0,"<secs>","keep-alive time [secs]");
		String remote_user=flags.getString("-c",null,"<uri>","remote user URI");		
		
		if (help) {
			System.out.println(flags.toUsageString(UAS.class.getName()));
			return;
		}
		
		//SystemUtils.setDefaultLogger(new LoggerWriter(System.out,LoggerLevel.DEBUG));
		
		SipUser sip_user=user!=null? new SipUser(new NameAddress(user)):null;
		MessageAgentCli mac=new MessageAgentCli(sip_port,sip_user,outbound_proxy,remote_user,keepalive_time);
		mac.cli();
	} 

}
