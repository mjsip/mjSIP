/*
 * Copyright (C) 2005 Luca Veltri - University of Parma - Italy
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
import java.io.PrintStream;

import org.mjsip.media.MediaDesc;
import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.ua.UserAgent;
import org.mjsip.ua.UserAgentListener;
import org.mjsip.ua.UserAgentProfile;
import org.zoolu.util.LoggerLevel;
import org.zoolu.util.ScheduledWork;
import org.zoolu.util.SystemUtils;


/** Simple command-line-based SIP user agent (UA).
  * It includes audio/video applications.
  * <p>It can use external audio/video tools as media applications.
  * Currently only RAT (Robust Audio Tool) and VIC are supported as external applications.
  */
public class UserAgentCli {
	

	/** SipProvider. */
	protected SipProvider sip_provider;

	/** User Agent */
	protected UserAgent ua;

	/** UserAgentProfile */
	protected UserAgentProfile ua_profile;
			
	/** Standard input */
	BufferedReader stdin=null; 
			
	/** Standard output */
	PrintStream stdout=null; 
	
	/** UA listener */
	UserAgentListener this_ua_listener=new UserAgentListener() {
		@Override
		public void onUaRegistrationSucceeded(UserAgent ua, String result) {
			processUaRegistrationSucceeded(ua,result);
		}
		@Override
		public void onUaRegistrationFailed(UserAgent ua, String result) {
			processUaRegistrationFailed(ua,result);
		}
		@Override
		public void onUaIncomingCall(UserAgent ua, NameAddress callee, NameAddress caller, MediaDesc[] media_descs) {
			processUaIncomingCall(ua,callee,caller,media_descs);
		}
		@Override
		public void onUaCallCancelled(UserAgent ua) {
			processUaCallCancelled(ua);
		}
		@Override
		public void onUaCallProgress(UserAgent ua) {
			processUaCallProgress(ua);
		}
		@Override
		public void onUaCallRinging(UserAgent ua) {
			processUaCallRinging(ua);
		}
		@Override
		public void onUaCallAccepted(UserAgent ua) {
			processUaCallAccepted(ua);
		}
		@Override
		public void onUaCallTransferred(UserAgent ua) {
			processUaCallTransferred(ua);
		}
		@Override
		public void onUaCallFailed(UserAgent ua, String reason) {
			processUaCallFailed(ua,reason);
		}
		@Override
		public void onUaCallClosed(UserAgent ua) {
			processUaCallClosed(ua);
		}
		@Override
		public void onUaMediaSessionStarted(UserAgent ua, String type, String codec) {
			processUaMediaSessionStarted(ua,type,codec);
		}
		@Override
		public void onUaMediaSessionStopped(UserAgent ua, String type) {
			processUaMediaSessionStopped(ua,type);
		}
	};

	
		  
	// ************************* UA internal state *************************
	  
	/** UA_IDLE=0 */
	protected static final String UA_IDLE="IDLE";
	/** UA_INCOMING_CALL=1 */
	protected static final String UA_INCOMING_CALL="INCOMING_CALL";
	/** UA_OUTGOING_CALL=2 */
	protected static final String UA_OUTGOING_CALL="OUTGOING_CALL";
	/** UA_ONCALL=3 */
	protected static final String UA_ONCALL="ONCALL";
	
	/** Call state: <P>UA_IDLE=0, <BR>UA_INCOMING_CALL=1, <BR>UA_OUTGOING_CALL=2, <BR>UA_ONCALL=3 */
	String call_state=UA_IDLE;
	

	/** Changes the call state */
	protected void changeStatus(String state) {
		call_state=state;
		log(LoggerLevel.DEBUG,"state: "+call_state); 
	}

	/** Checks the call state */
	protected boolean statusIs(String state) {
		return call_state.equals(state); 
	}

	/** Gets the call state */
	protected String getStatus() {
		return call_state; 
	}


	// *************************** Public methods **************************

	/** Creates a new UA. */
	public UserAgentCli(SipProvider sip_provider, UserAgentProfile ua_profile) {
		this.sip_provider=sip_provider;
		this.ua_profile=ua_profile;
		ua=new UserAgent(sip_provider,ua_profile,this_ua_listener);      
		if (!ua_profile.no_prompt) stdin=new BufferedReader(new InputStreamReader(System.in)); 
		if (!ua_profile.no_prompt) stdout=System.out;
		run();
	}


	/** Becomes ready for receive a new incoming call. */
	public void readyToReceive() {
		ua.log("WAITING FOR INCOMING CALL");
		if (!ua_profile.audio && !ua_profile.video) ua.log("ONLY SIGNALING, NO MEDIA");       
		//ua.listen();
		changeStatus(UA_IDLE);
		printOut("digit the callee's URI to make a call or press 'enter' to exit");
	} 


	/** Makes a new call */
	public void call(String target_uri) {
		ua.hangup();
		ua.log("CALLING "+target_uri);
		printOut("calling "+target_uri);
		if (!ua_profile.audio && !ua_profile.video) ua.log("ONLY SIGNALING, NO MEDIA");       
		ua.call(target_uri);
		changeStatus(UA_OUTGOING_CALL);
	} 


	/** Accepts an incoming call */
	public void accept() {
		ua.accept();
		changeStatus(UA_ONCALL);
		if (ua_profile.hangup_time>0) automaticHangup(ua_profile.hangup_time); 
		printOut("press 'enter' to hangup"); 
	} 


	/** Terminates a call */
	public void hangup() {
		printOut("hangup");
		ua.hangup();
		changeStatus(UA_IDLE);
		if (ua_profile.call_to!=null) {
			if (ua_profile.re_call_time>0 && (ua_profile.re_call_count--)>0) {
				automaticCall(ua_profile.re_call_time,ua_profile.call_to.toString());
			} 
			else exit();
		}
		else readyToReceive();
	} 
	

	/** Starts the UA */
	void run() {
		
		try {
			// Set the re-invite
			if (ua_profile.re_invite_time>0) {
				reInvite(ua_profile.re_invite_time);
			}

			// Set the transfer (REFER)
			if (ua_profile.transfer_to!=null && ua_profile.transfer_time>0) {
				callTransfer(ua_profile.transfer_to,ua_profile.transfer_time);
			}

			if (ua_profile.do_unregister_all) {
				// ########## unregisters ALL contact URIs
				ua.log("UNREGISTER ALL contact URIs");
				ua.unregisterall();
			} 

			if (ua_profile.do_unregister) {
				// unregisters the contact URI
				ua.log("UNREGISTER the contact URI");
				ua.unregister();
			} 

			if (ua_profile.do_register) {
				// ########## registers the contact URI with the registrar server
				ua.log("REGISTRATION");
				ua.loopRegister(ua_profile.expires,ua_profile.expires/2,ua_profile.keepalive_time);
			}         
			
			if (ua_profile.call_to!=null) {
				// UAC
				call(ua_profile.call_to.toString()); 
				printOut("press 'enter' to cancel");
				SystemUtils.readLine();
				hangup();
			}
			else {
				// UAS + UAC
				if (ua_profile.accept_time>=0) ua.log("AUTO ACCEPT MODE");
				readyToReceive();
				while (stdin!=null) {
					String line=SystemUtils.readLine();
					if (statusIs(UA_INCOMING_CALL)) {
						if (line.toLowerCase().startsWith("n")) {
							hangup();
						}
						else {
							accept();
						}
					}
					else
					if (statusIs(UA_IDLE)) {
						if (line!=null && line.length()>0) {
							call(line);
						}
						else {
							exit();
						}
					}
					else
					if (statusIs(UA_ONCALL)) {
						hangup();
					}
				}
			}
		}
		catch (Exception e)  {  e.printStackTrace(); System.exit(0);  }
	}


	/** Exits */
	public void exit() {
		try {  Thread.sleep(1000);  } catch (Exception e) {}
		System.exit(0);
	}


	// ******************* UserAgent callback functions ******************

	/** When a new call is incoming */
	protected void processUaIncomingCall(UserAgent ua, NameAddress callee, NameAddress caller, MediaDesc[] media_descs) {
		if (ua_profile.redirect_to!=null) {
			// redirect the call
			ua.redirect(ua_profile.redirect_to);
			printOut("call redirected to "+ua_profile.redirect_to);
		}         
		else
		if (ua_profile.accept_time>=0) {
			// automatically accept the call
			//accept();
			automaticAccept(ua_profile.accept_time);
		}
		else          {
			changeStatus(UA_INCOMING_CALL);
			printOut("incoming call from "+caller.toString());
			printOut("accept? [yes/no]");
		}
	}
	
	/** When an outgoing call is stated to be in progress */
	private void processUaCallProgress(UserAgent ua) {
		
	}

	/** When an outgoing call is remotely ringing */
	private void processUaCallRinging(UserAgent ua) {
		
	}

	/** When an outgoing call has been accepted */
	private void processUaCallAccepted(UserAgent ua) {
		changeStatus(UA_ONCALL);
		printOut("call accepted");
		if (ua_profile.hangup_time>0) automaticHangup(ua_profile.hangup_time); 
		else printOut("press 'enter' to hangup");
	}
	
	/** When a call has been transferred */
	private void processUaCallTransferred(UserAgent ua) {
		
	}

	/** When an incoming call has been cancelled */
	private void processUaCallCancelled(UserAgent ua) {
		readyToReceive();
	}

	/** When an outgoing call has been refused or timeout */
	private void processUaCallFailed(UserAgent ua, String reason) {
		if (ua_profile.call_to!=null) exit();
		else readyToReceive();
	}

	/** When a call has been locally or remotely closed */
	private void processUaCallClosed(UserAgent ua) {
		if (ua_profile.call_to!=null) exit();
		else readyToReceive();     
	}

	/** When a new media session is started. */
	private void processUaMediaSessionStarted(UserAgent ua, String type, String codec) {
		//printLog(type+" started "+codec);
	}

	/** When a media session is stopped. */
	private void processUaMediaSessionStopped(UserAgent ua, String type) {
		//log(type+" stopped");
	}


	/** When registration succeeded. */
	private void processUaRegistrationSucceeded(UserAgent ua, String result) {
		log("REGISTRATION SUCCESS: "+result); 
		printOut("UA: REGISTRATION SUCCESS: "+result);
	}

	/** When registration failed. */
	private void processUaRegistrationFailed(UserAgent ua, String result) {
		log("REGISTRATION FAILURE: "+result); 
		printOut("UA: REGISTRATION FAILURE: "+result);
	}
	

	// ************************ scheduled events ************************

	/** Schedules a re-inviting after <i>delay_time</i> secs. It simply changes the contact address. */
	/*void reInvite(final NameAddress contact, final int delay_time) {
		new ScheduledWork(delay_time*1000L) {
			public void doWork() {
				log("AUTOMATIC RE-INVITING/MODIFING");
				ua.modify(contact,null);
			}
		};
	}*/
	/** Schedules a re-inviting after <i>delay_time</i> secs. It simply changes the contact address. */
	void reInvite(final int delay_time) {
		log("AUTOMATIC RE-INVITING/MODIFING: "+delay_time+" secs"); 
		if (delay_time==0) ua.modify(null);
		else new ScheduledWork(delay_time*1000L) {  public void doWork() {  ua.modify(null);  }  };
	}


	/** Schedules a call-transfer after <i>delay_time</i> secs. */
	/*void callTransfer(final NameAddress transfer_to, final int delay_time) {
		new ScheduledWork(delay_time*1000L) {
			public void doWork() {
				printLog("AUTOMATIC REFER/TRANSFER");
				ua.transfer(transfer_to);
			}
		};
	}*/
	/** Schedules a call-transfer after <i>delay_time</i> secs. */
	void callTransfer(final NameAddress transfer_to, final int delay_time) {
		log("AUTOMATIC REFER/TRANSFER: "+delay_time+" secs");
		if (delay_time==0) ua.transfer(transfer_to);
		else new ScheduledWork(delay_time*1000L) {  public void doWork() {  ua.transfer(transfer_to);  }  };
	}

	/** Schedules an automatic answer after <i>delay_time</i> secs. */
	/*void automaticAccept(final int delay_time) {
		new ScheduledWork(delay_time*1000L) {
			public void doWork() {
				log("AUTOMATIC ANSWER");
				accept();
			}
		};
	}*/
	/** Schedules an automatic answer after <i>delay_time</i> secs. */
	void automaticAccept(final int delay_time) {
		log("AUTOMATIC ANSWER: "+delay_time+" secs");
		if (delay_time==0) accept();
		else new ScheduledWork(delay_time*1000L) {  public void doWork() {  accept();  }  };
	}

	/** Schedules an automatic hangup after <i>delay_time</i> secs. */
	/*void automaticHangup(final int delay_time) {
		new ScheduledWork(delay_time*1000L) {
			public void doWork() {
				log("AUTOMATIC HANGUP");
				hangup();
			}
		};
	}*/
	/** Schedules an automatic hangup after <i>delay_time</i> secs. */
	void automaticHangup(final int delay_time) {
		log("AUTOMATIC HANGUP: "+delay_time+" secs");
		if (delay_time==0) hangup();
		else new ScheduledWork(delay_time*1000L) {  public void doWork() {  hangup();  }  };
	}
	
	/** Schedules an automatic re-call after <i>delay_time</i> secs. */
	void automaticCall(final int delay_time, final String remote_uri) {
		log("AUTOMATIC RE-CALL: "+delay_time+" secs");
		if (delay_time==0) call(remote_uri);
		else new ScheduledWork(delay_time*1000L) {  public void doWork() {  call(remote_uri);  }  };
	}


	// ******************************* Logs ******************************

	/** Writes to standard output. */
	protected void printOut(String str) {
		if (stdout!=null) System.out.println(str);
	}

	/** Writes a log message. */
	private void log(String str) {
		log(LoggerLevel.INFO,str);
	}

	/** Writes a log message. */
	private void log(LoggerLevel level, String str) {
		SystemUtils.log(level,this,str);
	}

}
