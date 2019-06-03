package com.arghyam.backend.service.ServiceImpl;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.arghyam.backend.config.AppContext;
import com.arghyam.backend.dao.KeycloakDAO;
import com.arghyam.backend.dao.KeycloakService;
import com.arghyam.backend.dao.RegistryDAO;
import com.arghyam.backend.dto.*;
import com.arghyam.backend.entity.DischargeData;
import com.arghyam.backend.entity.RegistryUser;
import com.arghyam.backend.dto.LoginAndRegisterResponseMap;
import com.arghyam.backend.dto.LoginDTO;
import com.arghyam.backend.dto.RequestDTO;
import com.arghyam.backend.dto.ResponseDTO;
import com.arghyam.backend.entity.Springs;
import com.arghyam.backend.entity.Springuser;
import com.arghyam.backend.exceptions.UnprocessableEntitiesException;
import com.arghyam.backend.exceptions.ValidationError;
import com.arghyam.backend.service.UserService;
import com.arghyam.backend.utils.Constants;
import com.arghyam.backend.utils.AmazonUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import retrofit2.Call;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

import static com.arghyam.backend.utils.Constants.ARGHYAM_S3_FOLDER_LOCATION;
import static com.arghyam.backend.utils.Constants.IMAGE_UPLOAD_SUCCESS_MESSAGE;
import static java.util.Arrays.asList;

@Component
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    AmazonS3 amazonS3;

    @Autowired
    AppContext appContext;


    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    KeycloakDAO keycloakDAO;

    @Autowired
    Keycloak keycloak;

    @Autowired
    KeycloakService keycloakService;


    @Autowired
    RegistryDAO registryDao;

    @Autowired
    LoginServiceImpl loginServiceImpl;

    ObjectMapper mapper = new ObjectMapper();

    private static Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);


    @Override
    public void createUsers(RequestDTO requestDTO, String userToken, BindingResult bindingResult) throws IOException {
        validatePojo(bindingResult);
        UserRepresentation registerResponseDTO = mapper.convertValue(requestDTO.getRequest().get("person"), UserRepresentation.class);
        try {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue("password");
            credential.setTemporary(false);
            registerResponseDTO.setEnabled(Boolean.TRUE);      // A disabled user cannot login.
            registerResponseDTO.setCredentials(asList(credential));
            keycloakService.register(userToken, registerResponseDTO);
        } catch (Exception e) {
            System.out.println("exception" + e);
        }
    }



    public CredentialRepresentation setCredentialPassword(String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        return credential;
    }

    @Override
    public Keycloak getKeycloak() {
        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(appContext.getKeyCloakServiceUrl())
                .realm(appContext.getRealm())
                .username(appContext.getAdminUserName())
                .password(appContext.getAdminUserpassword())
                .clientId(appContext.getClientId())
                .clientSecret(appContext.getClientSecret())
                .resteasyClient(new ResteasyClientBuilder().connectionPoolSize(10).build())
                .build();

        return keycloak;
    }


    @Override
    public void validatePojo(BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            List<ValidationError> errorList = new ArrayList<>();

            for (FieldError error : bindingResult.getFieldErrors()) {
                ValidationError validationError = new ValidationError(error.getField(), error.getDefaultMessage());
                errorList.add(validationError);
            }
            throw new UnprocessableEntitiesException(errorList);
        }
    }


    @Override
    public String otpgenerator() {
        int randomPIN = (int) (Math.random() * 9000) + 1000;
        return String.valueOf(randomPIN);
    }

    @Override
    public LoginAndRegisterResponseMap getUserProfile(RequestDTO requestDTO, BindingResult bindingResult) throws IOException {
        LoginAndRegisterResponseMap loginAndRegisterResponseMap = new LoginAndRegisterResponseMap();
        UserRepresentation userRepresentation = getUserFromKeycloak(requestDTO);
        Map<String, Object> springUser = new HashMap<>();
        if (userRepresentation != null) {
            springUser.put("responseObject", userRepresentation);
            springUser.put("responseCode", 200);
            springUser.put("responseStatus", "user profile fetched");
        } else {
            springUser.put("responseObject", userRepresentation);
            springUser.put("responseCode", 404);
            springUser.put("responseStatus", "user profile not found");
        }
        BeanUtils.copyProperties(requestDTO, loginAndRegisterResponseMap);
        loginAndRegisterResponseMap.setResponse(springUser);
        return loginAndRegisterResponseMap;
    }


    @Override
    public LoginAndRegisterResponseMap reSendOtp(RequestDTO requestDTO, BindingResult bindingResult) throws IOException {
        UserRepresentation userRepresentation = null;
        validatePojo(bindingResult);
        String adminToken = keycloakService.generateAccessToken(appContext.getAdminUserName(), appContext.getAdminUserpassword());
        if (null != requestDTO.getRequest() && requestDTO.getRequest().keySet().contains("person")) {
            LoginDTO loginDTO = mapper.convertValue(requestDTO.getRequest().get("person"), LoginDTO.class);
            userRepresentation = keycloakService.getUserByUsername(adminToken, loginDTO.getUsername(), appContext.getRealm());
            loginServiceImpl.updateOtpForUser(loginDTO, adminToken, userRepresentation, "resendOtp");
        }
        LoginAndRegisterResponseMap responseDTO = new LoginAndRegisterResponseMap();
        responseDTO.setId(requestDTO.getId());
        responseDTO.setEts(requestDTO.getEts());
        responseDTO.setVer(requestDTO.getVer());
        responseDTO.
                setParams(requestDTO.getParams());
        HashMap<String, Object> map = new HashMap<>();
        map.put("responseCode", 200);
        map.put("responseStatus", "Otp sent successfully");
        map.put("response", null);

        responseDTO.setResponse(map);
        return responseDTO;
    }

    /**
     * Upload's images to amazon S3
     * @param file
     *
     * @return
     */
    @Override
    public ResponseDTO updateProfilePicture(MultipartFile file) {
        try {
            File imageFile = AmazonUtils.convertMultiPartToFile(file);
            String fileName = AmazonUtils.generateFileName(file);

            PutObjectRequest request = new PutObjectRequest(appContext.getBucketName(), ARGHYAM_S3_FOLDER_LOCATION+fileName, imageFile);
            amazonS3.putObject(request);
            imageFile.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return sendResponse();
    }

    /**
     * Image upload api response
     * @return
     */
    private ResponseDTO sendResponse() {
        ResponseDTO responseDTO=new ResponseDTO();
        responseDTO.setResponseCode(200);
        responseDTO.setMessage(IMAGE_UPLOAD_SUCCESS_MESSAGE);
        responseDTO.setResponse(null);
        return responseDTO;
    }


    private UserRepresentation getUserFromKeycloak(RequestDTO requestDTO) throws IOException {
        String userToken = keycloakService.generateAccessToken(appContext.getAdminUserName(), appContext.getAdminUserpassword());
        Springuser springuser = new Springuser();
        if (requestDTO.getRequest().keySet().contains("person")) {
            springuser = mapper.convertValue(requestDTO.getRequest().get("person"), Springuser.class);
        }
        return keycloakService.getUserByUsername(userToken, springuser.getPhonenumber(), appContext.getRealm());
    }


    @Override
    public LoginAndRegisterResponseMap updateUserProfile(RequestDTO requestDTO, BindingResult bindingResult) throws IOException {
        validatePojo(bindingResult);
        LoginAndRegisterResponseMap loginAndRegisterResponseMap = new LoginAndRegisterResponseMap();
        String userToken = keycloakService.generateAccessToken(appContext.getAdminUserName(), appContext.getAdminUserpassword());
        Springuser springuser = new Springuser();
        if (requestDTO.getRequest().keySet().contains("person")) {
            springuser = mapper.convertValue(requestDTO.getRequest().get("person"), Springuser.class);
        }

        UserRepresentation userRepresentation = keycloakService.getUserByUsername(userToken, springuser.getPhonenumber(), appContext.getRealm());
        if (userRepresentation != null) {
            userRepresentation.setFirstName(springuser.getName());
        }
        keycloakService.updateUser(userToken, userRepresentation.getId(), userRepresentation, appContext.getRealm());
        Map<String, Object> springUser = new HashMap<>();
        springUser.put("responseObject", null);
        springUser.put("responseCode", 200);
        springUser.put("responseStatus", "user profile updated");
        BeanUtils.copyProperties(requestDTO, loginAndRegisterResponseMap);
        loginAndRegisterResponseMap.setResponse(springUser);
        return loginAndRegisterResponseMap;
    }



    @Override
    public LoginAndRegisterResponseMap createRegistryUser(RequestDTO requestDTO, BindingResult bindingResult) throws IOException{
        String adminAccessToken = keycloakService.generateAccessToken(appContext.getAdminUserName(), appContext.getAdminUserpassword());
        Springuser springuser = new Springuser();
        if(requestDTO.getRequest().keySet().contains("person")) {
            springuser = mapper.convertValue(requestDTO.getRequest().get("person"), Springuser.class);
        }
        UserRepresentation userRepresentation = keycloakService.getUserByUsername(adminAccessToken, springuser.getPhonenumber(), appContext.getRealm());
        RegistryUser person = new RegistryUser(springuser.getName(), "", "",
                    "", "", new java.util.Date().toString(), new java.util.Date().toString(), springuser.getPhonenumber());


        Map<String, Object> personMap = new HashMap<>();
        personMap.put("person", person);
        String stringRequest = objectMapper.writeValueAsString(personMap);
        RegistryRequest registryRequest = new RegistryRequest(null, personMap, com.arghyam.backend.dto.RegistryResponse.API_ID.CREATE.getId(), stringRequest);

        try {
            Call<RegistryResponse> createRegistryEntryCall = registryDao.createUser(adminAccessToken, registryRequest);
            retrofit2.Response registryUserCreationResponse = createRegistryEntryCall.execute();
            if (!registryUserCreationResponse.isSuccessful()) {
                logger.error("Error Creating registry entry {} ", registryUserCreationResponse.errorBody().string());
            }

            userRepresentation.getAttributes().put(Constants.REG_ENTRY_CREATED, asList(Boolean.TRUE.toString()));
            retrofit2.Response updateKeycloakUser = keycloakDAO.updateUser("Bearer" + adminAccessToken, userRepresentation.getId(), userRepresentation, appContext.getRealm()).execute();
            if (!updateKeycloakUser.isSuccessful()) {
                logger.error("Error Updating user {} ", updateKeycloakUser.errorBody().string());
            }
            logger.info("Registry entry created and user is successfully logged in");

        } catch (IOException e) {
            logger.error("Error creating registry entry : {} ", e.getMessage());
        }
        return null;
    }


    @Override
    public LoginAndRegisterResponseMap getRegistereUsers() throws IOException {
        String adminAccessToken = keycloakService.generateAccessToken(appContext.getAdminUserName(), appContext.getAdminUserpassword());
        List<UserRepresentation> getRegisteredUsers = keycloakService.getRegisteredUsers(adminAccessToken, appContext.getRealm());
        LoginAndRegisterResponseMap loginAndRegisterResponseMap = new LoginAndRegisterResponseMap();
        Map<String, Object> response = new HashMap<>();
        response.put("responseCode", 200);
        response.put("responseStatus", "Fetched list of registered users");
        response.put("responseObject", getRegisteredUsers);
        loginAndRegisterResponseMap.setResponse(response);
        loginAndRegisterResponseMap.setVer("1.0");
        loginAndRegisterResponseMap.setId("forWater.user.getRegisteredUsers");
        return loginAndRegisterResponseMap;
    }

    @Override
    public LoginAndRegisterResponseMap createDischargeData(RequestDTO requestDTO, BindingResult bindingResult) throws IOException {
        String adminAccessToken = keycloakService.generateAccessToken(appContext.getAdminUserName(), appContext.getAdminUserpassword());
        DischargeData dischargeData = mapper.convertValue(requestDTO.getRequest().get("dischargeData"), DischargeData.class);
        LoginAndRegisterResponseMap loginAndRegisterResponseMap = new LoginAndRegisterResponseMap();

        DischargeData discharge = new DischargeData();
        discharge.setDischargeTime(dischargeData.getDischargeTime());
        discharge.setSpringCode(getAlphaNumericString(6));
        discharge.setSpringName(dischargeData.getSpringName());
        discharge.setCreatedDate(new Date().toString());
        discharge.setUserId(UUID.randomUUID().toString());

        Map<String, Object> dischargrMap = new HashMap<>();
        dischargrMap.put("dischargeData", discharge);
        String stringRequest = objectMapper.writeValueAsString(dischargrMap);
        RegistryRequest registryRequest = new RegistryRequest(null, dischargrMap, com.arghyam.backend.dto.RegistryResponse.API_ID.CREATE.getId(), stringRequest);

        try {
            Call<RegistryResponse> createRegistryEntryCall = registryDao.createUser(adminAccessToken, registryRequest);
            retrofit2.Response registryUserCreationResponse = createRegistryEntryCall.execute();
            if (!registryUserCreationResponse.isSuccessful()) {
                logger.error("Error Creating registry entry {} ", registryUserCreationResponse.errorBody().string());
            }

        } catch (IOException e) {
            logger.error("Error creating registry entry : {} ", e.getMessage());
        }

        BeanUtils.copyProperties(requestDTO, loginAndRegisterResponseMap);
        Map<String, Object> response = new HashMap<>();
        response.put("responseCode", 200);
        response.put("responseStatus", "created discharge data successfully");
        response.put("responseObject", discharge);
        loginAndRegisterResponseMap.setResponse(response);
        return loginAndRegisterResponseMap;
    }

    @Override
    public LoginAndRegisterResponseMap createSpring(RequestDTO requestDTO, BindingResult bindingResult) throws IOException {
        String adminAccessToken = keycloakService.generateAccessToken(appContext.getAdminUserName(), appContext.getAdminUserpassword());
        Springs springs = mapper.convertValue(requestDTO.getRequest().get("springs"), Springs.class);
        LoginAndRegisterResponseMap loginAndRegisterResponseMap = new LoginAndRegisterResponseMap();

        Springs springDto = new Springs();
        BeanUtils.copyProperties(springs, springDto);
        springDto.setSpringCode(getAlphaNumericString(6));
        springDto.setElevation("");
        springDto.setCrtdDttm(new Date().toString());
        springDto.setUpdtDttm("");
        springDto.setOrganization(Arrays.asList("organisation1"));
        springDto.setTenantId("");
        springDto.setUploadedBy("");
        springDto.setUsage(Arrays.asList("usage-short"));
        springDto.setVillage(Arrays.asList("village1"));

        Map<String, Object> springMap = new HashMap<>();
        springMap.put("springs", springDto);
        String stringRequest = objectMapper.writeValueAsString(springMap);
        RegistryRequest registryRequest = new RegistryRequest(null, springMap, com.arghyam.backend.dto.RegistryResponse.API_ID.CREATE.getId(), stringRequest);

        try {
            Call<RegistryResponse> createRegistryEntryCall = registryDao.createUser(adminAccessToken, registryRequest);
            retrofit2.Response registryUserCreationResponse = createRegistryEntryCall.execute();
            if (!registryUserCreationResponse.isSuccessful()) {
                logger.error("Error Creating registry entry {} ", registryUserCreationResponse.errorBody().string());
            }

        } catch (IOException e) {
            logger.error("Error creating registry entry : {} ", e.getMessage());
        }

        BeanUtils.copyProperties(requestDTO, loginAndRegisterResponseMap);
        Map<String, Object> response = new HashMap<>();
        response.put("responseCode", 200);
        response.put("responseStatus", "created discharge data successfully");
        response.put("responseObject", springDto);
        loginAndRegisterResponseMap.setResponse(response);
        return loginAndRegisterResponseMap;
    }


    public static String getAlphaNumericString(int n)
        {
            byte[] array = new byte[256];
            new Random().nextBytes(array);

            String randomString
                    = new String(array, Charset.forName("UTF-8"));
            StringBuffer r = new StringBuffer();

            String  AlphaNumericString
                    = randomString
                    .replaceAll("[^A-Za-z0-9]", "");

            for (int k = 0; k < AlphaNumericString.length(); k++) {
                if (Character.isLetter(AlphaNumericString.charAt(k))
                        && (n > 0)
                        || Character.isDigit(AlphaNumericString.charAt(k))
                        && (n > 0)) {
                    r.append(AlphaNumericString.charAt(k));
                    n--;
                }
            }
            return r.toString();
        }

}


