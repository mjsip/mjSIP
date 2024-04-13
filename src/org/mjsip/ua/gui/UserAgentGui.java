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

package org.mjsip.ua.gui;


import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.swing.ComboBoxEditor;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.plaf.basic.BasicComboBoxEditor;

import org.mjsip.media.MediaDesc;
import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.address.SipURI;
import org.mjsip.sip.provider.SipParser;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.ua.UserAgent;
import org.mjsip.ua.UserAgentListener;
import org.mjsip.ua.UserAgentProfile;
import org.zoolu.util.Archive;
import org.zoolu.util.ExceptionPrinter;
import org.zoolu.util.LoggerLevel;
import org.zoolu.util.Logger;
import org.zoolu.util.ScheduledWork;
import org.zoolu.util.SystemUtils;


/** Simple SIP user agent with GUI. */
public class UserAgentGui {
	
	/** This application */
	final String app_name="mjUA (http://www.mjsip.org)";

	/** Media file path */
	//private static final String MEDIA_PATH="media/media/org/mjsip/ua/";
	
	/** Call button image */
	private static final String CALL_GIF=/*MEDIA_PATH+*/"call.gif";

	/** Hangup button image */
	private static final String HANGUP_GIF=/*MEDIA_PATH+*/"hangup.gif";

	/** Buddy list size */
	protected static final int NMAX_CONTACTS=10;

	/** Window width */
	private static final int W_Width=320;

	/** Window height */
	private static final int W_Height=90;

	/** Buttons and combobox height (total) */
	private static final int C_Height=30;

	
	/** SipProvider. */
	protected SipProvider sip_provider;

	/** User agent */
	protected UserAgent ua;

	/** User agent profile */
	protected UserAgentProfile ua_profile;

	/** User agent GUI */
	protected JFrame ua_gui;

	/** Title */
	//String user_name=app_name;

	/** Buddy list */
	protected StringList buddy_list;
	
	/** URI box */
	JComboBox combobox_editor=new JComboBox();

	/** URI editor */
	ComboBoxEditor editor_uri=new BasicComboBoxEditor();

	/** Display */
	JTextField tf_display=new JTextField();

	
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
	public UserAgentGui(SipProvider sip_provider, UserAgentProfile ua_profile) {
		this.sip_provider=sip_provider;
		this.ua_profile=ua_profile;
		initUA(sip_provider,ua_profile,this_ua_listener);
		initGraphics();                  
		run();   
	}


	/** Initializes the UA. */
	protected void initUA(SipProvider sip_provider, UserAgentProfile ua_profile, UserAgentListener this_ua_listener) {
		ua=new UserAgent(sip_provider,ua_profile,this_ua_listener);
		//ua.listen();
		changeStatus(UA_IDLE);
	}

	
	/** Initializes the GUI. */
	protected void initGraphics() {
		
		ua_gui=new JFrame();
		
		Icon icon_call=null;
		Icon icon_hangup=null;
		// load icons
		try {
			icon_call=getImageIcon(ua_profile.ua_jar,ua_profile.res_path,ua_profile.media_path+"/"+CALL_GIF);
			icon_hangup=getImageIcon(ua_profile.ua_jar,ua_profile.res_path,ua_profile.media_path+"/"+HANGUP_GIF);
		}
		catch (IOException e) {
			e.printStackTrace();
			log(LoggerLevel.INFO,e);
		}
		
		// load buddy list
		if (ua_profile.buddy_list_file!=null && (ua_profile.buddy_list_file.startsWith("http://") || ua_profile.buddy_list_file.startsWith("file:/"))) {
			try {
				buddy_list=new StringList(new URL(ua_profile.buddy_list_file));
			}
			catch (MalformedURLException e) {
				e.printStackTrace();
				log(LoggerLevel.INFO,e);
				buddy_list=new StringList((String)null);
			}
		}
		else buddy_list=new StringList(ua_profile.buddy_list_file);
		combobox_editor=new JComboBox(buddy_list.getElements());

		// init frame
		try {
			// set frame dimensions
			ua_gui.setSize(W_Width,W_Height);
			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
			Dimension frameSize = ua_gui.getSize();
			if (frameSize.height > screenSize.height) frameSize.height = screenSize.height;
			if (frameSize.width > screenSize.width) frameSize.width = screenSize.width;
			ua_gui.setLocation((screenSize.width - frameSize.width)/2 - 40, (screenSize.height - frameSize.height)/2 - 40);
			ua_gui.setResizable(false);
	
			ua_gui.setTitle(sip_provider.getContactAddress(ua_profile.user).toString());
			ua_gui.addWindowListener(new java.awt.event.WindowAdapter() {
				public void windowClosing(WindowEvent e) { exit(); }
			});
			JPanel panel1=new JPanel();
			panel1.setLayout(new BorderLayout());
			JPanel panel2=new JPanel();
			panel2.setLayout(new BorderLayout());
			tf_display.setBackground(Color.black);
			tf_display.setForeground(Color.green);
			tf_display.setEditable(false);
			tf_display.setText(app_name);
			GridLayout panel3_layout=new GridLayout();
			panel3_layout.setRows(2);
			panel3_layout.setColumns(1);
			JPanel panel3=new JPanel();
			panel3.setLayout(panel3_layout);
			JPanel panel4=new JPanel();
			panel4.setLayout(new BorderLayout());
			JPanel panel5=new JPanel();
			panel5.setLayout(new GridLayout());
			
			JButton button_call=new JButton();
			if (icon_call!=null && icon_call.getIconWidth()>0) button_call.setIcon(icon_call);
			else button_call.setText("Call");
			button_call.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(ActionEvent e) { jButton1_actionPerformed(); }
			});
			button_call.addKeyListener(new java.awt.event.KeyAdapter() {
				public void keyTyped(KeyEvent e) { jButton1_actionPerformed(); }
			});
			
			JButton button_hangup=new JButton();
			if (icon_hangup!=null && icon_hangup.getIconWidth()>0) button_hangup.setIcon(icon_hangup);
			else button_hangup.setText("Hungup");
			button_hangup.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(ActionEvent e) { jButton2_actionPerformed(); }
			});
			button_hangup.addKeyListener(new java.awt.event.KeyAdapter() {
				public void keyTyped(KeyEvent e) { jButton2_actionPerformed(); }
			});
			
			combobox_editor.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(ActionEvent e) { jComboBox1_actionPerformed(e); }
			});
			
			editor_uri.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(ActionEvent e) { comboBoxEditor1_actionPerformed(e); }
			});

			button_hangup.setFont(new java.awt.Font("Dialog", 0, 10));
			button_call.setFont(new java.awt.Font("Dialog", 0, 10));
			editor_uri.getEditorComponent().setBackground(Color.yellow);
			combobox_editor.setEditable(true);
			combobox_editor.setEditor(editor_uri);
			combobox_editor.setSelectedItem(null);
			
			panel3.setPreferredSize(new Dimension(0,C_Height));
			ua_gui.getContentPane().add(panel1, BorderLayout.CENTER);
			panel1.add(panel2, BorderLayout.CENTER);
			panel1.add(panel3, BorderLayout.SOUTH);
			panel2.add(tf_display, BorderLayout.CENTER);
			panel3.add(panel4, null);
			panel3.add(panel5, null);
			panel4.add(combobox_editor, BorderLayout.CENTER);
			panel5.add(button_call, null);
			panel5.add(button_hangup, null);
	
			// show it
			ua_gui.setVisible(true);
			
			//Image image=Archive.getImage(Archive.getJarURL(jar_file,"media/org/mjsip/ua/intro.gif"));
			//PopupFrame about=new PopupFrame("About",image,this);
			//try  {  Thread.sleep(3000);  } catch(Exception e) {  }
			//about.closeWindow();
		}
		catch(Exception e) { e.printStackTrace(); }

	}



	/** Starts the UA */
	protected void run() {
		
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
			// ########## make a call with the remote URI
			ua.log("UAC: CALLING "+ua_profile.call_to);
			combobox_editor.setSelectedItem(null);
			editor_uri.setItem(ua_profile.call_to.toString());
			tf_display.setText("CALLING "+ua_profile.call_to);
			ua.call(ua_profile.call_to);
			changeStatus(UA_OUTGOING_CALL);       
		} 

		if (!ua_profile.audio && !ua_profile.video) ua.log("ONLY SIGNALING, NO MEDIA");   
	}


	/** Exits. */
	protected void exit() {
		// close possible active call before exiting
		jButton2_actionPerformed();
		// exit now
		System.exit(0);
	}


	/** When the call/accept button is pressed. */
	void jButton1_actionPerformed() {
		
		if (statusIs(UA_IDLE)) {
			String uri=(String)editor_uri.getItem();
			if (uri!=null && uri.length()>0) {
				ua.hangup();
				tf_display.setText("CALLING "+uri);
				ua.call(uri);
				changeStatus(UA_OUTGOING_CALL);
				if (ua_profile.hangup_time>0) automaticHangup(ua_profile.hangup_time); 
			}
		}
		else
		if (statusIs(UA_INCOMING_CALL)) {
			ua.accept();
			tf_display.setText("ON CALL");
			changeStatus(UA_ONCALL);
		}
	}


	/** When the refuse/hangup button is pressed. */
	void jButton2_actionPerformed() {
		
		if (!statusIs(UA_IDLE)) {
			ua.hangup();
			//ua.listen();
			changeStatus(UA_IDLE);      
 
			tf_display.setText("HANGUP");
		}
	}


	/** When the combo-box action is performed. */
	void jComboBox1_actionPerformed(ActionEvent e) {
		// if the edited URI is different from the selected item, copy the selected item in the editor
		/*
		String edit_name=(String)comboBoxEditor1.getItem();
		int index=jComboBox1.getSelectedIndex();
		if (index>=0) {
			String selected_name=buddy_list.elementAt(index);
			if (!selected_name.equals(edit_name)) comboBoxEditor1.setItem(selected_name);
		}*/
	}


	/** When the combo-box text field is changed. */
	void comboBoxEditor1_actionPerformed(ActionEvent e) {
		// if a new URI has been typed, insert it in the buddy_list and make it selected item
		// else, simply make the URI the selected item
		String name=(String)editor_uri.getItem();
		// parse separatly NameAddrresses or SipURIs
		if (name.indexOf("\"")>=0 || name.indexOf("<")>=0) {
			// try to parse a NameAddrress
			NameAddress nameaddr=(new SipParser(name)).getNameAddress();
			if (nameaddr!=null) name=nameaddr.toString();
			else name=null;
		}
		else {
			// try to parse a SipURI
			SipURI uri=new SipURI(name);
			if (uri!=null) name=uri.toString();
			else name=null;
		}

		if (name==null) {
			System.out.println("DEBUG: No SIP URI recognized in: "+(String)editor_uri.getItem());
			return;
		}

		// checks if the the URI is already present in the buddy_list
		if (!buddy_list.contains(name)) {
			combobox_editor.insertItemAt(name,0);
			combobox_editor.setSelectedIndex(0);
			// limit the list size
			while (buddy_list.getElements().size()>NMAX_CONTACTS) combobox_editor.removeItemAt(NMAX_CONTACTS);
			// save new contact list
			buddy_list.save();         
		}
		else {
			int index=buddy_list.indexOf(name);
			combobox_editor.setSelectedIndex(index);
		}
 
	}


	/** Gets the UserAgent */
	/*protected UserAgent getUA() {
		return ua;
	}*/


	// ********************** UA callback functions **********************

	/** When a new call is incoming */
	private void processUaIncomingCall(UserAgent ua, NameAddress callee, NameAddress caller, MediaDesc[] media_descs) {
		changeStatus(UA_INCOMING_CALL);
		if (ua_profile.redirect_to!=null) {
			// redirect the call
			tf_display.setText("CALL redirected to "+ua_profile.redirect_to);
			ua.redirect(ua_profile.redirect_to);
		}         
		else
		if (ua_profile.accept_time>=0) {
			// automatically accept the call
			tf_display.setText("ON CALL");
			combobox_editor.setSelectedItem(null);
			editor_uri.setItem(caller.toString());
			//accept();
			automaticAccept(ua_profile.accept_time);
		}
		else {
			tf_display.setText("INCOMING CALL");
			combobox_editor.setSelectedItem(null);
			editor_uri.setItem(caller.toString());
		}
	}


	/** When an outgoing call is stated to be in progress */
	private void processUaCallProgress(UserAgent ua) {
		tf_display.setText("PROGRESS");
	}


	/** When an outgoing call is remotely ringing */
	private void processUaCallRinging(UserAgent ua) {
		tf_display.setText("RINGING");
	}


	/** When an outgoing call has been accepted */
	private void processUaCallAccepted(UserAgent ua) {
		tf_display.setText("ON CALL");
		changeStatus(UA_ONCALL);
		if (ua_profile.hangup_time>0) automaticHangup(ua_profile.hangup_time); 
	}


	/** When an incoming call has been cancelled */
	private void processUaCallCancelled(UserAgent ua) {
		tf_display.setText("CANCELLED");
		//ua.listen();
		changeStatus(UA_IDLE);
	}


	/** When a call has been transferred */
	private void processUaCallTransferred(UserAgent ua) {
		tf_display.setText("TRASFERRED");
		//ua.listen();
		changeStatus(UA_IDLE);
	}


	/** When an outgoing call has been refused or timeout */
	private void processUaCallFailed(UserAgent ua, String reason) {
		tf_display.setText("FAILED"+((reason!=null)? " ("+reason+")" : ""));
		//ua.listen();
		changeStatus(UA_IDLE);
	}


	/** When a call has been locally or remotely closed */
	private void processUaCallClosed(UserAgent ua) {
		tf_display.setText("BYE");
		//ua.listen();
		changeStatus(UA_IDLE);
	}

	/** When a new media session is started. */
	private void processUaMediaSessionStarted(UserAgent ua, String type, String codec) {
		//log(type+" started "+codec);
	}

	/** When a media session is stopped. */
	private void processUaMediaSessionStopped(UserAgent ua, String type) {
		//log(type+" stopped");
	}


	/** When registration succeeded. */
	private void processUaRegistrationSucceeded(UserAgent ua, String result) {
		ua_gui.setTitle(ua_profile.getUserURI().toString());
	log("REGISTRATION SUCCESS: "+result); 
	}

	/** When registration failed. */
	private void processUaRegistrationFailed(UserAgent ua, String result) {
		ua_gui.setTitle(sip_provider.getContactAddress(ua_profile.user).toString());
	log("REGISTRATION FAILURE: "+result); 
	}


	// ************************ scheduled events ************************

	/** Schedules a re-inviting after <i>delay_time</i> secs. It simply changes the contact address. */
	/*void reInvite(final NameAddress contact, final int delay_time) {
		new ScheduledWork(delay_time*1000) {
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
		else new ScheduledWork(delay_time*1000) {  public void doWork() {  ua.modify(null);  }  };
	}


	/** Schedules a call-transfer after <i>delay_time</i> secs. */
	/*void callTransfer(final NameAddress transfer_to, final int delay_time) {
		new ScheduledWork(delay_time*1000) {
			public void doWork() {
				log("AUTOMATIC REFER/TRANSFER");
				ua.transfer(transfer_to);
			}
		};
	}*/
	/** Schedules a call-transfer after <i>delay_time</i> secs. */
	void callTransfer(final NameAddress transfer_to, final int delay_time) {
		log("AUTOMATIC REFER/TRANSFER: "+delay_time+" secs");
		if (delay_time==0) ua.transfer(transfer_to);
		else new ScheduledWork(delay_time*1000) {  public void doWork() {  ua.transfer(transfer_to);  }  };
	}


	/** Schedules an automatic answer after <i>delay_time</i> secs. */
	/*void automaticAccept(final int delay_time) {
		new ScheduledWork(delay_time*1000) {
			public void doWork() {
				log("AUTOMATIC ANSWER");
				jButton1_actionPerformed();
			}
		};
	}*/
	/** Schedules an automatic answer after <i>delay_time</i> secs. */
	void automaticAccept(final int delay_time) {
		log("AUTOMATIC ANSWER: "+delay_time+" secs");
		if (delay_time==0) jButton1_actionPerformed();
		else new ScheduledWork(delay_time*1000) {  public void doWork() {  jButton1_actionPerformed();  }  };
	}
	

	/** Schedules an automatic hangup after <i>delay_time</i> secs. */
	/*void automaticHangup(final int delay_time) {
		new ScheduledWork(delay_time*1000) {
			public void doWork() {
				printLog("AUTOMATIC HANGUP");
				jButton2_actionPerformed();
			}
		};
	}*/
	/** Schedules an automatic hangup after <i>delay_time</i> secs. */
	void automaticHangup(final int delay_time) {
		log("AUTOMATIC HANGUP: "+delay_time+" secs");
		if (delay_time==0) jButton2_actionPerformed();
		else new ScheduledWork(delay_time*1000) {  public void doWork() {  jButton2_actionPerformed();  }  };
	}


	// ****************************** Static ******************************

	private static ImageIcon getImageIcon(String jar_file, String res_path, String image_file) throws java.io.IOException {
		if (jar_file!=null && new File(jar_file).canRead()) {
			return Archive.getImageIcon(Archive.getJarURL(jar_file,image_file));
		}
		else
		if (new File(res_path+"/"+image_file).canRead()) {
			return Archive.getImageIcon(res_path+"/"+image_file);
		}
		else {
			return Archive.getImageIcon(new URL(res_path+"/"+image_file));
		}
	}


	// ******************************* Logs ******************************

	/** Adds a new string to the default Log. */
	private void log(String str) {
		log(LoggerLevel.INFO,str);
	}

	/** Adds a new string to the default Log. */
	private void log(LoggerLevel level, String str) {
		Logger logger=SystemUtils.getDefaultLogger();
		if (logger!=null) logger.log(level,"GraphicalUA: "+str);  
	}

	/** Adds the Exception message to the default Log. */
	private void log(LoggerLevel level, Exception e) {
		log(level,"Exception: "+ExceptionPrinter.getStackTraceOf(e));
	}
}
