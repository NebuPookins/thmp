/*globals module, require*/
(function() {
	'use strict';

	var _ = require('underscore');

	module.exports.functions = [
		'putMediaFile', 'listMediaFile', 'findMediaFileByPath'
	];

	module.exports.assertIsImplementedBy = function (obj) {
		_.each(module.exports.functions, function(f) {
			if (typeof obj[f] !== 'function') {
				throw new Error('Did not implement ' + f + '.');
			}
		});
	};
}());