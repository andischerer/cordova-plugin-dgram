# cordova-plugin-dgramsocket

fork from [https://github.com/gramakri/cordova-plugin-datagram]

Cordova plugin for sending datagram/UDP. Supports multicast UDP.

## Fixes
- reset packet length due to incomplete UDP Packet received
- threadded send to prevent NetworkOnMainThreadException
- added js-module clobbers: dgramSocket

