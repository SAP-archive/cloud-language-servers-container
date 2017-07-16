// import * as fs from "tns-core-modules/file-system";

const assert = require("chai").assert;
const fs = require('fs');
const request = require('request');
const path = require('path');
const pathPrefix = "http://localhost:8080/WSSynchronization";
var folderPath = "";
var filePath = "";

describe('Sync Integration Test', function () {

    after(function () {
        try {
            fs.rmdirSync(folderPath);
            console.log("Root folder deleted");
        } catch (e) {
            console.error("Error deleting root folder");
        }
    });


    function onPutResponse(err, res, body, resolve, reject) {
        if (res) {
            folderPath = body.substring(7);
            filePath = path.join(path.normalize(folderPath + "/test.js"));
            if (res.statusCode == 201 && fs.existsSync(filePath)) {
                assert("Put request succeeded");
            } else {
                assert.fail("Error creating file");
            }
            resolve(res);
        }
        if (err) {
            assert.fail();
            reject(err);
        }
    }

    function onPostResponse(err, res, body, resolve, reject) {
        if (res) {
            if (res.statusCode == 200 && fs.existsSync(filePath)) {
                // if (fs.readFileSync(filePath).toString() == "test") {
                //     assert("Post request succeeded");
                // } else {
                //     assert.fail("Post request failed");
                // }
            } else {
                assert.fail("Error " + request);
            }
            resolve(res);
        }
        if (err) {
            assert.fail();
            reject(err);
        }
    }

    function onDeleteResponse(err, res, body, resolve, reject) {
        if (res) {
            if (res.statusCode == 200 && !fs.existsSync(filePath)) {
                assert("Delete request succeeded");
            } else {
                assert.fail("Error deleting file");
            }
            resolve(res);
        }
        if (err) {
            assert.fail(err);
            reject(err);
        }
    }

    it('Check create and delete file', function () {
        var zipFilePath = path.resolve(__dirname, '../resources/putTest.zip');
        var options = {
            headers: {
                "Content-Type": "multipart/form-data"
            }
        };
        return new Promise(function (resolve, reject) {
            var req = request.put(pathPrefix, options, function (err, res, body) {
                onPutResponse(err, res, body, resolve, reject);
            });
            var form = req.form();
            form.append('file', fs.createReadStream(zipFilePath));

        }).then(function () {
            return new Promise(function (resolve, reject) {
                request.delete(pathPrefix + '/test.js', options, function (err, res, body) {
                    onDeleteResponse(err, res, body, resolve, reject, "delete");
                });
            });
        });
    });

    it('Check create update and delete file', function () {
        var zipPutFilePath = path.resolve(__dirname, '../resources/putTest.zip');
        var zipPostFilePath = path.resolve(__dirname, '../resources/postTest.zip');
        var options = {
            headers: {
                "Content-Type": "multipart/form-data"
            }
        };
        return new Promise(function (resolve, reject) {
            var req = request.put(pathPrefix, options, function (err, res, body) {
                onPutResponse(err, res, body, resolve, reject);
            });
            var putForm = req.form();
            putForm.append('file', fs.createReadStream(zipPutFilePath));
        }).then(function () {
            return new Promise(function (resolve, reject) {
                var req = request.post(pathPrefix + '/test.js', options, function (err, res, body) {
                    onPostResponse(err, res, body, resolve, reject, "post");
                });
                var postForm = req.form();
                postForm.append('file', fs.createReadStream(zipPostFilePath));
            }).then(function () {
                return new Promise(function (resolve, reject) {
                    request.delete(pathPrefix + '/test.js', options, function (err, res, body) {
                        onDeleteResponse(err, res, body, resolve, reject, "delete");
                    });
                });
            });
        });
    });
});