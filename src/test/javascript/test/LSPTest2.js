const WebSocket = require('ws');
const Promise = require('promise');
const PromiseTimeout = require('promise-timeout');
const assert = require("chai").assert;
const expect = require("chai").expect;

var ws;
var openPromise;
var aSubscribers = [];

describe('Protocol test (LSP is socket server)', () => {
	

	function onMessage(msg) {
		console.log("Receiving message: " + msg);
		if ( msg.startsWith("Content-Length:") ) {
			var body = msg.substr(msg.indexOf("{"));
			var mObj = JSON.parse(body);

			// Find subscriber
			var indexFound = -1;
			aSubscribers.forEach(function(oSubscr,index) {
				if (oSubscr.method === mObj.method) {
					indexFound = index;
					oSubscr.callback(mObj);
				}
			});
			if ( indexFound != -1 ) {
				delete aSubscribers[indexFound];
			}

		}
	}

	before(function(){
		debugger;
		ws = null;
		return PromiseTimeout.timeout(new Promise(function(resolve, reject){
			openPromise = new Promise(function(openRes,openRej){
				aSubscribers.push({ method: "protocol/Ready", callback: function(msg){
					openRes(true);
				}})
			});
			var ws_o = new WebSocket('ws://localhost:8080/LanguageServer/abcd/cdx');
			ws_o.on('open',function open(){
				ws = ws_o;
				ws.on('message',onMessage);
				resolve();
			})
		}),1000);
	});

	after(function(){

		return PromiseTimeout.timeout(new Promise(function(resolve,reject){
			if ( ws ) {
				ws.close();
				ws.on('close',function close() {
					ws = null;
				});
			}
			resolve();
		}), 1000

		);
	});

	it('Check for open', function() {
		this.timeout(1000);
		return openPromise.then(function(isOpened){
			expect(isOpened).to.be.true;
		});

	});

	it('Check for Mirror',function(){
		this.timeout(2000);
		var testMessage = "Content-Length: 113\r\n\r\n" +
		"{\r\n" +
		"\"jsonrpc\": \"2.0\",\r\n" +
		"\"id\" : \"2\",\r\n" +
		"\"method\" : \"workspace/symbol\",\r\n" +
		"\"params\" : {\r\n" +
		"\"query\": \"ProductService*\"\r\n" +
		"}\r\n}";
		console.log("Sending test message:\r\n" + testMessage);
		return openPromise.then(function(isOpened){
			if ( isOpened ) {
				return new Promise(function(openRes,openRej){
					aSubscribers.push({ method: "workspace/symbol", callback: function(msg){
						openRes(msg);
					}});
					ws.send(testMessage);
				}).then(function(recvMsg){
					//TODO check for message consistency
				});


			} else {
				assert.fail('Not opened');
			}

		});

	});
});
