package com.my.userstore.internal;

import com.my.userstore.MyUserStoreManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.user.api.UserStoreManager;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.user.core.service.RealmService;

/**
 * @scr.component name="my.userstore.service" immediate=true
 */
public class MyUserStoreManagerServiceComponent {

    private static Log log = LogFactory.getLog(MyUserStoreManagerServiceComponent.class);

    private static RealmService realmService;

    protected void activate(ComponentContext ctxt) {

        MyUserStoreManager userStoreManager = new MyUserStoreManager();
        ctxt.getBundleContext().registerService(UserStoreManager.class.getName(), userStoreManager, null);
        log.info("MyUserStoreManager bundle activated successfully.");


    }

    protected void deactivate(ComponentContext ctxt) {
        log.info("MyUserStoreManager is deactivated");

    }
}
