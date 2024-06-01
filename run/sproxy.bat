@echo off
echo :
echo :
echo : ----------------------- Stateful SIP Proxy -----------------------
echo :
@echo on
java -cp lib/mjproxy.jar org.mjsip.server.StatefulProxy -f config\server.cfg %*