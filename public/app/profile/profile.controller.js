(function(){
	'use strict';
	
	angular
		.module('PLMApp')
		.controller('Profile', Profile);

	Profile.$inject = ['$scope', 'userService'];

	function Profile($scope, userService) {
		var profile = this;

		$scope.$on('$destroy', $scope.$watch('userService.getUser()', setUser));

		profile.mode = 'view';
		profile.user;
		profile.userTemp;

		profile.switchToMode = switchToMode;
		profile.updateProfile = updateProfile;

		function switchToMode(mode) {
			profile.mode = mode;
		}

		function setUser() {
			profile.user = userService.getUser();
			profile.userTemp = userService.cloneUser();
		}

		function updateProfile() {
			userService.updateUser(profile.userTemp);
			switchToMode('view');
		}
	}
})();