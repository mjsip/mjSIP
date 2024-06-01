#!/bin/sh
echo :
echo :
echo : ----------------------- Stateful SIP Proxy -----------------------
echo :
java -cp lib/mjproxy.jar org.mjsip.server.StatefulProxy -f config/server.cfg $*