package org.zoolu.util;



/** Log level.
 */
public class LoggerLevel {
	
	/** Level SEVERE, for very high priority logs (e.g. errors). */
	public static final LoggerLevel SEVERE=new LoggerLevel("SEVERE",100);

	/** Level WARNING, for high priority logs. */
	public static final LoggerLevel WARNING=new LoggerLevel("WARNING",80);

	/** Level INFO, for medium priority logs. */
	public static final LoggerLevel INFO=new LoggerLevel("INFO",60);  

	/** Level DEBUG, for low priority logs. */
	public static final LoggerLevel DEBUG=new LoggerLevel("DEBUG",40); 

	/** Level DEBUG, for very low priority logs. */
	public static final LoggerLevel TRACE=new LoggerLevel("TRACE",20); 

	/** Priority level OFF, for no logs. */
	public static final LoggerLevel OFF=new LoggerLevel("OFF",Integer.MAX_VALUE); 

	/** Priority level ALL, for all logs. */
	public static final LoggerLevel ALL=new LoggerLevel("ALL",Integer.MIN_VALUE); 

	
	/** Level name */
	String name;
	
	/** Level value */
	int value;

	
	/** Creates a new log level.
	 * @param name the level name
	 * @param value the level value */
	public LoggerLevel(String name, int value) {
		this.name=name;
		this.value=value;
	}

	/** Whether this object equals to an other object.
	 * @param obj the other object that is compared to
	 * @return true if the object is a LoggerLevel and the two level values are equal */
	public boolean equals(Object obj) {
		if (this==obj) return true;
		// else
		if (obj!=null && obj instanceof LoggerLevel) return value==((LoggerLevel)obj).getValue();
		// else
		return false;
	}

	/** Gets the level value.
	 * @return the level value */
	public int getValue() {
		return value;
	}

	/** Gets the level name.
	 * @return the level name */
	public String getName() {
		return name;
	}

	/** Gets a string representation of this object.
	 * @return the level name */
	public String toString() {
		return name;
	}

}
