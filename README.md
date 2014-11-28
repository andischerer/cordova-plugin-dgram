# fork from (here)[https://github.com/gramakri/cordova-plugin-datagram]

# cordova-plugin-dgramsocket
=======================
Cordova plugin for sending datagram/UDP. Supports multicast UDP.

# Fixes
- reset packet length due to incomplete UDP Packet received
- threadded send to prevent NetworkOnMainThreadException
- added js-module clobbers: dgramSocket

