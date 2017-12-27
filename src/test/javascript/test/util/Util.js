const fs = require('fs');

module.exports = {

	deleteFolderRecursive: function (path) {
		let that = this;
		if (fs.existsSync(path)) {
			fs.readdirSync(path).forEach(function (file, index) {
				let curPath = path + "/" + file;
				if (fs.lstatSync(curPath).isDirectory()) { // recurse
					that.deleteFolderRecursive(curPath);
				} else { // delete file
					fs.unlinkSync(curPath);
				}
			});
			fs.rmdirSync(path);
		}
	}

};