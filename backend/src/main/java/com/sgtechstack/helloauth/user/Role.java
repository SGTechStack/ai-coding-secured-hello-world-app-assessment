package com.sgtechstack.helloauth.user;

public enum Role {

	USER,
	ADMIN;

	public String authority() {
		return "ROLE_" + name();
	}

}
