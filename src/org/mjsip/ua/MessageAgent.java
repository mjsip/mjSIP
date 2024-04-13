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

package org.mjsip.ua;


import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.address.SipURI;
import org.mjsip.sip.call.Messaging;
import org.mjsip.sip.call.MessagingListener;
import org.mjsip.sip.call.RegistrationClient;
import org.mjsip.sip.call.RegistrationClientListener;
import org.mjsip.sip.call.SipUser;
import org.mjsip.sip.message.SipMessage;
import org.mjsip.sip.provider.SipId;
import org.mjsip.sip.provider.SipKeepAlive;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.sip.provider.SipStack;
import org.zoolu.util.SystemUtils;
import org.zoolu.net.SocketAddress;


/** Simple Message Agent (MA).
  * <br>
  * It allows a user to send and receive messages.
  */
public class MessageAgent {
	
	/** SIP provider */
	SipProvider sip_provider;
	
	/** User */
	SipUser sip_user;

	/** Registration client */
	RegistrationClient rc=null;
	
	/** Registration client */
	RegistrationClientListener this_rc_listener=new RegistrationClientListener() {
		@Override
		public void onRegistrationSuccess(RegistrationClient regist, NameAddress target, NameAddress contact, int expires, String result) {
			if (listener!=null) listener.onMaRegistrationSuccess(MessageAgent.this,target,contact,expires);
		}
		@Override
		public void onRegistrationFailure(RegistrationClient regist, NameAddress target, NameAddress contact, String result) {
			if (listener!=null) listener.onMaRegistrationFailure(MessageAgent.this,target,contact,result);
		}
	};

	/** Keep-alive */
	SipKeepAlive keep_alive=null;
	
	/** Messaging service */
	Messaging mg;
	
	/** Messaging listener */
	MessagingListener this_messaging_listener=new MessagingListener() {
		@Override
		public void onMessagingReceivedMessage(Messaging mg, NameAddress sender, NameAddress recipient, String subject, String content_type, byte[] body, SipMessage message) {
			if (listener!=null) listener.onMaReceivedMessage(MessageAgent.this,sender,recipient,subject,content_type,body);
		}
		@Override
		public void onMessagingDeliverySuccess(Messaging mg, SipId id, NameAddress recipient, String subject, String result) {
			if (listener!=null) listener.onMaDeliverySuccess(MessageAgent.this,id,recipient,subject);
		}
		@Override
		public void onMessagingDeliveryFailure(Messaging mg, SipId id, NameAddress recipient, String subject, String result) {
			if (listener!=null) listener.onMaDeliveryFailure(MessageAgent.this,id,recipient,subject,result);
		}		
	};

	/** Message listener */
	protected MessageAgentListener listener;

	
	
	/** Creates a new MessageAgent.
	 * @param sip_port SIP port
	 * @param sip_user user configuration
	 * @param outboud_proxy outbound proxy
	 * @param keepalive_time keepalive time, in seconds
	 * @param listener */
	public MessageAgent(int sip_port, SipUser sip_user, SipURI outboud_proxy, int keepalive_time, MessageAgentListener listener) {
		this.listener=listener;
		this.sip_provider=new SipProvider(sip_port);
		this.sip_user=sip_user;
		if (outboud_proxy!=null) sip_provider.setOutboundProxy(outboud_proxy);
		if (sip_user!=null) {
			SipURI aor=new SipURI(sip_user.getAddress().getAddress());
			String registrar_addr=aor.getHost();
			int registrar_port=aor.getPort();
			SipURI registrar=new SipURI(registrar_addr,registrar_port);
			rc=new RegistrationClient(sip_provider,registrar,sip_user.getAddress(),sip_user.getAuhUserName(),sip_user.getAuhRealm(),sip_user.getAuhPasswd(),null);
			rc.loopRegister(3600,3000);
			if (registrar_port<=0) registrar_port=SipStack.default_port;
			if (keepalive_time>0) keep_alive=new SipKeepAlive(sip_provider,new SocketAddress(registrar_addr,registrar_port),keepalive_time*1000L);
		}
		mg=new Messaging(sip_provider,sip_user,this_messaging_listener);
	}   

	
	/** Sends a new text message.
	 * @param recipient the recipient of this message
	 * @param subject the subject
	 * @param content the text message
	 * @return an identifier that can be used for matching the message delivery status */
	public SipId send(String recipient, String subject, String content) {
		return mg.send(new NameAddress(recipient),subject,content);
	}   


	/** Sends a new message.
	 * @param recipient the recipient of this message
	 * @param subject the subject
	 * @param content_type content type
	 * @param content message content
	 * @return an identifier that can be used for matching the message delivery status */
	public SipId send(String recipient, String subject, String content_type, byte[] content) {
		return mg.send(new NameAddress(recipient),subject,content_type,content);
	}
	
	
	/** Unregisters the AOR and stops receiving SIP messages. */
	public void halt() {
		if (sip_provider==null) return;
		//else
		if (rc!=null) rc.unregister();
		if (keep_alive!=null) {
			keep_alive.halt();
			keep_alive=null;
		}
		if (mg!=null) {
			mg.halt();
			mg=null;
		}
		SystemUtils.runAfter(2000,new Runnable() {
			@Override
			public void run() {					
				if (rc!=null) rc.halt();
				sip_provider.halt();
				rc=null;
				sip_provider=null;
			}
		});		
	}

}
