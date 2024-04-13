package org.mjsip.media;




/** Listens for RTP stream sender events.
 */
public interface RtpStreamSenderListener {
	
	/** When the stream sender terminated. */
	public void onRtpStreamSenderTerminated(RtpStreamSender rs, Exception error);

}
