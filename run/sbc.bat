@echo off
echo :
echo :
echo : ----------------------- SessionBorderController -----------------------
echo :
@echo on
java -cp lib/mjproxy.jar org.mjsip.server.sbc.SessionBorderController -f config\sbc.cfg %*