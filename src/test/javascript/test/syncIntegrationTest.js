"use strict";

const assert = require("chai").assert;
const fs = require('fs');
const request = require('request');
const path = require('path');
const tu = require('./util/Util');


const pathPrefix = "http://localhost:8080/WSSynchronization";
const modulePath = "/myProject/myModule";

const COMMON_OPTIONS = {
	headers: {
		"Content-Type": "multipart/form-data",
		"DiToken": "THEDITOKEN"
	}
};

describe('Sync Integration Test', function () {

	let folderPath = "";
	let filePath = "";

    after(function () {
        try {
        	tu.deleteFolderRecursive(folderPath);
        } catch (e) {
            console.error("Error while deleting root folder " + folderPath + " due to " + e);
        }
    });

    function deleteSingleFile() {
		return new Promise(function (resolve, reject) {
			request.delete(pathPrefix + modulePath + '/java/test.java', COMMON_OPTIONS, function (err, res, body) {
				onDeleteResponse(err, res, body, resolve, reject);
			});
		});
	}


    function onPutResponse(err, res, body, resolve, reject, isInit) {
		assert.ok(!err);
		assert.ok(res);
        if (isInit){
            folderPath = body.substring(7);
            filePath = path.join(path.normalize(folderPath + modulePath +"/java/test.java"));
        }
        console.log("Put resp: " + body);
        assert.equal(201, res.statusCode, "File creation error ");
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
				let req = request.put(pathPrefix + modulePath + '/java/test.java', COMMON_OPTIONS, function (err, res, body) {
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
			let req = request.post(pathPrefix + modulePath + '/java/testMissed.java', COMMON_OPTIONS, function (err, res, body) {
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
		return new Promise(function (resolve, reject) {
			let req = request.put(pathPrefix + modulePath + '/java/test.java', COMMON_OPTIONS, function (err, res, body) {
				onPutResponse(err, res, body, resolve, reject, false);
			});
			let putForm = req.form();
			putForm.append('file', fs.createReadStream(zipPutFilePath));
		}).then(function () {
			return new Promise(function (resolve, reject) {
				let req = request.post(pathPrefix + modulePath + '/java/test.java', COMMON_OPTIONS, function (err, res, body) {
					onPostResponse(err, res, body, resolve, reject);
				});
				let postForm = req.form();
				postForm.append('file', fs.createReadStream(zipPostFilePath));
			});
		}).then(deleteSingleFile);
    });
});