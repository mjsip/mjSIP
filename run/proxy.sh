#!/bin/sh
echo :
echo :
echo : ----------------------- SIP Proxy -----------------------
echo :
java -cp lib/mjproxy.jar org.mjsip.server.Proxy -f config/server.cfg $*