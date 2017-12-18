package org.wso2.carbon.sample.user.operation.event.listener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.common.AbstractUserOperationEventListener;
import com.my.userstore.MyUserStoreManager;

public class SampleUserOperationEventListener extends AbstractUserOperationEventListener {

    private static Log log = LogFactory.getLog(SampleUserOperationEventListener.class);

    private static final Log audit = CarbonConstants.AUDIT_LOG;
    @Override
    public int getExecutionOrderId() {

        //This listener should execute before the IdentityMgtEventListener
        //Hence the number should < 50
        return 1356;
    }

    @Override
    public boolean doPostAuthenticate(String userName, boolean authenticated, UserStoreManager userStoreManager) throws UserStoreException {

        if(!authenticated) {
            //user authentication has failed
            log.info("doPostAuthenticate: authentication has failed. Retrieving error message");
            String errorMessage = MyUserStoreManager.getUserAuthenticationStatus();
            log.info("error message retrieved: " + errorMessage);
            log.info("clearing thread local variable UserAuthenticationStatus");
            MyUserStoreManager.clearUserAuthenticationStatus();
            log.info("throwing exception with the error message: " + errorMessage);
            throw new UserStoreException(errorMessage);
        }

        return true;
    }


    /**
     * Get the logged in user's username who is calling the operation
     * @return username
     */

    private String getUser() {
        return CarbonContext.getThreadLocalCarbonContext().getUsername() + "@" +
                CarbonContext.getThreadLocalCarbonContext().getTenantDomain();
    }
}
