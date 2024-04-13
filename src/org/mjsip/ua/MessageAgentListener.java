package org.mjsip.ua;


import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.provider.SipId;


/** Listener of Message Agent.
 * */
public interface MessageAgentListener {
	
	/** When a new Message is received.
	 * @param ma messaging agent
	 * @param sender sender address
	 * @param recipient recipient address
	 * @param subject the subject
	 * @param content_type content type 
	 * @param body message content */
	public void onMaReceivedMessage(MessageAgent ma, NameAddress sender, NameAddress recipient, String subject, String content_type, byte[] body);

	/** When a message delivery successes.
	 * @param ma messaging agent
	 * @param id identifier that can be used for matching the message that has been sent
	 * @param recipient recipient address
	 * @param subject the subject */
	public void onMaDeliverySuccess(MessageAgent ma, SipId id, NameAddress recipient, String subject);

	/** When a message delivery fails.
	 * @param ma messaging agent
	 * @param id identifier that can be used for matching the message that has been sent
	 * @param recipient recipient address
	 * @param subject the subject
	 * @param result reason of failure */
	public void onMaDeliveryFailure(MessageAgent ma, SipId id, NameAddress recipient, String subject, String result);

	/** When registration successes.
	 * @param ma messaging agent
	 * @param target the registered address-of-record
	 * @param contact the registered contact address
	 * @param expires registration expiration time, in seconds */
	public void onMaRegistrationSuccess(MessageAgent ma, NameAddress target, NameAddress contact, int expires);

	/** When registration fails.
	 * @param ma messaging agent
	 * @param target the registered address-of-record
	 * @param contact the registered contact address
	 * @param result reason of failure */
	public void onMaRegistrationFailure(MessageAgent ma, NameAddress target, NameAddress contact, String result);

}
