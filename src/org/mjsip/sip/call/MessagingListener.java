package org.mjsip.sip.call;


import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.message.SipMessage;
import org.mjsip.sip.provider.SipId;


/** Message listener. */
public interface MessagingListener {
	
	/** When a new Message is received.
	 * @param mg messaging end-point
	 * @param sender sender address
	 * @param recipient recipient address
	 * @param subject the subject
	 * @param content_type content type 
	 * @param body message content
	 * @param message the SIP message */
	public void onMessagingReceivedMessage(Messaging mg, NameAddress sender, NameAddress recipient, String subject, String content_type, byte[] body, SipMessage message);

	/** When a message delivery successes.
	 * @param mg messaging end-point
	 * @param id identifier that can be used for matching the message that has been sent
	 * @param recipient recipient address
	 * @param subject the subject
	 * @param result reason of success */
	public void onMessagingDeliverySuccess(Messaging mg, SipId id, NameAddress recipient, String subject, String result);

	/** When a message delivery fails.
	 * @param mg messaging end-point
	 * @param id identifier that can be used for matching the message that has been sent
	 * @param recipient recipient address
	 * @param subject the subject
	 * @param result reason of failure */
	public void onMessagingDeliveryFailure(Messaging mg, SipId id, NameAddress recipient, String subject, String result);

}
