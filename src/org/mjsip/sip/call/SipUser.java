/*
 * Copyright (C) 2013 Luca Veltri - University of Parma - Italy
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



import org.mjsip.sip.address.NameAddress;



/** SIP user.
  */
public class SipUser {
	
	/** User's address (AOR) */
	protected NameAddress user_naddr;

	/** User's contact address */
	//protected NameAddress contact_naddr;
	
	/** User's name for authentication */
	protected String auth_user;

	/** User's realm for authentication */
	protected String auth_realm;

	/** User's password for authentication */
	protected String auth_passwd;
	


	/** Creates a new user.
	  * @param user_naddr user's address (AOR) */
	public SipUser(NameAddress user_naddr) {
		this(user_naddr,null,null,null,null);
	}


	/** Creates a new user.
	  * @param user_naddr user's address (AOR)
	  * @param contact_naddr user's contact address (contact URI) */
	/*public SipUser(NameAddress user_naddr, NameAddress contact_naddr) {
		this(user_naddr,contact_naddr,null,null,null);
	}*/


	/** Creates a new user.
	  * @param user_naddr user's address (AOR)
	  * @param auth_user authentication user's name
	  * @param auth_realm authentication realm
	  * @param auth_passwd authentication password */
	public SipUser(NameAddress user_naddr, String auth_user, String auth_realm, String auth_passwd) {
		this(user_naddr,null,auth_user,auth_realm,auth_passwd);
	}


	/** Creates a new user.
	  * @param user_naddr user's address (AOR)
	  * @param contact_naddr user's contact address (contact URI)
	  * @param auth_user authentication user's name
	  * @param auth_realm authentication realm
	  * @param auth_passwd authentication password */
	private SipUser(NameAddress user_naddr, NameAddress contact_naddr, String auth_user, String auth_realm, String auth_passwd) {
		this.user_naddr=user_naddr;
		//this.contact_naddr=contact_naddr;
		this.auth_user=auth_user;
		this.auth_realm=auth_realm;
		this.auth_passwd=auth_passwd;
	}


	/** Gets the user's address (AOR).
	  * @return the address */
	public NameAddress getAddress() {
		return user_naddr;
	}


	/** Gets the user's contact address.
	  * @return the address */
	/*public NameAddress getContactAddress() {
		return contact_naddr;
	}*/

  
	/** Gets the authentication user's name.
	  * @return the name */
	public String getAuhUserName() {
		return auth_user;
	}


	/** Gets the authentication realm.
	  * @return the realm */
	public String getAuhRealm() {
		return auth_realm;
	}


	/** Gets the authentication password.
	  * @return the password */
	public String getAuhPasswd() {
		return auth_passwd;
	}
	
}
