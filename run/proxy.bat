@echo off
echo :
echo :
echo : ----------------------- SIP Proxy -----------------------
echo :
@echo on
java -cp lib/mjproxy.jar org.mjsip.server.Proxy -f config\server.cfg %*