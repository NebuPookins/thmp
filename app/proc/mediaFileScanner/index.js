/*globals __dirname, __filename, console, module, require*/
(function () {
	'use strict';

	var commands    = require('./commands');
	var config      = require('../../../config');
	var MediaFile   = require('../../models/MediaFile');
	var path        = require('path');
	var q           = require('q');

	var childProcess = require('child_process').fork(
		path.join(__dirname, './mediaFileScanner.proc'),
		[]
	);

	var logger = config.getLogger(__filename);

	/**
	 * This is a map where the keys are the paths being analyzed, and the values
	 * are the deferred objects representing the promise to analyze that path.
	 */
	var pathToDeferredMap = {};

	/**
	 * Returns a promise that, when resolved, means all the files in the specified
	 * directory have been processed.
	 */
	module.exports.addMediaFileToDB = function (entry) {
		if (pathToDeferredMap[entry]) {
			return pathToDeferredMap[entry].promise;
		}
		var deferred = q.defer();
		pathToDeferredMap[entry] = deferred;
		childProcess.send(new (commands.SCAN_DIR)(entry));
		return deferred.promise;
	};

	childProcess.on('message', function (msg) {
		if (msg.code === commands.FOUND_FILE.code) {
			MediaFile.fromPath(msg.path).then(function (mediaFile) {
				return config.modelDAO.putMediaFile(mediaFile).fail(function (err) {
					if (err instanceof Error) {
						logger.warn('Error saving MediaFile %j: %s.', mediaFile.toJSON(), err.message);
					} else {
						logger.warn('Error saving MediaFile %j: %s.', mediaFile.toJSON(), err);
					}
				});
			});
		} else if (msg.code === commands.SCAN_DONE) {
			var defer = pathToDeferredMap[msg.path];
			if (defer) {
				defer.resolve();
			} else {
				logger.warn('Child process finish scan of %s, but it was not in my defer list.', msg.path);
			}
		} else {
			logger.warn('Received unrecognized command: %s.', msg.cmd);
		}
	});

	//TODO: Need to monitor child process, and when it dies, restart it.
}());