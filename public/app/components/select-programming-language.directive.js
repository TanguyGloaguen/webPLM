(function(){
	'use strict';
	
	angular
		.module('PLMApp')
		.directive('selectProgrammingLanguage', selectProgrammingLanguage);
	
	function selectProgrammingLanguage() {
		return {
			restrict: 'E',
			templateUrl: '/assets/app/components/select-programming-language.directive.html',
			link: function (scope, element, attrs) {
				$(document).foundation('dropdown', 'reflow');
			}
		};
	}
})();