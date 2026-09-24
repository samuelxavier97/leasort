package com.resort.platform.auth;

import com.resort.platform.prospectors.Prospector;
import com.resort.platform.prospectors.ProspectorRepository;
import com.resort.platform.users.Role;
import org.springframework.stereotype.Component;

@Component
public class ViewerResolver {

    private final ProspectorRepository prospectors;

    public ViewerResolver(ProspectorRepository prospectors) {
        this.prospectors = prospectors;
    }

    public Viewer resolve(AuthenticatedUser user) {
        if (user.role() != Role.PROSPECTOR) {
            return new Viewer(user.id(), user.role(), null);
        }
        // Todo usuário PROSPECTOR tem registro em prospectors (criado na mesma transação).
        return new Viewer(user.id(), user.role(), prospectors.findByUserId(user.id()).map(Prospector::getId).orElseThrow());
    }
}
