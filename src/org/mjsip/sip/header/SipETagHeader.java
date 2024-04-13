/*
 * Copyright (C) 2011 Luca Veltri - University of Parma - Italy
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

package org.mjsip.sip.header;



/** SIP-ETag Header.
  * SIP header field defined in RFC 3903.
  */
public class SipETagHeader extends Header {
	
	/** Creates a SipETagHeader with a given <i>tag</i>. */
	public SipETagHeader(String tag) {
		super("SIP-ETag",tag);
	}

	/** Creates a new SipETagHeader equal to another SipETagHeader <i>hd</i>. */
	public SipETagHeader(Header hd) {
		super(hd);
	}

	/** Gets the entity-tag. */
	public String getEntityTag() {
		return value;
	}

}
