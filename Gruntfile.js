/*globals module*/
(function () {
	'use strict';
	module.exports = function(grunt) {
		grunt.initConfig({
			pkg: grunt.file.readJSON('package.json'),
			jshint: {
				options: {
					bitwise: true,
					camelcase: true,
					curly: true,
					eqeqeq: true,
					forin: true,
					freeze: true,
					immed: true,
					indent:2,
					latedef: true,
					newcap: true,
					noarg: true,
					noempty: true,
					nonew: true,
					plusplus: true,
					undef: true,
					unused: true,
					strict: true,
					trailing: true,
					maxlen: 120,
					'-W104': true,
				},
				all: ['Gruntfile.js', 'app/**/*.js', 'public/**/*.js']
			}
		});

		grunt.loadNpmTasks('grunt-contrib-jshint');
		grunt.registerTask('default', ['jshint']);
	};
}());