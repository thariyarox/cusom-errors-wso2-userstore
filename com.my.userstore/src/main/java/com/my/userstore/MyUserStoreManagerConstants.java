package com.my.userstore;

import org.wso2.carbon.user.api.Property;

import java.util.ArrayList;

public class MyUserStoreManagerConstants {

    //Properties for User Store Manager
    public static final ArrayList<Property> CUSTOM_UM_MANDATORY_PROPERTIES = new ArrayList<Property>();
    public static final ArrayList<Property> CUSTOM_UM_OPTIONAL_PROPERTIES = new ArrayList<Property>();
    public static final ArrayList<Property> CUSTOM_UM_ADVANCED_PROPERTIES = new ArrayList<Property>();

    public static final String SERVICE_URL_PROPERTY_NAME = "ServiceURL";
    public static final String READ_ONLY_PROPERTY_NAME = "ReadOnly";


    private static void setProperty(String name, String displayName, String value, String description) {
        Property property = new Property(name, value, displayName + "#" +description, (Property[])null);
        CUSTOM_UM_OPTIONAL_PROPERTIES.add(property);

    }

    private static void setMandatoryProperty(String name, String displayName, String value, String description) {
        Property property = new Property(name, value, displayName + "#" +description, (Property[])null);
        CUSTOM_UM_MANDATORY_PROPERTIES.add(property);

    }

    private static void setAdvancedProperty(String name, String displayName, String value, String description) {
        Property property = new Property(name, value, displayName + "#" +description, (Property[])null);
        CUSTOM_UM_ADVANCED_PROPERTIES.add(property);

    }

    static {
        //adding mandatory properties
        setMandatoryProperty(SERVICE_URL_PROPERTY_NAME, "Service URL", "https://", "location of webservice");

        //adding optional properties
        setProperty("Disabled", "Disable Userstore",  "false", "Whether user store is disabled");
        setProperty("SCIMEnabled", "Enable SCIM",  "true", "Whether SCIM is enabled for the user store");
        setProperty(READ_ONLY_PROPERTY_NAME, "Read Only",  "false", "Indicates whether the user store is in read only mode or not");
    }
}
