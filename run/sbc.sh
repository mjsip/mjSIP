#!/bin/sh
echo :
echo :
echo : ----------------------- SessionBorderController -----------------------
echo :
java -cp lib/mjproxy.jar org.mjsip.server.sbc.SessionBorderController -f config/sbc.cfg $*