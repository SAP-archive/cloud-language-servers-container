"use strict";

const WebSocket = require('ws');
const Promise = require('promise');
const PromiseTimeout = require('promise-timeout');
const assert = require("chai").assert;
const expect = require("chai").expect;
const fs = require('fs');
const request = require('request');
const rp = require('request-promise');
const path = require('path');


const pathPrefix = "http://localhost:8080/WSSynchronization";

const COMMON_OPTIONS = {
	headers: {
		"Content-Type": "multipart/form-data"
	}
};

describe('Sync Integration Full loop Test', function () {

	let folderPath = "";
	let filePath = "";
	let ws = null;
	let aSubscribers = [];	
	const create1Resp = {
			"jsonrpc":"2.0",
			"method":"workspace/didChangeWatchedFiles",
			"params":{
				"changes":[{
					"uri":"file:///home/travis/di_ws_root/java/test.java","type":1
				}]
				
			}
		};
	const update1Resp = {
			"jsonrpc":"2.0",
			"method":"workspace/didChangeWatchedFiles",
			"params":{
				"changes":[{
					"uri":"file:///home/travis/di_ws_root/java/test.java","type":2
				}]
				
			}
		};
	const delete1Resp = {
			"jsonrpc":"2.0",
			"method":"workspace/didChangeWatchedFiles",
			"params":{
				"changes":[{
					"uri":"file:///home/travis/di_ws_root/java/test.java","type":3
				}]
				
			}
		};
	

    function onMessage(msg) {
	    console.log("Test receiving message from LSP: \n" + msg);
	    expect(msg.startsWith("Content-Length:"),"Invalid message received").to.be.true;
	    expect(msg.indexOf("{"),"Invalid message received").to.be.above(0);

        var body = msg.substr(msg.indexOf("{"));
        var mObj = JSON.parse(body);

        var oSubscr = aSubscribers.shift();
        expect(oSubscr.method).to.equal(mObj.method);
        oSubscr.callback(mObj);
	        
    }
    
    function startLSP() {
		var d = new Date();
		var milliSec = d.getTime() + 60 * 60 * 1000;
	    var tokenSync = {
		    method: "POST",
		    uri: "http://localhost:8080/UpdateToken/?expiration=" + milliSec + "&token=12345",
		    body: {},
		    json: true
	    };
		var readyPromise = new Promise(function(readyRes,readyRej){
            aSubscribers.push({ method: "protocol/Ready", callback: function(msg){
            	console.log("Test - Ready received!");
                readyRes(true);
            }})
        });
		
	    PromiseTimeout.timeout(new Promise(function(resolve, reject){

		    rp(tokenSync).then(function(parsedResp) {
		    	console.log("Open WS after Sec Token sent");
	            var subprotocol = ["access_token", "12345"];
	            var ws_o = new WebSocket('ws://localhost:8080/LanguageServer/ws/java', subprotocol);
	            ws_o.on('open',function open(){
	                ws = ws_o;
	                ws.on('message',onMessage);
	                console.log("Test for ready.........");
	                resolve();
	            })
	            ws_o.on('close',function close(ev) {
	            	console.log("Test WS closed........ due to " + ev);
	                ws = null;
	            });
		    }).catch(function(err){
			    reject(err);
		    });
	    }),1000);
	    return readyPromise;
    }
    
	before(function(){
	    this.timeout(1000);

		return new Promise(function(ready, startfailed){
			startLSP().then(function st(){
				ready();
			}, function fl(res){
				startfailed(res);
			} );
		});
	});
	
    after(function () {
    	ws.close();
        try {
            fs.rmdirSync(folderPath + "/java");
            fs.rmdirSync(folderPath);
        } catch (e) {
            console.error("Error while deleting root folder");
        }
    });

    function deleteSingleFile() {
    	return Promise.all([
        	new Promise(function (resolve, reject) {
    			request.delete(pathPrefix + '/java/test.java', COMMON_OPTIONS, function (err, res, body) {
    				onDeleteResponse(err, res, body, resolve, reject);
    			})
    		}),
    		new Promise(function (resolve, reject) {
    	        aSubscribers.push({ method: "workspace/didChangeWatchedFiles", callback: function(oLspMsg){
    	        	console.log("Test response delete single file - loopback received:\n" + JSON.stringify(oLspMsg));
    	        	expect(oLspMsg,"Delete notification faillure").to.deep.equal(delete1Resp);
    	        	resolve();
    	        }})
    		}),
    	]);
    	

    }


    function onPutResponse(err, res, body, resolve, reject, isInit) {
		assert.ok(!err);
		assert.ok(res);
        if (isInit){
            folderPath = body.substring(7);
            filePath = path.join(path.normalize(folderPath + "/java/test.java"));
        }
        assert.equal(res.statusCode, 201);
        assert.ok(fs.existsSync(filePath));
        resolve(res);
    }

    function onPostResponse(err, res, body, resolve, reject) {
		assert.ok(!err);
        assert.ok(res);
        assert.equal(res.statusCode, 200);
        assert.ok(fs.existsSync(filePath));
        let newFileContent = fs.readFileSync(filePath).toString();
        assert.equal(newFileContent, "test", "Update file content check");
        resolve(res);
    }

    function onDeleteResponse(err, res, body, resolve, reject) {
		assert.ok(!err);
		assert.ok(res);
        assert.equal(res.statusCode, 200);
        assert.ok(!fs.existsSync(filePath));
        resolve(res);
    }

    it('Initial sync and delete file', function () {
        let zipFilePath = path.resolve(__dirname, '../resources/putTest.zip');
        return new Promise(function (resolve, reject) {
            let req = request.put(pathPrefix, COMMON_OPTIONS, function (err, res, body) {
                onPutResponse(err, res, body, resolve, reject, true);
            });
            let form = req.form();
            form.append('file', fs.createReadStream(zipFilePath));
		}).then(function () {
			// putting file that already exists should fail
			return new Promise(function (resolve, reject) {
				let req = request.put(pathPrefix + '/java/test.java', COMMON_OPTIONS, function (err, res, body) {
					assert.equal(res.statusCode, 403);
					resolve(res);
				});
				let form = req.form();
				form.append('file', fs.createReadStream(zipFilePath));
			});
		}).then(deleteSingleFile);
    });

    it('Update file that does not exist should fail', () => {
		let zipPostFilePath = path.resolve(__dirname, '../resources/postTest.zip');
		return new Promise(function (resolve, reject) {
			let req = request.post(pathPrefix + '/java/test.java', COMMON_OPTIONS, function (err, res, body) {
				assert.equal(res.statusCode, 500);
				resolve(res);
			});
			let postForm = req.form();
			postForm.append('file', fs.createReadStream(zipPostFilePath));
		});
	});

	it('Create update and delete file', function () {
		let zipPutFilePath = path.resolve(__dirname, '../resources/putTest.zip');
		let zipPostFilePath = path.resolve(__dirname, '../resources/postTest.zip');
		
		// Create a new artifact and send create notification
		return Promise.all([
			new Promise(function (resolve, reject) {
		        let req = request.put(pathPrefix + '/java/test.java', COMMON_OPTIONS, function (err, res, body) {
					onPutResponse(err, res, body, resolve, reject, false);
				});
				let putForm = req.form();
				putForm.append('file', fs.createReadStream(zipPutFilePath));
			}),
			new Promise(function(resolve,reject){
		        aSubscribers.push({ method: "workspace/didChangeWatchedFiles", callback: function(oLspMsg){
		        	console.log("Test create - loopback received:\n" + JSON.stringify(oLspMsg));
		        	expect(oLspMsg,"Create notification faillure").to.deep.equal(create1Resp);
		        	resolve();
		        }})
			})
		])
		// Update the artifact and send update notification
		.then(function () {
			return Promise.all([
				new Promise(function (resolve, reject) {
					let req = request.post(pathPrefix + '/java/test.java', COMMON_OPTIONS, function (err, res, body) {
						onPostResponse(err, res, body, resolve, reject);
					});
					let postForm = req.form();
					postForm.append('file', fs.createReadStream(zipPostFilePath));
				}),
				new Promise(function(resolve,reject){
			        aSubscribers.push({ method: "workspace/didChangeWatchedFiles", callback: function(oLspMsg){
			        	console.log("Test update - loopback received:\n" + JSON.stringify(oLspMsg));
			        	expect(oLspMsg,"Update notification faillure").to.deep.equal(update1Resp);
			        	resolve();
			        }})
				})
			])
		})
		// Delete the artifact and send delete notification
		.then(deleteSingleFile);
    });
});