package com.arghyam.backend.dao;

import com.arghyam.backend.dto.AccessTokenResponseDTO;
import com.arghyam.backend.dto.LoginDTO;
import com.arghyam.backend.dto.LoginResponseDTO;
import com.arghyam.backend.dto.ResponseDTO;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.validation.BindingResult;

import java.io.IOException;

public interface KeycloakService {

    public void register(String token, UserRepresentation userRepresentation) throws IOException;

    public String generateAccessTokenFromUserName(String username) throws IOException;

    public UserRepresentation getUserByUsername(String token,String username,String realm) throws IOException;

    public AccessTokenResponseDTO refreshAccessToken(LoginDTO loginDTO) ;

    public ResponseDTO logout(String id) throws IOException;

    String generateAccessToken(String adminUserName, String adminPassword) throws IOException;

    public void updateUser(String token, String id, UserRepresentation user, String realm) throws IOException;

    public LoginResponseDTO login(UserRepresentation loginRequest, BindingResult bindingResult) throws IOException;
}
