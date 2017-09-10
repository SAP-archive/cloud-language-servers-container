const WebSocket = require('ws');
const Promise = require('promise');
const PromiseTimeout = require('promise-timeout');
const assert = require("chai").assert;
const expect = require("chai").expect;
const request = require('request');
const rp = require('request-promise');
const sleep = require('sleep');


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
		    rp(tokenSync).then(function(parsedResp) {
		    	console.log("Open WS after Sec Token sent");
	            var subprotocol = ["access_token", "12345"];
	            var ws_o = new WebSocket('ws://localhost:8080/LanguageServer/ws/java', subprotocol);
	            ws_o.on('open',function open(){
	                let ws = ws_o;
		            aSubscribers.push({ method: "protocol/Ready", callback: function(msg){
		            	console.log("Connect WS Test - Ready received!");
		                sleep.msleep(1000);
		                ws.on('close',function(){
		                	resolve(true);
		                });
		                ws.close();
		            }});
	                ws.on('message',onMessage);
	            })
		    }).catch(function(err){
			    reject(err);
		    });
		}),10000);
	};

	function connectWS() {
		var ws = null;
	    return PromiseTimeout.timeout(new Promise(function(resolve, reject){
	    		

		    	console.log("Open WS ");
	            var subprotocol = ["access_token", "12345"];
	            var ws_o1 = new WebSocket('ws://localhost:8080/LanguageServer/ws/java?lsp_timeout=500', subprotocol);
	            ws_o1.on('open',function open(){
	                let ws = ws_o1;
	                
		            aSubscribers.push({ method: "protocol/Ready", callback: function(msg){
		            	console.log("Connect WS Test - Ready received!");
		                resolve(ws);
		            }});
		            

	                ws.on('message',onMessage);
	            })
	            
 		}),10000);
		
	}
	
	function isAliveWS(ws) {
		console.log("TEST - Check for Mirror");
	    var testMessage = "Content-Length: 113\r\n\r\n" +
	        "{\r\n" +
	        "\"jsonrpc\": \"2.0\",\r\n" +
	        "\"id\" : \"2\",\r\n" +
	        "\"method\" : \"workspace/symbol\",\r\n" +
	        "\"params\" : {\r\n" +
	        "\"query\": \"ProductService*\"\r\n" +
	        "}\r\n}";
	    console.log("Sending test message:\r\n" + testMessage);
        return new Promise(function(mirrorRes,mirrorRej){
            aSubscribers.push({ method: "workspace/symbol", callback: function(msg){
                mirrorRes(msg);
            }});
            ws.send(testMessage);
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

	it('Check for re-enter due short disconnect WebIDE', function() {
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
		
		let that = this;
		
		return rp(tokenSync).then(function(){
			return connectWS().then(function(ws1) {
				ws1.on('close',function() {
					console.log("Test - close WS1 OK");
				});
				sleep.msleep(100);
				return connectWS().then(function(ws2){
					ws2.on('close',function() {
						console.log("Test - close WS2 OK");
					});
					sleep.msleep(100);
					// Check for alive
					console.log("Test - closing both WS");
					ws1.close();
					sleep.msleep(100);
					
					return isAliveWS(ws2)
					.then(function(echoMsg) {
						console.log("Echo succeeded - WS2 is alive");
						expect(ws2.readyState).to.equal(1);
						ws2.close();
						sleep.msleep(100);
						return Promise.resolve();
						
					})
					.catch(function() {
						ws2.close();
						assert.fail("Mirror failed - reenter socket closed");
						sleep.msleep(100);
						return Promise.reject();
					});
					
				})
			})
		});
		
	});

});
