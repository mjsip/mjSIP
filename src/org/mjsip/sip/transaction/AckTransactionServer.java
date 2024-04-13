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

package org.mjsip.sip.transaction;


import org.mjsip.sip.message.SipMessage;
import org.mjsip.sip.provider.ConnectionId;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.sip.provider.SipProviderListener;
import org.mjsip.sip.provider.SipStack;
import org.mjsip.sip.provider.TransactionServerId;
import org.zoolu.util.Logger;
import org.zoolu.util.LoggerLevel;
import org.zoolu.util.SystemUtils;
import org.zoolu.util.Timer;
import org.zoolu.util.TimerListener;


/** ACK transaction server should follow an INVITE server transaction within an INVITE Dialog in a SIP UAC.
  * The ACK transaction server sends the final response message and retransmits it
  * several times until the method terminate() is called or the transaction timeout fires.
  */ 
public class AckTransactionServer extends Transaction {
	
	/** the TransactionServerListener that captures the events fired by the AckTransactionServer */
	AckTransactionServerListener transaction_listener;

	/** last response message */
	SipMessage response;
	
	/** retransmission timeout */
	Timer retransmission_to;

	/** transaction timeout */
	Timer transaction_to;
	
	/** Timer listener */
	private TimerListener this_timer_listener=new TimerListener() {
		@Override
		public void onTimeout(Timer t) {
			processTimeout(t);
		}
	};


	/** Creates a new AckTransactionServer.
	  * The AckTransactionServer starts sending a the response message <i>resp</i>.
	  * <br>
	  * It periodically re-sends the response if no ACK request is received.
	  * The response is also sent each time a duplicate INVITE request is received. */
	public AckTransactionServer(SipProvider sip_provider, SipMessage invite, SipMessage resp, AckTransactionServerListener listener) {
		super(sip_provider);
		init(null,invite,resp,listener);
	}  

	/** Creates a new AckTransactionServer.
	  * The AckTransactionServer starts sending a the response message <i>resp</i>.
	  * <br>
	  * It periodically re-sends the response if no ACK request is received.
	  * The response is also sent each time a duplicate INVITE request is received.
	  * <p>
	  * The response is sent through the connection <i>conn_id</i>. */
	public AckTransactionServer(SipProvider sip_provider, ConnectionId connection_id, SipMessage invite, SipMessage resp, AckTransactionServerListener listener) {
		super(sip_provider);
		init(connection_id,invite,resp,listener);
	}  

	/** Initializes timeouts and listener. */
	void init(ConnectionId connection_id, SipMessage invite, SipMessage resp, AckTransactionServerListener listener) {
		this.transaction_listener=listener;
		this.connection_id=connection_id;
		this.response=resp;
		response.setConnectionId(connection_id);
		transaction_id=new TransactionServerId(invite);
		// init the timer just to set the timeout value and label, without listener (never started)
		transaction_to=new Timer(SipStack.transaction_timeout,null);
		retransmission_to=new Timer(SipStack.retransmission_timeout,null);
		// (CHANGE-040905) now timeouts are started when method respond() is called
		//transaction_to=new Timer(transaction_to.getTime(),this_timer_listener);
		//transaction_to.start();
		//if (connection_id==null)
		//{  retransmission_to=new Timer(retransmission_to.getTime(),this_timer_listener);
		//   retransmission_to.start();
		//}
		log(LoggerLevel.INFO,"new transaction-id: "+transaction_id.toString());
	}    

	/** Starts the AckTransactionServer. */
	public void respond() {
		log(LoggerLevel.TRACE,"start");
		changeStatus(STATE_PROCEEDING); 
		// (CHANGE-071209) add sip provider listener
		sip_provider.addSelectiveListener(transaction_id,new SipProviderListener(){
			@Override
			public void onReceivedMessage(SipProvider sip_provider, SipMessage message) {
				processReceivedMessage(sip_provider,message);
			}
		
		});
		//transaction_id=null; // it is not required since no SipProviderListener is implemented 
		// (CHANGE-040905) now timeouts are started when method respond() is called
		transaction_to.start();
		if (connection_id==null) retransmission_to.start();

		sip_provider.sendMessage(response); 
	}  


	/** When a new message is received for to this transaction. */
	private void processReceivedMessage(SipProvider sip_provider, SipMessage msg) {
		if (statusIs(STATE_PROCEEDING) && msg.isRequest()) {
			if (msg.isInvite()) {
				log(LoggerLevel.TRACE,"response retransmission");
				sip_provider.sendMessage(response);
			}
			/*else
			if (msg.isAck()) {
				doTerminate();
				if (transaction_listener!=null) transaction_listener.onTransAck(this,msg);
				transaction_listener=null;
			}*/
			else log(LoggerLevel.WARNING,msg.getRequestLine().getMethod()+" method erroneously passed to this trasaction");
		}
	}


	/** When a timer expires. */
	private void processTimeout(Timer to) {
		try {
			if (to.equals(retransmission_to) && statusIs(STATE_PROCEEDING)) {
				log(LoggerLevel.INFO,"Retransmission timeout expired");
				long timeout=2*retransmission_to.getTime();
				if (timeout>SipStack.max_retransmission_timeout) timeout=SipStack.max_retransmission_timeout;
				retransmission_to=new Timer(timeout,new TimerListener(){
					@Override
					public void onTimeout(Timer t) {
						processTimeout(t);
					}					
				});
				retransmission_to.start();
				sip_provider.sendMessage(response);
			}  
			if (to.equals(transaction_to) && statusIs(STATE_PROCEEDING)) {
				log(LoggerLevel.INFO,"Transaction timeout expired");
				doTerminate();
				//retransmission_to=null;
				//transaction_to=null;
				if (transaction_listener!=null) transaction_listener.onTransAckTimeout(this);
			}  
		}
		catch (Exception e) {
			log(LoggerLevel.INFO,e);
		}
	}   

	/** Method used to drop an active transaction. */
	public void terminate() {
		doTerminate();
		transaction_listener=null;
		//retransmission_to=null;
		//transaction_to=null;
  }


	// *********************** Protected methods ***********************

	/** Moves to terminate state. */
	protected void doTerminate() {
		if (!statusIs(STATE_TERMINATED)) {
			changeStatus(STATE_TERMINATED);
			if (retransmission_to!=null) retransmission_to.halt();
			transaction_to.halt();  
			//retransmission_to=null;
			//transaction_to=null;
			sip_provider.removeSelectiveListener(transaction_id);
		}
	}


	//**************************** Logs ****************************/

	/** Adds a new string to the default log. */
	protected void log(LoggerLevel level, String str) {
		Logger logger=SystemUtils.getDefaultLogger();
		if (logger!=null) logger.log(level,"AckTransactionServer#"+transaction_sqn+": "+str);  
	}

}
