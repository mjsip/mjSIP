#!/bin/sh
echo :
echo :
echo : ----------------------- SBC -----------------------
echo :
java -cp lib/mjproxy.jar org.mjsip.server.sbc.SessionBorderController -f config/sbc.cfg $1 $2 $3 $4 $5 $6 $7 $8 $9