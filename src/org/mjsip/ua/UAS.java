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
import org.mjsip.sip.call.Call;
import org.mjsip.sip.call.CallListener;
import org.mjsip.sip.call.CallListenerAdapter;
import org.mjsip.sip.call.Messaging;
import org.mjsip.sip.call.MessagingListener;
import org.mjsip.sip.call.RegistrationClient;
import org.mjsip.sip.call.SipUser;
import org.mjsip.sip.message.SipMessage;
import org.mjsip.sip.provider.SipId;
import org.mjsip.sip.provider.SipKeepAlive;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.sip.provider.SipStack;
import org.zoolu.net.SocketAddress;
import org.zoolu.util.SystemUtils;


/** Simple server-side UA (UAS).
  * It handles both incoming calls and SIP MESSAGE messages.
  */
public abstract class UAS {
	
	/** SIP provider */
	SipProvider sip_provider;
	
	/** User */
	SipUser sip_user;

	/** Registration client */
	RegistrationClient rc=null;
	
	/** Keep-alive */
	SipKeepAlive keep_alive=null;
	
	/** Messaging service */
	Messaging mg;
	
	
	/** Call listener */
	CallListener this_call_listener=new CallListenerAdapter() {
		@Override
		public void onCallInvite(Call call, NameAddress callee, NameAddress caller, String sdp, SipMessage invite) {
			super.onCallInvite(call,callee,caller,sdp,invite);
			processCallInvite(call,callee,caller,sdp,invite);
		}
		@Override
		public void onCallModify(Call call, String sdp, SipMessage invite) {
			super.onCallModify(call,sdp,invite);
			CallListenerAdapter.acceptModify(call);
		}
		@Override
		public void onCallBye(Call call, SipMessage bye) {
			super.onCallBye(call,bye);
			processCallBye(call,bye);
		}
	};
	
	/** Messaging listener */
	MessagingListener this_messaging_listener=new MessagingListener() {
		@Override
		public void onMessagingReceivedMessage(Messaging mg, NameAddress sender, NameAddress recipient, String subject, String content_type, byte[] body, SipMessage message) {
			processReceivedMessage(mg,sender,recipient,subject,content_type,body,message);
		}
		@Override
		public void onMessagingDeliverySuccess(Messaging mg, SipId id, NameAddress recipient, String subject, String result) {
			// do-nothing
		}
		@Override
		public void onMessagingDeliveryFailure(Messaging mg, SipId id, NameAddress recipient, String subject, String result) {
			// do-nothing
		}		
	};

	
	
	/** Creates a new UAS waiting for an incoming call.
	 * @param sip_port the SIP port */
	public UAS(int sip_port) {
		this(sip_port,null,null,0);
	}


	/** Creates a new UAS waiting for an incoming call.
	 * <p>
	 * If a SIP user is provided, it is used for registering the user's AOR with the proxy.
	 * @param sip_port the SIP port
	 * @param sip_user SIP user
	 * @param outboud_proxy outboud proxy
	 * @param keepalive_time keep-alive time in seconds */ 
	public UAS(int sip_port, SipUser sip_user, SipURI outboud_proxy, int keepalive_time) {
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
		listen();
	}


	/** Listens for a next incoming call. */
	public void listen() {
		Call call=new Call(sip_provider,sip_user,this_call_listener);	
		call.listen();		
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
	
	
	/** Processes an incoming call invite.
	 * @param call the call
	 * @param callee called user
	 * @param caller calling user
	 * @param sdp SDP body
	 * @param invite the INVITE request */
	protected abstract void processCallInvite(Call call, NameAddress callee, NameAddress caller, String sdp, SipMessage invite);

	
	/** Processes an incoming call invite.
	 * @param call the call
	 * @param bye the BYE request */
	protected abstract void processCallBye(Call call, SipMessage bye);

	
	/** When a new Message is received.
	 * @param mg messaging end-point
	 * @param sender sender address
	 * @param recipient recipient address
	 * @param subject the subject
	 * @param content_type content type 
	 * @param content message content
	 * @param message the SIP message */
	protected abstract void processReceivedMessage(Messaging mg, NameAddress sender, NameAddress recipient, String subject, String content_type, byte[] content, SipMessage message);

}
