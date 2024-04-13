# mjSIP

Welcome to mjSIP

mjSIP is a complete java-based implementation of a SIP stack.
It provides in the same time the API and implementation bound together into the mjSIP packages. mjSIP is available open source under the terms of the GNU GPL license (General Public Licence) as published by the Free Software Foundation.
SIP (Session Initiation Protocol) is the IETF (Internet Engineering Task Force) signaling standard for managing multimedia session initiation; it is currently defined in RFC 3261. SIP can be used to initiate voice, video and multimedia sessions, for both interactive applications (e.g. an IP phone call or a videoconference) and not interactive ones (e.g. a Video Streaming), and it is the more promising candidate as call setup signaling for the present day and future IP based telephony services. SIP has been also proposed for session initiation related uses, such as for messaging, gaming, etc.

The mjSIP stack has been used in research activities by Dept. of Engineering and Architecture at University of Parma and several commercial products.
 

# mjSIP Features

mjSIP includes all classes and methods for creating SIP-based applications. It implements the complete layered stack architecture as defined in RFC 3261 (Transport, Transaction, and Dialog sublayers), and is fully compliant with RFC 3261 and successive standard RFCs. Moreover it includes higher level interfaces for Call Control and User Agent implementations. mjSIP comes with a core package implementation that includes:

all standard SIP layers and components,
various SIP extensions (already defined within IETF),
some useful Call Control APIs (e.g. Call-Control, UserAgent, etc.),
a reference implementation of some SIP systems (Proxy Server, Session Border Controlleer, and UA).