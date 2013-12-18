/*globals module*/
(function() {
	'use strict';

	/**
	 * Command from the dispatcher to the child process that the child process
	 * should start scanning a directory for any media files it can find.
	 */
	function SCAN_DIR(path) {
		this.path = path;
	}
	SCAN_DIR.prototype.code = 'SCAN_DIR';
	module.exports.SCAN_DIR = SCAN_DIR;

	/**
	 * Command from child process to dispatcher indicating that it found a file
	 * that it believes to be a media file.
	 */
	function FOUND_FILE(path) {
		this.path = path;
	}
	FOUND_FILE.prototype.code = 'FOUND_FILE';
	module.exports.FOUND_FILE = FOUND_FILE;

	/**
	 * Command from child process to dispatcher indicating that the scan of the
	 * specified directory is done.
	 */
	function SCAN_DONE(path) {
		this.path = path;
	}
	SCAN_DONE.prototype.code = 'SCAN_DONE';
	module.exports.SCAN_DONE = SCAN_DONE;
}());