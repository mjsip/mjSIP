/*
 * Copyright (C) 2005 Luca Veltri - University of Parma - Italy
 * 
 * This file is part of MjSip (http://www.mjsip.org)
 * 
 * MjSip is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * MjSip is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with MjSip; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 * Author(s):
 * Luca Veltri (luca.veltri@unipr.it)
 */

package org.mjsip.sip.call;


//import java.util.Iterator;
import java.util.Vector;

import org.mjsip.sdp.OfferAnswerModel;
import org.mjsip.sdp.SdpMessage;
import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.message.SipMessage;
import org.zoolu.util.LoggerLevel;
import org.zoolu.util.Logger;
import org.zoolu.util.SystemUtils;


/** It implements the {@link CallListener} interface providing a dummy implementation
  * of all Call callback functions used to capture call events.
  * <p>
  * It can be extended to manage basic SIP calls.
  * The callback methods defined in this class have a void implementation.
  * This class has been introduced as convenience for creating call listener objects.
  * <br>
  * You can extend this class overriding only methods corresponding to events you want to handle.
  * <p>
  * The only non-empty methods are:
  * <ul>
  *   <li>{@link #onCallInvite(Call, NameAddress, NameAddress, String, SipMessage)},</li>
  *   <li>{@link #onCallModify(Call, String, SipMessage)},</li>
  * </ul>
  * It signals the receiver the ring status (by using method {@link Call#ring()}),
  * computes the SDP answer, and accepts the call (by using method {@link Call#accept(String)})). 
  */
public abstract class CallListenerAdapter implements ExtendedCallListener {

	/** Writes a log message.
	 * @param str the message to write */
	private void log(String str) {
		Logger logger=SystemUtils.getDefaultLogger();
		if (logger!=null) logger.log(LoggerLevel.INFO,getClass(),str);
	}
	

	/** Accepts an incoming call.
	 * @param call the call */
	public static void accept(Call call) {
		String local_session;
		String remote_session=call.getRemoteSessionDescriptor();
		if (remote_session!=null && remote_session.length()>0) {
			SdpMessage remote_sdp=new SdpMessage(remote_session);     
			SdpMessage local_sdp=new SdpMessage(call.getLocalSessionDescriptor());
			SdpMessage new_sdp=new SdpMessage(local_sdp.getOrigin(),remote_sdp.getSessionName(),local_sdp.getConnection(),local_sdp.getTime());
			new_sdp.addMediaDescriptors(local_sdp.getMediaDescriptors());
			new_sdp=OfferAnswerModel.makeSessionDescriptorProduct(new_sdp,remote_sdp);
			local_session=new_sdp.toString();
		}
		else local_session=call.getLocalSessionDescriptor();
		call.accept(local_session);
	}

	/** Accepts a call modify request.
	 * @param call the call */
	public static void acceptModify(Call call) {
		String local_session;
		String remote_session=call.getRemoteSessionDescriptor();
		if (remote_session!=null && remote_session.length()>0) {
			SdpMessage remote_sdp=new SdpMessage(remote_session);
			SdpMessage local_sdp=new SdpMessage(call.getLocalSessionDescriptor());
			SdpMessage new_sdp=new SdpMessage(local_sdp.getOrigin(),remote_sdp.getSessionName(),local_sdp.getConnection(),local_sdp.getTime());
			new_sdp.addMediaDescriptors(local_sdp.getMediaDescriptors());
			new_sdp=OfferAnswerModel.makeSessionDescriptorProduct(new_sdp,remote_sdp);
			local_session=new_sdp.toString();
		}
		else local_session=call.getLocalSessionDescriptor();
		// accept immediately
		call.accept(local_session);
	}


	// *********************** Callback functions ***********************

	/** Sends ringing response.
	  * Callback function called when arriving a new INVITE method (incoming call). */
	@Override
	public void onCallInvite(Call call, NameAddress callee, NameAddress caller, String sdp, SipMessage invite) {
		log("INCOMING");
		call.ring();
	}

	/** Does nothing.
	  * Callback function called when arriving a new Re-INVITE method (re-inviting/call modify). */
	@Override
	public void onCallModify(Call call, String sdp, SipMessage invite) {
		log("RE-INVITE/MODIFY");
	}

	/** Does nothing.
	  * Callback function called when arriving a 183 Session Progress. */
	@Override
	public void onCallProgress(Call call, SipMessage resp) {
		log("PROGRESS");
	}

	/** Does nothing.
	  * Callback function called when arriving a 180 Ringing. */
	@Override
	public void onCallRinging(Call call, SipMessage resp) {
		log("RINGING");
	}

	/** Does nothing.
	  * Callback function called when arriving a 2xx (call accepted). */
	@Override
	public void onCallAccepted(Call call, String sdp, SipMessage resp) {
		log("ACCEPTED/CALL");
	}

	/** Does nothing.
	  * Callback function called when arriving a 4xx (call failure). */
	@Override
	public void onCallRefused(Call call, String reason, SipMessage resp) {
		log("REFUSED ("+reason+")");
	}

	/** Redirects the call when remotely requested.
	  * Callback function called when arriving a 3xx (call redirection). */
	@Override
	public void onCallRedirected(Call call, String reason, Vector contact_list, SipMessage resp) {
		log("REDIRECTION ("+reason+")");
		NameAddress first_contact=new NameAddress((String)contact_list.elementAt(0));
		call.call(first_contact); 
	}

	/** Does nothing.
	  * Callback function called when arriving an ACK method (call confirmed). */
	@Override
	public void onCallConfirmed(Call call, String sdp, SipMessage ack) {
		log("CONFIRMED/CALL");
	}

	/** Does nothing.
	  * Callback function called when arriving an  INFO method. */ 
	@Override
	public void onCallInfo(Call call, String info_package, String content_type, byte[] body, SipMessage msg) {
		log("INFO");
	}

	/** Does nothing.
	  * Callback function called when the invite expires. */
	@Override
	public void onCallTimeout(Call call) {
		log("TIMEOUT/CLOSE");
	}   

	/** Does nothing.
	  * Callback function called when arriving a 2xx (re-invite/modify accepted). */
	@Override
	public void onCallModifyAccepted(Call call, String sdp, SipMessage resp) {
		log("RE-INVITE-ACCEPTED/CALL");
	}

	/** Does nothing.
	  * Callback function called when arriving a 4xx (re-invite/modify failure). */
	@Override
	public void onCallModifyRefused(Call call, String reason, SipMessage resp) {
		log("RE-INVITE-REFUSED ("+reason+")/CALL");
	}

	/** Does nothing.
	  * Callback function called when a re-invite expires. */
	@Override
	public void onCallModifyTimeout(Call call) {
		log("RE-INVITE-TIMEOUT/CALL");
	}   

	/** Does nothing.
	  * Callback function called when arriving a CANCEL request. */
	@Override
	public void onCallCancel(Call call, SipMessage cancel) {
		log("CANCELING");
	}

	/** Does nothing.
	  * Callback function that may be overloaded (extended). Called when arriving a BYE request. */
	@Override
	public void onCallBye(Call call, SipMessage bye) {
		log("CLOSING");
	}

	/** Does nothing.
	  * Callback function that may be overloaded (extended). Called when arriving a response for a BYE request (call closed). */
	@Override
	public void onCallClosed(Call call, SipMessage resp) {
		log("CLOSED");
	}


	/** Callback function called when arriving a new UPDATE method (update request). */
	@Override
	public void onCallUpdate(Call call, String sdp, SipMessage update) {
		String local_session;
		if (sdp!=null && sdp.length()>0) {
			SdpMessage remote_sdp=new SdpMessage(sdp);
			SdpMessage local_sdp=new SdpMessage(call.getLocalSessionDescriptor());
			SdpMessage new_sdp=new SdpMessage(local_sdp.getOrigin(),remote_sdp.getSessionName(),local_sdp.getConnection(),local_sdp.getTime());
			new_sdp.addMediaDescriptors(local_sdp.getMediaDescriptors());
			new_sdp=OfferAnswerModel.makeSessionDescriptorProduct(new_sdp,remote_sdp);
			local_session=new_sdp.toString();
		}
		else local_session=call.getLocalSessionDescriptor();
		// accept immediately
		call.acceptUpdate(local_session);
	}

	/** Callback function called when arriving a 2xx for an UPDATE request. */
	@Override
	public void onCallUpdateAccepted(Call call, String sdp, SipMessage resp) {
		log("UPDATE ACCEPTED");		
	}

	/** Callback function called when arriving a non 2xx for an UPDATE request. */
	@Override
	public void onCallUpdateRefused(Call call, String sdp, SipMessage resp) {
		log("UPDATE REFUSED");				
	}

	/** Does nothing.
	  * Callback function called when arriving a new REFER method (transfer request). */
	@Override
	public void onCallTransfer(ExtendedCall call, NameAddress refer_to, NameAddress refered_by, SipMessage refer) {
		log("REFER-TO/TRANSFER");
	}

	/** Callback function called when arriving a new REFER method (transfer request) with Replaces header, replacing an existing call. */
	@Override
	public void onCallAttendedTransfer(ExtendedCall call, NameAddress refer_to, NameAddress refered_by, String replcall_id, SipMessage refer) {
		log("REFER-TO/TRANSFER");
	}

	/** Does nothing.
	  * Callback function called when a call transfer is accepted. */
	@Override
	public void onCallTransferAccepted(ExtendedCall call, SipMessage resp) {
		log("TRANSFER ACCEPTED");						
	}

	/** Does nothing.
	  * Callback function called when a call transfer is refused. */
	@Override
	public void onCallTransferRefused(ExtendedCall call, String reason, SipMessage resp) {
		log("TRANSFER REFUSED");								
	}

	/** Does nothing.
	  * Callback function called when a call transfer is successfully completed */
	@Override
	public void onCallTransferSuccess(ExtendedCall call, SipMessage notify) {
		log("TRANSFER SUCCESS");
	}

	/** Does nothing.
	  * Callback function called when a call transfer is NOT sucessfully completed */
	@Override
	public void onCallTransferFailure(ExtendedCall call, String reason, SipMessage notify) {
		log("TRANSFER FAILURE");
	}


	@Override
	public void onCallConfirmableProgress(Call call, SipMessage resp) {
		log("CONFIRMABLE PROGRESS");						
	}


	@Override
	public void onCallProgressConfirmed(Call call, SipMessage resp, SipMessage prack) {
		log("PROGRESS CONFIRMED");						
	}

}

