package com.trimlink.common.audit;

import com.trimlink.common.utils.RequestUtils;
import com.trimlink.security.AuthenticatedUser;
import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        UserRevisionEntity userRev = (UserRevisionEntity) revisionEntity;
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            userRev.setUserId(user.getUserId());
            userRev.setUsername(user.getPhone()); // phone is username in TrimLink
        } else if (auth != null) {
            userRev.setUsername(auth.getName());
        } else {
            userRev.setUsername("SYSTEM");
        }

        userRev.setIpAddress(RequestUtils.getClientIp());
    }
}
