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

package org.mjsip.sip.call;


import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.message.SipMessage;
import org.mjsip.sip.message.SipMessageFactory;
import org.mjsip.sip.message.SipMethods;
import org.mjsip.sip.provider.MethodId;
import org.mjsip.sip.provider.SipId;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.sip.provider.SipProviderListener;
import org.mjsip.sip.transaction.TransactionClient;
import org.mjsip.sip.transaction.TransactionClientListener;
import org.mjsip.sip.transaction.TransactionServer;
import org.zoolu.util.LoggerLevel;
import org.zoolu.util.SystemUtils;


/** Messaging end-point.
  * <br>
  * It sends and receives SIP MESSAGE messages.
  */
public class Messaging {
	
	/** User */
	SipUser user;
	
	/** Message listener */
	protected MessagingListener listener;
	
	/** SIP provider */
	protected SipProvider sip_provider;

	/** SIP listener */
	SipProviderListener this_sip_provider_listener=new SipProviderListener() {
		@Override
		public void onReceivedMessage(SipProvider sip_provider, SipMessage message) {
			processReceivedMessage(sip_provider,message);
		}	
	};		

	/** Transaction client listener */
	TransactionClientListener this_tc_listener=new TransactionClientListener() {
		@Override
		public void onTransProvisionalResponse(TransactionClient tc, SipMessage resp) {
			processTransProvisionalResponse(tc,resp);
		}
		@Override
		public void onTransSuccessResponse(TransactionClient tc, SipMessage resp) {
			processTransSuccessResponse(tc,resp);
		}
		@Override
		public void onTransFailureResponse(TransactionClient tc, SipMessage resp) {
			processTransFailureResponse(tc,resp);
		}
		@Override
		public void onTransTimeout(TransactionClient tc) {
			processTransTimeout(tc);
		}
	};

	
	/** Creates a new message end-point.
	 * @param sip_provider SIP provider
	 * @param user the local user
	 * @param listener the listener */
	public Messaging(SipProvider sip_provider, SipUser user, MessagingListener listener) {
		this.sip_provider=sip_provider;
		this.user=user!=null? user : new SipUser(sip_provider.getContactAddress());
		this.listener=listener;
		sip_provider.addSelectiveListener(new MethodId(SipMethods.MESSAGE),this_sip_provider_listener);
	} 
	
	
	/** Sends a new message.
	 * @param recipient the recipient of this message
	 * @param subject the subject
	 * @param content_type content type
	 * @param content message content
	 * @return an identifier that can be used for matching the message delivery status */
	public SipId send(NameAddress recipient, String subject, String content_type, byte[] content) {
		SipMessage req=SipMessageFactory.createMessageRequest(recipient,user.getAddress(),sip_provider.pickCallId(),subject,content_type,content);
		TransactionClient t=new TransactionClient(sip_provider,req,this_tc_listener);
		t.request();
		return t.getTransactionId();
	}

	
	/** Sends a new text message.
	 * @param recipient the recipient of this message
	 * @param subject the subject
	 * @param content the text message
	 * @return an identifier that can be used for matching the message delivery status */
	public SipId send(NameAddress recipient, String subject, String content) {
		return send(recipient,subject,"application/text",content.getBytes());
	}   

	
	/** Stops receiving messages. */
	public void halt() {
		if (sip_provider!=null) sip_provider.removeSelectiveListener(new MethodId(SipMethods.MESSAGE));
		sip_provider=null;
	} 


	// ******************* Callback functions ********************

	/** When a new Message is received. */
	private void processReceivedMessage(SipProvider provider, SipMessage msg) {
		SystemUtils.log(LoggerLevel.DEBUG,this,"processReceivedMessage(): "+msg.getFirstLine().substring(0,msg.getFirstLine().indexOf('\r')));
		if (msg.isRequest()) {
			(new TransactionServer(sip_provider,msg,null)).respondWith(SipMessageFactory.createResponse(msg,200,null,null));
			NameAddress sender=msg.getFromHeader().getNameAddress();
			NameAddress recipient=msg.getToHeader().getNameAddress();
			String subject=null;
			if (msg.hasSubjectHeader()) subject=msg.getSubjectHeader().getSubject();
			String content_type=null;
			if (msg.hasContentTypeHeader()) content_type=msg.getContentTypeHeader().getContentType();
			byte[] content=msg.getBody();
			if (listener!=null) {
				SystemUtils.log(LoggerLevel.TRACE,this,"processReceivedMessage(): passed to listener: "+listener);
				listener.onMessagingReceivedMessage(this,sender,recipient,subject,content_type,content,msg);
			}
		}
	}

	/** When the TransactionClient goes into the "Completed" state receiving a 2xx response. */
	private void processTransSuccessResponse(TransactionClient tc, SipMessage resp)  {
		onDeliverySuccess(tc,resp.getStatusLine().getReason());
	}

	/** When the TransactionClient goes into the "Completed" state receiving a 300-699 response. */
	private void processTransFailureResponse(TransactionClient tc, SipMessage resp)  {
		onDeliveryFailure(tc,resp.getStatusLine().getReason());
	}
	 
	/** When the TransactionClient is (or goes) in "Proceeding" state and receives a new 1xx provisional response. */
	private void processTransProvisionalResponse(TransactionClient tc, SipMessage resp) {
		// do nothing.
	}
		
	/** When the TransactionClient goes into the "Terminated" state, caused by transaction timeout. */
	private void processTransTimeout(TransactionClient tc) {
		onDeliveryFailure(tc,"Timeout");
	}
	
	/** When the delivery successes. */
	private void onDeliverySuccess(TransactionClient tc, String result) {
		SystemUtils.log(LoggerLevel.DEBUG,this,"Message successfully delivered ("+result+")");
		SipMessage req=tc.getRequestMessage();
		NameAddress recipient=req.getToHeader().getNameAddress();
		String subject=null;
		if (req.hasSubjectHeader()) subject=req.getSubjectHeader().getSubject();
		if (listener!=null) listener.onMessagingDeliverySuccess(this,tc.getTransactionId(),recipient,subject,result);
	}

	/** When the delivery fails. */
	private void onDeliveryFailure(TransactionClient tc, String result) {
		SystemUtils.log(LoggerLevel.DEBUG,this,"Message delivery failed ("+result+")");
		SipMessage req=tc.getRequestMessage();
		NameAddress recipient=req.getToHeader().getNameAddress();
		String subject=null;
		if (req.hasSubjectHeader()) subject=req.getSubjectHeader().getSubject();
		if (listener!=null) listener.onMessagingDeliveryFailure(this,tc.getTransactionId(),recipient,subject,result);
	}

}
