var exec = cordova.require('cordova/exec');

// Events: 'message', 'error'
function Socket(type, port) {
    this._multicastSocket = type === 'multicast-udp4';
    this._socketId = ++Socket.socketCount;
    this._eventHandlers = { };
    Socket.sockets[this._socketId] = this;
    exec(null, null, 'Dgram', 'create', [ this._socketId, this._multicastSocket, port ]);
}

Socket.socketCount = 0;
Socket.sockets = { };

Socket.prototype.on = function (event, callback) {
    this._eventHandlers[event] = callback;
};

Socket.prototype.bind = function (callback) {
    callback = callback || function () { };
    exec(callback.bind(null, null), callback.bind(null), 'Dgram', 'bind', [ this._socketId ]);
};

Socket.prototype.close = function () {
    exec(null, null, 'Dgram', 'close', [ this._socketId ]);
    delete Socket.sockets[this._socketId];
    this._socketId = 0;
};

// sends utf-8
Socket.prototype.send = function (buffer, destAddress, destPort, callback) {
    callback = callback || function () { };
    exec(callback.bind(null, null), // success
         callback.bind(null), // failure
         'Dgram',
         'send',
         [ this._socketId, buffer, destAddress, destPort ]);
};

Socket.prototype.address = function () {
};

Socket.prototype.joinGroup = function (address, callback) {
    callback = callback || function () { };
    if (!this._multicastSocket) throw new Error('Invalid operation');
    exec(callback.bind(null, null), callback.bind(null), 'Dgram', 'joinGroup', [ this._socketId, address ]);
};

Socket.prototype.leaveGroup = function (address, callback) {
    callback = callback || function () { };
    if (!this._multicastSocket) throw new Error('Invalid operation');
    exec(callback.bind(null, null), callback.bind(null), 'Dgram', 'leaveGroup', [ this._socketId, address ]);
};

function createSocket(type, port) {
    if (type !== 'udp4' && type !== 'multicast-udp4') {
        throw new Error('Illegal Argument, only udp4 and multicast-udp4 supported');
    }
    iport = parseInt(port, 10);
    if (isNaN(iport) || iport === 0){
        throw new Error('Illegal Port number');
    }
    return new Socket(type, iport);
}

function onMessage(id, msg, remoteAddress, remotePort) {
    var socket = Socket.sockets[id];
    if (socket && 'message' in socket._eventHandlers) {
        socket._eventHandlers['message'].call(null, msg, { address: remoteAddress, port: remotePort });
    }
}

module.exports = {
    createSocket: createSocket,
    _onMessage: onMessage
};