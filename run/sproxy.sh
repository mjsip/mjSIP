#!/bin/sh
echo :
echo :
echo : ----------------------- Stateful SIP Proxy -----------------------
echo :
java -cp lib/mjproxy.jar org.mjsip.server.StatefulProxy -f config/server.cfg $1 $2 $3 $4 $5 $6 $7 $8 $9