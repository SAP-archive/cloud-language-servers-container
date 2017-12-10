"use strict";

const assert = require("chai").assert;
const fs = require('fs');
const request = require('request');
const path = require('path');
const Util = require('./util/Util');

const pathPrefix = "http://localhost:8080/WSSynchronization";
const modulePath = "/myProject/myModule";
const COMMON_OPTIONS = {
	headers: {
		"Content-Type": "multipart/form-data",
		"DiToken": "THEDITOKEN"
	}
};

describe('Sync Integration Test - no websocket', function () {

	let folderPath = "";
	let filePath = "";

	afterEach(function () {
		try {
			Util.deleteFolderRecursive(folderPath);
		} catch (e) {
			console.error("Error while deleting root folder " + folderPath + " due to " + e);
		}
	});

	function deletePath(relPath, filePath) {
		return new Promise(function (resolve) {
			request.delete(pathPrefix + modulePath + relPath, COMMON_OPTIONS, function (err, res) {
				onDeleteResponse(filePath, err, res, resolve);
			});
		});
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

	function onDeleteResponse(filePath, err, res, resolve) {
		assert.ok(!err);
		assert.ok(res);
		assert.equal(res.statusCode, 200);
		assert.ok(!fs.existsSync(filePath));
		resolve(res);
	}

	//Initial sync and delete file before every test
	beforeEach(function () {
		let zipFilePath = path.resolve(__dirname, '../resources/putTest.zip');
		return new Promise(function (resolve) {
			let req = request.put(pathPrefix, COMMON_OPTIONS, function (err, res, body) {
				assert.ok(!err);
				assert.ok(res);
				folderPath = body.substring(7);
				filePath = path.join(path.normalize(folderPath + modulePath + "/java/test.java"));
				console.log("Put resp: " + body);
				assert.equal(201, res.statusCode, "File creation error ");
				assert.ok(fs.existsSync(filePath));
				resolve(res);
			});
			let form = req.form();
			form.append('file', fs.createReadStream(zipFilePath));
		}).then(function () {
			// putting file that already exists should fail
			setTimeout(function () {
				return new Promise(function (resolve) {
					let req = request.put(pathPrefix + modulePath + '/java/test.java', COMMON_OPTIONS, function (err, res) {
						console.log("error: " + err);
						console.log("res: " + res);
						assert.ok(res);
						assert.equal(res.statusCode, 403);
						resolve(res);
					});
					req.form().append('file', fs.createReadStream(zipFilePath));
				});
			}, 10);
		}).then(deletePath.bind(undefined, '/java/test.java', filePath));
	});

	it('Update file that does not exist should fail', () => {
		let zipPostFilePath = path.resolve(__dirname, '../resources/postTest.zip');
		return new Promise(function (resolve) {
			let req = request.post(pathPrefix + modulePath + '/java/testMissed.java', COMMON_OPTIONS, function (err, res) {
				assert.equal(res.statusCode, 500);
				resolve(res);
			});
			req.form().append('file', fs.createReadStream(zipPostFilePath));
		});
	});

	it('Create update and delete file', function () {
		let zipPutFilePath = path.resolve(__dirname, '../resources/putTest.zip');
		let zipPostFilePath = path.resolve(__dirname, '../resources/postTest.zip');
		return new Promise(function (resolve) {
			let req = request.put(pathPrefix + modulePath + '/java/test.java', COMMON_OPTIONS, function (err, res, body) {
				assert.ok(!err);
				assert.ok(res);
				console.log("Put resp: " + body);
				assert.equal(201, res.statusCode, "File creation error");
				assert.ok(fs.existsSync(filePath));
				resolve(res);
			});
			let putForm = req.form();
			putForm.append('file', fs.createReadStream(zipPutFilePath));
		}).then(function () {
			return new Promise(function (resolve, reject) {
				let req = request.post(pathPrefix + modulePath + '/java/test.java', COMMON_OPTIONS, function (err, res, body) {
					onPostResponse(err, res, body, resolve, reject);
				});
				req.form().append('file', fs.createReadStream(zipPostFilePath));
			});
		}).then(deletePath.bind(undefined, '/java/test.java', filePath));
	});

	it('Create folder and delete it', function () {
		let zipPutFolderPath = path.resolve(__dirname, '../resources/putFolderTest.zip');
		return new Promise(function (resolve) {
			let req = request.put(pathPrefix + modulePath + '/java1', COMMON_OPTIONS, function (err, res, body) {
				assert.ok(!err);
				assert.ok(res);
				let filePath = path.join(path.normalize(folderPath + modulePath + "/java1/test.java"));
				console.log("Put resp: " + body);
				assert.equal(201, res.statusCode, "File creation error ");
				assert.ok(fs.existsSync(filePath));
				let newFileContent = fs.readFileSync(filePath).toString();
				assert.equal(newFileContent, "test");
				resolve(res);
			});
			req.form().append('file', fs.createReadStream(zipPutFolderPath));
		}).then(deletePath.bind(undefined, '/java1', path.join(path.normalize(folderPath + modulePath + "/java1"))));
	});
});