package org.mjsip.media;



import org.mjsip.net.UdpRelay;
import org.zoolu.util.LoggerLevel;
import org.zoolu.util.SystemUtils;
import org.zoolu.util.Logger;



/** Media streamer based on a native command-line application.
  */
public class NativeMediaStreamer implements MediaStreamer {
	
	/** Runtime media process (native media application) */
	Process media_process=null;
	
	int local_port;
	int remote_port;
	
	/** Media application command */
	String command;

	/** Command-line arguments */
	String[] args;


	/** Creates a new media streamer.
	  * @param command the command-line program to be run
	  * @param args command-line arguments that have to be passed to the program */
	public NativeMediaStreamer(String command, String[] args) {
		init(command,args,0,0);
	}

	/** Creates a new media streamer.
	  * @param command the command-line program to be run
	  * @param args command-line arguments that have to be passed to the program
	  * @param local_port local media port
	  * @param remote_port remote media port */
	public NativeMediaStreamer(String command, String[] args, int local_port, int remote_port) {
		init(command,args,local_port,remote_port);
	}

	/** Inits the media streamer.
	  * @param command the command-line program to be run
	  * @param args command-line arguments that have to be passed to the program
	  * @param local_port local media port
	  * @param remote_port remote media port */
	private void init(String command, String[] args, int local_port, int remote_port) {
		this.command=command;
		this.args=args;
		this.local_port=local_port;
		this.remote_port=remote_port;
	}

	/** Starts this media streams. */
	public boolean start() {
		// udp flow adaptation for media streamer
		if (local_port!=remote_port)  {
			log("UDP local relay: src_port="+local_port+", dest_port="+remote_port);
			log("UDP local relay: src_port="+(local_port+1)+", dest_port="+(remote_port+1));
			new UdpRelay(local_port,"127.0.0.1",remote_port,null);
			new UdpRelay(local_port+1,"127.0.0.1",remote_port+1,null);  
		}
		else {
			log("local_port==remote_port --> no UDP relay is needed");
		}

		//debug...
		log("starting native media application ("+command+")");
	 
		String cmds[]=new String[((args!=null)?args.length:0)+1];
		cmds[0]=command;
		for (int i=1; i<cmds.length; i++) cmds[i]=args[i-1];

		// try to start the media application
		try {
			media_process=Runtime.getRuntime().exec(cmds);
			return true;
		}
		catch (Exception e) {
			e.printStackTrace();
			return false;
		}          
	}

	/** Stops this media streams. */
	public boolean halt() {
		log("stopping native media application ("+command+")");
		if (media_process!=null) media_process.destroy();
		return true;
	}


	// ****************************** Logs *****************************

	/** Adds a new string to the default Log.
	  * @param str the string message to be logged. */
	private void log(String str) {
		Logger logger=SystemUtils.getDefaultLogger();
		if (logger!=null) logger.log(LoggerLevel.INFO,"NativeMediaApp: "+str);  
		System.out.println("NativeMediaApp: "+str);
	}
		
}
