const WebSocket = require('ws');
const Promise = require('promise');
const PromiseTimeout = require('promise-timeout');
const assert = require("chai").assert;
const expect = require("chai").expect;
const request = require('request');
const rp = require('request-promise');


const aSubscribers = [];

describe('WebIDE reload test', function () {

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
	
	function openAndClose() {
		var ws_o = null;
		var d = new Date();
		var milliSec = d.getTime() + 60 * 60 * 1000;
	    var tokenSync = {
		    method: "POST",
		    uri: "http://localhost:8080/UpdateToken/?expiration=" + milliSec + "&token=12345",
		    headers: {
		        'DiToken': 'THEDITOKEN'
		    },
		    body: {},
		    json: true
	    };
	    return PromiseTimeout.timeout(new Promise(function(resolve, reject){
	        openPromise = new Promise(function(openRes,openRej){
	            aSubscribers.push({ method: "protocol/Ready", callback: function(msg){
	            	console.log("Test - Ready received!");
	                openRes(true);
	            }})
	        });
		    rp(tokenSync).then(function(parsedResp) {
		    	console.log("Open WS after Sec Token sent");
	            var subprotocol = ["access_token", "12345"];
	            var ws_o = new WebSocket('ws://localhost:8080/LanguageServer/ws/java', subprotocol);
	            ws_o.on('open',function open(){
	                let ws = ws_o;
	                ws.on('message',onMessage);
	                resolve(ws);
	            })
		    }).catch(function(err){
			    reject(err);
		    });
		}),10000).then(function(ws) {
			return new Promise(function(closeRes,closeRej) {
				console.log("closed by openAndClose()");
				ws.close();
				ws.on('close',function close() {
					closeRes(true);
				});
			})
		});
		
	}

	it('Check for Reload WebIDE', function() {
		return openAndClose().then(function(bOpen1){
			console.log("1st time open & close " + bOpen1);
			expect(bOpen1).to.be.true;
			return openAndClose().then(function(bOpen2){
				console.log("After reload " + bOpen2);
				expect(bOpen2).to.be.true;
			});
		});

	});

});
