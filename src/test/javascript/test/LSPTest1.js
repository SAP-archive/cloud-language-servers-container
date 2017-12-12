const WebSocket = require('ws');
const Promise = require('promise');
const PromiseTimeout = require('promise-timeout');
const assert = require("chai").assert;
const expect = require("chai").expect;
const request = require('request');
const rp = require('request-promise');

let ws;
let closePromise;
let openPromise;
const aSubscribers = [];

describe('Protocol test (LSP is socket client)', function () {

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
    ws = null;
    console.log("BEFORE - Protocol test (LSP is socket client)");
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
                ws = ws_o;
                ws.on('message',onMessage);
                console.log("Test for ready.........");
                resolve();
            });
			closePromise = new Promise(function(resolve) {
				ws_o.on('close',function close(ev) {
					console.log("Test WS closed........ due to " + ev);
					ws = null;
					resolve();
				});
			});
	    }).catch(function(err){
		    reject(err);
	    });
    }),10000);
});


after(function(){
	if (ws) {
		console.log("closed by test after()");
		ws.close();
		return closePromise;
	}
});

it('Check for open', function() {
    return openPromise.then(function(isOpened){
        expect(isOpened).to.be.true;
    });

});

it('Check for Mirror',function(){
	console.log("TEST - Check for Mirror");
    var testMessage = "Content-Length: 114\r\n\r\n" +
        "{\r\n" +
        "\"jsonrpc\": \"2.0\",\r\n" +
        "\"id\" : \"2\",\r\n" +
        "\"method\" : \"workspace/symbol\",\r\n" +
        "\"params\" : {\r\n" +
        "\"query\": \"ProductServicÚ*\"\r\n" +
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