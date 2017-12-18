package com.my.userstore;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.identity.application.common.IdentityApplicationManagementException;
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.user.api.Permission;
import org.wso2.carbon.user.api.Properties;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.claim.Claim;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.common.RoleContext;
import org.wso2.carbon.user.core.tenant.Tenant;
import org.wso2.carbon.user.api.Property;
import org.wso2.carbon.user.core.util.DatabaseUtil;
import org.wso2.carbon.identity.application.common.model.ThreadLocalProvisioningServiceProvider;
import org.wso2.carbon.identity.application.common.util.IdentityApplicationManagementUtil;
import org.wso2.carbon.identity.application.common.model.ProvisioningServiceProviderType;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;

import javax.net.ssl.HttpsURLConnection;
import javax.sql.DataSource;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class MyUserStoreManager extends AbstractUserStoreManager {

    private static Log log = LogFactory.getLog(MyUserStoreManager.class);

    private static ThreadLocal<String> userAuthenticationStatus = new ThreadLocal<String>() {
        @Override
        protected String initialValue() {
            return ""; //setting initial value to an empty string
        }
    };


    public static String getUserAuthenticationStatus() {
        log.info("retrieving thread local variable userAuthenticationStatus");
        return userAuthenticationStatus.get();
    }

    public static void setUserAuthenticationStatus(String status) {
        log.info("setting thread local variable userAuthenticationStatus to: " + status);

        userAuthenticationStatus.set(status);
    }

    @Override
    protected boolean authenticate(String userName, Object credential, boolean domainProvided) throws UserStoreException {
        return super.authenticate(userName, credential, domainProvided);
    }

    @Override
    protected void doAddInternalRole(String roleName, String[] userList, Permission[] permissions) throws UserStoreException {
        super.doAddInternalRole(roleName, userList, permissions);
    }

    /**
     * Clear the thread local variable userAuthenticationStatus
     */
    public static void clearUserAuthenticationStatus() {
        log.info("clearing thread local variable userAuthenticationStatus");

        userAuthenticationStatus.remove();
    }


    public MyUserStoreManager() {

        log.info("MyUserStoreManager Initializing Started " + System.currentTimeMillis());

        this.tenantId = -1234;
        this.readGroupsEnabled = false;
        this.writeGroupsEnabled = false;

        log.info("MyUserStoreManager initialized...");
    }

    public MyUserStoreManager(RealmConfiguration realmConfig, Map<String, Object> properties) throws UserStoreException {

        log.info("MyUserStoreManager Initializing Started " + System.currentTimeMillis());


        this.realmConfig = realmConfig;
        this.tenantId = -1234;
        this.readGroupsEnabled = false;
        this.writeGroupsEnabled = false;

        this.realmConfig = realmConfig;
        this.dataSource = (DataSource) properties.get("um.datasource");
        if (this.dataSource == null) {
            this.dataSource = DatabaseUtil.getRealmDataSource(realmConfig);
        }

        if (this.dataSource == null) {
            throw new UserStoreException("Data Source is null");
        } else {
            properties.put("um.datasource", this.dataSource);

            this.persistDomain();
            this.doInitialSetup();
            this.initUserRolesCache();
            log.info("Initializing Ended " + System.currentTimeMillis());
        }

        try {
            claimManager = (org.wso2.carbon.user.core.claim.ClaimManager) CarbonContext.getThreadLocalCarbonContext().getUserRealm().getClaimManager();
        } catch (org.wso2.carbon.user.api.UserStoreException e) {
            log.error("Error initializing the Claim Manager", e);
        }

        log.info("MyUserStoreManager initialized...");
    }

    @Override
    public Properties getDefaultUserStoreProperties() {

        Property[] mandatoryProperties = MyUserStoreManagerConstants.CUSTOM_UM_MANDATORY_PROPERTIES.toArray(
                new Property[MyUserStoreManagerConstants.CUSTOM_UM_MANDATORY_PROPERTIES.size()]);
        Property[] optionalProperties = MyUserStoreManagerConstants.CUSTOM_UM_OPTIONAL_PROPERTIES.toArray
                (new Property[MyUserStoreManagerConstants.CUSTOM_UM_OPTIONAL_PROPERTIES.size()]);
        Property[] advancedProperties = MyUserStoreManagerConstants.CUSTOM_UM_ADVANCED_PROPERTIES.toArray
                (new Property[MyUserStoreManagerConstants.CUSTOM_UM_ADVANCED_PROPERTIES.size()]);

        Properties properties = new Properties();
        properties.setMandatoryProperties(mandatoryProperties);
        properties.setOptionalProperties(optionalProperties);

        //Since there are no advanced properties yet, following is not used
        //properties.setAdvancedProperties(advancedProperties);
        return properties;
    }

    private String getServiceProviderName(){
        ThreadLocalProvisioningServiceProvider tsp = IdentityApplicationManagementUtil.getThreadLocalProvisioningServiceProvider();
        String clientID = tsp.getServiceProviderName();
        String tenantDomainName = tsp.getTenantDomain();
        String serviceProviderName = null;
        if (tsp.getServiceProviderType() == ProvisioningServiceProviderType.OAUTH) {

            try {
                serviceProviderName = ApplicationManagementService.getInstance().getServiceProviderNameByClientId(clientID, "oauth2", tenantDomainName);
            } catch (IdentityApplicationManagementException e) {
                // handle
            }

        }

        return serviceProviderName;
    }

    private String getTeamIDofServiceProvider(){

        ThreadLocalProvisioningServiceProvider tsp = IdentityApplicationManagementUtil.getThreadLocalProvisioningServiceProvider();
        String clientID = tsp.getServiceProviderName();
        String tenantDomainName = tsp.getTenantDomain();

        String teamID = null;
        String serviceProviderName = null;


        try {
            serviceProviderName = ApplicationManagementService.getInstance().getServiceProviderNameByClientId(clientID, "oauth2", tenantDomainName);

            ServiceProvider serviceProvider = ApplicationManagementService.getInstance().getServiceProvider(serviceProviderName,tenantDomainName);
            teamID = serviceProvider.getDescription();

        } catch (IdentityApplicationManagementException e) {
            // handle
        }

        return teamID;
    }



    @Override
    protected void doAddUser(String userName, Object credential, String[] roleList, Map<String, String> claims, String profileName, boolean requirePasswordChange) throws UserStoreException {


        String apiURL = realmConfig.getUserStoreProperty(MyUserStoreManagerConstants.SERVICE_URL_PROPERTY_NAME);
        log.info("doAddUser method called for usre: " + userName);

        for (Map.Entry<String, String> entry : claims.entrySet()) {
            log.info("Key : " + entry.getKey() + ", Value : " + entry.getValue());
        }

        //urn:scim:schemas:core:1.0:id claim contains the SCIM ID of the user

        log.info("Invoking the API : " + apiURL);


        log.info("Service Provider Name : " + getServiceProviderName());
        log.info("Team ID : " + getTeamIDofServiceProvider());

        //Prepare the JSON payload
        String jsonPayload = "{\"userName\": \"" + userName + "\"}";

        //make API Call
        try {

            URL url = new URL(apiURL);
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

            //add reuqest header
            con.setRequestMethod("POST");
            con.setRequestProperty("User-Agent", "");
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

            // Send post request
            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(jsonPayload);
            wr.flush();
            wr.close();

            int responseCode = con.getResponseCode();
            log.info("\nSending 'POST' request to URL : " + url);
            log.info("Post parameters : " + jsonPayload);
            log.info("Response Code : " + responseCode);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            //print result
            log.info(response.toString());


        } catch(Exception e){
            log.error("error occured");
            e.printStackTrace();
        }
    }

    @Override
    protected void doDeleteUser(String userName) throws UserStoreException {

        log.info("doDeleteUser method called for user: " + userName);

    }

    public Claim [] getUserClaims (String userName, String profileName) throws UserStoreException{

        log.info("getUserClaims method called for user: " + userName);
        Claim[] claims = super.getUserClaimValues(userName, profileName);
        return claims;
    }

    @Override
    protected String[] doListUsers(String filter, int maxItemLimit) throws UserStoreException {

        log.info("doListUsers method called");

        if (filter.equals("*")) {
            return new String[]{"*multiple-users*"};
        } else {
            //Test only
            return new String[]{"*multiple-users*"};
        }
    }

    @Override
    protected boolean doCheckExistingUser(String userName) throws UserStoreException {

        log.info("doCheckExistingUser method called for userName: "+ userName);

        //hard coding for testing purpose
        if("admin".equals(userName)){
            return true;
        }
        return false;
    }


    @Override
    protected String[] getUserListFromProperties(String property, String value, String profileName) throws UserStoreException {

        log.info("getUserListFromProperties method called");
        log.info("property: " + property + ", value: " + value + ", profileName: " + profileName);


        // call the external API and get the username matching this request's user

        if("scimId".equals(property)){
            //value is XXXXX in this URL https://localhost:9443/wso2/scim/Users/XXXXX when making the request


        } else if("uid".equals(property)){
            // value is XXXXX in https://localhost:9443/wso2/scim/Users?filter=username+eq+XXXXX when searching users
        }

        if (profileName == null) {
            profileName = UserCoreConstants.DEFAULT_PROFILE;
        }


        String matchingUserName = "admin"; //hard coded for testing purpose
        //add the username to the array

        String [] userNameArray = new String []{matchingUserName};

        return userNameArray;
    }


    // system methods that are important for the usercase

    @Override
    public int getTenantId(String s) throws UserStoreException {
        //log.info("getTenantId method called");
        return -1234;
    }

    @Override
    public int getTenantId() throws UserStoreException {
        //log.info("getTenantId method called");
        return -1234;
    }

    @Override
    public RealmConfiguration getRealmConfiguration() {
        //log.info("getRealmConfiguration method called");


        return this.realmConfig;
    }

    @Override
    public boolean isReadOnly() throws UserStoreException {
        log.info("isReadOnly method called");

        return Boolean.parseBoolean(realmConfig.getUserStoreProperty(MyUserStoreManagerConstants.READ_ONLY_PROPERTY_NAME));
    }


    // Other methods in the abstract class are not important for this usecase

    @Override
    protected String[] doGetSharedRoleNames(String s, String s1, int i) throws UserStoreException {

        log.info("doGetSharedRoleNames method called");
        return new String[0];
    }

    @Override
    protected Map<String, String> getUserPropertyValues(String userName, String[] propertyNames, String profileName) throws UserStoreException {

        log.info("getUserPropertyValues method called for user: " + userName + " with profile name: " + profileName);
        log.info("property Names : ");
        for(String property : propertyNames){
            log.info(property);
        }

        //createdDate, lastModifiedDate, scimId

        Map<String, String> propertyValues = new HashMap<>();

        // date should be yyyy-MM-dd'T'HH:mm:ss format

        //SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        //sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        propertyValues.put("createdDate", "2017-09-29T18:46:19-0700");
        propertyValues.put("lastModifiedDate", "2017-09-29T18:46:19-0700");

        //Set the new User ID here
        propertyValues.put("scimId", "79434348938434");

        return propertyValues;
    }

    @Override
    protected boolean doCheckExistingRole(String s) throws UserStoreException {

        log.info("doCheckExistingRole method called");
        return false;
    }

    @Override
    protected RoleContext createRoleContext(String s) throws UserStoreException {

        log.info("createRoleContext method called");

        RoleContext rc = new RoleContext();
        return rc;
    }

    @Override
    protected boolean doAuthenticate(String userName, Object o) throws UserStoreException {

        //Clear the thread local variable before starting authentication process
        clearUserAuthenticationStatus();

        if(userName.equals("admin")){
            return true;
        } else {

            String errorMessage = "username is not equal to admin. You cannot proceed further";
            //set the thread local variable
            userAuthenticationStatus.set(errorMessage);
            log.info("user authentication failed. Error message is set to thread local variable");

            //here we don't throw exception since the doPostAuthenticate method in the listener would throw the exception
            return false;
        }
    }


    @Override
    protected void doUpdateCredential(String s, Object o, Object o1) throws UserStoreException {

        log.info("doUpdateCredential method called");

    }

    @Override
    protected void doUpdateCredentialByAdmin(String s, Object o) throws UserStoreException {

        log.info("doUpdateCredentialByAdmin method called");

    }


    @Override
    protected void doSetUserClaimValue(String s, String s1, String s2, String s3) throws UserStoreException {

        log.info("doSetUserClaimValue method called");

    }

    @Override
    protected void doSetUserClaimValues(String s, Map<String, String> map, String s1) throws UserStoreException {

        log.info("doSetUserClaimValues method called");

    }

    @Override
    protected void doDeleteUserClaimValue(String s, String s1, String s2) throws UserStoreException {

        log.info("doDeleteUserClaimValue method called");

    }

    @Override
    protected void doDeleteUserClaimValues(String s, String[] strings, String s1) throws UserStoreException {

        log.info("doDeleteUserClaimValues method called");

    }

    @Override
    protected void doUpdateUserListOfRole(String s, String[] strings, String[] strings1) throws UserStoreException {

        log.info("doUpdateUserListOfRole method called");

    }

    @Override
    protected void doUpdateRoleListOfUser(String s, String[] strings, String[] strings1) throws UserStoreException {

        log.info("doUpdateRoleListOfUser method called");

    }

    @Override
    protected String[] doGetExternalRoleListOfUser(String s, String s1) throws UserStoreException {
        log.info("doGetExternalRoleListOfUser method called");
        return new String[0];
    }

    @Override
    protected String[] doGetSharedRoleListOfUser(String s, String s1, String s2) throws UserStoreException {

        log.info("doGetSharedRoleListOfUser method called");
        return new String[0];
    }

    @Override
    protected void doAddRole(String s, String[] strings, boolean b) throws UserStoreException {

        log.info("doAddRole method called");

    }

    @Override
    protected void doDeleteRole(String s) throws UserStoreException {

        log.info("doDeleteRole method called");

    }

    @Override
    protected void doUpdateRoleName(String s, String s1) throws UserStoreException {
        log.info("doUpdateRoleName method called");

    }

    @Override
    protected String[] doGetRoleNames(String s, int i) throws UserStoreException {

        log.info("doGetRoleNames method called");
        return new String[]{"testrole"};
    }


    @Override
    protected String[] doGetDisplayNamesForInternalRole(String[] strings) throws UserStoreException {
        log.info("doGetDisplayNamesForInternalRole method called");
        return new String[0];
    }

    @Override
    public boolean doCheckIsUserInRole(String s, String s1) throws UserStoreException {
        log.info("doCheckIsUserInRole method called");
        return false;
    }

    @Override
    protected String[] doGetUserListOfRole(String s, String s1) throws UserStoreException {
        log.info("doGetUserListOfRole method called");
        return new String[0];
    }

    @Override
    public String[] getProfileNames(String s) throws UserStoreException {

        log.info("getProfileNames method called");
        return new String[]{"default"};
    }

    @Override
    public String[] getAllProfileNames() throws UserStoreException {
        log.info("getAllProfileNames method called");
        return new String[]{"default"};
    }

    @Override
    public int getUserId(String s) throws UserStoreException {
        log.info("getUserId method called");
        return 0;
    }

    @Override
    public Map<String, String> getProperties(Tenant tenant) throws UserStoreException {

        log.info("getProperties");

        return this.realmConfig.getUserStoreProperties();
    }

    @Override
    public boolean isBulkImportSupported() throws UserStoreException {
        log.info("isBulkImportSupported method called");
        return false;
    }



    @Override
    public Map<String, String> getProperties(org.wso2.carbon.user.api.Tenant tenant) throws org.wso2.carbon.user.api.UserStoreException {
        log.info("getProperties method called");
        return this.realmConfig.getUserStoreProperties();
    }

    @Override
    public boolean isMultipleProfilesAllowed() {
        log.info("isMultipleProfilesAllowed method called");

        return false;
    }

    @Override
    public void addRememberMe(String s, String s1) throws org.wso2.carbon.user.api.UserStoreException {
        log.info("addRememberMe method called");

    }

    @Override
    public boolean isValidRememberMeToken(String s, String s1) throws org.wso2.carbon.user.api.UserStoreException {
        log.info("isValidRememberMeToken method called");
        return false;
    }


}
