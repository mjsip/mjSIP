/*
 * Copyright (C) 2006 Luca Veltri - University of Parma - Italy
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

package org.mjsip.server;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;

import org.mjsip.sip.address.GenericURI;
import org.mjsip.sip.address.SipURI;
import org.mjsip.sip.header.RequestLine;
import org.mjsip.sip.message.SipMessage;
import org.mjsip.sip.message.SipMessageFactory;
import org.mjsip.sip.message.SipMethods;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.sip.provider.SipStack;
import org.mjsip.sip.transaction.InviteTransactionServer;
import org.mjsip.sip.transaction.ProxyInviteTransactionClient;
import org.mjsip.sip.transaction.Transaction;
import org.mjsip.sip.transaction.TransactionClient;
import org.mjsip.sip.transaction.TransactionClientListener;
import org.mjsip.sip.transaction.TransactionServer;
import org.zoolu.util.Flags;
import org.zoolu.util.Logger;
import org.zoolu.util.LoggerLevel;
import org.zoolu.util.SystemUtils;


/** StatefulProxy server. 
  * Class StatefulProxy implement a stateful SIP proxy server.
  * It extends class Registrar. A StatefulProxy can work as simply SIP proxy,
  * or it can handle calls for registered users. 
  */
public class StatefulProxy extends Proxy {
	
	/** Transactions state */
	protected StatefulProxyState state=null;
	
	/** End timeout for client transactions ("Timer C" in RFC 3261) */
	//Timer proxy_transaction_to;
	
	/** SIP provider for client transactions */
	protected SipProvider sip_provider_client;   

	/** SIP provider for server transactions */
	protected SipProvider sip_provider_server;
	
	/** Transaction client listener */
	TransactionClientListener this_tc_listener=new TransactionClientListener() {
		@Override
		public void onTransProvisionalResponse(TransactionClient tc, SipMessage resp) {
			processProvisionalResponse(tc,resp);
		}
		@Override
		public void onTransSuccessResponse(TransactionClient tc, SipMessage resp) {
			processSuccessResponse(tc,resp);
		}
		@Override
		public void onTransFailureResponse(TransactionClient tc, SipMessage resp) {
			processFailureResponse(tc,resp);
		}
		@Override
		public void onTransTimeout(TransactionClient tc) {
			processTimeout(tc);
		}
	};

		
	/** Creates a void StatefulProxy */
	protected StatefulProxy() {}


	/** Inits the stateful server */
	private void init() {
		sip_provider_client=sip_provider;
		sip_provider_server=sip_provider;
		state=new StatefulProxyState();
	}   

		
	/** Creates a new StatefulProxy that acts also as location server for registered users. */
	/*public StatefulProxy(SipProvider provider_server, SipProvider provider_client, ServerProfile server_profile) {
		super(provider_server,server_profile);
		sip_provider_client=provider_client;
		sip_provider_server=provider_server;
		init();
	}*/


	/** Creates a new StatefulProxy that acts also as location server for registered users. */
	public StatefulProxy(SipProvider provider, ServerProfile server_profile) {
		super(provider,server_profile);
		init();
	}


	/** When a new request is received for the local server */
	@Override
	protected void processRequestToLocalServer(SipMessage req) {
		log(LoggerLevel.DEBUG,"inside processRequestToLocalServer(msg)");
		super.processRequestToLocalServer(req);
	}


	/** When a new request message is received for a local user */
	@Override
	protected void processRequestToLocalUser(SipMessage msg) {
		log(LoggerLevel.DEBUG,"inside processRequestToLocalUser(msg)");

		if (msg.isAck()) {
			log(LoggerLevel.DEBUG,"ACK received out of an active InviteServerTransaction, message forwarded");
			// ACK out of an active transaction is treated in statelss manner
			super.processRequestToLocalUser(msg);
			return; 
		}
		
		TransactionServer ts;
		if (msg.isInvite()) ts=new InviteTransactionServer(sip_provider_server,msg,null);
		else ts=new TransactionServer(sip_provider_server,msg,null);
	
		// proxy authentication
		/*if (server_profile.do_proxy_authentication && !msg.isAck() && !msg.isCancel()) {
			SipMessage err_resp=as.authenticateProxyRequest(msg);  
			if (err_resp!=null) {
				ts.respondWith(err_resp);
				return;
			}
		}*/

		// message targets
		Vector targets=getTargets(msg);

		if (targets.isEmpty()) {
			// prefix-based forwarding
			GenericURI request_uri=msg.getRequestLine().getAddress();
			SipURI new_target=null;
			if (isResponsibleFor(msg.getFromHeader().getNameAddress().getAddress())) new_target=getAuthPrefixBasedProxyingTarget(request_uri);
			if (new_target==null) new_target=getPrefixBasedProxyingTarget(request_uri);
			if (new_target!=null) targets.addElement(new_target.toString());
		}
		if (targets.isEmpty()) {
			log(LoggerLevel.INFO,"No target found, message discarded");
			// the msg is not an ACK (already checked)
			sendStatefulServerResponse(ts,SipMessageFactory.createResponse(msg,404,null,null));
			return;
		}

		log(LoggerLevel.DEBUG,"message will be forwarded to "+targets.size()+" user's contact(s)"); 
		for (int i=0; i<targets.size(); i++)  {
			SipURI target_uri=new SipURI((String)(targets.elementAt(i)));
			SipMessage request=new SipMessage(msg);
			request.removeRequestLine();
			request.setRequestLine(new RequestLine(msg.getRequestLine().getMethod(),target_uri));

			updateProxyingRequest(request);         

			TransactionClient tc;
			if (msg.isInvite()) tc=new ProxyInviteTransactionClient(sip_provider_client,request,this_tc_listener);
			else tc=new TransactionClient(sip_provider_client,request,this_tc_listener);
			//printLog("DEBUG: processLocalRequest()\r\n"+tc.getRequestMessage().toString(),LoggerWriter.LEVEL_LOWER);
			state.addClient(ts,tc);
		}
		HashSet clients=state.getClients(ts);
		for (Iterator i=clients.iterator(); i.hasNext(); ) ((TransactionClient)i.next()).request();
	}

	
	/** When a new request message is received for a remote UA */
	@Override
	protected void processRequestToRemoteUA(SipMessage msg) {
		log(LoggerLevel.DEBUG,"inside processRequestToRemoteUA(msg)");
		if (msg.isAck()) {
			log(LoggerLevel.DEBUG,"ACK received out of an active InviteServerTransaction, message forwarded");
			// just send the ack..
			super.processRequestToRemoteUA(msg);
			return; 
		}
		TransactionServer ts;
		if (msg.isInvite()) ts=new InviteTransactionServer(sip_provider_server,msg,null);
		else ts=new TransactionServer(sip_provider_server,msg,null);

		if (!server_profile.is_open_proxy) {
			// check whether the caller or callee is a local user 
			if (!isResponsibleFor(msg.getFromHeader().getNameAddress().getAddress()) && !isResponsibleFor(msg.getToHeader().getNameAddress().getAddress())) {
				log(LoggerLevel.INFO,"both caller and callee are not registered with the local server: proxy denied.");
				ts.respondWith(SipMessageFactory.createResponse(msg,503,null,null));
				return;
			}
		}

		// proxy authentication
		/*if (server_profile.do_proxy_authentication && !msg.isAck() && !msg.isCancel()) {
			SipMessage err_resp=as.authenticateProxyRequest(msg);  
			if (err_resp!=null) {
				ts.respondWith(err_resp);
				return;
			}
		}*/

		// domain-based forwarding
		RequestLine rl=msg.getRequestLine();
		GenericURI request_uri=rl.getAddress();
		SipURI nexthop=null;
		if (isResponsibleFor(msg.getFromHeader().getNameAddress().getAddress())) nexthop=getAuthDomainBasedProxyingTarget(request_uri);
		if (nexthop==null) nexthop=getDomainBasedProxyingTarget(request_uri);
		if (nexthop!=null) msg.setRequestLine(new RequestLine(rl.getMethod(),nexthop));
		
		updateProxyingRequest(msg);         

		TransactionClient tc;
		if (msg.isInvite()) tc=new ProxyInviteTransactionClient(sip_provider_client,msg,this_tc_listener);
		else tc=new TransactionClient(sip_provider_client,msg,this_tc_listener);
		state.addClient(ts,tc);
		tc.request(); 
	}   


	/** When a new response message is received */
	@Override
	protected void processResponse(SipMessage resp) {
		log(LoggerLevel.DEBUG,"inside processResponse(msg)");
		//printLog("Response received out of an active ClientTransaction, message discarded",LoggerWriter.LEVEL_HIGH);
		super.processResponse(resp);   
	}


	/** Sends a server final response */
	protected void sendStatefulServerResponse(TransactionServer ts, SipMessage resp) {
		log(LoggerLevel.DEBUG,"inside sendStatefulServerResponse(msg)");
	log(LoggerLevel.DEBUG,"Server response: "+resp.getStatusLine().toString());
		ts.respondWith(resp);
	}   


	/** Process provisional response */
	protected void processProvisionalResponse(TransactionClient transaction, SipMessage resp) {
		log(LoggerLevel.DEBUG,"inside processProvisionalResponse(t,resp)");
		int code=resp.getStatusLine().getCode();
		TransactionServer ts=state.getServer(transaction);
		if (ts!=null && code!=100) {
			updateProxyingResponse(resp);
			if (resp.hasViaHeader()) ts.respondWith(resp);
		}
	}
	
	/** Process failure response */
	protected void processFailureResponse(TransactionClient transaction, SipMessage resp) {
		log(LoggerLevel.DEBUG,"inside processFailureResponse(t,resp)");
		TransactionServer ts=state.getServer(transaction);
		state.removeClient(transaction);
		if (ts==null) return;
		if (!state.hasServer(ts)) return;
		// updates the non-2xx final response
		state.setFinalResponse(ts,resp);
		// if there are no more pending clients, sends the final response
		HashSet clients=state.getClients(ts);
		if (clients.isEmpty()) {
			log(LoggerLevel.TRACE,"only this t_client remained: send the response");
			resp=state.getFinalResponse(ts);
			updateProxyingResponse(resp);
			if (resp.hasViaHeader()) ts.respondWith(resp); else ts.terminate();
			state.removeServer(ts);
		}
		log(LoggerLevel.TRACE,"t_clients still active: "+state.numOfClients());
		log(LoggerLevel.TRACE,"t_servers still active: "+state.numOfClients());
	}

	/** Process success response */
	protected void processSuccessResponse(TransactionClient transaction, SipMessage resp) {
		log(LoggerLevel.DEBUG,"inside processSuccessResponse(t,resp)");
		TransactionServer ts=state.getServer(transaction);
		state.removeClient(transaction);
		if (ts==null) return;
		updateProxyingResponse(resp);
		if (resp.hasViaHeader()) {
			ts.respondWith(resp);
			if (!state.hasServer(ts)) return;
			//else
			// cancel all other pending transaction clients
			HashSet clients=state.getClients(ts);
			//printLog("Cancel pending clients..",LoggerWriter.LEVEL_LOW);
			// cancel ONLY INVITE transaction clients
			if (transaction.getTransactionMethod().equals(SipMethods.INVITE)) {
				//log(LoggerLevel.TRACE,"Cancelling "+clients.size()+" pending clients");
				log(LoggerLevel.TRACE,clients.size()+" pending clients");
				int canc_counter=0;
				for (Iterator i=clients.iterator(); i.hasNext(); ) {
					Transaction tc=(Transaction)i.next();
					// cancel ONLY transaction clients that has (only) received a provisional response
					if (tc.isProceeding()) {
						SipMessage cancel=SipMessageFactory.createCancelRequest(tc.getRequestMessage());
						TransactionClient tc_cancel=new TransactionClient(sip_provider_server,cancel,null);
						tc_cancel.request();
						canc_counter++;
					}
				}
				log(LoggerLevel.TRACE,"Cancelled "+canc_counter+" clients in \"proceeding\" state");
			}
			state.removeServer(ts);
		}
		log(LoggerLevel.TRACE,"t_clients still active: "+state.numOfClients());
		log(LoggerLevel.TRACE,"t_servers still active: "+state.numOfServers());
	}


	/** Process tmeout */
	protected void processTimeout(TransactionClient transaction) {
		log(LoggerLevel.DEBUG,"inside processTimeout(t)");
		TransactionServer ts=state.getServer(transaction);
		state.removeClient(transaction);
		if (ts==null) return;
		HashSet clients=state.getClients(ts);
		if (clients==null) return;
		if (clients.isEmpty()) {
			log(LoggerLevel.TRACE,"responding..");
			//printLog("DEBUG:\r\n"+state.getFinalResponse(ts),LoggerWriter.LEVEL_LOW);
			SipMessage resp=state.getFinalResponse(ts);
			updateProxyingResponse(resp);
			if (resp.hasViaHeader()) sendStatefulServerResponse(ts,resp);
			else ts.terminate();
			state.removeServer(ts);
		}      
		log(LoggerLevel.TRACE,"t_clients still active: "+state.numOfClients());
		log(LoggerLevel.TRACE,"t_servers still active: "+state.numOfClients());
	}

	// ****************************** Logs *****************************

	/** Adds a new string to the default Log. */
	private void log(LoggerLevel level, String str) {
		Logger logger=SystemUtils.getDefaultLogger();
		if (logger!=null) logger.log(level,"StatefulProxy: "+str);  
	}


	// ****************************** MAIN *****************************

	/** The main method. */
	public static void main(String[] args) {
		
		Flags flags=new Flags(args);
		boolean help=flags.getBoolean("-h","prints this message");
		boolean prompt_exit=flags.getBoolean("--prompt","prompt for exit");
		String file=flags.getString("-f",null,"<file>","loads configuration from the given file");
		
		if (help) {
			System.out.println(flags.toUsageString(StatefulProxy.class.getName()));
			return;
		}
						
		SipStack.init(file);
		SipProvider sip_provider=new SipProvider(file);
		ServerProfile server_profile=new ServerProfile(file);
		
		StatefulProxy sproxy=new StatefulProxy(sip_provider,server_profile);   
		
		if (prompt_exit) {
			System.out.println("press 'enter' to exit");
			SystemUtils.readLine();
			System.exit(0);
		}
	}
	
}
